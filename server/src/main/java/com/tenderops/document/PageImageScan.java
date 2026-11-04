package com.tenderops.document;

import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * One vector-pass scan of a page's images: presence + largest single-image area fraction.
 * Used by the classifier (image &gt; 60% rule) and SAM candidate-page selection (§3.3).
 * Tracks its own CTM (PDFBox 3's bare engine keeps none).
 */
public final class PageImageScan {

  public record Result(boolean hasImages, double maxFraction) {}

  private PageImageScan() {}

  public static Result scan(PDPage page) throws IOException {
    Collector collector = new Collector();
    collector.processPage(page);
    float pageW = page.getMediaBox().getWidth();
    float pageH = page.getMediaBox().getHeight();
    double pageArea = Math.max(1.0, (double) pageW * pageH);
    double max = 0.0;
    for (double area : collector.areas) {
      max = Math.max(max, Math.min(1.0, area / pageArea));
    }
    return new Result(!collector.areas.isEmpty(), max);
  }

  private static class Collector extends PDFStreamEngine {
    final List<Double> areas = new ArrayList<>();
    final Deque<AffineTransform> stack = new ArrayDeque<>();

    Collector() {
      stack.push(new AffineTransform());
    }

    @Override
    protected void processOperator(Operator operator, List<COSBase> operands)
        throws IOException {
      String name = operator.getName();
      switch (name) {
        case "q" -> stack.push(new AffineTransform(stack.peek()));
        case "Q" -> {
          if (stack.size() > 1) {
            stack.pop();
          }
        }
        case "cm" -> stack.peek().concatenate(new AffineTransform(
            num(operands, 0), num(operands, 1), num(operands, 2), num(operands, 3),
            num(operands, 4), num(operands, 5)));
        case "Do" -> drawObject(operands);
        default -> {
        }
      }
    }

    private double num(List<COSBase> operands, int index) {
      if (index < operands.size() && operands.get(index) instanceof COSNumber number) {
        return number.floatValue();
      }
      return 0;
    }

    private void drawObject(List<COSBase> operands) throws IOException {
      if (operands.isEmpty() || !(operands.get(0) instanceof COSName name)) {
        return;
      }
      PDXObject xobject = getResources().getXObject(name);
      if (xobject instanceof PDImageXObject) {
        AffineTransform ctm = stack.peek();
        double det = ctm.getScaleX() * ctm.getScaleY()
            - ctm.getShearX() * ctm.getShearY();
        areas.add(Math.abs(det));
      } else if (xobject instanceof PDFormXObject form) {
        stack.push(new AffineTransform(stack.peek()));
        stack.peek().concatenate(form.getMatrix().createAffineTransform());
        try {
          showForm(form);
        } finally {
          if (stack.size() > 1) {
            stack.pop();
          }
        }
      }
    }
  }
}
