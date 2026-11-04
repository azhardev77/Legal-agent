package com.tenderops.visual;

import java.util.List;

/** Shared region hygiene: drops watermark-style false positives covering > 40% of the page. */
public final class RegionFilter {

  /** Maximum region area as a fraction of the page (TC-VIS-11, §3.3). */
  public static final double MAX_AREA_FRACTION = 0.40;

  private RegionFilter() {}

  public static List<VisualRegionDetector.Detection> discardOversized(
      List<VisualRegionDetector.Detection> detections) {
    return detections.stream().filter(d -> d.area() <= MAX_AREA_FRACTION).toList();
  }
}
