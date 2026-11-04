package com.tenderops.compliance;

import com.tenderops.tender.TenderRequirement;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Requirement fields the engine reads, decoupled from the JPA entity for unit tests. */
public record RequirementView(String clauseRef, boolean mandatory, String metric,
    String operator, String amountText, BigDecimal amountValue, String currency,
    Integer countValue, Integer lookbackYears, List<String> domainTags,
    String certificateName, Integer certificateLevel, String conditionTree,
    boolean quoteVerified, BigDecimal confidence) {

  public static RequirementView from(TenderRequirement row) {
    List<String> tags = new ArrayList<>();
    if (row.getDomainTags() != null && !row.getDomainTags().isBlank()) {
      for (String part : row.getDomainTags().split(",")) {
        if (!part.isBlank()) {
          tags.add(part.trim());
        }
      }
    }
    return new RequirementView(row.getClauseRef(), row.isMandatory(), row.getMetric(),
        row.getOperator(), row.getAmountText(), row.getAmountValue(), row.getCurrency(),
        row.getCountValue(), row.getLookbackYears(), tags, row.getCertificateName(),
        row.getCertificateLevel(), row.getConditionTree(), row.isQuoteVerified(),
        row.getConfidence());
  }

  /** Top-level fields as a leaf input (used when no condition tree is present). */
  public LeafInput toLeaf() {
    return new LeafInput(metric, operator, amountText, amountValue, countValue,
        lookbackYears, domainTags == null ? List.of() : domainTags, certificateName,
        certificateLevel);
  }
}
