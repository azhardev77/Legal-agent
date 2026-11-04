package com.tenderops.compliance;

import com.tenderops.common.FileSafetyValidator;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * evaluationContextHash (COMPLIANCE_ENGINE section 12): SHA-256 hex of
 * deadlineDate|estimatedContractValue|ruleVersion|companyDataVersion with NULL literals.
 */
public final class EvaluationContextHash {

  private EvaluationContextHash() {
  }

  public static String of(LocalDate deadlineDate, BigDecimal estimatedValue,
      String ruleVersion, long companyDataVersion) {
    String canonical = (deadlineDate == null ? "NULL" : deadlineDate.toString())
        + "|"
        + (estimatedValue == null ? "NULL" : estimatedValue.toPlainString())
        + "|"
        + ruleVersion
        + "|"
        + companyDataVersion;
    return FileSafetyValidator.sha256Hex(canonical.getBytes(StandardCharsets.UTF_8));
  }
}
