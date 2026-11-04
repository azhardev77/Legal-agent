package com.tenderops.ai;

import java.util.List;

/** Extractor output contract (live Muse and rule-based fallback produce the same schema). */
public final class Extraction {

  private Extraction() {}

  public record TenderBlock(String reference, String estimatedValueText, String emdText,
      String tenderFeeText, String deadlineText) {}

  public record RequirementItem(
      String clauseRef,
      String category,
      Boolean mandatory,
      Boolean mandatoryAmbiguous,
      String metric,
      String operator,
      String amountText,
      Integer countValue,
      Integer lookbackYears,
      List<String> domainTags,
      String certificateName,
      Integer certificateLevel,
      ConditionNode conditionTree,
      Long sourceRegionId,
      String originalQuote,
      Double confidence,
      String amendmentAction,
      String targetClauseRef) {}

  public record Result(TenderBlock tender, List<RequirementItem> requirements) {}
}
