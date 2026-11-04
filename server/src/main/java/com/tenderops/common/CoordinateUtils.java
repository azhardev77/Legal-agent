package com.tenderops.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * PDFBox bottom-left points to normalized top-left [0,1] boxes, rotation-aware (TC-ING-05).
 * PDFBox text positions live in unrotated user space; this maps them onto the rendered image
 * (which the viewer displays), so overlays line up after /Rotate is applied.
 */
public final class CoordinateUtils {

  public record NormalizedBox(BigDecimal x0, BigDecimal y0, BigDecimal x1, BigDecimal y1) {
    public BigDecimal area() {
      return x1.subtract(x0).multiply(y1.subtract(y0));
    }
  }

  private CoordinateUtils() {}

  /**
   * @param x,y,w,h box in PDFBox user-space points (bottom-left origin, unrotated)
   * @param pageW,pageH media-box size in points (unrotated)
   * @param rotation page /Rotate (0/90/180/270)
   */
  public static NormalizedBox pdfBoxToNormalized(double x, double y, double w, double h,
      double pageW, double pageH, int rotation) {
    int r = ((rotation % 360) + 360) % 360;
    if (r != 0 && r != 90 && r != 180 && r != 270) {
      throw new IllegalArgumentException("Unsupported rotation: " + rotation);
    }
    double renderedW = (r == 90 || r == 270) ? pageH : pageW;
    double renderedH = (r == 90 || r == 270) ? pageW : pageH;
    double[] xs = new double[2];
    double[] ys = new double[2];
    double[][] corners = {{x, y}, {x + w, y + h}};
    for (int i = 0; i < 2; i++) {
      double rx;
      double ry;
      double px = corners[i][0];
      double py = corners[i][1];
      switch (r) {
        case 90 -> { rx = pageH - py; ry = px; }
        case 180 -> { rx = pageW - px; ry = pageH - py; }
        case 270 -> { rx = py; ry = pageW - px; }
        default -> { rx = px; ry = py; }
      }
      xs[i] = rx / renderedW;
      ys[i] = 1.0 - ry / renderedH;
    }
    return new NormalizedBox(scale(Math.min(xs[0], xs[1])), scale(Math.min(ys[0], ys[1])),
        scale(Math.max(xs[0], xs[1])), scale(Math.max(ys[0], ys[1])));
  }

  private static BigDecimal scale(double v) {
    double clamped = Math.min(1.0, Math.max(0.0, v));
    return BigDecimal.valueOf(clamped).setScale(6, RoundingMode.HALF_UP);
  }

  /**
   * Inverse of {@link #pdfBoxToNormalized}: normalized top-left box back to unrotated
   * user-space points (bottom-left origin). Returns {x0, y0, x1, y1}.
   */
  public static double[] normalizedToPdfBox(double nx0, double ny0, double nx1, double ny1,
      double pageW, double pageH, int rotation) {
    int r = ((rotation % 360) + 360) % 360;
    if (r != 0 && r != 90 && r != 180 && r != 270) {
      throw new IllegalArgumentException("Unsupported rotation: " + rotation);
    }
    double renderedW = (r == 90 || r == 270) ? pageH : pageW;
    double renderedH = (r == 90 || r == 270) ? pageW : pageH;
    double[][] corners = new double[4][2];
    double[] nxs = {nx0, nx1};
    double[] nys = {ny0, ny1};
    int k = 0;
    for (double nx : nxs) {
      for (double ny : nys) {
        double rx = nx * renderedW;
        double ry = (1.0 - ny) * renderedH;
        double px;
        double py;
        switch (r) {
          case 90 -> { px = ry; py = pageH - rx; }
          case 180 -> { px = pageW - rx; py = pageH - ry; }
          case 270 -> { px = pageW - ry; py = rx; }
          default -> { px = rx; py = ry; }
        }
        corners[k][0] = px;
        corners[k][1] = py;
        k++;
      }
    }
    double x0 = Math.min(Math.min(corners[0][0], corners[1][0]),
        Math.min(corners[2][0], corners[3][0]));
    double y0 = Math.min(Math.min(corners[0][1], corners[1][1]),
        Math.min(corners[2][1], corners[3][1]));
    double x1 = Math.max(Math.max(corners[0][0], corners[1][0]),
        Math.max(corners[2][0], corners[3][0]));
    double y1 = Math.max(Math.max(corners[0][1], corners[1][1]),
        Math.max(corners[2][1], corners[3][1]));
    return new double[] {x0, y0, x1, y1};
  }
}
