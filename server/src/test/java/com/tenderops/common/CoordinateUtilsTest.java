package com.tenderops.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.tenderops.common.CoordinateUtils.NormalizedBox;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** TC-ING-05 (math part): PDFBox points -> normalized top-left [0,1] boxes, rotation-aware. */
class CoordinateUtilsTest {

  @Test
  void fullPageMapsToUnitBox() {
    NormalizedBox box = CoordinateUtils.pdfBoxToNormalized(0, 0, 612, 792, 612, 792, 0);
    assertThat(box.x0()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(box.y0()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(box.x1()).isEqualByComparingTo(BigDecimal.ONE);
    assertThat(box.y1()).isEqualByComparingTo(BigDecimal.ONE);
  }

  @Test
  void headingNearTopHasSmallY0() {
    // Page 612x792pt; heading band y=700..720 in bottom-left points (near the top).
    NormalizedBox box = CoordinateUtils.pdfBoxToNormalized(72, 700, 468, 20, 612, 792, 0);
    assertThat(box.y0().doubleValue()).isLessThan(0.2);
    assertThat(box.y0()).isEqualByComparingTo(new BigDecimal("0.090909"));
    assertThat(box.x0().doubleValue()).isBetween(0.0, 1.0);
    assertThat(box.x1().doubleValue()).isBetween(0.0, 1.0);
  }

  @Test
  void rotated90CenterMapsToCenter() {
    NormalizedBox box = CoordinateUtils.pdfBoxToNormalized(296, 386, 20, 20, 612, 792, 90);
    assertThat(box.x0().doubleValue()).isCloseTo(0.487, org.assertj.core.data.Offset.offset(0.02));
    assertThat(box.y0().doubleValue()).isCloseTo(0.467, org.assertj.core.data.Offset.offset(0.02));
  }

  @Test
  void rotated90FullPageMapsToUnitBox() {
    NormalizedBox box = CoordinateUtils.pdfBoxToNormalized(0, 0, 612, 792, 612, 792, 90);
    assertThat(box.x0()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(box.y0()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(box.x1()).isEqualByComparingTo(BigDecimal.ONE);
    assertThat(box.y1()).isEqualByComparingTo(BigDecimal.ONE);
  }

  @Test
  void rotated180And270StayInUnitRange() {
    for (int rotation : new int[]{180, 270}) {
      NormalizedBox box = CoordinateUtils.pdfBoxToNormalized(72, 700, 468, 20, 612, 792, rotation);
      assertThat(box.x0().doubleValue()).isBetween(0.0, 1.0);
      assertThat(box.y0().doubleValue()).isBetween(0.0, 1.0);
      assertThat(box.x1().doubleValue()).isBetween(0.0, 1.0);
      assertThat(box.y1().doubleValue()).isBetween(0.0, 1.0);
    }
  }

  @Test
  void overflowingBoxesAreClamped() {
    NormalizedBox box = CoordinateUtils.pdfBoxToNormalized(-100, -100, 2000, 2000, 612, 792, 0);
    assertThat(box.x0()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(box.y0()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(box.x1()).isEqualByComparingTo(BigDecimal.ONE);
    assertThat(box.y1()).isEqualByComparingTo(BigDecimal.ONE);
  }

  @Test
  void unsupportedRotationThrows() {
    assertThatThrownBy(() -> CoordinateUtils.pdfBoxToNormalized(0, 0, 10, 10, 612, 792, 45))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void inverseMappingRoundTripsForAllRotations() {
    for (int rotation : new int[] {0, 90, 180, 270}) {
      NormalizedBox box =
          CoordinateUtils.pdfBoxToNormalized(100, 200, 150, 80, 612, 792, rotation);
      double[] back = CoordinateUtils.normalizedToPdfBox(box.x0().doubleValue(),
          box.y0().doubleValue(), box.x1().doubleValue(), box.y1().doubleValue(), 612,
          792, rotation);
      assertThat(back[0]).isCloseTo(100.0, within(0.01));
      assertThat(back[1]).isCloseTo(200.0, within(0.01));
      assertThat(back[2]).isCloseTo(250.0, within(0.01));
      assertThat(back[3]).isCloseTo(280.0, within(0.01));
    }
  }
}
