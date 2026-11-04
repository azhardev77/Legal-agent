package com.tenderops.document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Position-sorted word boxes in PDF points, bottom-left origin. Used for geometric table
 * rebuilds, stamp text extraction and label-relative signature detection.
 */
public final class PdfWordBoxExtractor {

  public record WordBox(String text, double x0, double y0, double x1, double y1) {}

  private PdfWordBoxExtractor() {}

  public static List<WordBox> extract(PDDocument doc, int pageIndex0) throws IOException {
    double pageHeight = doc.getPage(pageIndex0).getMediaBox().getHeight();
    Collector collector = new Collector(pageHeight);
    collector.setSortByPosition(true);
    collector.setStartPage(pageIndex0 + 1);
    collector.setEndPage(pageIndex0 + 1);
    collector.getText(doc);
    return collector.words();
  }

  private static class Collector extends PDFTextStripper {
    /**
     * Unrotated media-box height. PDFBox's YDirAdj is TOP-origin in unrotated user space
     * for every page rotation (verified: getYDirAdj = pageHeight - translateY for normal
     * text; rotated probes agree), so boxes flip back to bottom-left origin here.
     */
    private final double pageHeight;
    private final List<WordBox> words = new ArrayList<>();
    private final StringBuilder current = new StringBuilder();
    private double cx0;
    private double cy0;
    private double cx1;
    private double cy1;
    private double prevX;
    private double prevY;
    private double prevW;
    private double prevH;
    private boolean hasPrev;

    Collector(double pageHeight) throws IOException {
      super();
      this.pageHeight = pageHeight;
    }

    List<WordBox> words() {
      flush();
      List<WordBox> flipped = new ArrayList<>(words.size());
      for (WordBox word : words) {
        flipped.add(new WordBox(word.text(), word.x0(), pageHeight - word.y1(),
            word.x1(), pageHeight - word.y0()));
      }
      return flipped;
    }

    @Override
    protected void processTextPosition(TextPosition pos) {
      String unicode = pos.getUnicode();
      if (unicode == null || unicode.isBlank()) {
        flush();
        hasPrev = false;
        return;
      }
      float x = pos.getXDirAdj();
      float y = pos.getYDirAdj();
      float w = pos.getWidthDirAdj();
      float h = Math.max(1f, pos.getHeightDir());
      if (hasPrev) {
        boolean sameLine = Math.abs(y - prevY) <= Math.max(h, prevH) * 0.6;
        double gap = x - (prevX + prevW);
        boolean wordBreak = !sameLine || gap > Math.max(prevW, 1.0) * 0.35;
        if (wordBreak) {
          flush();
        }
      }
      if (current.length() == 0) {
        cx0 = x;
        cy0 = y - 0.3 * h;
        cx1 = x + w;
        cy1 = y + 0.8 * h;
      } else {
        cx0 = Math.min(cx0, x);
        cy0 = Math.min(cy0, y - 0.3 * h);
        cx1 = Math.max(cx1, x + w);
        cy1 = Math.max(cy1, y + 0.8 * h);
      }
      current.append(unicode);
      prevX = x;
      prevY = y;
      prevW = w;
      prevH = h;
      hasPrev = true;
    }

    private void flush() {
      if (current.length() > 0) {
        words.add(new WordBox(current.toString(), cx0, cy0, cx1, cy1));
        current.setLength(0);
      }
    }
  }
}
