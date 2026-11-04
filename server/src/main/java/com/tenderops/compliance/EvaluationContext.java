package com.tenderops.compliance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Evaluation context: deadline as an Asia/Kolkata date (R13), tender estimated value,
 * rule version and company data version, plus their SHA-256 context hash (section 6.11).
 */
public record EvaluationContext(LocalDate deadlineDate, BigDecimal estimatedValue,
    String ruleVersion, long companyDataVersion, String contextHash) {

  public static EvaluationContext of(OffsetDateTime deadline, BigDecimal estimatedValue,
      String ruleVersion, long companyDataVersion) {
    LocalDate date = deadline == null ? null
        : deadline.atZoneSameInstant(ZoneId.of("Asia/Kolkata")).toLocalDate();
    return new EvaluationContext(date, estimatedValue, ruleVersion, companyDataVersion,
        EvaluationContextHash.of(date, estimatedValue, ruleVersion, companyDataVersion));
  }
}
