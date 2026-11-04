package com.tenderops.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** TC-AI-09: the grounding check flags numbers absent from the input facts. */
class GroundingCheckerTest {

  private static final List<String> FACTS = List.of(
      "Estimated contract value: Rs 60 Cr.",
      "EMD: Rs 1.2 Cr. Tender fee: Rs 50,000.",
      "Deadline: 15 December 2026, 17:00 IST.",
      "Readiness score 40.00. Decision NO_BID.");

  @Test
  void groundedSummaryPasses() {
    String summary = "The Rs 60 Cr tender needs Rs 1.2 Cr EMD and Rs 50,000 fee "
        + "by 15 December 2026. Score 40.00.";
    assertThat(GroundingChecker.ungroundedNumbers(summary, FACTS)).isEmpty();
  }

  @Test
  void inventedNumberIsFlagged() {
    String summary = "The Rs 60 Cr tender needs Rs 9.9 Cr EMD by 15 December 2026.";
    assertThat(GroundingChecker.ungroundedNumbers(summary, FACTS)).containsExactly("Rs 9.9 Cr");
  }

  @Test
  void inventedDateIsFlagged() {
    String summary = "Submit by 01 January 2027 for the Rs 60 Cr tender.";
    assertThat(GroundingChecker.ungroundedNumbers(summary, FACTS))
        .containsExactly("01", "2027", "01 January 2027");
  }

  @Test
  void blankSummaryHasNothingToGround() {
    assertThat(GroundingChecker.ungroundedNumbers("  ", FACTS)).isEmpty();
    assertThat(GroundingChecker.ungroundedNumbers(null, FACTS)).isEmpty();
  }
}
