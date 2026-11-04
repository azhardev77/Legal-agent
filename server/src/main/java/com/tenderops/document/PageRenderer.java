package com.tenderops.document;

import com.tenderops.config.TenderopsProperties;
import com.tenderops.tender.TenderVersion;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Page renderer (configurable DPI, default 150) with storage-backed PNG persistence. */
@Service
public class PageRenderer {

  private static final Logger LOG = LoggerFactory.getLogger(PageRenderer.class);

  /** Review reason when a page cannot be rendered (degrades; never crashes the pipeline). */
  public static final String RENDER_FAILED = "RENDER_FAILED";

  private final TenderopsProperties properties;
  private final StorageService storage;
  private final DocumentPageRepository pages;

  public PageRenderer(TenderopsProperties properties, StorageService storage,
      DocumentPageRepository pages) {
    this.properties = properties;
    this.storage = storage;
    this.pages = pages;
  }

  /** Pure render of one page (0-based) at the given DPI. */
  public static BufferedImage render(PDDocument doc, int pageIndex0, int dpi)
      throws IOException {
    return new PDFRenderer(doc).renderImageWithDPI(pageIndex0, dpi, ImageType.RGB);
  }

  public static byte[] toPng(BufferedImage image) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, "png", out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot encode PNG", e);
    }
  }

  /**
   * Renders the page, stores the PNG, and records path + dimensions on the page row.
   * A page that cannot be rendered is flagged for human review and returned unrendered;
   * callers must tolerate a null rendered path (geometric detection needs no PNG).
   */
  public DocumentPage ensureRendered(TenderVersion version, DocumentPage page) {
    if (page.getRenderedPath() != null) {
      return page;
    }
    try {
      byte[] pdf = storage.load(version.getStoragePath());
      try (PDDocument doc = Loader.loadPDF(pdf)) {
        BufferedImage image = render(doc, page.getPageNo() - 1, properties.render().dpi());
        String path = storage.store(toPng(image), ".png");
        page.setRenderedPath(path);
        page.setImageWidth(image.getWidth());
        page.setImageHeight(image.getHeight());
        return pages.save(page);
      }
    } catch (IOException | RuntimeException e) {
      LOG.warn("Render failed for version {} page {}; flagged for human review: {}",
          version.getId(), page.getPageNo(), e.toString());
      if (!page.isNeedsHumanReview()) {
        page.setNeedsHumanReview(true);
        page.setReviewReason(RENDER_FAILED);
      }
      return pages.save(page);
    }
  }
}
