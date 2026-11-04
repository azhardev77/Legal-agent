package com.tenderops.compliance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenderops.ai.ConditionNode;
import com.tenderops.company.Certificate;
import com.tenderops.company.FinancialRecord;
import com.tenderops.company.Project;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/** Shared M5 fixtures: fixed clock, golden section-5 company snapshot, input builders. */
public final class ComplianceFixtures {

  public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
  public static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-11-04T04:30:00Z"), IST);
  public static final OffsetDateTime DEADLINE =
      OffsetDateTime.parse("2026-12-15T17:00+05:30");
  public static final OffsetDateTime VARIANT_DEADLINE =
      OffsetDateTime.parse("2026-12-20T17:00+05:30");
  public static final BigDecimal E60 = new BigDecimal("600000000.0000");
  public static final BigDecimal E50 = new BigDecimal("500000000.0000");
  public static final BigDecimal CONF = new BigDecimal("0.8500");
  public static final List<String> GOLD_DOMAINS = List.of("SMART_CITY", "HIGHWAY");

  private ComplianceFixtures() {
  }

  public static BigDecimal cr(long crores) {
    return BigDecimal.valueOf(crores).multiply(new BigDecimal("10000000")).setScale(4);
  }

  /** Sets a JPA id on an in-memory entity (unit fixtures are never persisted). */
  public static void setId(Object entity, long id) {
    try {
      Field field = entity.getClass().getDeclaredField("id");
      field.setAccessible(true);
      field.set(entity, id);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  public static FinancialRecord fin(long id, String fy, LocalDate end, BigDecimal turnover,
      BigDecimal netWorth, boolean audited, Long docId) {
    FinancialRecord record = new FinancialRecord(1L, fy, end);
    record.setTurnover(turnover);
    record.setNetWorth(netWorth);
    record.setAudited(audited);
    record.setDocumentId(docId);
    setId(record, id);
    return record;
  }

  public static Project proj(long id, String name, BigDecimal value, LocalDate done,
      String domain, Long certDocId) {
    Project project = new Project(1L, name);
    project.setContractValue(value);
    project.setCompletionDate(done);
    project.setDomainCategory(domain);
    project.setCompletionCertDocId(certDocId);
    setId(project, id);
    return project;
  }

  public static Certificate cert(long id, String name, Integer level, LocalDate expiry,
      Long docId, BigDecimal amount) {
    Certificate certificate = new Certificate(1L, name);
    certificate.setLevelNumber(level);
    certificate.setExpiryDate(expiry);
    certificate.setDocumentId(docId);
    certificate.setAmount(amount);
    setId(certificate, id);
    return certificate;
  }

  /** Section-5 demo company: 98/105/112 Cr turnover, 35 Cr net worth, 2 projects, 3 certs. */
  public static CompanySnapshot goldenSnapshot() {
    List<FinancialRecord> fins = List.of(
        fin(1L, "FY2023-24", LocalDate.of(2024, 3, 31), cr(98), null, true, 11L),
        fin(2L, "FY2024-25", LocalDate.of(2025, 3, 31), cr(105), null, true, 12L),
        fin(3L, "FY2025-26", LocalDate.of(2026, 3, 31), cr(112), cr(35), true, 13L));
    List<Project> projs = List.of(
        proj(4L, "Smart City Integrated Command Centre", cr(38),
            LocalDate.of(2024, 6, 30), "SMART_CITY", 14L),
        proj(5L, "Highway ITS Corridor", cr(29), LocalDate.of(2023, 3, 31), "HIGHWAY", 15L));
    List<Certificate> certs = List.of(
        cert(6L, "ISO_9001", null, LocalDate.of(2027, 8, 31), 16L, null),
        cert(7L, "ISO_27001", null, LocalDate.of(2027, 5, 31), 17L, null),
        cert(8L, "CMMI", 3, LocalDate.of(2026, 3, 31), 18L, null));
    return new CompanySnapshot(fins, projs, certs);
  }

  public static EvaluationContext ctx(OffsetDateTime deadline, BigDecimal estimated) {
    return EvaluationContext.of(deadline, estimated, "1.0", 7L);
  }

  public static RequirementView view(String clauseRef, boolean mandatory, String metric,
      String operator, BigDecimal amountValue, Integer countValue, Integer lookbackYears,
      List<String> domainTags, String certificateName, Integer certificateLevel,
      String conditionTree) {
    String amountText = amountValue == null ? null : amountValue.toPlainString();
    return new RequirementView(clauseRef, mandatory, metric, operator, amountText,
        amountValue, "INR", countValue, lookbackYears, domainTags, certificateName,
        certificateLevel, conditionTree, true, CONF);
  }

  public static LeafInput finLeaf(String metric, String operator, BigDecimal amount,
      Integer lookback) {
    return new LeafInput(metric, operator, amount == null ? null : amount.toPlainString(),
        amount, null, lookback, List.of(), null, null);
  }

  public static LeafInput countLeaf(BigDecimal amount, int count, Integer lookback,
      List<String> domains) {
    return new LeafInput("SIMILAR_PROJECTS_COUNT_VALUE", "GTE", amount.toPlainString(),
        amount, count, lookback, domains, null, null);
  }

  public static LeafInput ruleLeaf(Integer lookback, List<String> domains) {
    return new LeafInput("SIMILAR_PROJECTS_80_50_40", "GTE", null, null, null, lookback,
        domains, null, null);
  }

  public static LeafInput certLeaf(String name, Integer level) {
    return new LeafInput("CERTIFICATE_VALID", "GTE", null, null, null, null, List.of(),
        name, level);
  }

  public static LeafInput solvLeaf(BigDecimal amount) {
    return new LeafInput("SOLVENCY_CERTIFICATE", "GTE", amount.toPlainString(), amount,
        null, null, List.of(), null, null);
  }

  public static String treeJson(ConditionNode node) {
    try {
      return new ObjectMapper().writeValueAsString(node.canonical());
    } catch (JsonProcessingException e) {
      throw new AssertionError(e);
    }
  }

  /** Golden v1 clause 5.1: OR(3x25Cr count leaf, 80/50/40 leaf), lookback 7, both domains. */
  public static ConditionNode v1ProjectsTree() {
    return ConditionNode.branch("OR", List.of(
        ConditionNode.leaf("SIMILAR_PROJECTS_COUNT_VALUE", "GTE", "Rs 25 Cr", 3, 7,
            GOLD_DOMAINS, null, null),
        ConditionNode.leaf("SIMILAR_PROJECTS_80_50_40", "GTE", null, null, 7,
            GOLD_DOMAINS, null, null)));
  }

  /** Golden v2 clause 6.1: OR(CMMI L3, ISO 9001). */
  public static ConditionNode v2CertTree() {
    return ConditionNode.branch("OR", List.of(
        ConditionNode.leaf("CERTIFICATE_VALID", "GTE", null, null, null, null, "CMMI", 3),
        ConditionNode.leaf("CERTIFICATE_VALID", "GTE", null, null, null, null,
            "ISO_9001", null)));
  }
}
