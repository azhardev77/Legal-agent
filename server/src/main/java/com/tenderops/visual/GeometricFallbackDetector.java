package com.tenderops.visual;

import com.tenderops.common.CoordinateUtils;
import com.tenderops.document.PdfWordBoxExtractor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.springframework.stereotype.Component;

/**
 * Heuristic region detection on PDFBox vector content + word positions. General rules only:
 * ruling-line grids (tables), colored near-circular curved paths (stamps), thin wavy dark
 * strokes near a "Signature" label (signatures). Never reads generator ground truth (CL-23).
 */
@Component
public class GeometricFallbackDetector implements VisualRegionDetector {

  private static final double TABLE_CONFIDENCE = 0.65;
  private static final double STAMP_CONFIDENCE = 0.60;
  private static final double SIGNATURE_CONFIDENCE = 0.55;

  @Override
  public List<Detection> detect(PDDocument doc, int pageIndex0, byte[] renderedPng,
      int imageWidth, int imageHeight, String pageSha256) throws IOException {
    PDPage page = doc.getPage(pageIndex0);
    List<VectorPathScan.VectorPath> paths = VectorPathScan.scan(page);
    List<PdfWordBoxExtractor.WordBox> words = PdfWordBoxExtractor.extract(doc, pageIndex0);
    double pageW = page.getMediaBox().getWidth();
    double pageH = page.getMediaBox().getHeight();
    int rotation = page.getRotation();

    List<Detection> out = new ArrayList<>();
    for (double[] box : findTableBoxes(paths)) {
      out.add(normalized(VisualRegion.TABLE_REGION, box, TABLE_CONFIDENCE, pageW, pageH,
          rotation));
    }
    for (double[] box : findStampBoxes(paths)) {
      out.add(normalized(VisualRegion.OFFICIAL_STAMP, box, STAMP_CONFIDENCE, pageW, pageH,
          rotation));
    }
    for (double[] box : findSignatureBoxes(paths, words)) {
      out.add(normalized(VisualRegion.HANDWRITTEN_SIGNATURE, box, SIGNATURE_CONFIDENCE, pageW,
          pageH, rotation));
    }
    return RegionFilter.discardOversized(out);
  }

  private Detection normalized(String type, double[] box, double confidence, double pageW,
      double pageH, int rotation) {
    CoordinateUtils.NormalizedBox b = CoordinateUtils.pdfBoxToNormalized(
        box[0], box[1], box[2] - box[0], box[3] - box[1], pageW, pageH, rotation);
    return new Detection(type, b.x0().doubleValue(), b.y0().doubleValue(),
        b.x1().doubleValue(), b.y1().doubleValue(), confidence,
        VisualRegion.SOURCE_FALLBACK);
  }

  /** Straight segments of curve-free paths, each as {x0, y0, x1, y1}. */
  private List<double[]> straightSegments(List<VectorPathScan.VectorPath> paths) {
    List<double[]> segs = new ArrayList<>();
    for (VectorPathScan.VectorPath path : paths) {
      if (path.hasCurve() || path.points().size() < 2) {
        continue;
      }
      List<double[]> pts = path.points();
      for (int i = 1; i < pts.size(); i++) {
        double[] a = pts.get(i - 1);
        double[] b = pts.get(i);
        if (Math.hypot(b[0] - a[0], b[1] - a[1]) >= 15) {
          segs.add(new double[] {a[0], a[1], b[0], b[1]});
        }
      }
    }
    return segs;
  }

  /**
   * Tables: >= 3 horizontal ruling lines with mutually overlapping spans, crossed by >= 2
   * vertical rulings spanning at least half the row band.
   */
  private List<double[]> findTableBoxes(List<VectorPathScan.VectorPath> paths) {
    List<double[]> horizontals = new ArrayList<>();
    List<double[]> verticals = new ArrayList<>();
    for (double[] s : straightSegments(paths)) {
      double dx = Math.abs(s[2] - s[0]);
      double dy = Math.abs(s[3] - s[1]);
      if (dy <= 1.5 && dx >= 15) {
        horizontals.add(new double[] {Math.min(s[0], s[2]), Math.max(s[0], s[2]),
            (s[1] + s[3]) / 2});
      } else if (dx <= 1.5 && dy >= 15) {
        verticals.add(new double[] {(s[0] + s[2]) / 2, Math.min(s[1], s[3]),
            Math.max(s[1], s[3])});
      }
    }
    horizontals.sort(Comparator.comparingDouble(h -> h[2]));
    List<List<double[]>> bands = new ArrayList<>();
    for (double[] h : horizontals) {
      List<double[]> band = null;
      for (List<double[]> candidate : bands) {
        if (Math.abs(candidate.get(0)[2] - h[2]) <= 3) {
          band = candidate;
          break;
        }
      }
      if (band == null) {
        band = new ArrayList<>();
        bands.add(band);
      }
      band.add(h);
    }
    List<double[]> merged = new ArrayList<>();
    for (List<double[]> band : bands) {
      double x0 = Double.MAX_VALUE;
      double x1 = -Double.MAX_VALUE;
      for (double[] h : band) {
        x0 = Math.min(x0, h[0]);
        x1 = Math.max(x1, h[1]);
      }
      merged.add(new double[] {x0, x1, band.get(0)[2]});
    }
    List<double[]> boxes = new ArrayList<>();
    if (merged.size() < 3) {
      return boxes;
    }
    double[] spanX0 = merged.stream().mapToDouble(m -> m[0]).sorted().toArray();
    double[] spanX1 = merged.stream().mapToDouble(m -> m[1]).sorted().toArray();
    double medX0 = spanX0[spanX0.length / 2];
    double medX1 = spanX1[spanX1.length / 2];
    List<double[]> rows = new ArrayList<>();
    for (double[] m : merged) {
      double overlap = Math.min(m[1], medX1) - Math.max(m[0], medX0);
      if (overlap >= 0.5 * (m[1] - m[0])) {
        rows.add(m);
      }
    }
    if (rows.size() < 3) {
      return boxes;
    }
    double yTop = rows.stream().mapToDouble(r -> r[2]).max().orElse(0);
    double yBot = rows.stream().mapToDouble(r -> r[2]).min().orElse(0);
    List<Double> crossings = new ArrayList<>();
    for (double[] v : verticals) {
      if (v[0] >= medX0 - 5 && v[0] <= medX1 + 5 && (v[2] - v[1]) >= 0.5 * (yTop - yBot)) {
        boolean distinct = true;
        for (double x : crossings) {
          if (Math.abs(x - v[0]) <= 5) {
            distinct = false;
            break;
          }
        }
        if (distinct) {
          crossings.add(v[0]);
        }
      }
    }
    if (crossings.size() >= 2 && medX1 - medX0 >= 20 && yTop - yBot >= 20) {
      boxes.add(new double[] {medX0, yBot, medX1, yTop});
    }
    return boxes;
  }

  /** Stamps: groups of overlapping colored near-circular curved paths. */
  private List<double[]> findStampBoxes(List<VectorPathScan.VectorPath> paths) {
    List<double[]> circles = new ArrayList<>();
    for (VectorPathScan.VectorPath path : paths) {
      if (!path.hasCurve() || path.strokeRgb() == null) {
        continue;
      }
      float[] rgb = path.strokeRgb();
      double spread = Math.max(rgb[0], Math.max(rgb[1], rgb[2]))
          - Math.min(rgb[0], Math.min(rgb[1], rgb[2]));
      double w = path.x1() - path.x0();
      double h = path.y1() - path.y0();
      if (spread >= 0.20 && w >= 25 && w <= 320 && h >= 25 && h <= 320
          && w / h >= 0.55 && w / h <= 1.8 && path.lineWidth() <= 8) {
        circles.add(new double[] {path.x0(), path.y0(), path.x1(), path.y1()});
      }
    }
    return mergeOverlapping(circles, 0.30);
  }

  /**
   * Signatures: thin wavy dark curved strokes near a case-insensitive "signat*" label;
   * same-ink straight strokes (underlines) within 45pt are merged, and the label box joins
   * the signature block.
   */
  private List<double[]> findSignatureBoxes(List<VectorPathScan.VectorPath> paths,
      List<PdfWordBoxExtractor.WordBox> words) {
    List<double[]> labels = new ArrayList<>();
    for (PdfWordBoxExtractor.WordBox word : words) {
      if (word.text().toLowerCase().contains("signat")) {
        labels.add(new double[] {word.x0(), word.y0(), word.x1(), word.y1()});
      }
    }
    if (labels.isEmpty()) {
      return List.of();
    }
    List<VectorPathScan.VectorPath> wavy = new ArrayList<>();
    for (VectorPathScan.VectorPath path : paths) {
      if (!path.hasCurve() || path.strokeRgb() == null || path.lineWidth() > 2.2) {
        continue;
      }
      float[] rgb = path.strokeRgb();
      double w = path.x1() - path.x0();
      double h = path.y1() - path.y0();
      double diag = Math.max(1, Math.hypot(w, h));
      if (rgb[0] <= 0.4 && rgb[1] <= 0.4 && rgb[2] <= 0.4 && w >= 35 && h >= 6
          && path.pathLength() / diag >= 1.3 && nearAnyLabel(path, labels)) {
        wavy.add(path);
      }
    }
    List<double[]> boxes = new ArrayList<>();
    for (VectorPathScan.VectorPath seed : wavy) {
      double x0 = seed.x0();
      double y0 = seed.y0();
      double x1 = seed.x1();
      double y1 = seed.y1();
      for (VectorPathScan.VectorPath other : paths) {
        if (other == seed || other.strokeRgb() == null || other.hasCurve()) {
          continue;
        }
        if (colorDist(seed.strokeRgb(), other.strokeRgb()) > 0.12
            || Math.abs(seed.lineWidth() - other.lineWidth()) > 0.7) {
          continue;
        }
        double gapX = Math.max(0, Math.max(x0 - other.x1(), other.x0() - x1));
        double gapY = Math.max(0, Math.max(y0 - other.y1(), other.y0() - y1));
        if (Math.hypot(gapX, gapY) <= 45) {
          x0 = Math.min(x0, other.x0());
          y0 = Math.min(y0, other.y0());
          x1 = Math.max(x1, other.x1());
          y1 = Math.max(y1, other.y1());
        }
      }
      for (double[] label : labels) {
        double gapX = Math.max(0, Math.max(x0 - label[2], label[0] - x1));
        double gapY = Math.max(0, Math.max(y0 - label[3], label[1] - y1));
        if (gapX <= 120 && gapY <= 120) {
          x0 = Math.min(x0, label[0]);
          y0 = Math.min(y0, label[1]);
          x1 = Math.max(x1, label[2]);
          y1 = Math.max(y1, label[3]);
        }
      }
      boxes.add(new double[] {x0, y0, x1, y1});
    }
    return mergeOverlapping(boxes, 0.30);
  }

  private boolean nearAnyLabel(VectorPathScan.VectorPath path, List<double[]> labels) {
    for (double[] label : labels) {
      double gapX = Math.max(0, Math.max(path.x0() - label[2], label[0] - path.x1()));
      double gapY = Math.max(0, Math.max(path.y0() - label[3], label[1] - path.y1()));
      if (gapX <= 150 && gapY <= 150) {
        return true;
      }
    }
    return false;
  }

  private double colorDist(float[] a, float[] b) {
    return Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2)
        + Math.pow(a[2] - b[2], 2));
  }

  private List<double[]> mergeOverlapping(List<double[]> boxes, double minOverlap) {
    List<double[]> merged = new ArrayList<>(boxes);
    boolean changed = true;
    while (changed) {
      changed = false;
      outer:
      for (int i = 0; i < merged.size(); i++) {
        for (int j = i + 1; j < merged.size(); j++) {
          double[] a = merged.get(i);
          double[] b = merged.get(j);
          double inter = Math.max(0, Math.min(a[2], b[2]) - Math.max(a[0], b[0]))
              * Math.max(0, Math.min(a[3], b[3]) - Math.max(a[1], b[1]));
          double smaller = Math.min(area(a), area(b));
          if (smaller > 0 && inter / smaller >= minOverlap) {
            merged.set(i, new double[] {Math.min(a[0], b[0]), Math.min(a[1], b[1]),
                Math.max(a[2], b[2]), Math.max(a[3], b[3])});
            merged.remove(j);
            changed = true;
            break outer;
          }
        }
      }
    }
    return merged;
  }

  private double area(double[] box) {
    return Math.max(0, box[2] - box[0]) * Math.max(0, box[3] - box[1]);
  }
}
