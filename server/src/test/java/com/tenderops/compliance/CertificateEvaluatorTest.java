package com.tenderops.compliance;

import static com.tenderops.compliance.ComplianceFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenderops.company.Certificate;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** TC-CRT-01..06: expiry against the deadline, documents, levels, name normalization. */
class CertificateEvaluatorTest {

  @Test
  void expiredCmmiFails() {
    LeafResult result = CertificateEvaluator.evaluate(
        certLeaf("CMMI", 3), goldenSnapshot().certificates(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
    assertThat(result.reasonCode()).isEqualTo("CERT_EXPIRED");
  }

  @Test
  void missingCertificateIsUnknown() {
    LeafResult result = CertificateEvaluator.evaluate(
        certLeaf("ISO_14001", null), goldenSnapshot().certificates(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.UNKNOWN);
    assertThat(result.reasonCode()).isEqualTo("CERT_NOT_FOUND");
  }

  @Test
  void expiryOnDeadlineDayPasses() {
    List<Certificate> certs = List.of(
        cert(1L, "ISO_9001", null, LocalDate.of(2026, 12, 15), 31L, null));

    LeafResult result = CertificateEvaluator.evaluate(
        certLeaf("ISO_9001", null), certs, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    assertThat(result.evidenceIds()).containsExactly(1L);
  }

  @Test
  void validWithoutDocumentNeedsReview() {
    List<Certificate> certs = List.of(
        cert(1L, "ISO_9001", null, LocalDate.of(2027, 8, 31), null, null));

    LeafResult result = CertificateEvaluator.evaluate(
        certLeaf("ISO_9001", null), certs, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("MISSING_SUPPORTING_DOCUMENT");
  }

  @Test
  void orTreePassesViaValidIso() {
    CompanySnapshot snapshot = goldenSnapshot();
    LeafResult result = ConditionTreeEvaluator.evaluate(treeJson(v2CertTree()),
        leaf -> CertificateEvaluator.evaluate(leaf, snapshot.certificates(),
            ctx(DEADLINE, E60)),
        new ObjectMapper());

    assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    assertThat(result.evidenceIds()).containsExactly(6L);
  }

  @Test
  void higherLevelSatisfiesRequirement() {
    List<Certificate> certs = List.of(
        cert(1L, "CMMI", 5, LocalDate.of(2027, 8, 31), 31L, null));

    LeafResult result = CertificateEvaluator.evaluate(
        certLeaf("CMMI", 3), certs, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.PASS);
  }

  @Test
  void lowerLevelFailsRequirement() {
    List<Certificate> certs = List.of(
        cert(1L, "CMMI", 2, LocalDate.of(2027, 8, 31), 31L, null));

    LeafResult result = CertificateEvaluator.evaluate(
        certLeaf("CMMI", 3), certs, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
    assertThat(result.reasonCode()).isEqualTo("CERT_LEVEL_INSUFFICIENT");
  }

  @Test
  void namesMatchAcrossSeparators() {
    LeafResult result = CertificateEvaluator.evaluate(
        certLeaf("ISO 9001", null), goldenSnapshot().certificates(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    assertThat(result.evidenceIds()).containsExactly(6L);
  }

  @Test
  void storedNameWithSpacesMatches() {
    List<Certificate> certs = List.of(
        cert(1L, "ISO 9001", null, LocalDate.of(2027, 8, 31), 31L, null));

    LeafResult result = CertificateEvaluator.evaluate(
        certLeaf("ISO_9001", null), certs, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.PASS);
  }

  @Test
  void missingExpiryNeedsReview() {
    List<Certificate> certs = List.of(
        cert(1L, "ISO_9001", null, null, 31L, null));

    LeafResult result = CertificateEvaluator.evaluate(
        certLeaf("ISO_9001", null), certs, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("EXPIRY_MISSING");
  }

  @Test
  void missingNameNeedsReview() {
    LeafResult result = CertificateEvaluator.evaluate(
        certLeaf(null, null), goldenSnapshot().certificates(), ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("CERT_NAME_MISSING");
  }

  @Test
  void validCertificateWinsOverExpiredTwin() {
    List<Certificate> certs = List.of(
        cert(1L, "CMMI", 3, LocalDate.of(2026, 3, 31), 31L, null),
        cert(2L, "CMMI", 3, LocalDate.of(2027, 8, 31), 32L, null));

    LeafResult result = CertificateEvaluator.evaluate(
        certLeaf("CMMI", 3), certs, ctx(DEADLINE, E60));

    assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    assertThat(result.evidenceIds()).containsExactly(2L);
  }
}
