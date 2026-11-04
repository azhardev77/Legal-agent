package com.tenderops.visual;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenderops.common.CoordinateUtils;
import com.tenderops.demo.GroundTruth;
import com.tenderops.demo.TenderPdfGenerator;
import com.tenderops.visual.VisualRegionDetector.Detection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TC-VIS-01/02/11: the geometric fallback finds the golden stamp, signature and table with
 * IoU &gt;= 0.5 vs ground truth, labels everything FALLBACK_HEURISTIC, and oversized regions
 * are discarded. (The TEST may read ground-truth.json; runtime code never does.)
 */
class GeometricFallbackDetectorTest {

  @TempDir
  Path temp;

  private final GeometricFallbackDetector detector = new GeometricFallbackDetector();

  private PDDocument v1Doc() throws Exception {
    new TenderPdfGenerator(new ObjectMapper()).generateSamples(temp);
    return Loader.loadPDF(temp.resolve(TenderPdfGenerator.V1_FILE).toFile());
  }

  private GroundTruth.DocTruth v1Truth() throws Exception {
    String json = Files.readString(temp.resolve(TenderPdfGenerator.TRUTH_FILE),
        StandardCharsets.UTF_8);
    return new ObjectMapper().readValue(json, GroundTruth.class).documents().stream()
        .filter(d -> d.file().equals(TenderPdfGenerator.V1_FILE)).findFirst().orElseThrow();
  }

  private double[] normalized(GroundTruth.Box box, PDPage page) {
    CoordinateUtils.NormalizedBox b = CoordinateUtils.pdfBoxToNormalized(
        box.x0(), box.y0(), box.x1() - box.x0(), box.y1() - box.y0(),
        page.getMediaBox().getWidth(), page.getMediaBox().getHeight(), page.getRotation());
    return new double[] {b.x0().doubleValue(), b.y0().doubleValue(), b.x1().doubleValue(),
        b.y1().doubleValue()};
  }

  private double iou(Detection detection, double[] truth) {
    double inter = Math.max(0, Math.min(detection.x1(), truth[2])
        - Math.max(detection.x0(), truth[0]))
        * Math.max(0, Math.min(detection.y1(), truth[3])
        - Math.max(detection.y0(), truth[1]));
    double union = detection.area() + Math.max(0, truth[2] - truth[0])
        * Math.max(0, truth[3] - truth[1]) - inter;
    return union <= 0 ? 0 : inter / union;
  }

  private Detection onlyOfType(List<Detection> detections, String type) {
    List<Detection> matching =
        detections.stream().filter(d -> d.regionType().equals(type)).toList();
    assertThat(matching).hasSize(1);
    return matching.get(0);
  }

  @Test
  void findsTableOnP3WithHighIou() throws Exception {
    try (PDDocument doc = v1Doc()) {
      List<Detection> detections = detector.detect(doc, 2, null, 0, 0, null);
      assertThat(detections).hasSize(1);
      Detection table = onlyOfType(detections, VisualRegion.TABLE_REGION);
      double[] truth = normalized(v1Truth().table().bbox(), doc.getPage(2));
      assertThat(iou(table, truth)).isGreaterThanOrEqualTo(0.5);
      assertThat(table.source()).isEqualTo(VisualRegion.SOURCE_FALLBACK);
    }
  }

  @Test
  void findsStampAndSignatureOnP6WithHighIou() throws Exception {
    try (PDDocument doc = v1Doc()) {
      List<Detection> detections = detector.detect(doc, 5, null, 0, 0, null);
      assertThat(detections).hasSize(2);
      Detection stamp = onlyOfType(detections, VisualRegion.OFFICIAL_STAMP);
      Detection signature = onlyOfType(detections, VisualRegion.HANDWRITTEN_SIGNATURE);
      GroundTruth.DocTruth truth = v1Truth();
      assertThat(iou(stamp, normalized(truth.stamp().bbox(), doc.getPage(5))))
          .isGreaterThanOrEqualTo(0.5);
      assertThat(iou(signature, normalized(truth.signature().bbox(), doc.getPage(5))))
          .isGreaterThanOrEqualTo(0.5);
      assertThat(detections).allSatisfy(
          d -> assertThat(d.source()).isEqualTo(VisualRegion.SOURCE_FALLBACK));
    }
  }

  @Test
  void textOnlyPagesYieldNoRegions() throws Exception {
    try (PDDocument doc = v1Doc()) {
      for (int page : new int[] {0, 1, 3, 4}) {
        assertThat(detector.detect(doc, page, null, 0, 0, null)).isEmpty();
      }
    }
  }

  @Test
  void oversizedRegionsAreDiscarded() {
    Detection huge = new Detection(VisualRegion.TABLE_REGION, 0, 0, 1, 0.5, 0.9,
        VisualRegion.SOURCE_FALLBACK);
    Detection edge = new Detection(VisualRegion.TABLE_REGION, 0, 0, 1, 0.39, 0.9,
        VisualRegion.SOURCE_FALLBACK);
    assertThat(RegionFilter.discardOversized(List.of(huge, edge))).containsExactly(edge);
  }
}
