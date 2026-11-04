package com.tenderops.compliance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenderops.common.MoneyParseException;
import com.tenderops.common.MoneyUtils;
import com.tenderops.tender.TenderRequirement;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Requirement-level gates (R5 quote, R7 confidence, R15 currency, metric support)
 * plus condition-tree-or-leaf dispatch. Trees take precedence over the top-level
 * metric; tree-leaf amounts convert via MoneyUtils at eval time (R6).
 */
@Component
public class RequirementEvaluator {

  private static final Set<String> METRICS = Set.of("AVG_ANNUAL_TURNOVER", "NET_WORTH",
      "SIMILAR_PROJECTS_COUNT_VALUE", "SIMILAR_PROJECTS_80_50_40", "CERTIFICATE_VALID",
      "SOLVENCY_CERTIFICATE", "OTHER");
  private static final BigDecimal MIN_CONFIDENCE = new BigDecimal("0.80");
  private static final BigDecimal MAX_CONFIDENCE = BigDecimal.ONE;

  private final ObjectMapper mapper;

  public RequirementEvaluator(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public EvaluatedRequirement evaluate(TenderRequirement row, CompanySnapshot snapshot,
      EvaluationContext context) {
    return evaluateView(row.getId(), RequirementView.from(row), snapshot, context);
  }

  public EvaluatedRequirement evaluateView(Long requirementId, RequirementView view,
      CompanySnapshot snapshot, EvaluationContext context) {
    LeafResult leaf = evaluateGated(view, snapshot, context);
    Map<String, Object> trace = new LinkedHashMap<>(leaf.trace());
    trace.put("ruleVersion", context.ruleVersion());
    trace.put("contextHash", context.contextHash());
    String json;
    try {
      json = mapper.writeValueAsString(trace);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize calculation trace", e);
    }
    return new EvaluatedRequirement(requirementId, view.clauseRef(), view.mandatory(),
        leaf.verdict(), leaf.reasonCode(), json, leaf.evidenceIds());
  }

  private LeafResult evaluateGated(RequirementView view, CompanySnapshot snapshot,
      EvaluationContext context) {
    if (view.confidence() == null) {
      return gate("CONFIDENCE_MISSING", "model confidence is missing", context);
    }
    if (view.confidence().compareTo(MIN_CONFIDENCE) < 0) {
      return gate("LOW_CONFIDENCE",
          "model confidence " + view.confidence().toPlainString() + " is below 0.80",
          context);
    }
    if (view.confidence().compareTo(MAX_CONFIDENCE) > 0) {
      return gate("CONFIDENCE_OUT_OF_RANGE",
          "model confidence " + view.confidence().toPlainString()
              + " is outside [0.0, 1.0]",
          context);
    }
    if (!view.quoteVerified()) {
      return gate("QUOTE_NOT_VERIFIED",
          "quote is not verified against the page text", context);
    }
    if (context.deadlineDate() == null) {
      return gate("DEADLINE_MISSING", "tender has no submission deadline", context);
    }
    if (view.currency() != null && !TextNorms.isInr(view.currency())) {
      return gate("CURRENCY_UNSUPPORTED",
          "requirement currency " + view.currency() + " is not supported", context);
    }
    if (view.amountText() != null && !view.amountText().isBlank()
        && !"INR".equals(MoneyUtils.currencyOf(view.amountText()))) {
      return gate("CURRENCY_UNSUPPORTED",
          "requirement amount is not in INR", context);
    }
    if (view.conditionTree() != null && !view.conditionTree().isBlank()) {
      return ConditionTreeEvaluator.evaluate(view.conditionTree(),
          leaf -> evaluateLeaf(leaf, snapshot, context), mapper);
    }
    String metric = normalizedMetric(view.metric());
    if (metric == null) {
      return gate("METRIC_UNSUPPORTED",
          "metric " + view.metric() + " is not supported", context);
    }
    if (!CompareOperator.isKnown(view.operator())) {
      return gate("METRIC_UNSUPPORTED",
          "operator " + view.operator() + " is not supported", context);
    }
    return evaluateLeaf(view.toLeaf(), snapshot, context);
  }

  /** Evaluates one leaf with eval-time amount conversion. Null metric/OTHER never pass. */
  public LeafResult evaluateLeaf(LeafInput raw, CompanySnapshot snapshot,
      EvaluationContext context) {
    String metric = normalizedMetric(raw.metric());
    if (metric == null) {
      return leafGate("METRIC_UNSUPPORTED",
          "metric " + raw.metric() + " is not supported", context);
    }
    if (!CompareOperator.isKnown(raw.operator())) {
      return leafGate("METRIC_UNSUPPORTED",
          "operator " + raw.operator() + " is not supported", context);
    }
    String operator = raw.operator() == null || raw.operator().isBlank() ? "GTE"
        : raw.operator().trim().toUpperCase(Locale.ROOT);
    BigDecimal amount = raw.amountValue();
    if (amount == null && raw.amountText() != null && !raw.amountText().isBlank()) {
      if (!"INR".equals(MoneyUtils.currencyOf(raw.amountText()))) {
        return leafGate("CURRENCY_UNSUPPORTED",
            "leaf amount " + raw.amountText() + " is not in INR", context);
      }
      try {
        amount = MoneyUtils.parse(raw.amountText());
      } catch (MoneyParseException e) {
        return leafGate("AMOUNT_UNPARSEABLE",
            "leaf amount " + raw.amountText() + " cannot be parsed", context);
      }
    }
    LeafInput input = new LeafInput(metric, operator, raw.amountText(), amount,
        raw.countValue(), raw.lookbackYears(),
        raw.domainTags() == null ? List.of() : raw.domainTags(),
        raw.certificateName(), raw.certificateLevel());
    return switch (metric) {
      case "AVG_ANNUAL_TURNOVER" -> FinancialEvaluator.evaluateTurnover(input,
          snapshot.financials(), context);
      case "NET_WORTH" -> FinancialEvaluator.evaluateNetWorth(input,
          snapshot.financials(), context);
      case "SOLVENCY_CERTIFICATE" -> FinancialEvaluator.evaluateSolvency(input,
          snapshot.certificates(), context);
      case "SIMILAR_PROJECTS_COUNT_VALUE" -> ProjectEvaluator.evaluateCountValue(
          input, snapshot.projects(), context);
      case "SIMILAR_PROJECTS_80_50_40" -> ProjectEvaluator.evaluate805040(input,
          snapshot.projects(), context);
      case "CERTIFICATE_VALID" -> CertificateEvaluator.evaluate(input,
          snapshot.certificates(), context);
      default -> leafGate("METRIC_UNSUPPORTED",
          "metric " + raw.metric() + " is not supported", context);
    };
  }

  /** Upper-cased known metric, or null for OTHER/unrecognized (never evaluated). */
  private static String normalizedMetric(String metric) {
    if (metric == null) {
      return null;
    }
    String upper = metric.trim().toUpperCase(Locale.ROOT);
    if (!METRICS.contains(upper) || "OTHER".equals(upper)) {
      return null;
    }
    return upper;
  }

  private static LeafResult gate(String reason, String formula,
      EvaluationContext context) {
    return new TraceBuilder(Verdict.NEEDS_REVIEW, reason, formula, context)
        .build(List.of(), "requirement_gate");
  }

  private static LeafResult leafGate(String reason, String formula,
      EvaluationContext context) {
    return new TraceBuilder(Verdict.NEEDS_REVIEW, reason, formula, context)
        .build(List.of(), "leaf_gate");
  }
}
