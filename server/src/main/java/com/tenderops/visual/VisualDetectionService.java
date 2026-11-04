package com.tenderops.visual;

import com.tenderops.common.FileSafetyValidator;
import com.tenderops.config.TenderopsProperties;
import com.tenderops.document.DocumentPage;
import com.tenderops.document.DocumentPageRepository;
import com.tenderops.document.PageImageScan;
import com.tenderops.document.PageRenderer;
import com.tenderops.document.StorageService;
import com.tenderops.tender.TenderVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Page-level visual detection orchestration: SAM candidate selection (§3.3), live SAM with
 * geometric fallback, oversized-region filtering, persistence, samMode bookkeeping (H3).
 */
@Service
public class VisualDetectionService {

  private static final Logger LOG = LoggerFactory.getLogger(VisualDetectionService.class);
  private static final Pattern CANDIDATE_KEYWORDS =
      Pattern.compile("signatur|annexure|\\bstamp\\b|\\bseal\\b", Pattern.CASE_INSENSITIVE);

  private final TenderopsProperties properties;
  private final Sam31Detector samDetector;
  private final GeometricFallbackDetector fallbackDetector;
  private final PageRenderer pageRenderer;
  private final DocumentPageRepository pages;
  private final VisualRegionRepository regions;
  private final StorageService storage;
  private final AiCallLogger callLogger;

  public VisualDetectionService(TenderopsProperties properties, Sam31Detector samDetector,
      GeometricFallbackDetector fallbackDetector, PageRenderer pageRenderer,
      DocumentPageRepository pages, VisualRegionRepository regions, StorageService storage,
      AiCallLogger callLogger) {
    this.properties = properties;
    this.samDetector = samDetector;
    this.fallbackDetector = fallbackDetector;
    this.pageRenderer = pageRenderer;
    this.pages = pages;
    this.regions = regions;
    this.storage = storage;
    this.callLogger = callLogger;
  }

  public record DetectResult(long versionId, String samMode, List<VisualRegion> regions) {}

  @Transactional
  public DetectResult detectVersion(TenderVersion version) {
    byte[] pdf = storage.load(version.getStoragePath());
    List<VisualRegion> all = new ArrayList<>();
    boolean samLive = false;
    try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(pdf))) {
      for (DocumentPage page : pages.findByTenderVersionIdOrderByPageNo(version.getId())) {
        PageOutcome outcome = detectPage(version, page, doc);
        samLive |= outcome.samLive();
        all.addAll(outcome.regions());
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot reload PDF for detection", e);
    }
    return new DetectResult(version.getId(), samLive ? "LIVE" : "FALLBACK", all);
  }

  private record PageOutcome(boolean samLive, List<VisualRegion> regions) {}

  private PageOutcome detectPage(TenderVersion version, DocumentPage page, PDDocument doc) {
    int pageIndex0 = page.getPageNo() - 1;
    PDPage pdPage = doc.getPage(pageIndex0);
    boolean candidate;
    try {
      candidate = isCandidatePage(pdPage, page.getTextContent());
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot scan page " + page.getPageNo(), e);
    }
    List<VisualRegionDetector.Detection> detections;
    boolean samLive = false;
    String key = properties.metaApi().key();
    if (candidate && key != null && !key.isBlank()) {
      DocumentPage rendered = pageRenderer.ensureRendered(version, page);
      if (rendered.getRenderedPath() != null) {
        byte[] png = storage.load(rendered.getRenderedPath());
        String pageSha = FileSafetyValidator.sha256Hex(png);
        try {
          detections = samDetector.detect(doc, pageIndex0, png, rendered.getImageWidth(),
              rendered.getImageHeight(), pageSha);
          samLive = true;
        } catch (IOException e) {
          throw new UncheckedIOException("SAM detection I/O failure", e);
        } catch (SamException e) {
          LOG.warn("SAM failed on version {} page {}; geometric fallback: {}",
              version.getId(), page.getPageNo(), e.getMessage());
          callLogger.log(null, "SAM", properties.metaApi().samModel(), "sam-detect:fallback",
              "FALLBACK_USED", 1, null, "FALLBACK", e.getMessage());
          detections = geometric(doc, pageIndex0);
        }
      } else {
        detections = geometric(doc, pageIndex0);
      }
    } else {
      detections = geometric(doc, pageIndex0);
    }
    List<VisualRegion> persisted = new ArrayList<>();
    for (VisualRegionDetector.Detection detection
        : RegionFilter.discardOversized(detections)) {
      VisualRegion region = new VisualRegion(page.getId(), detection.regionType(),
          scale6(detection.x0()), scale6(detection.y0()), scale6(detection.x1()),
          scale6(detection.y1()), scale4(detection.confidence()), detection.source());
      region.setMaskRef(detection.maskRef());
      persisted.add(regions.save(region));
    }
    return new PageOutcome(samLive, persisted);
  }

  private List<VisualRegionDetector.Detection> geometric(PDDocument doc, int pageIndex0) {
    try {
      return fallbackDetector.detect(doc, pageIndex0, null, 0, 0, null);
    } catch (IOException e) {
      throw new UncheckedIOException("Geometric detection failed", e);
    }
  }

  /**
   * SAM candidate pages (§3.3): pages with images, signature/annexure/stamp keywords,
   * or table-like ruling layout.
   */
  private boolean isCandidatePage(PDPage pdPage, String text) throws IOException {
    if (PageImageScan.scan(pdPage).hasImages()) {
      return true;
    }
    if (text != null && CANDIDATE_KEYWORDS.matcher(text).find()) {
      return true;
    }
    int horizontals = 0;
    int verticals = 0;
    for (VectorPathScan.VectorPath path : VectorPathScan.scan(pdPage)) {
      if (path.hasCurve() || path.points().size() < 2) {
        continue;
      }
      List<double[]> pts = path.points();
      for (int i = 1; i < pts.size(); i++) {
        double dx = Math.abs(pts.get(i)[0] - pts.get(i - 1)[0]);
        double dy = Math.abs(pts.get(i)[1] - pts.get(i - 1)[1]);
        double len = Math.hypot(dx, dy);
        if (len >= 30 && dy <= 1.5) {
          horizontals++;
        } else if (len >= 30 && dx <= 1.5) {
          verticals++;
        }
      }
    }
    return horizontals >= 2 && verticals >= 1;
  }

  private BigDecimal scale6(double value) {
    return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
  }

  private BigDecimal scale4(double value) {
    return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
  }
}
