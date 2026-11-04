package com.tenderops.compliance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Time-saved metric (section 6.9). Baselines are configured assumptions, not results. */
public final class TimeSavedCalculator {

  private TimeSavedCalculator() {
  }

  public static TimeSavedResult calculate(int totalPages, long processingMs,
      int reviewItems, double baselineMinutesPerPage, double reviewMinutesPerItem) {
    BigDecimal manual = BigDecimal.valueOf(totalPages)
        .multiply(BigDecimal.valueOf(baselineMinutesPerPage))
        .setScale(2, RoundingMode.HALF_UP);
    BigDecimal assisted = BigDecimal.valueOf(processingMs)
        .divide(new BigDecimal(60000), 10, RoundingMode.HALF_UP)
        .add(BigDecimal.valueOf(reviewItems)
            .multiply(BigDecimal.valueOf(reviewMinutesPerItem)))
        .setScale(2, RoundingMode.HALF_UP);
    BigDecimal percent;
    if (manual.compareTo(BigDecimal.ZERO) <= 0) {
      percent = new BigDecimal("0.00");
    } else {
      percent = manual.subtract(assisted).multiply(new BigDecimal(100))
          .divide(manual, 2, RoundingMode.HALF_UP);
      if (percent.compareTo(BigDecimal.ZERO) < 0) {
        percent = new BigDecimal("0.00");
      }
    }
    return new TimeSavedResult(manual, assisted, percent);
  }
}
