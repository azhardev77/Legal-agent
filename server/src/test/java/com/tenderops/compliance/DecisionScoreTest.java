package com.tenderops.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** TC-DEC-01..07, TC-SCR-01 formula pins, TC-INV-06 score bounds with a BigInteger oracle. */
class DecisionScoreTest {

  private static EvaluatedRequirement ev(long id, String clause, boolean mandatory,
      Verdict verdict) {
    return new EvaluatedRequirement(id, clause, mandatory, verdict, "TEST", "{}", List.of());
  }

  private static List<EvaluatedRequirement> mandatory(Verdict... verdicts) {
    List<EvaluatedRequirement> results = new ArrayList<>();
    for (int i = 0; i < verdicts.length; i++) {
      results.add(ev(i + 1L, "M." + (i + 1), true, verdicts[i]));
    }
    return results;
  }

  @Test
  void singleFailForcesNoBid() {
    List<EvaluatedRequirement> results = mandatory(Verdict.PASS, Verdict.PASS, Verdict.PASS,
        Verdict.PASS, Verdict.PASS, Verdict.PASS, Verdict.PASS, Verdict.PASS, Verdict.PASS,
        Verdict.FAIL);

    Decision decision = BidDecisionEngine.decide(results);

    assertThat(decision.recommendation()).isEqualTo("NO_BID");
    assertThat(ReadinessScore.of(results)).isEqualByComparingTo(new BigDecimal("90.00"));
  }

  @Test
  void fortyPercentUnknownIsInsufficientData() {
    List<EvaluatedRequirement> results = mandatory(Verdict.PASS, Verdict.PASS, Verdict.PASS,
        Verdict.UNKNOWN, Verdict.UNKNOWN);

    assertThat(BidDecisionEngine.decide(results).recommendation())
        .isEqualTo("INSUFFICIENT_DATA");
  }

  @Test
  void exactlyThirtyPercentUnknownIsConditional() {
    List<EvaluatedRequirement> results = mandatory(Verdict.PASS, Verdict.PASS, Verdict.PASS,
        Verdict.PASS, Verdict.PASS, Verdict.PASS, Verdict.PASS, Verdict.UNKNOWN,
        Verdict.UNKNOWN, Verdict.UNKNOWN);

    assertThat(BidDecisionEngine.decide(results).recommendation())
        .isEqualTo("CONDITIONAL_BID");
  }

  @Test
  void onlyNeedsReviewIsConditional() {
    List<EvaluatedRequirement> results =
        mandatory(Verdict.NEEDS_REVIEW, Verdict.NEEDS_REVIEW, Verdict.NEEDS_REVIEW);

    Decision decision = BidDecisionEngine.decide(results);

    assertThat(decision.recommendation()).isEqualTo("CONDITIONAL_BID");
    assertThat(ReadinessScore.of(results)).isEqualByComparingTo(new BigDecimal("50.00"));
  }

  @Test
  void allPassIsBidPendingApproval() {
    List<EvaluatedRequirement> results =
        mandatory(Verdict.PASS, Verdict.PASS, Verdict.PASS);

    Decision decision = BidDecisionEngine.decide(results);

    assertThat(decision.recommendation()).isEqualTo("BID");
    assertThat(decision.approvalStatus()).isEqualTo("PENDING_HUMAN_APPROVAL");
    assertThat(ReadinessScore.of(results)).isEqualByComparingTo(new BigDecimal("100.00"));
  }

  @Test
  void nonMandatoryFailWarnsWithoutBlocking() {
    List<EvaluatedRequirement> results = new ArrayList<>(mandatory(Verdict.PASS, Verdict.PASS));
    results.add(ev(3L, "8.1", false, Verdict.FAIL));

    Decision decision = BidDecisionEngine.decide(results);

    assertThat(decision.recommendation()).isEqualTo("BID");
    assertThat(decision.warnings()).hasSize(1);
    assertThat(decision.warnings().get(0)).contains("8.1");
    assertThat(ReadinessScore.of(results)).isEqualByComparingTo(new BigDecimal("100.00"));
  }

  @Test
  void zeroMandatoryIsInsufficientData() {
    assertThat(BidDecisionEngine.decide(List.of()).recommendation())
        .isEqualTo("INSUFFICIENT_DATA");
    assertThat(ReadinessScore.of(List.of())).isEqualByComparingTo(BigDecimal.ZERO);

    List<EvaluatedRequirement> onlyOptional =
        List.of(ev(1L, "8.1", false, Verdict.PASS));
    assertThat(BidDecisionEngine.decide(onlyOptional).recommendation())
        .isEqualTo("INSUFFICIENT_DATA");
    assertThat(ReadinessScore.of(onlyOptional)).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void goldenScoresFollowTheFormula() {
    assertThat(ReadinessScore.of(mandatory(Verdict.PASS, Verdict.PASS, Verdict.FAIL,
        Verdict.FAIL, Verdict.UNKNOWN))).isEqualByComparingTo(new BigDecimal("40.00"));
    assertThat(ReadinessScore.of(mandatory(Verdict.PASS, Verdict.PASS, Verdict.PASS,
        Verdict.PASS, Verdict.UNKNOWN))).isEqualByComparingTo(new BigDecimal("80.00"));
    assertThat(ReadinessScore.of(mandatory(Verdict.FAIL, Verdict.PASS, Verdict.PASS,
        Verdict.PASS))).isEqualByComparingTo(new BigDecimal("75.00"));
    assertThat(ReadinessScore.of(mandatory(Verdict.UNKNOWN, Verdict.UNKNOWN)))
        .isEqualByComparingTo(new BigDecimal("0.00"));
  }

  @Test
  void mixedScoresRoundHalfUp() {
    assertThat(ReadinessScore.of(
        mandatory(Verdict.PASS, Verdict.FAIL, Verdict.UNKNOWN)))
        .isEqualByComparingTo(new BigDecimal("33.33"));
    assertThat(ReadinessScore.of(
        mandatory(Verdict.PASS, Verdict.PASS, Verdict.UNKNOWN)))
        .isEqualByComparingTo(new BigDecimal("66.67"));
    assertThat(ReadinessScore.of(mandatory(Verdict.PASS, Verdict.NEEDS_REVIEW, Verdict.FAIL,
        Verdict.UNKNOWN))).isEqualByComparingTo(new BigDecimal("37.50"));
  }

  @Test
  void scoreBoundsAndFormulaHoldOnRandomMixes() {
    Random random = new Random(20261104L);
    Verdict[] values = Verdict.values();
    for (int iter = 0; iter < 500; iter++) {
      try {
        int count = 1 + random.nextInt(12);
        List<EvaluatedRequirement> results = new ArrayList<>();
        int pass = 0;
        int review = 0;
        for (int i = 0; i < count; i++) {
          Verdict verdict = values[random.nextInt(values.length)];
          if (verdict == Verdict.PASS) {
            pass++;
          }
          if (verdict == Verdict.NEEDS_REVIEW) {
            review++;
          }
          results.add(ev(i + 1L, "M." + (i + 1), true, verdict));
        }

        BigDecimal score = ReadinessScore.of(results);

        assertThat(score.compareTo(BigDecimal.ZERO) >= 0
            && score.compareTo(new BigDecimal("100.00")) <= 0).isTrue();
        assertThat(score).isEqualByComparingTo(oracleScore(pass, review, count));
      } catch (AssertionError e) {
        throw new AssertionError(
            "seed 20261104 iteration " + iter + " failed: " + e.getMessage(), e);
      }
    }
  }

  /** Independent score oracle: exact integer math, HALF_UP to 2dp, no BigDecimal divide. */
  private static BigDecimal oracleScore(int pass, int review, int count) {
    BigInteger hundred = BigInteger.valueOf(100);
    BigInteger numerator = BigInteger.valueOf(2L * pass + review)
        .multiply(hundred).multiply(hundred);
    BigInteger denominator = BigInteger.valueOf(2L * count);
    BigInteger[] div = numerator.divideAndRemainder(denominator);
    BigInteger rounded = div[1].multiply(BigInteger.TWO).compareTo(denominator) >= 0
        ? div[0].add(BigInteger.ONE)
        : div[0];
    return new BigDecimal(rounded).movePointLeft(2).setScale(2);
  }
}
