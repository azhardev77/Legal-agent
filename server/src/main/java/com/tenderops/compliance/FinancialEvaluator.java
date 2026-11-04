package com.tenderops.compliance;

import com.tenderops.company.Certificate;
import com.tenderops.company.FinancialRecord;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic financial evaluators (section 6.3). Exact math, compareTo only. */
public final class FinancialEvaluator {

  private static final String RECORDS = "company_financial_records";
  private static final String CERTS = "company_certificates";

  private FinancialEvaluator() {
  }

  public static LeafResult evaluateTurnover(LeafInput input, List<FinancialRecord> records,
      EvaluationContext context) {
    int window = input.lookbackYears() == null ? 3 : input.lookbackYears();
    if (window <= 0) {
      return new TraceBuilder(Verdict.NEEDS_REVIEW, "AMBIGUOUS_WINDOW",
          "window N must be positive, got " + input.lookbackYears(), context)
          .build(List.of(), RECORDS);
    }
    if (input.amountValue() == null) {
      return new TraceBuilder(Verdict.NEEDS_REVIEW, "AMOUNT_MISSING",
          "threshold amount is missing", context).build(List.of(), RECORDS);
    }
    List<FinancialRecord> candidates = new ArrayList<>();
    for (FinancialRecord record : records) {
      if (record.getFyEndDate() != null
          && !record.getFyEndDate().isAfter(context.deadlineDate())) {
        candidates.add(record);
      }
    }
    candidates.sort(Comparator.comparing(FinancialRecord::getFyEndDate,
        Comparator.reverseOrder()).thenComparing(
        record -> record.getFyLabel() == null ? "" : record.getFyLabel(),
        Comparator.reverseOrder()));
    if (candidates.size() < window) {
      return new TraceBuilder(Verdict.UNKNOWN, "FEWER_THAN_N_FY",
          "only " + candidates.size() + " of " + window
              + " required financial years on record",
          context).put("required", String.valueOf(window))
          .put("available", String.valueOf(candidates.size()))
          .build(ids(candidates), RECORDS);
    }
    List<FinancialRecord> selected = candidates.subList(0, window);
    for (FinancialRecord record : selected) {
      if (!TextNorms.isInr(record.getCurrency())) {
        return new TraceBuilder(Verdict.NEEDS_REVIEW, "CURRENCY_UNSUPPORTED",
            "financial record " + record.getFyLabel() + " is not in INR", context)
            .build(ids(selected), RECORDS);
      }
    }
    for (FinancialRecord record : selected) {
      if (!record.isAudited()) {
        return new TraceBuilder(Verdict.NEEDS_REVIEW, "UNAUDITED_RECORD",
            "financial record " + record.getFyLabel() + " is not audited", context)
            .build(ids(selected), RECORDS);
      }
    }
    for (FinancialRecord record : selected) {
      if (record.getDocumentId() == null) {
        return new TraceBuilder(Verdict.NEEDS_REVIEW, "MISSING_SUPPORTING_DOCUMENT",
            "financial record " + record.getFyLabel() + " has no supporting document",
            context).build(ids(selected), RECORDS);
      }
    }
    List<String> operands = new ArrayList<>();
    BigDecimal sum = BigDecimal.ZERO;
    for (FinancialRecord record : selected) {
      if (record.getTurnover() == null) {
        return new TraceBuilder(Verdict.UNKNOWN, "VALUE_MISSING",
            "financial record " + record.getFyLabel() + " has no turnover value",
            context).build(ids(selected), RECORDS);
      }
      sum = sum.add(record.getTurnover());
      operands.add(record.getTurnover().toPlainString());
    }
    CompareOperator operator = CompareOperator.of(input.operator());
    BigDecimal bound = input.amountValue().multiply(BigDecimal.valueOf(window));
    boolean met = operator.test(sum.compareTo(bound));
    return new TraceBuilder(met ? Verdict.PASS : Verdict.FAIL,
        met ? "THRESHOLD_MET" : "THRESHOLD_NOT_MET",
        "sum(turnover, " + window + " most recent FY) " + operator.symbol()
            + " threshold x " + window,
        context).put("operands", operands).put("sum", sum.toPlainString())
        .put("threshold", input.amountValue().toPlainString())
        .put("thresholdTimesN", bound.toPlainString())
        .put("comparison",
            sum.toPlainString() + " " + operator.symbol() + " " + bound.toPlainString())
        .build(ids(selected), RECORDS);
  }

  public static LeafResult evaluateNetWorth(LeafInput input, List<FinancialRecord> records,
      EvaluationContext context) {
    if (input.amountValue() == null) {
      return new TraceBuilder(Verdict.NEEDS_REVIEW, "AMOUNT_MISSING",
          "threshold amount is missing", context).build(List.of(), RECORDS);
    }
    List<FinancialRecord> candidates = new ArrayList<>();
    for (FinancialRecord record : records) {
      if (record.getFyEndDate() != null
          && !record.getFyEndDate().isAfter(context.deadlineDate())) {
        candidates.add(record);
      }
    }
    if (candidates.isEmpty()) {
      return new TraceBuilder(Verdict.UNKNOWN, "NO_RECORDS",
          "no financial records on or before the deadline", context)
          .build(List.of(), RECORDS);
    }
    List<FinancialRecord> audited = new ArrayList<>();
    for (FinancialRecord record : candidates) {
      if (record.isAudited()) {
        audited.add(record);
      }
    }
    if (audited.isEmpty()) {
      return new TraceBuilder(Verdict.NEEDS_REVIEW, "NO_AUDITED_RECORD",
          "no audited financial record on or before the deadline", context)
          .build(ids(candidates), RECORDS);
    }
    audited.sort(Comparator.comparing(FinancialRecord::getFyEndDate,
        Comparator.reverseOrder()).thenComparing(
        record -> record.getFyLabel() == null ? "" : record.getFyLabel(),
        Comparator.reverseOrder()));
    FinancialRecord latest = audited.get(0);
    if (!TextNorms.isInr(latest.getCurrency())) {
      return new TraceBuilder(Verdict.NEEDS_REVIEW, "CURRENCY_UNSUPPORTED",
          "financial record " + latest.getFyLabel() + " is not in INR", context)
          .build(List.of(latest.getId()), RECORDS);
    }
    if (latest.getDocumentId() == null) {
      return new TraceBuilder(Verdict.NEEDS_REVIEW, "MISSING_SUPPORTING_DOCUMENT",
          "financial record " + latest.getFyLabel() + " has no supporting document",
          context).build(List.of(latest.getId()), RECORDS);
    }
    if (latest.getNetWorth() == null) {
      return new TraceBuilder(Verdict.UNKNOWN, "VALUE_MISSING",
          "financial record " + latest.getFyLabel() + " has no net worth value",
          context).build(List.of(latest.getId()), RECORDS);
    }
    CompareOperator operator = CompareOperator.of(input.operator());
    boolean met = operator.test(latest.getNetWorth().compareTo(input.amountValue()));
    return new TraceBuilder(met ? Verdict.PASS : Verdict.FAIL,
        met ? "THRESHOLD_MET" : "THRESHOLD_NOT_MET",
        "netWorth(" + latest.getFyLabel() + ") " + operator.symbol() + " threshold",
        context).put("operands", List.of(latest.getNetWorth().toPlainString()))
        .put("threshold", input.amountValue().toPlainString())
        .put("comparison", latest.getNetWorth().toPlainString() + " " + operator.symbol()
            + " " + input.amountValue().toPlainString())
        .build(List.of(latest.getId()), RECORDS);
  }

  public static LeafResult evaluateSolvency(LeafInput input, List<Certificate> certs,
      EvaluationContext context) {
    if (input.amountValue() == null) {
      return new TraceBuilder(Verdict.NEEDS_REVIEW, "AMOUNT_MISSING",
          "threshold amount is missing", context).build(List.of(), CERTS);
    }
    List<Certificate> matches = new ArrayList<>();
    for (Certificate certificate : certs) {
      String normalized = TextNorms.alphaNumUpper(certificate.getName());
      if (normalized != null && normalized.contains("SOLVENCY")) {
        matches.add(certificate);
      }
    }
    if (matches.isEmpty()) {
      return new TraceBuilder(Verdict.UNKNOWN, "NO_SOLVENCY_CERTIFICATE",
          "no bank solvency certificate on record", context).build(List.of(), CERTS);
    }
    matches.sort(Comparator.comparing(Certificate::getExpiryDate,
        Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(
        Certificate::getId, Comparator.nullsLast(Comparator.naturalOrder())));
    Certificate chosen = matches.get(0);
    if (!TextNorms.isInr(chosen.getCurrency())) {
      return new TraceBuilder(Verdict.NEEDS_REVIEW, "CURRENCY_UNSUPPORTED",
          "solvency certificate is not in INR", context)
          .build(List.of(chosen.getId()), CERTS);
    }
    if (chosen.getExpiryDate() == null) {
      return new TraceBuilder(Verdict.NEEDS_REVIEW, "EXPIRY_MISSING",
          "solvency certificate has no expiry date", context)
          .build(List.of(chosen.getId()), CERTS);
    }
    if (chosen.getAmount() == null) {
      return new TraceBuilder(Verdict.UNKNOWN, "VALUE_MISSING",
          "solvency certificate has no amount", context)
          .build(List.of(chosen.getId()), CERTS);
    }
    CompareOperator operator = CompareOperator.of(input.operator());
    boolean amountMet = operator.test(chosen.getAmount().compareTo(input.amountValue()));
    boolean timeMet = !chosen.getExpiryDate().isBefore(context.deadlineDate());
    String formula = "amount " + operator.symbol() + " threshold AND expiry "
        + chosen.getExpiryDate() + " on/after deadline " + context.deadlineDate();
    if (!amountMet) {
      return new TraceBuilder(Verdict.FAIL, "THRESHOLD_NOT_MET", formula, context)
          .put("operands", List.of(chosen.getAmount().toPlainString()))
          .put("threshold", input.amountValue().toPlainString())
          .put("comparison", chosen.getAmount().toPlainString() + " " + operator.symbol()
              + " " + input.amountValue().toPlainString())
          .build(List.of(chosen.getId()), CERTS);
    }
    if (!timeMet) {
      return new TraceBuilder(Verdict.FAIL, "CERT_EXPIRED", formula, context)
          .put("expiryDate", chosen.getExpiryDate().toString())
          .put("deadlineDate", context.deadlineDate().toString())
          .build(List.of(chosen.getId()), CERTS);
    }
    if (chosen.getDocumentId() == null) {
      return new TraceBuilder(Verdict.NEEDS_REVIEW, "MISSING_SUPPORTING_DOCUMENT",
          "solvency certificate has no supporting document", context)
          .build(List.of(chosen.getId()), CERTS);
    }
    return new TraceBuilder(Verdict.PASS, "CERT_VALID", formula, context)
        .put("operands", List.of(chosen.getAmount().toPlainString()))
        .put("threshold", input.amountValue().toPlainString())
        .put("expiryDate", chosen.getExpiryDate().toString())
        .put("deadlineDate", context.deadlineDate().toString())
        .build(List.of(chosen.getId()), CERTS);
  }

  private static List<Long> ids(List<FinancialRecord> records) {
    List<Long> ids = new ArrayList<>();
    for (FinancialRecord record : records) {
      ids.add(record.getId());
    }
    return ids;
  }
}
