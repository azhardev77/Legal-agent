package com.tenderops.visual;

import java.util.List;
import java.util.Optional;

/**
 * Muse-vision seam for region content on SCANNED/MIXED pages (§3.4). No bean exists until M4;
 * until then {@link RegionContentService} degrades honestly to needsHumanReview.
 * Vision-derived text always yields contentSource=MUSE_VISION (M4 routes vision-only
 * requirements to NEEDS_REVIEW).
 */
public interface VisionRegionReader {

  record StructuredTable(List<List<String>> rows) {}

  Optional<StructuredTable> readTable(byte[] cropPng);

  Optional<String> readText(byte[] cropPng);
}
