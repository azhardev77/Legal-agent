package com.tenderops.ai;

import com.tenderops.common.FileSafetyValidator;
import java.nio.charset.StandardCharsets;

/**
 * Requirement fingerprint (§6.11, COMPLIANCE_ENGINE §12): SHA-256 hex of canonical
 * {@code clauseRef|metric|operator|amount|count|lookback|domains|certName|certLevel|
 * conditionTree|mandatory} with literal NULLs.
 */
public final class RequirementFingerprint {

  private RequirementFingerprint() {}

  public static String of(String clauseRef, String metric, String operator, String amountText,
      Integer countValue, Integer lookbackYears, String domainTags, String certificateName,
      Integer certificateLevel, String conditionTreeJson, boolean mandatory) {
    String canonical = String.join("|",
        nullToNull(clauseRef),
        nullToNull(metric),
        nullToNull(operator),
        amountText == null ? "NULL" : amountText.trim(),
        countValue == null ? "NULL" : countValue.toString(),
        lookbackYears == null ? "NULL" : lookbackYears.toString(),
        domainTags == null || domainTags.isBlank() ? "NULL" : domainTags.trim(),
        certificateName == null ? "NULL" : certificateName.trim(),
        certificateLevel == null ? "NULL" : certificateLevel.toString(),
        conditionTreeJson == null ? "NULL" : conditionTreeJson,
        Boolean.toString(mandatory));
    return FileSafetyValidator.sha256Hex(canonical.getBytes(StandardCharsets.UTF_8));
  }

  private static String nullToNull(String value) {
    return value == null ? "NULL" : value;
  }
}
