package com.tenderops.visual;

import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;

/** Visual region detection (SAM 3.1 live or geometric fallback). */
public interface VisualRegionDetector {

  /**
   * @param doc open document (geometric detectors read vectors/text; SAM detectors ignore it)
   * @param pageIndex0 0-based page index
   * @param renderedPng rendered page PNG (SAM input; geometric detectors ignore it)
   * @param imageWidth imageHeight render size in pixels (SAM box scaling)
   * @param pageSha256 hex SHA-256 of the rendered PNG (SAM cache key)
   * @return detections with NORMALIZED [0,1] top-left-origin boxes
   */
  List<Detection> detect(PDDocument doc, int pageIndex0, byte[] renderedPng, int imageWidth,
      int imageHeight, String pageSha256) throws IOException;

  /** Normalized-box detection. Coordinates are top-left origin [0,1]. */
  record Detection(String regionType, double x0, double y0, double x1, double y1,
      double confidence, String source, String maskRef) {

    public Detection(String regionType, double x0, double y0, double x1, double y1,
        double confidence, String source) {
      this(regionType, x0, y0, x1, y1, confidence, source, null);
    }

    public double area() {
      return Math.max(0, x1 - x0) * Math.max(0, y1 - y0);
    }
  }
}
