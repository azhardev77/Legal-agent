package com.tenderops.visual;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenderops.config.TenderopsProperties;
import com.tenderops.document.DocumentPage;
import com.tenderops.document.DocumentPageRepository;
import com.tenderops.document.PageRenderer;
import com.tenderops.document.PdfWordBoxExtractor;
import com.tenderops.document.StorageService;
import com.tenderops.tender.TenderVersion;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Region content extraction (§3.4): 2%-padded crops for every OFFICIAL_STAMP/TABLE_REGION,
 * deterministic PDFBox-geometric table rebuild + stamp text on DIGITAL_TEXT pages
 * (source PDFBOX_GEOMETRIC), Muse-vision path when a reader is wired and image input is
 * available, honest needsHumanReview degradation otherwise. Never claims stamp/signature
 * validity or authenticity.
 */
@Service
public class RegionContentService {

  private static final Logger LOG = LoggerFactory.getLogger(RegionContentService.class);

  private final PageRenderer pageRenderer;
  private final StorageService storage;
  private final VisualRegionRepository regions;
  private final DocumentPageRepository pages;
  private final ObjectMapper objectMapper;
  private final TenderopsProperties properties;
  private final Optional<VisionRegionReader> visionReader;

  public RegionContentService(PageRenderer pageRenderer, StorageService storage,
      VisualRegionRepository regions, DocumentPageRepository pages, ObjectMapper objectMapper,
      TenderopsProperties properties, Optional<VisionRegionReader> visionReader) {
    this.pageRenderer = pageRenderer;
    this.storage = storage;
    this.regions = regions;
    this.pages = pages;
    this.objectMapper = objectMapper;
    this.properties = properties;
    this.visionReader = visionReader;
  }

  @Transactional
  public VisualRegion ensureStampContent(TenderVersion version, DocumentPage page,
      VisualRegion region, PDDocument doc) {
    byte[] crop = cropAndStore(version, page, region);
    if (DocumentPage.DIGITAL_TEXT.equals(page.getClassification())) {
      List<PdfWordBoxExtractor.WordBox> words = wordsInside(doc, page, region);
      words.sort(Comparator.comparingDouble(
          (PdfWordBoxExtractor.WordBox w) -> (w.y0() + w.y1()) / 2).reversed()
          .thenComparingDouble(PdfWordBoxExtractor.WordBox::x0));
      StringBuilder text = new StringBuilder();
      for (PdfWordBoxExtractor.WordBox word : words) {
        if (text.length() > 0) {
          text.append(' ');
        }
        text.append(word.text());
      }
      region.setRegionText(text.toString());
      region.setContentSource(VisualRegion.CONTENT_GEOMETRIC);
    } else if (!tryVisionText(region, crop)) {
      markUnreadable(page);
    }
    return regions.save(region);
  }

  @Transactional
  public VisualRegion ensureTableContent(TenderVersion version, DocumentPage page,
      VisualRegion region, PDDocument doc) {
    byte[] crop = cropAndStore(version, page, region);
    if (DocumentPage.DIGITAL_TEXT.equals(page.getClassification())) {
      List<List<String>> rows = rebuildTable(doc, page, region);
      region.setStructuredTable(toJson(rows));
      region.setContentSource(VisualRegion.CONTENT_GEOMETRIC);
    } else if (!tryVisionTable(region, crop)) {
      markUnreadable(page);
    }
    return regions.save(region);
  }

  private boolean tryVisionText(VisualRegion region, byte[] crop) {
    if (!visionAvailable() || crop == null) {
      return false;
    }
    Optional<String> text = visionReader.orElseThrow().readText(crop);
    if (text.isEmpty()) {
      return false;
    }
    region.setRegionText(text.get());
    region.setContentSource(VisualRegion.CONTENT_MUSE);
    return true;
  }

  private boolean tryVisionTable(VisualRegion region, byte[] crop) {
    if (!visionAvailable() || crop == null) {
      return false;
    }
    Optional<VisionRegionReader.StructuredTable> table =
        visionReader.orElseThrow().readTable(crop);
    if (table.isEmpty()) {
      return false;
    }
    region.setStructuredTable(toJson(table.get().rows()));
    region.setContentSource(VisualRegion.CONTENT_MUSE);
    return true;
  }

  private boolean visionAvailable() {
    return visionReader.isPresent() && properties.muse().imageInput()
        && !properties.metaApi().key().isBlank();
  }

  private void markUnreadable(DocumentPage page) {
    page.setNeedsHumanReview(true);
    page.setReviewReason(DocumentPage.OCR_UNAVAILABLE);
    pages.save(page);
    LOG.info("Page {} of version {} needs human review (no vision reader)",
        page.getPageNo(), page.getTenderVersionId());
  }

  private byte[] cropAndStore(TenderVersion version, DocumentPage page, VisualRegion region) {
    try {
      DocumentPage rendered = pageRenderer.ensureRendered(version, page);
      if (rendered.getRenderedPath() == null) {
        return null;
      }
      byte[] png = storage.load(rendered.getRenderedPath());
      BufferedImage image;
      try (ByteArrayInputStream in = new ByteArrayInputStream(png)) {
        image = ImageIO.read(in);
      }
      if (image == null) {
        return null;
      }
      int width = image.getWidth();
      int height = image.getHeight();
      int x0 = clamp((int) Math.floor(region.getX0().doubleValue() * width - 0.02 * width),
          0, width - 1);
      int y0 = clamp((int) Math.floor(region.getY0().doubleValue() * height - 0.02 * height),
          0, height - 1);
      int x1 = clamp((int) Math.ceil(region.getX1().doubleValue() * width + 0.02 * width),
          x0 + 1, width);
      int y1 = clamp((int) Math.ceil(region.getY1().doubleValue() * height + 0.02 * height),
          y0 + 1, height);
      byte[] crop = PageRenderer.toPng(image.getSubimage(x0, y0, x1 - x0, y1 - y0));
      region.setCropPath(storage.store(crop, ".png"));
      return crop;
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot crop region " + region.getId(), e);
    }
  }

  private int clamp(int value, int min, int max) {
    return Math.min(max, Math.max(min, value));
  }

  private List<PdfWordBoxExtractor.WordBox> wordsInside(PDDocument doc, DocumentPage page,
      VisualRegion region) {
    double[] box = regionBoxInPoints(doc, page, region);
    List<PdfWordBoxExtractor.WordBox> inside = new ArrayList<>();
    try {
      for (PdfWordBoxExtractor.WordBox word
          : PdfWordBoxExtractor.extract(doc, page.getPageNo() - 1)) {
        double cx = (word.x0() + word.x1()) / 2;
        double cy = (word.y0() + word.y1()) / 2;
        if (cx >= box[0] - 1 && cx <= box[2] + 1 && cy >= box[1] - 1 && cy <= box[3] + 1) {
          inside.add(word);
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot extract words", e);
    }
    return inside;
  }

  private double[] regionBoxInPoints(PDDocument doc, DocumentPage page, VisualRegion region) {
    org.apache.pdfbox.pdmodel.PDPage pdPage = doc.getPage(page.getPageNo() - 1);
    return com.tenderops.common.CoordinateUtils.normalizedToPdfBox(
        region.getX0().doubleValue(), region.getY0().doubleValue(),
        region.getX1().doubleValue(), region.getY1().doubleValue(),
        pdPage.getMediaBox().getWidth(), pdPage.getMediaBox().getHeight(),
        pdPage.getRotation());
  }

  /**
   * Deterministic table rebuild: words clustered into rows by y gaps, columns from the
   * ruling vertical lines when present (x-gap clustering fallback). All-empty rows dropped.
   */
  private List<List<String>> rebuildTable(PDDocument doc, DocumentPage page,
      VisualRegion region) {
    List<PdfWordBoxExtractor.WordBox> words = wordsInside(doc, page, region);
    if (words.isEmpty()) {
      return List.of();
    }
    List<Double> heights = words.stream()
        .map(w -> w.y1() - w.y0()).sorted().toList();
    double medianH = Math.max(1, heights.get(heights.size() / 2));
    words.sort(Comparator.comparingDouble(
        (PdfWordBoxExtractor.WordBox w) -> (w.y0() + w.y1()) / 2).reversed());
    List<List<PdfWordBoxExtractor.WordBox>> rows = new ArrayList<>();
    List<PdfWordBoxExtractor.WordBox> current = new ArrayList<>();
    double prevCenter = Double.NaN;
    for (PdfWordBoxExtractor.WordBox word : words) {
      double center = (word.y0() + word.y1()) / 2;
      if (!current.isEmpty() && Math.abs(prevCenter - center) > Math.max(4, 0.5 * medianH)) {
        rows.add(current);
        current = new ArrayList<>();
      }
      current.add(word);
      prevCenter = current.size() == 1 ? center
          : (prevCenter * (current.size() - 1) + center) / current.size();
    }
    if (!current.isEmpty()) {
      rows.add(current);
    }
    List<Double> boundaries = columnBoundaries(doc, page, region);
    List<List<String>> table = new ArrayList<>();
    int cols = Math.max(1, boundaries.size() + 1);
    for (List<PdfWordBoxExtractor.WordBox> row : rows) {
      row.sort(Comparator.comparingDouble(PdfWordBoxExtractor.WordBox::x0));
      List<StringBuilder> cells = new ArrayList<>();
      for (int c = 0; c < cols; c++) {
        cells.add(new StringBuilder());
      }
      for (PdfWordBoxExtractor.WordBox word : row) {
        int col = 0;
        while (col < boundaries.size()
            && (word.x0() + word.x1()) / 2 > boundaries.get(col)) {
          col++;
        }
        StringBuilder cell = cells.get(col);
        if (cell.length() > 0) {
          cell.append(' ');
        }
        cell.append(word.text());
      }
      List<String> texts =
          cells.stream().map(cell -> cell.toString().trim()).toList();
      if (texts.stream().anyMatch(text -> !text.isEmpty())) {
        table.add(texts);
      }
    }
    return table;
  }

  /** Column boundaries from vertical ruling lines inside the region (x-gap fallback). */
  private List<Double> columnBoundaries(PDDocument doc, DocumentPage page,
      VisualRegion region) {
    double[] box = regionBoxInPoints(doc, page, region);
    double rx0 = box[0];
    double rx1 = box[2];
    List<Double> verticals = new ArrayList<>();
    try {
      for (VectorPathScan.VectorPath path
          : VectorPathScan.scan(doc.getPage(page.getPageNo() - 1))) {
        if (path.hasCurve() || path.points().size() < 2) {
          continue;
        }
        List<double[]> pts = path.points();
        for (int i = 1; i < pts.size(); i++) {
          double dx = Math.abs(pts.get(i)[0] - pts.get(i - 1)[0]);
          double dy = Math.abs(pts.get(i)[1] - pts.get(i - 1)[1]);
          double x = (pts.get(i)[0] + pts.get(i - 1)[0]) / 2;
          if (dx <= 1.5 && dy >= 15 && x > rx0 + 5 && x < rx1 - 5) {
            boolean distinct = true;
            for (double v : verticals) {
              if (Math.abs(v - x) <= 3) {
                distinct = false;
                break;
              }
            }
            if (distinct) {
              verticals.add(x);
            }
          }
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot scan vectors", e);
    }
    verticals.sort(Double::compareTo);
    if (!verticals.isEmpty()) {
      return verticals;
    }
    List<PdfWordBoxExtractor.WordBox> words = wordsInside(doc, page, region);
    List<Double> starts = words.stream().map(PdfWordBoxExtractor.WordBox::x0).sorted()
        .toList();
    List<Double> gaps = new ArrayList<>();
    for (int i = 1; i < starts.size(); i++) {
      if (starts.get(i) - starts.get(i - 1) > 15) {
        gaps.add((starts.get(i) + starts.get(i - 1)) / 2);
      }
    }
    return gaps;
  }

  private String toJson(List<List<String>> rows) {
    try {
      Map<String, Object> json = new LinkedHashMap<>();
      json.put("rows", rows);
      return objectMapper.writeValueAsString(json);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot serialize table", e);
    }
  }
}
