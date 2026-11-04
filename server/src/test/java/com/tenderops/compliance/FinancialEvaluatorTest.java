package com.tenderops.compliance;

import static com.tenderops.compliance.ComplianceFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.tenderops.company.Certificate;
import com.tenderops.company.FinancialRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** TC-FIN-01..08 and TC-CUR-01 (financial half). Exact BigDecimal math, never rounded. */
class FinancialEvaluatorTest {

  private static final List<LocalDate> FY_ENDS = List.of(
      LocalDate.of(2024, 3, 31), LocalDate.of(2025, 3, 31), LocalDate.of(2026, 3, 31));

  private static List<FinancialRecord> threeYears(BigDecimal first, BigDecimal second,
      BigDecimal third) {
    return List.of(
        fin(1L, "FY2023-24", FY_ENDS.get(0), first, null, true, 11L),
        fin(2L, "FY2024-25", FY_ENDS.get(1), second, null, true, 12L),
        fin(3L, "FY2025-26", FY_ENDS.get(2), third, null, true, 13L));
  }

  private static Certificate solvency(long id, BigDecimal amount, LocalDate expiry,
      Long docId) {
    return cert(id, "BANK_SOLVENCY_CERTIFICATE", null, expiry, docId, amount);
  }

  @Test
  void averageTurnoverPassesOnGoldenFigures() {
    LeafResult result = FinancialEvaluator.evaluateTurnover(
        finLeaf("AVG_ANNUAL_TURNOVER", "GTE", cr(100), 3),
        goldenSnapshot().financials(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    assertThat(result.reasonCode()).isEqualTo("THRESHOLD_MET");
    assertThat(result.evidenceIds()).containsExactly(1L, 2L, 3L);
    List<?> operands = (List<?>) result.trace().get("operands");
    assertThat(operands.stream().map(Object::toString).toList())
        .containsExactlyInAnyOrder("980000000.0000", "1050000000.0000", "1120000000.0000");
    assertThat(result.trace().get("sum")).isEqualTo("3150000000.0000");
    assertThat(result.trace().get("threshold")).isEqualTo("1000000000.0000");
  }

  @Test
  void averageEqualToThresholdPassesAcrossScales() {
    LeafResult result = FinancialEvaluator.evaluateTurnover(
        finLeaf("AVG_ANNUAL_TURNOVER", "GTE", new BigDecimal("1000000000"), 3),
        threeYears(cr(100), cr(100), cr(100)), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.PASS);
  }

  @Test
  void roundingTrapFailsWithoutDivision() {
    LeafResult result = FinancialEvaluator.evaluateTurnover(
        finLeaf("AVG_ANNUAL_TURNOVER", "GTE", new BigDecimal("1000000000"), 3),
        threeYears(new BigDecimal("1000000000.0000"), new BigDecimal("1000000000.0000"),
            new BigDecimal("999999999.9999")),
        ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
    assertThat(result.reasonCode()).isEqualTo("THRESHOLD_NOT_MET");
    assertThat(result.trace().get("sum")).isEqualTo("2999999999.9999");
  }

  @Test
  void fewerThanRequiredYearsIsUnknown() {
    List<FinancialRecord> two = List.of(
        fin(1L, "FY2024-25", FY_ENDS.get(1), cr(105), null, true, 12L),
        fin(2L, "FY2025-26", FY_ENDS.get(2), cr(112), null, true, 13L));

    LeafResult result = FinancialEvaluator.evaluateTurnover(
        finLeaf("AVG_ANNUAL_TURNOVER", "GTE", cr(100), 3), two, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.UNKNOWN);
    assertThat(result.reasonCode()).isEqualTo("FEWER_THAN_N_FY");
  }

  @Test
  void unauditedYearNeedsReview() {
    List<FinancialRecord> records = List.of(
        fin(1L, "FY2023-24", FY_ENDS.get(0), cr(98), null, true, 11L),
        fin(2L, "FY2024-25", FY_ENDS.get(1), cr(105), null, false, 12L),
        fin(3L, "FY2025-26", FY_ENDS.get(2), cr(112), null, true, 13L));

    LeafResult result = FinancialEvaluator.evaluateTurnover(
        finLeaf("AVG_ANNUAL_TURNOVER", "GTE", cr(100), 3), records, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("UNAUDITED_RECORD");
  }

  @Test
  void financialYearsAfterDeadlineAreExcluded() {
    List<FinancialRecord> records = List.of(
        fin(1L, "FY2024-25", FY_ENDS.get(1), cr(105), null, true, 12L),
        fin(2L, "FY2025-26", FY_ENDS.get(2), cr(112), null, true, 13L),
        fin(3L, "FY2026-27", LocalDate.of(2027, 3, 31), cr(200), null, true, 14L));

    LeafResult result = FinancialEvaluator.evaluateTurnover(
        finLeaf("AVG_ANNUAL_TURNOVER", "GTE", cr(100), 3), records, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.UNKNOWN);
    assertThat(result.reasonCode()).isEqualTo("FEWER_THAN_N_FY");
  }

  @Test
  void defaultWindowIsThreeYears() {
    LeafResult result = FinancialEvaluator.evaluateTurnover(
        finLeaf("AVG_ANNUAL_TURNOVER", "GTE", cr(100), null),
        goldenSnapshot().financials(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    assertThat(result.evidenceIds()).hasSize(3);
  }

  @Test
  void zeroWindowNeedsReview() {
    LeafResult result = FinancialEvaluator.evaluateTurnover(
        finLeaf("AVG_ANNUAL_TURNOVER", "GTE", cr(100), 0),
        goldenSnapshot().financials(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("AMBIGUOUS_WINDOW");
  }

  @Test
  void generalOperatorsApplyToAverages() {
    List<FinancialRecord> records = threeYears(cr(100), cr(100), cr(100));

    assertThat(FinancialEvaluator.evaluateTurnover(
        finLeaf("AVG_ANNUAL_TURNOVER", "LTE", cr(100), 3), records,
        ctx(DEADLINE, E60)).verdict()).isEqualTo(Verdict.PASS);
    assertThat(FinancialEvaluator.evaluateTurnover(
        finLeaf("AVG_ANNUAL_TURNOVER", "LT", cr(100), 3), records,
        ctx(DEADLINE, E60)).verdict()).isEqualTo(Verdict.FAIL);
    assertThat(FinancialEvaluator.evaluateTurnover(
        finLeaf("AVG_ANNUAL_TURNOVER", "GT", cr(99), 3), records,
        ctx(DEADLINE, E60)).verdict()).isEqualTo(Verdict.PASS);
  }

  @Test
  void turnoverUnknownWhenValueMissing() {
    LeafResult result = FinancialEvaluator.evaluateTurnover(
        finLeaf("AVG_ANNUAL_TURNOVER", "GTE", cr(100), 3),
        threeYears(cr(98), null, cr(112)), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.UNKNOWN);
    assertThat(result.reasonCode()).isEqualTo("VALUE_MISSING");
  }

  @Test
  void turnoverNeedsReviewWithoutDocument() {
    List<FinancialRecord> records = List.of(
        fin(1L, "FY2023-24", FY_ENDS.get(0), cr(98), null, true, null),
        fin(2L, "FY2024-25", FY_ENDS.get(1), cr(105), null, true, 12L),
        fin(3L, "FY2025-26", FY_ENDS.get(2), cr(112), null, true, 13L));

    LeafResult result = FinancialEvaluator.evaluateTurnover(
        finLeaf("AVG_ANNUAL_TURNOVER", "GTE", cr(100), 3), records, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("MISSING_SUPPORTING_DOCUMENT");
  }

  @Test
  void turnoverNeedsReviewForForeignCurrencyRecord() {
    FinancialRecord foreign =
        fin(1L, "FY2023-24", FY_ENDS.get(0), cr(98), null, true, 11L);
    foreign.setCurrency("USD");
    List<FinancialRecord> records = List.of(foreign,
        fin(2L, "FY2024-25", FY_ENDS.get(1), cr(105), null, true, 12L),
        fin(3L, "FY2025-26", FY_ENDS.get(2), cr(112), null, true, 13L));

    LeafResult result = FinancialEvaluator.evaluateTurnover(
        finLeaf("AVG_ANNUAL_TURNOVER", "GTE", cr(100), 3), records, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("CURRENCY_UNSUPPORTED");
  }

  @Test
  void netWorthPassesAboveThreshold() {
    LeafResult result = FinancialEvaluator.evaluateNetWorth(
        finLeaf("NET_WORTH", "GTE", cr(30), null),
        goldenSnapshot().financials(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    assertThat(result.evidenceIds()).containsExactly(3L);
  }

  @Test
  void netWorthFailsBelowThreshold() {
    List<FinancialRecord> records = List.of(
        fin(1L, "FY2025-26", FY_ENDS.get(2), cr(112),
            new BigDecimal("299999000.0000"), true, 13L));

    LeafResult result = FinancialEvaluator.evaluateNetWorth(
        finLeaf("NET_WORTH", "GTE", cr(30), null), records, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
  }

  @Test
  void netWorthUnknownWithoutRecords() {
    LeafResult result = FinancialEvaluator.evaluateNetWorth(
        finLeaf("NET_WORTH", "GTE", cr(30), null), List.of(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.UNKNOWN);
    assertThat(result.reasonCode()).isEqualTo("NO_RECORDS");
  }

  @Test
  void netWorthUsesLatestAuditedYear() {
    List<FinancialRecord> records = List.of(
        fin(1L, "FY2024-25", FY_ENDS.get(1), cr(105), cr(20), true, 12L),
        fin(2L, "FY2025-26", FY_ENDS.get(2), cr(112), cr(35), false, 13L));

    LeafResult result = FinancialEvaluator.evaluateNetWorth(
        finLeaf("NET_WORTH", "GTE", cr(30), null), records, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
    assertThat(result.evidenceIds()).containsExactly(1L);
  }

  @Test
  void netWorthNeedsReviewWhenNoneAudited() {
    List<FinancialRecord> records = List.of(
        fin(1L, "FY2025-26", FY_ENDS.get(2), cr(112), cr(35), false, 13L));

    LeafResult result = FinancialEvaluator.evaluateNetWorth(
        finLeaf("NET_WORTH", "GTE", cr(30), null), records, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("NO_AUDITED_RECORD");
  }

  @Test
  void netWorthNeedsReviewWithoutDocument() {
    List<FinancialRecord> records = List.of(
        fin(1L, "FY2025-26", FY_ENDS.get(2), cr(112), cr(35), true, null));

    LeafResult result = FinancialEvaluator.evaluateNetWorth(
        finLeaf("NET_WORTH", "GTE", cr(30), null), records, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("MISSING_SUPPORTING_DOCUMENT");
  }

  @Test
  void netWorthUnknownWhenValueMissing() {
    List<FinancialRecord> records = List.of(
        fin(1L, "FY2025-26", FY_ENDS.get(2), cr(112), null, true, 13L));

    LeafResult result = FinancialEvaluator.evaluateNetWorth(
        finLeaf("NET_WORTH", "GTE", cr(30), null), records, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.UNKNOWN);
    assertThat(result.reasonCode()).isEqualTo("VALUE_MISSING");
  }

  @Test
  void solvencyUnknownWithoutCertificate() {
    LeafResult result = FinancialEvaluator.evaluateSolvency(
        solvLeaf(cr(15)), List.of(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.UNKNOWN);
    assertThat(result.reasonCode()).isEqualTo("NO_SOLVENCY_CERTIFICATE");
  }

  @Test
  void solvencyFailsBelowThreshold() {
    LeafResult result = FinancialEvaluator.evaluateSolvency(
        solvLeaf(cr(15)),
        List.of(solvency(1L, new BigDecimal("149999000.0000"),
            LocalDate.of(2027, 3, 31), 21L)),
        ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
    assertThat(result.reasonCode()).isEqualTo("THRESHOLD_NOT_MET");
  }

  @Test
  void solvencyPassesWhenValidAndDocumented() {
    LeafResult result = FinancialEvaluator.evaluateSolvency(
        solvLeaf(cr(15)),
        List.of(solvency(1L, cr(15), LocalDate.of(2027, 3, 31), 21L)),
        ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    assertThat(result.evidenceIds()).containsExactly(1L);
  }

  @Test
  void solvencyNeedsReviewWithoutDocument() {
    LeafResult result = FinancialEvaluator.evaluateSolvency(
        solvLeaf(cr(15)),
        List.of(solvency(1L, cr(15), LocalDate.of(2027, 3, 31), null)),
        ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("MISSING_SUPPORTING_DOCUMENT");
  }

  @Test
  void solvencyFailsWhenExpired() {
    LeafResult result = FinancialEvaluator.evaluateSolvency(
        solvLeaf(cr(15)),
        List.of(solvency(1L, cr(15), LocalDate.of(2026, 12, 1), 21L)),
        ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
    assertThat(result.reasonCode()).isEqualTo("CERT_EXPIRED");
  }

  @Test
  void solvencyNeedsReviewWithoutExpiry() {
    LeafResult result = FinancialEvaluator.evaluateSolvency(
        solvLeaf(cr(15)),
        List.of(solvency(1L, cr(15), null, 21L)),
        ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("EXPIRY_MISSING");
  }

  @Test
  void solvencyUnknownWithoutAmount() {
    LeafResult result = FinancialEvaluator.evaluateSolvency(
        solvLeaf(cr(15)),
        List.of(solvency(1L, null, LocalDate.of(2027, 3, 31), 21L)),
        ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.UNKNOWN);
    assertThat(result.reasonCode()).isEqualTo("VALUE_MISSING");
  }

  @Test
  void solvencyNeedsReviewForForeignCurrency() {
    Certificate foreign = solvency(1L, cr(15), LocalDate.of(2027, 3, 31), 21L);
    foreign.setCurrency("USD");

    LeafResult result = FinancialEvaluator.evaluateSolvency(
        solvLeaf(cr(15)), List.of(foreign), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("CURRENCY_UNSUPPORTED");
  }
}
