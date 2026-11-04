package com.tenderops.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** TC-TIME-01: honest time-saved arithmetic with configured assumptions. */
class TimeSavedTest {

  @Test
  void goldenExampleSavesAlmostNinetyPercent() {
    TimeSavedResult result = TimeSavedCalculator.calculate(6, 30000L, 1, 4.0, 2.0);

    assertThat(result.manualMinutes()).isEqualByComparingTo(new BigDecimal("24.00"));
    assertThat(result.assistedMinutes()).isEqualByComparingTo(new BigDecimal("2.50"));
    assertThat(result.percent()).isEqualByComparingTo(new BigDecimal("89.58"));
  }

  @Test
  void percentFloorsAtZero() {
    TimeSavedResult result = TimeSavedCalculator.calculate(1, 3600000L, 10, 4.0, 2.0);

    assertThat(result.percent()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.assistedMinutes()).isEqualByComparingTo(new BigDecimal("80.00"));
  }

  @Test
  void zeroPagesAvoidsDivisionByZero() {
    TimeSavedResult result = TimeSavedCalculator.calculate(0, 1000L, 0, 4.0, 2.0);

    assertThat(result.percent()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.manualMinutes()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void configuredAssumptionsAreRespected() {
    TimeSavedResult result = TimeSavedCalculator.calculate(10, 60000L, 2, 5.0, 3.0);

    assertThat(result.manualMinutes()).isEqualByComparingTo(new BigDecimal("50.00"));
    assertThat(result.assistedMinutes()).isEqualByComparingTo(new BigDecimal("7.00"));
    assertThat(result.percent()).isEqualByComparingTo(new BigDecimal("86.00"));
  }
}
