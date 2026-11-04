package com.tenderops.demo;

import java.util.List;

/**
 * Generator ground truth (bounding boxes in PDF points, bottom-left origin).
 * Written for TESTS ONLY; runtime code must never read it (CL-23).
 */
public record GroundTruth(List<DocTruth> documents) {

  public record Box(double x0, double y0, double x1, double y1) {}

  public record TableTruth(int page, Box bbox, int rows, int cols, List<List<String>> cells) {}

  public record RegionTruth(int page, Box bbox) {}

  public record DocTruth(
      String file, int pages, TableTruth table, RegionTruth stamp, RegionTruth signature) {}
}
