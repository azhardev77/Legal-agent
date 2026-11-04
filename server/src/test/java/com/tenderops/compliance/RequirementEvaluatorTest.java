package com.tenderops.compliance;

import static com.tenderops.compliance.ComplianceFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenderops.ai.ConditionNode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Requirement-level gates: quote verification, confidence, currency, metric support,
 * condition-tree precedence, and eval-time leaf amount conversion (M4 carryovers).
 */
class RequirementEvaluatorTest {

  private final RequirementEvaluator evaluator = new RequirementEvaluator(new ObjectMapper());

  private static RequirementView goldenTurnover() {
    return view("4.1", true, "AVG_ANNUAL_TURNOVER", "GTE", cr(100), null, 3, List.of(),
        null, null, null);
  }

  @Test
  void goldenTurnoverPassesEndToEnd() {
    EvaluatedRequirement result = evaluator.evaluateView(101L, goldenTurnover(),
        goldenSnapshot(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    assertThat(result.clauseRef()).isEqualTo("4.1");
    assertThat(result.requirementId()).isEqualTo(101L);
  }

  @Test
  void unverifiedQuoteNeedsReview() {
    RequirementView input = new RequirementView("4.1", true, "AVG_ANNUAL_TURNOVER", "GTE",
        cr(100).toPlainString(), cr(100), "INR", null, 3, List.of(), null, null, null,
        false, CONF);

    EvaluatedRequirement result =
        evaluator.evaluateView(101L, input, goldenSnapshot(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("QUOTE_NOT_VERIFIED");
  }

  @Test
  void lowConfidenceNeedsReview() {
    RequirementView input = new RequirementView("4.1", true, "AVG_ANNUAL_TURNOVER", "GTE",
        cr(100).toPlainString(), cr(100), "INR", null, 3, List.of(), null, null, null,
        true, new BigDecimal("0.5000"));

    EvaluatedRequirement result =
        evaluator.evaluateView(101L, input, goldenSnapshot(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("LOW_CONFIDENCE");
  }

  @Test
  void missingConfidenceNeedsReview() {
    RequirementView input = new RequirementView("4.1", true, "AVG_ANNUAL_TURNOVER", "GTE",
        cr(100).toPlainString(), cr(100), "INR", null, 3, List.of(), null, null, null,
        true, null);

    EvaluatedRequirement result =
        evaluator.evaluateView(101L, input, goldenSnapshot(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("CONFIDENCE_MISSING");
  }

  @Test
  void outOfRangeConfidenceNeedsReview() {
    RequirementView input = new RequirementView("4.1", true, "AVG_ANNUAL_TURNOVER", "GTE",
        cr(100).toPlainString(), cr(100), "INR", null, 3, List.of(), null, null, null,
        true, new BigDecimal("1.5000"));

    EvaluatedRequirement result =
        evaluator.evaluateView(101L, input, goldenSnapshot(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("CONFIDENCE_OUT_OF_RANGE");
  }

  @Test
  void boundaryConfidenceProceeds() {
    RequirementView input = new RequirementView("4.1", true, "AVG_ANNUAL_TURNOVER", "GTE",
        cr(100).toPlainString(), cr(100), "INR", null, 3, List.of(), null, null, null,
        true, new BigDecimal("0.8000"));

    EvaluatedRequirement result =
        evaluator.evaluateView(101L, input, goldenSnapshot(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.PASS);
  }

  @Test
  void foreignCurrencyRequirementNeedsReview() {
    RequirementView input = new RequirementView("4.1", true, "AVG_ANNUAL_TURNOVER", "GTE",
        cr(100).toPlainString(), cr(100), "USD", null, 3, List.of(), null, null, null,
        true, CONF);

    EvaluatedRequirement result =
        evaluator.evaluateView(101L, input, goldenSnapshot(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("CURRENCY_UNSUPPORTED");
  }

  @Test
  void foreignAmountTextNeedsReview() {
    RequirementView input = new RequirementView("4.1", true, "AVG_ANNUAL_TURNOVER", "GTE",
        "USD 100 Cr", cr(100), "INR", null, 3, List.of(), null, null, null, true, CONF);

    EvaluatedRequirement result =
        evaluator.evaluateView(101L, input, goldenSnapshot(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("CURRENCY_UNSUPPORTED");
  }

  @Test
  void unsupportedMetricNeverPasses() {
    RequirementView input = view("9.9", true, "OTHER", "GTE", null, null, null, List.of(),
        null, null, null);

    EvaluatedRequirement result =
        evaluator.evaluateView(101L, input, goldenSnapshot(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("METRIC_UNSUPPORTED");
  }

  @Test
  void unrecognizedMetricNeverPasses() {
    RequirementView input = view("9.9", true, "BOGUS", "GTE", cr(10), null, null,
        List.of(), null, null, null);

    EvaluatedRequirement result =
        evaluator.evaluateView(101L, input, goldenSnapshot(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("METRIC_UNSUPPORTED");
  }

  @Test
  void lowercaseMetricIsNormalized() {
    RequirementView input = view("4.1", true, "avg_annual_turnover", "gte", cr(100), null,
        3, List.of(), null, null, null);

    EvaluatedRequirement result =
        evaluator.evaluateView(101L, input, goldenSnapshot(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.PASS);
  }

  @Test
  void treeTakesPrecedenceOverTopLevelMetric() {
    RequirementView input = view("5.1", true, "OTHER", "GTE", null, null, null, List.of(),
        null, null, treeJson(v1ProjectsTree()));

    EvaluatedRequirement result =
        evaluator.evaluateView(101L, input, goldenSnapshot(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
  }

  @Test
  void treeLeafAmountConvertsAtEvalTime() {
    ConditionNode root = ConditionNode.leaf("SIMILAR_PROJECTS_COUNT_VALUE", "GTE",
        "Rs 25 Cr", 2, 7, GOLD_DOMAINS, null, null);
    RequirementView input = view("5.1", true, "OTHER", "GTE", null, null, null, List.of(),
        null, null, treeJson(root));

    EvaluatedRequirement result =
        evaluator.evaluateView(101L, input, goldenSnapshot(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.PASS);
  }

  @Test
  void unparseableTreeLeafAmountNeedsReview() {
    ConditionNode root = ConditionNode.leaf("SIMILAR_PROJECTS_COUNT_VALUE", "GTE",
        "about twenty five", 2, 7, GOLD_DOMAINS, null, null);
    RequirementView input = view("5.1", true, "OTHER", "GTE", null, null, null, List.of(),
        null, null, treeJson(root));

    EvaluatedRequirement result =
        evaluator.evaluateView(101L, input, goldenSnapshot(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("AMOUNT_UNPARSEABLE");
  }

  @Test
  void foreignTreeLeafAmountNeedsReview() {
    ConditionNode root = ConditionNode.leaf("SIMILAR_PROJECTS_COUNT_VALUE", "GTE",
        "USD 25 Cr", 2, 7, GOLD_DOMAINS, null, null);
    RequirementView input = view("5.1", true, "OTHER", "GTE", null, null, null, List.of(),
        null, null, treeJson(root));

    EvaluatedRequirement result =
        evaluator.evaluateView(101L, input, goldenSnapshot(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("CURRENCY_UNSUPPORTED");
  }

  @Test
  void missingDeadlineNeedsReview() {
    EvaluatedRequirement result = evaluator.evaluateView(101L, goldenTurnover(),
        goldenSnapshot(), EvaluationContext.of(null, E60, "1.0", 7L));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("DEADLINE_MISSING");
  }

  @Test
  void traceJsonCarriesVerdictAndProvenance() throws Exception {
    EvaluatedRequirement result = evaluator.evaluateView(101L, goldenTurnover(),
        goldenSnapshot(), ctx(DEADLINE, E60));

    JsonNode trace = new ObjectMapper().readTree(result.traceJson());
    assertThat(trace.get("verdict").asText()).isEqualTo("PASS");
    assertThat(trace.get("reasonCode").asText()).isEqualTo("THRESHOLD_MET");
    assertThat(trace.get("ruleVersion").asText()).isEqualTo("1.0");
    assertThat(trace.get("contextHash").asText()).hasSize(64);
    assertThat(trace.get("evidenceIds").size()).isEqualTo(3);
    assertThat(trace.get("formula").asText()).isNotBlank();
  }

  @Test
  void repeatedEvaluationIsIdentical() {
    EvaluatedRequirement first = evaluator.evaluateView(101L, goldenTurnover(),
        goldenSnapshot(), ctx(DEADLINE, E60));
    EvaluatedRequirement second = new RequirementEvaluator(new ObjectMapper())
        .evaluateView(101L, goldenTurnover(), goldenSnapshot(), ctx(DEADLINE, E60));

    assertThat(second.verdict()).isEqualTo(first.verdict());
    assertThat(second.traceJson()).isEqualTo(first.traceJson());
  }
}
