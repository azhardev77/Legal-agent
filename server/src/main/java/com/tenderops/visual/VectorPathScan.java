package com.tenderops.visual;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
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

/**
 * Collects painted vector paths of a page in user-space points: point runs (including curve
 * control points), curve/closed flags, stroking/fill colors normalized to RGB, line width.
 *
 * <p>Tracks its own graphics state (PDFBox 3's bare {@link PDFStreamEngine} registers no
 * state operators, so {@code super} would leave color/width/CTM at defaults).
 */
public final class VectorPathScan {

  public record VectorPath(
      List<double[]> points,
      boolean hasCurve,
      boolean closed,
      float lineWidth,
      float[] strokeRgb,
      float[] fillRgb,
      double x0, double y0, double x1, double y1,
      double pathLength) {}

  private VectorPathScan() {}

  public static List<VectorPath> scan(PDPage page) throws IOException {
    Collector collector = new Collector();
    collector.processPage(page);
    return collector.paths;
  }

  private static final class GState {
    float lineWidth = 1.0f;
    float[] strokeRgb = {0f, 0f, 0f};
    float[] fillRgb = {0f, 0f, 0f};
    final AffineTransform ctm = new AffineTransform();

    GState copy() {
      GState copy = new GState();
      copy.lineWidth = lineWidth;
      copy.strokeRgb = strokeRgb == null ? null : strokeRgb.clone();
      copy.fillRgb = fillRgb == null ? null : fillRgb.clone();
      copy.ctm.setTransform(ctm);
      return copy;
    }
  }

  private static class Collector extends PDFStreamEngine {
    final List<VectorPath> paths = new ArrayList<>();
    final List<double[]> current = new ArrayList<>();
    final Deque<GState> stack = new ArrayDeque<>();
    double startX;
    double startY;
    double curX;
    double curY;
    boolean hasCurrent;
    boolean hasCurve;
    boolean closed;

    Collector() {
      stack.push(new GState());
    }

    private GState state() {
      return stack.peek();
    }

    @Override
    protected void processOperator(Operator operator, List<COSBase> operands)
        throws IOException {
      String name = operator.getName();
      switch (name) {
        case "m" -> moveTo(num(operands, 0), num(operands, 1));
        case "l" -> lineTo(num(operands, 0), num(operands, 1));
        case "c" -> curveTo(num(operands, 0), num(operands, 1), num(operands, 2),
            num(operands, 3), num(operands, 4), num(operands, 5));
        case "v" -> curveTo(curX, curY, num(operands, 0), num(operands, 1), num(operands, 2),
            num(operands, 3));
        case "y" -> curveTo(num(operands, 0), num(operands, 1), num(operands, 2),
            num(operands, 3), num(operands, 2), num(operands, 3));
        case "h" -> closePath();
        case "re" -> rect(num(operands, 0), num(operands, 1), num(operands, 2), num(operands, 3));
        case "S", "s", "f", "F", "f*", "B", "B*", "b", "b*" -> paint();
        case "n" -> reset();
        case "q" -> stack.push(state().copy());
        case "Q" -> {
          if (stack.size() > 1) {
            stack.pop();
          }
        }
        case "w" -> state().lineWidth = (float) num(operands, 0);
        case "G" -> state().strokeRgb = gray(num(operands, 0));
        case "g" -> state().fillRgb = gray(num(operands, 0));
        case "RG" -> state().strokeRgb = rgb(num(operands, 0), num(operands, 1),
            num(operands, 2));
        case "rg" -> state().fillRgb = rgb(num(operands, 0), num(operands, 1),
            num(operands, 2));
        case "K" -> state().strokeRgb = cmyk(num(operands, 0), num(operands, 1),
            num(operands, 2), num(operands, 3));
        case "k" -> state().fillRgb = cmyk(num(operands, 0), num(operands, 1), num(operands, 2),
            num(operands, 3));
        case "CS" -> state().strokeRgb = null;
        case "cs" -> state().fillRgb = null;
        case "SC", "SCN" -> state().strokeRgb = components(operands);
        case "sc", "scn" -> state().fillRgb = components(operands);
        case "cm" -> state().ctm.concatenate(new AffineTransform(
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

    private float[] gray(double g) {
      float v = (float) g;
      return new float[] {v, v, v};
    }

    private float[] rgb(double r, double g, double b) {
      return new float[] {(float) r, (float) g, (float) b};
    }

    private float[] cmyk(double c, double m, double y, double k) {
      return new float[] {(float) ((1 - c) * (1 - k)), (float) ((1 - m) * (1 - k)),
          (float) ((1 - y) * (1 - k))};
    }

    private float[] components(List<COSBase> operands) {
      List<Double> numbers = new ArrayList<>();
      for (COSBase operand : operands) {
        if (operand instanceof COSNumber number) {
          numbers.add((double) number.floatValue());
        } else {
          return null;
        }
      }
      if (numbers.size() == 1) {
        return gray(numbers.get(0));
      }
      if (numbers.size() == 3) {
        return rgb(numbers.get(0), numbers.get(1), numbers.get(2));
      }
      if (numbers.size() == 4) {
        return cmyk(numbers.get(0), numbers.get(1), numbers.get(2), numbers.get(3));
      }
      return null;
    }

    private void drawObject(List<COSBase> operands) throws IOException {
      if (operands.isEmpty() || !(operands.get(0) instanceof COSName name)) {
        return;
      }
      PDXObject xobject = getResources().getXObject(name);
      if (xobject instanceof PDFormXObject form) {
        stack.push(state().copy());
        state().ctm.concatenate(form.getMatrix().createAffineTransform());
        try {
          showForm(form);
        } finally {
          if (stack.size() > 1) {
            stack.pop();
          }
        }
      }
    }

    private double[] transform(double x, double y) {
      Point2D out = state().ctm.transform(new Point2D.Double(x, y), null);
      return new double[] {out.getX(), out.getY()};
    }

    private void moveTo(double x, double y) {
      double[] p = transform(x, y);
      curX = p[0];
      curY = p[1];
      if (!hasCurrent) {
        startX = curX;
        startY = curY;
      }
      current.add(p);
      hasCurrent = true;
    }

    private void lineTo(double x, double y) {
      if (!hasCurrent) {
        moveTo(x, y);
        return;
      }
      double[] p = transform(x, y);
      current.add(p);
      curX = p[0];
      curY = p[1];
    }

    private void curveTo(double x1, double y1, double x2, double y2, double x3, double y3) {
      if (!hasCurrent) {
        moveTo(x1, y1);
      }
      current.add(transform(x1, y1));
      current.add(transform(x2, y2));
      double[] end = transform(x3, y3);
      current.add(end);
      curX = end[0];
      curY = end[1];
      hasCurve = true;
    }

    private void closePath() {
      if (hasCurrent) {
        current.add(new double[] {startX, startY});
        curX = startX;
        curY = startY;
        closed = true;
      }
    }

    private void rect(double x, double y, double w, double h) {
      moveTo(x, y);
      lineTo(x + w, y);
      lineTo(x + w, y + h);
      lineTo(x, y + h);
      closePath();
    }

    private void paint() {
      if (!current.isEmpty()) {
        double x0 = Double.MAX_VALUE;
        double y0 = Double.MAX_VALUE;
        double x1 = -Double.MAX_VALUE;
        double y1 = -Double.MAX_VALUE;
        double length = 0;
        double[] prev = null;
        for (double[] p : current) {
          x0 = Math.min(x0, p[0]);
          y0 = Math.min(y0, p[1]);
          x1 = Math.max(x1, p[0]);
          y1 = Math.max(y1, p[1]);
          if (prev != null) {
            length += Math.hypot(p[0] - prev[0], p[1] - prev[1]);
          }
          prev = p;
        }
        GState state = state();
        paths.add(new VectorPath(new ArrayList<>(current), hasCurve, closed,
            state.lineWidth,
            state.strokeRgb == null ? null : state.strokeRgb.clone(),
            state.fillRgb == null ? null : state.fillRgb.clone(),
            x0, y0, x1, y1, length));
      }
      reset();
    }

    private void reset() {
      current.clear();
      hasCurrent = false;
      hasCurve = false;
      closed = false;
    }
  }
}
