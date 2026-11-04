package com.tenderops.compliance;

import static com.tenderops.compliance.ComplianceFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.tenderops.company.Project;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** TC-PRJ-01..08: eligibility windows, domains, documented-vs-full ladder, 80/50/40 rule. */
class ProjectEvaluatorTest {

  private static final LocalDate RECENT = LocalDate.of(2025, 6, 30);

  private static Project documented(long id, java.math.BigDecimal value, LocalDate done,
      String domain) {
    return proj(id, "Project " + id, value, done, domain, 100L + id);
  }

  @Test
  void goldenCountOfThreeFails() {
    LeafResult result = ProjectEvaluator.evaluateCountValue(
        countLeaf(cr(25), 3, 7, GOLD_DOMAINS),
        goldenSnapshot().projects(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
    assertThat(result.reasonCode()).isEqualTo("CONDITION_NOT_MET");
  }

  @Test
  void goldenCountOfTwoPasses() {
    LeafResult result = ProjectEvaluator.evaluateCountValue(
        countLeaf(cr(25), 2, 7, GOLD_DOMAINS),
        goldenSnapshot().projects(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    assertThat(result.evidenceIds()).containsExactly(4L, 5L);
  }

  @Test
  void goldenRule805040Fails() {
    LeafResult result = ProjectEvaluator.evaluate805040(
        ruleLeaf(7, GOLD_DOMAINS), goldenSnapshot().projects(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
  }

  @Test
  void projectOutsideLookbackIsExcluded() {
    List<Project> projects = List.of(
        documented(1L, cr(50), LocalDate.of(2019, 1, 1), "SMART_CITY"));

    LeafResult result = ProjectEvaluator.evaluateCountValue(
        countLeaf(cr(25), 1, 7, GOLD_DOMAINS), projects, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
  }

  @Test
  void domainMismatchIsExcluded() {
    List<Project> projects = List.of(
        documented(1L, cr(50), RECENT, "METRO_RAIL"));

    LeafResult result = ProjectEvaluator.evaluateCountValue(
        countLeaf(cr(25), 1, 7, List.of("SMART_CITY")), projects, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
  }

  @Test
  void emptyDomainTagsMatchAnyDomain() {
    List<Project> projects = List.of(
        documented(1L, cr(50), RECENT, "METRO_RAIL"));

    LeafResult result = ProjectEvaluator.evaluateCountValue(
        countLeaf(cr(25), 1, 7, List.of()), projects, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.PASS);
  }

  @Test
  void missingCompletionCertificateNeedsReview() {
    List<Project> projects = List.of(
        proj(1L, "Undocumented", cr(38), RECENT, "SMART_CITY", null));

    LeafResult result = ProjectEvaluator.evaluateCountValue(
        countLeaf(cr(25), 1, 7, GOLD_DOMAINS), projects, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("MISSING_COMPLETION_CERTIFICATE");
  }

  @Test
  void rule805040PassesOnEightyPercentBranch() {
    List<Project> projects = List.of(documented(1L, cr(48), RECENT, "SMART_CITY"));

    LeafResult result = ProjectEvaluator.evaluate805040(
        ruleLeaf(7, GOLD_DOMAINS), projects, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.PASS);
  }

  @Test
  void rule805040PassesOnFiftyPercentBranch() {
    List<Project> projects = List.of(
        documented(1L, cr(30), RECENT, "SMART_CITY"),
        documented(2L, cr(30), RECENT, "HIGHWAY"));

    LeafResult result = ProjectEvaluator.evaluate805040(
        ruleLeaf(7, GOLD_DOMAINS), projects, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.PASS);
  }

  @Test
  void rule805040PassesOnFortyPercentBranch() {
    List<Project> projects = List.of(
        documented(1L, cr(24), RECENT, "SMART_CITY"),
        documented(2L, cr(24), RECENT, "HIGHWAY"),
        documented(3L, cr(24), RECENT, "SMART_CITY"));

    LeafResult result = ProjectEvaluator.evaluate805040(
        ruleLeaf(7, GOLD_DOMAINS), projects, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.PASS);
  }

  @Test
  void rule805040FailsJustBelowBoundary() {
    List<Project> projects = List.of(
        documented(1L, new java.math.BigDecimal("479999000.0000"), RECENT, "SMART_CITY"));

    LeafResult result = ProjectEvaluator.evaluate805040(
        ruleLeaf(7, GOLD_DOMAINS), projects, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
  }

  @Test
  void zeroRecordsIsUnknown() {
    assertThat(ProjectEvaluator.evaluateCountValue(
        countLeaf(cr(25), 1, 7, GOLD_DOMAINS), List.of(),
        ctx(DEADLINE, E60)).verdict()).isEqualTo(Verdict.UNKNOWN);
    assertThat(ProjectEvaluator.evaluate805040(
        ruleLeaf(7, GOLD_DOMAINS), List.of(), ctx(DEADLINE, E60)).verdict())
        .isEqualTo(Verdict.UNKNOWN);
  }

  @Test
  void nonCompletedProjectIsExcluded() {
    Project ongoing = documented(1L, cr(50), RECENT, "SMART_CITY");
    ongoing.setStatus("IN_PROGRESS");

    LeafResult result = ProjectEvaluator.evaluateCountValue(
        countLeaf(cr(25), 1, 7, GOLD_DOMAINS), List.of(ongoing), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
  }

  @Test
  void missingValueCapsResultAtNeedsReview() {
    List<Project> projects = List.of(
        documented(1L, cr(30), RECENT, "SMART_CITY"),
        documented(2L, cr(30), RECENT, "HIGHWAY"),
        documented(3L, null, RECENT, "SMART_CITY"));

    LeafResult result = ProjectEvaluator.evaluateCountValue(
        countLeaf(cr(25), 2, 7, GOLD_DOMAINS), projects, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("INCOMPLETE_PROJECT_EVIDENCE");
  }

  @Test
  void foreignCurrencyProjectNeedsReview() {
    Project foreign = documented(1L, cr(50), RECENT, "SMART_CITY");
    foreign.setCurrency("USD");

    LeafResult result = ProjectEvaluator.evaluateCountValue(
        countLeaf(cr(25), 1, 7, GOLD_DOMAINS), List.of(foreign), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
  }

  @Test
  void rule805040UnknownWithoutEstimatedValue() {
    LeafResult result = ProjectEvaluator.evaluate805040(
        ruleLeaf(7, GOLD_DOMAINS), goldenSnapshot().projects(), ctx(DEADLINE, null));

    assertThat(result.verdict()).isEqualTo(Verdict.UNKNOWN);
    assertThat(result.reasonCode()).isEqualTo("ESTIMATED_VALUE_MISSING");
  }

  @Test
  void missingDateNeedsReviewWhenWindowed() {
    List<Project> projects = List.of(documented(1L, cr(50), null, "SMART_CITY"));

    LeafResult result = ProjectEvaluator.evaluateCountValue(
        countLeaf(cr(25), 1, 7, GOLD_DOMAINS), projects, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
  }

  @Test
  void missingDateIsIgnoredWithoutWindow() {
    List<Project> projects = List.of(documented(1L, cr(50), null, "SMART_CITY"));

    LeafResult result = ProjectEvaluator.evaluateCountValue(
        countLeaf(cr(25), 1, null, GOLD_DOMAINS), projects, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.PASS);
  }

  @Test
  void exactValueBoundaryPasses() {
    List<Project> projects = List.of(documented(1L, cr(25), RECENT, "HIGHWAY"));

    LeafResult result = ProjectEvaluator.evaluateCountValue(
        countLeaf(cr(25), 1, 7, GOLD_DOMAINS), projects, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.PASS);
  }

  @Test
  void negativeLookbackNeedsReview() {
    LeafResult result = ProjectEvaluator.evaluateCountValue(
        countLeaf(cr(25), 1, -2, GOLD_DOMAINS),
        goldenSnapshot().projects(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("AMBIGUOUS_WINDOW");
  }

  @Test
  void missingCountNeedsReview() {
    LeafInput input = new LeafInput("SIMILAR_PROJECTS_COUNT_VALUE", "GTE",
        cr(25).toPlainString(), cr(25), null, 7, GOLD_DOMAINS, null, null);

    LeafResult result = ProjectEvaluator.evaluateCountValue(
        input, goldenSnapshot().projects(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("COUNT_MISSING");
  }
}
