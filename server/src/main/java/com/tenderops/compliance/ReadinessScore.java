package com.tenderops.compliance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Readiness score (rule-based, NOT a probability): 100 x sum/N over mandatory
 * requirements with PASS=1, NEEDS_REVIEW=0.5, UNKNOWN/FAIL=0. Scale 2 HALF_UP.
 */
public final class ReadinessScore {

  private ReadinessScore() {
  }

  public static BigDecimal of(List<EvaluatedRequirement> results) {
    List<EvaluatedRequirement> mandatory = new ArrayList<>();
    for (EvaluatedRequirement result : results) {
      if (result.mandatory()) {
        mandatory.add(result);
      }
    }
    if (mandatory.isEmpty()) {
      return new BigDecimal("0.00");
    }
    int pass = 0;
    int review = 0;
    for (EvaluatedRequirement result : mandatory) {
      if (result.verdict() == Verdict.PASS) {
        pass++;
      } else if (result.verdict() == Verdict.NEEDS_REVIEW) {
        review++;
      }
    }
    BigDecimal sum = BigDecimal.valueOf(pass).add(
        BigDecimal.valueOf(review).multiply(new BigDecimal("0.5")));
    return sum.multiply(new BigDecimal(100))
        .divide(BigDecimal.valueOf(mandatory.size()), 2, RoundingMode.HALF_UP);
  }
}
