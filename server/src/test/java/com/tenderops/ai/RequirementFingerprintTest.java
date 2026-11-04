package com.tenderops.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Fingerprint stability + sensitivity (§6.11 canonical form). */
class RequirementFingerprintTest {

  @Test
  void stableAndSensitive() {
    String first = RequirementFingerprint.of("4.1", "AVG_ANNUAL_TURNOVER", "GTE",
        "Rs 100 Cr", null, 3, null, null, null, null, true);
    String second = RequirementFingerprint.of("4.1", "AVG_ANNUAL_TURNOVER", "GTE",
        "Rs 100 Cr", null, 3, null, null, null, null, true);
    assertThat(first).isEqualTo(second);
    assertThat(first).hasSize(64).matches("[0-9a-f]+");
    assertThat(RequirementFingerprint.of("4.1", "AVG_ANNUAL_TURNOVER", "GTE",
        "Rs 100 Cr", null, 3, null, null, null, null, false)).isNotEqualTo(first);
    assertThat(RequirementFingerprint.of("4.1", "AVG_ANNUAL_TURNOVER", "GTE",
        "Rs 101 Cr", null, 3, null, null, null, null, true)).isNotEqualTo(first);
    assertThat(RequirementFingerprint.of("4.1", "AVG_ANNUAL_TURNOVER", "GTE",
        "Rs 100 Cr", null, 5, null, null, null, null, true)).isNotEqualTo(first);
  }
}
