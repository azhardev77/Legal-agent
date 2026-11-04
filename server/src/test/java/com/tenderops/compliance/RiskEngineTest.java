package com.tenderops.compliance;

import static com.tenderops.compliance.ComplianceFixtures.DEADLINE;
import static com.tenderops.compliance.ComplianceFixtures.FIXED_CLOCK;
import static com.tenderops.compliance.ComplianceFixtures.IST;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** TC-RSK-01..03: deterministic severity mapping plus clock-driven deadline risks. */
class RiskEngineTest {

  private static EvaluatedRequirement ev(String clause, boolean mandatory, Verdict verdict) {
    return new EvaluatedRequirement(1L, clause, mandatory, verdict, "TEST", "{}", List.of());
  }

  private static List<EvaluatedRequirement> v1Results() {
    return List.of(
        ev("4.1", true, Verdict.PASS),
        ev("4.2", true, Verdict.PASS),
        ev("5.1", true, Verdict.FAIL),
        ev("6.1", true, Verdict.FAIL),
        ev("6.2", true, Verdict.UNKNOWN),
        ev("8.1", false, Verdict.PASS));
  }

  @Test
  void goldenV1HasTwoCriticalAndOneHigh() {
    List<RiskFinding> risks = RiskEngine.assess(v1Results(), DEADLINE, FIXED_CLOCK);

    assertThat(risks.stream().map(RiskFinding::severity).toList())
        .containsExactly("CRITICAL", "CRITICAL", "HIGH");
    assertThat(risks.stream().map(RiskFinding::sourceClause).toList())
        .containsExactly("5.1", "6.1", "6.2");
    assertThat(risks.stream().map(RiskFinding::riskCode).toList()).containsExactly(
        "MANDATORY_REQUIREMENT_FAILED", "MANDATORY_REQUIREMENT_FAILED",
        "MISSING_DOCUMENTATION");
    assertThat(risks).allSatisfy(risk -> {
      assertThat(risk.evidenceSummary()).isNotBlank();
      assertThat(risk.detectionReason()).isNotBlank();
      assertThat(risk.businessImpact()).isNotBlank();
      assertThat(risk.correctiveAction()).isNotBlank();
    });
  }

  @Test
  void goldenV2HasOneHigh() {
    List<EvaluatedRequirement> results = List.of(
        ev("4.1", true, Verdict.PASS),
        ev("4.2", true, Verdict.PASS),
        ev("5.1", true, Verdict.PASS),
        ev("6.1", true, Verdict.PASS),
        ev("6.2", true, Verdict.UNKNOWN),
        ev("8.1", false, Verdict.PASS));

    List<RiskFinding> risks = RiskEngine.assess(results, DEADLINE, FIXED_CLOCK);

    assertThat(risks).hasSize(1);
    assertThat(risks.get(0).severity()).isEqualTo("HIGH");
    assertThat(risks.get(0).sourceClause()).isEqualTo("6.2");
  }

  @Test
  void variantBHasOneCritical() {
    List<EvaluatedRequirement> results = List.of(
        ev("3.1", true, Verdict.FAIL),
        ev("3.2", true, Verdict.PASS),
        ev("3.3", true, Verdict.PASS),
        ev("3.4", true, Verdict.PASS));

    List<RiskFinding> risks = RiskEngine.assess(results, DEADLINE, FIXED_CLOCK);

    assertThat(risks).hasSize(1);
    assertThat(risks.get(0).severity()).isEqualTo("CRITICAL");
    assertThat(risks.get(0).sourceClause()).isEqualTo("3.1");
  }

  @Test
  void approachingDeadlineWithUnknownRaisesHigh() {
    Clock december10 = Clock.fixed(Instant.parse("2026-12-10T04:30:00Z"), IST);
    List<EvaluatedRequirement> results =
        List.of(ev("6.2", true, Verdict.UNKNOWN));

    List<RiskFinding> risks = RiskEngine.assess(results, DEADLINE, december10);

    assertThat(risks.stream().map(RiskFinding::riskCode).toList())
        .contains("SUBMISSION_DEADLINE_APPROACHING");
    assertThat(risks.stream()
        .filter(risk -> "SUBMISSION_DEADLINE_APPROACHING".equals(risk.riskCode()))
        .map(RiskFinding::severity).toList()).containsExactly("HIGH");
  }

  @Test
  void distantDeadlineRaisesNoDeadlineRisk() {
    List<EvaluatedRequirement> results =
        List.of(ev("6.2", true, Verdict.UNKNOWN));

    List<RiskFinding> risks = RiskEngine.assess(results, DEADLINE, FIXED_CLOCK);

    assertThat(risks.stream().map(RiskFinding::riskCode).toList())
        .containsExactly("MISSING_DOCUMENTATION");
  }

  @Test
  void passedDeadlineRaisesCritical() {
    Clock december16 = Clock.fixed(Instant.parse("2026-12-16T04:30:00Z"), IST);
    List<EvaluatedRequirement> results =
        List.of(ev("4.1", true, Verdict.PASS));

    List<RiskFinding> risks = RiskEngine.assess(results, DEADLINE, december16);

    assertThat(risks).hasSize(1);
    assertThat(risks.get(0).severity()).isEqualTo("CRITICAL");
    assertThat(risks.get(0).riskCode()).isEqualTo("SUBMISSION_DEADLINE_PASSED");
  }

  @Test
  void needsReviewIsMediumAndOptionalFailIsLow() {
    List<EvaluatedRequirement> results = List.of(
        ev("4.1", true, Verdict.NEEDS_REVIEW),
        ev("8.1", false, Verdict.FAIL),
        ev("8.2", false, Verdict.UNKNOWN));

    List<RiskFinding> risks = RiskEngine.assess(results, DEADLINE, FIXED_CLOCK);

    assertThat(risks.stream().map(RiskFinding::severity).toList())
        .containsExactly("MEDIUM", "LOW");
  }

  @Test
  void allPassWithDistantDeadlineHasNoRisks() {
    List<EvaluatedRequirement> results = List.of(
        ev("4.1", true, Verdict.PASS),
        ev("8.1", false, Verdict.PASS));

    assertThat(RiskEngine.assess(results, DEADLINE, FIXED_CLOCK)).isEmpty();
  }
}
