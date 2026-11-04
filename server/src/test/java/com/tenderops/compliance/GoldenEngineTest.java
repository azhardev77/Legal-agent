package com.tenderops.compliance;

import static com.tenderops.compliance.ComplianceFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Engine-level golden tests: fixture requirements (no AI) over the section-5 company
 * reproduce the golden verdicts, decisions, scores and risks exactly. TC-SCR-01,
 * TC-PRJ-01/02, TC-CRT-05 and TC-PRB-03 (engine part) live here.
 */
class GoldenEngineTest {

  private final RequirementEvaluator evaluator = new RequirementEvaluator(new ObjectMapper());

  private static RequirementView turnover() {
    return view("4.1", true, "AVG_ANNUAL_TURNOVER", "GTE", cr(100), null, 3, List.of(),
        null, null, null);
  }

  private static RequirementView netWorth() {
    return view("4.2", true, "NET_WORTH", "GTE", cr(30), null, null, List.of(), null, null,
        null);
  }

  private static RequirementView solvency() {
    return view("6.2", true, "SOLVENCY_CERTIFICATE", "GTE", cr(15), null, null, List.of(),
        null, null, null);
  }

  private static RequirementView desirable() {
    return view("8.1", false, "CERTIFICATE_VALID", "GTE", null, null, null, List.of(),
        "ISO_27001", null, null);
  }

  private static List<RequirementView> v1Views() {
    return List.of(turnover(), netWorth(),
        view("5.1", true, "OTHER", "GTE", null, null, null, List.of(), null, null,
            treeJson(v1ProjectsTree())),
        view("6.1", true, "CERTIFICATE_VALID", "GTE", null, null, null, List.of(), "CMMI",
            3, null),
        solvency(), desirable());
  }

  private static List<RequirementView> v2Views() {
    return List.of(turnover(), netWorth(),
        view("5.1", true, "SIMILAR_PROJECTS_COUNT_VALUE", "GTE", cr(25), 2, 7,
            GOLD_DOMAINS, null, null, null),
        view("6.1", true, "OTHER", "GTE", null, null, null, List.of(), null, null,
            treeJson(v2CertTree())),
        solvency(), desirable());
  }

  private static List<RequirementView> variantBViews() {
    return List.of(
        view("3.1", true, "AVG_ANNUAL_TURNOVER", "GTE", cr(110), null, 3, List.of(), null,
            null, null),
        view("3.2", true, "NET_WORTH", "GTE", cr(20), null, null, List.of(), null, null,
            null),
        view("3.3", true, "SIMILAR_PROJECTS_COUNT_VALUE", "GTE", cr(30), 1, 5,
            GOLD_DOMAINS, null, null, null),
        view("3.4", true, "CERTIFICATE_VALID", "GTE", null, null, null, List.of(),
            "ISO_9001", null, null));
  }

  private List<EvaluatedRequirement> evaluate(List<RequirementView> views,
      CompanySnapshot snapshot, EvaluationContext context) {
    List<EvaluatedRequirement> results = new ArrayList<>();
    long id = 101L;
    for (RequirementView view : views) {
      results.add(evaluator.evaluateView(id++, view, snapshot, context));
    }
    return results;
  }

  private static Map<String, Verdict> verdicts(List<EvaluatedRequirement> results) {
    Map<String, Verdict> map = new LinkedHashMap<>();
    for (EvaluatedRequirement result : results) {
      map.put(result.clauseRef(), result.verdict());
    }
    return map;
  }

  @Test
  void v1YieldsGoldenMatrix() {
    List<EvaluatedRequirement> results =
        evaluate(v1Views(), goldenSnapshot(), ctx(DEADLINE, E60));

    assertThat(verdicts(results)).isEqualTo(Map.of(
        "4.1", Verdict.PASS, "4.2", Verdict.PASS, "5.1", Verdict.FAIL,
        "6.1", Verdict.FAIL, "6.2", Verdict.UNKNOWN, "8.1", Verdict.PASS));
    assertThat(BidDecisionEngine.decide(results).recommendation()).isEqualTo("NO_BID");
    assertThat(ReadinessScore.of(results)).isEqualByComparingTo(new BigDecimal("40.00"));
    assertThat(RiskEngine.assess(results, DEADLINE, FIXED_CLOCK).stream()
        .map(RiskFinding::severity).toList())
        .containsExactly("CRITICAL", "CRITICAL", "HIGH");
  }

  @Test
  void v2YieldsGoldenMatrix() {
    List<EvaluatedRequirement> results =
        evaluate(v2Views(), goldenSnapshot(), ctx(DEADLINE, E60));

    assertThat(verdicts(results)).isEqualTo(Map.of(
        "4.1", Verdict.PASS, "4.2", Verdict.PASS, "5.1", Verdict.PASS,
        "6.1", Verdict.PASS, "6.2", Verdict.UNKNOWN, "8.1", Verdict.PASS));
    assertThat(BidDecisionEngine.decide(results).recommendation())
        .isEqualTo("CONDITIONAL_BID");
    assertThat(ReadinessScore.of(results)).isEqualByComparingTo(new BigDecimal("80.00"));
    assertThat(RiskEngine.assess(results, DEADLINE, FIXED_CLOCK)).hasSize(1);
  }

  @Test
  void v3WithSolvencyReachesBid() {
    CompanySnapshot base = goldenSnapshot();
    List<com.tenderops.company.Certificate> certs = new ArrayList<>(base.certificates());
    certs.add(cert(9L, "BANK_SOLVENCY_CERTIFICATE", null,
        java.time.LocalDate.of(2027, 3, 31), 21L, new BigDecimal("180000000.0000")));
    CompanySnapshot snapshot =
        new CompanySnapshot(base.financials(), base.projects(), certs);

    List<EvaluatedRequirement> results = evaluate(v2Views(), snapshot, ctx(DEADLINE, E60));

    assertThat(verdicts(results)).isEqualTo(Map.of(
        "4.1", Verdict.PASS, "4.2", Verdict.PASS, "5.1", Verdict.PASS,
        "6.1", Verdict.PASS, "6.2", Verdict.PASS, "8.1", Verdict.PASS));
    Decision decision = BidDecisionEngine.decide(results);
    assertThat(decision.recommendation()).isEqualTo("BID");
    assertThat(decision.approvalStatus()).isEqualTo("PENDING_HUMAN_APPROVAL");
    assertThat(ReadinessScore.of(results)).isEqualByComparingTo(new BigDecimal("100.00"));
    assertThat(RiskEngine.assess(results, DEADLINE, FIXED_CLOCK)).isEmpty();
  }

  @Test
  void emptyCompanyYieldsInsufficientData() {
    CompanySnapshot empty =
        new CompanySnapshot(List.of(), List.of(), List.of());
    List<EvaluatedRequirement> results = evaluate(v1Views(), empty, ctx(DEADLINE, E60));

    assertThat(results.stream().filter(EvaluatedRequirement::mandatory)
        .map(EvaluatedRequirement::verdict).toList())
        .containsExactly(Verdict.UNKNOWN, Verdict.UNKNOWN, Verdict.UNKNOWN, Verdict.UNKNOWN,
            Verdict.UNKNOWN);
    assertThat(BidDecisionEngine.decide(results).recommendation())
        .isEqualTo("INSUFFICIENT_DATA");
    assertThat(ReadinessScore.of(results)).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(RiskEngine.assess(results, DEADLINE, FIXED_CLOCK)).hasSize(5);
  }

  @Test
  void variantBYieldsGoldenMatrix() {
    List<EvaluatedRequirement> results = evaluate(variantBViews(), goldenSnapshot(),
        ctx(VARIANT_DEADLINE, E50));

    assertThat(verdicts(results)).isEqualTo(Map.of(
        "3.1", Verdict.FAIL, "3.2", Verdict.PASS, "3.3", Verdict.PASS,
        "3.4", Verdict.PASS));
    assertThat(BidDecisionEngine.decide(results).recommendation()).isEqualTo("NO_BID");
    assertThat(ReadinessScore.of(results)).isEqualByComparingTo(new BigDecimal("75.00"));
    List<RiskFinding> risks =
        RiskEngine.assess(results, VARIANT_DEADLINE, FIXED_CLOCK);
    assertThat(risks).hasSize(1);
    assertThat(risks.get(0).severity()).isEqualTo("CRITICAL");
  }

  @Test
  void falsePassRateIsZeroOnGoldenSets() {
    Map<String, Verdict> v1Expected = Map.of(
        "4.1", Verdict.PASS, "4.2", Verdict.PASS, "5.1", Verdict.FAIL,
        "6.1", Verdict.FAIL, "6.2", Verdict.UNKNOWN);
    Map<String, Verdict> v2Expected = Map.of(
        "4.1", Verdict.PASS, "4.2", Verdict.PASS, "5.1", Verdict.PASS,
        "6.1", Verdict.PASS, "6.2", Verdict.UNKNOWN);
    Map<String, Verdict> bExpected = Map.of(
        "3.1", Verdict.FAIL, "3.2", Verdict.PASS, "3.3", Verdict.PASS,
        "3.4", Verdict.PASS);

    int falsePasses = 0;
    falsePasses += countFalsePasses(
        evaluate(v1Views(), goldenSnapshot(), ctx(DEADLINE, E60)), v1Expected);
    falsePasses += countFalsePasses(
        evaluate(v2Views(), goldenSnapshot(), ctx(DEADLINE, E60)), v2Expected);
    falsePasses += countFalsePasses(
        evaluate(variantBViews(), goldenSnapshot(), ctx(VARIANT_DEADLINE, E50)), bExpected);

    assertThat(falsePasses).isZero();
  }

  private static int countFalsePasses(List<EvaluatedRequirement> results,
      Map<String, Verdict> expected) {
    int count = 0;
    for (EvaluatedRequirement result : results) {
      if (result.mandatory() && result.verdict() == Verdict.PASS
          && expected.get(result.clauseRef()) != Verdict.PASS) {
        count++;
      }
    }
    return count;
  }
}
