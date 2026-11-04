package com.tenderops.compliance;

import static com.tenderops.compliance.ComplianceFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenderops.ai.ConditionNode;
import com.tenderops.company.Certificate;
import com.tenderops.company.FinancialRecord;
import com.tenderops.company.Project;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * TC-INV-01..05: seeded randomized properties, 500 iterations each.
 * Every failure message names the seed and iteration for reproduction.
 */
class ComplianceInvariantsTest {

  private static final long SEED = 20261104L;
  private static final int ITERATIONS = 500;

  private static final List<LocalDate> FY_ENDS = List.of(
      LocalDate.of(2024, 3, 31), LocalDate.of(2025, 3, 31), LocalDate.of(2026, 3, 31));
  private static final List<String> FY_LABELS =
      List.of("FY2023-24", "FY2024-25", "FY2025-26");
  private static final String[] OPERATORS = {"GTE", "GT", "LTE", "LT", "EQ"};
  private static final String[] DOMAINS = {"SMART_CITY", "HIGHWAY", "METRO_RAIL"};

  private final RequirementEvaluator evaluator = new RequirementEvaluator(new ObjectMapper());

  private static BigDecimal randomMoney(Random random) {
    long rupees = 1_000_000L + random.nextInt(2_000_000_000);
    return BigDecimal.valueOf(rupees).setScale(random.nextInt(5));
  }

  private static List<FinancialRecord> randomFinancials(Random random) {
    int count = random.nextInt(4);
    List<FinancialRecord> records = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      FinancialRecord record = fin(i + 1L, FY_LABELS.get(i), FY_ENDS.get(i),
          random.nextDouble() < 0.08 ? null : randomMoney(random),
          random.nextDouble() < 0.5 ? null : randomMoney(random),
          random.nextDouble() < 0.8,
          random.nextDouble() < 0.8 ? 100L + i : null);
      if (random.nextDouble() < 0.1) {
        record.setCurrency("USD");
      }
      records.add(record);
    }
    return records;
  }

  private static List<Project> randomProjects(Random random) {
    int count = random.nextInt(5);
    List<Project> projects = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      double pick = random.nextDouble();
      LocalDate done = pick < 0.7 ? LocalDate.of(2025, 6, 30)
          : pick < 0.85 ? LocalDate.of(2015, 4, 1) : null;
      Project project = proj(i + 1L, "Random " + i,
          random.nextDouble() < 0.15 ? null : randomMoney(random),
          done, DOMAINS[random.nextInt(DOMAINS.length)],
          random.nextDouble() < 0.7 ? 200L + i : null);
      if (random.nextDouble() < 0.15) {
        project.setStatus("IN_PROGRESS");
      }
      if (random.nextDouble() < 0.1) {
        project.setCurrency("USD");
      }
      projects.add(project);
    }
    return projects;
  }

  private static List<Certificate> randomCertificates(Random random) {
    String[] names = {"CMMI", "ISO_9001", "ISO_27001"};
    int count = random.nextInt(4);
    List<Certificate> certs = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      double pick = random.nextDouble();
      LocalDate expiry = pick < 0.6 ? LocalDate.of(2027, 8, 31)
          : pick < 0.8 ? LocalDate.of(2026, 3, 31) : null;
      certs.add(cert(i + 1L, names[random.nextInt(names.length)],
          random.nextDouble() < 0.3 ? 2 + random.nextInt(4) : null,
          expiry, random.nextDouble() < 0.8 ? 300L + i : null, null));
    }
    return certs;
  }

  @Test
  void unknownOnlyWhenEvidenceMissing() {
    Random random = new Random(SEED);
    for (int iter = 0; iter < ITERATIONS; iter++) {
      try {
        List<FinancialRecord> records = randomFinancials(random);
        boolean complete = records.size() == 3 && records.stream()
            .allMatch(r -> r.getTurnover() != null && r.isAudited()
                && r.getDocumentId() != null && "INR".equals(r.getCurrency()));
        boolean missing = records.size() < 3
            || records.stream().anyMatch(r -> r.getTurnover() == null);
        LeafResult turnover = FinancialEvaluator.evaluateTurnover(
            finLeaf("AVG_ANNUAL_TURNOVER", "GTE", randomMoney(random), 3),
            records, ctx(DEADLINE, E60));
        if (complete) {
          assertThat(turnover.verdict()).isNotEqualTo(Verdict.UNKNOWN);
        }
        if (missing) {
          assertThat(turnover.verdict()).isNotEqualTo(Verdict.FAIL);
        }

        List<Project> projects = randomProjects(random);
        LeafResult counted = ProjectEvaluator.evaluateCountValue(
            countLeaf(randomMoney(random), 1 + random.nextInt(3),
                random.nextBoolean() ? 7 : null, GOLD_DOMAINS),
            projects, ctx(DEADLINE, E60));
        if (projects.isEmpty()) {
          assertThat(counted.verdict()).isEqualTo(Verdict.UNKNOWN);
        } else {
          assertThat(counted.verdict()).isNotEqualTo(Verdict.UNKNOWN);
        }

        List<Certificate> certs = randomCertificates(random);
        String wanted = random.nextBoolean() ? "CMMI" : "ISO_9999";
        boolean exactMatch =
            certs.stream().anyMatch(c -> wanted.equals(c.getName()));
        LeafResult certResult = CertificateEvaluator.evaluate(
            certLeaf(wanted, "CMMI".equals(wanted) ? 3 : null), certs, ctx(DEADLINE, E60));
        if (exactMatch) {
          assertThat(certResult.verdict()).isNotEqualTo(Verdict.UNKNOWN);
        } else {
          assertThat(certResult.verdict()).isEqualTo(Verdict.UNKNOWN);
        }
      } catch (AssertionError e) {
        throw new AssertionError(
            "seed " + SEED + " iteration " + iter + " failed: " + e.getMessage(), e);
      }
    }
  }

  @Test
  void unvaluedEligibleProjectForcesNeedsReview() {
    Random random = new Random(SEED);
    for (int iter = 0; iter < ITERATIONS; iter++) {
      try {
        List<Project> projects = List.of(
            proj(1L, "Valued", randomMoney(random), LocalDate.of(2025, 6, 30),
                "SMART_CITY", 201L),
            proj(2L, "Unvalued", null, LocalDate.of(2025, 6, 30), "HIGHWAY", 202L));

        LeafResult result = ProjectEvaluator.evaluateCountValue(
            countLeaf(randomMoney(random), 1, 7, GOLD_DOMAINS),
            projects, ctx(DEADLINE, E60));

        assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
      } catch (AssertionError e) {
        throw new AssertionError(
            "seed " + SEED + " iteration " + iter + " failed: " + e.getMessage(), e);
      }
    }
  }

  @Test
  void passAlwaysLinksEvidence() {
    Random random = new Random(SEED);
    ObjectMapper mapper = new ObjectMapper();
    for (int iter = 0; iter < ITERATIONS; iter++) {
      try {
        CompanySnapshot snapshot = new CompanySnapshot(randomFinancials(random),
            randomProjects(random), randomCertificates(random));
        EvaluationContext context = ctx(DEADLINE, E60);

        LeafResult turnover = FinancialEvaluator.evaluateTurnover(
            finLeaf("AVG_ANNUAL_TURNOVER", "GTE", randomMoney(random), 3),
            snapshot.financials(), context);
        if (turnover.verdict() == Verdict.PASS) {
          assertThat(turnover.evidenceIds()).isNotEmpty();
        }
        LeafResult counted = ProjectEvaluator.evaluateCountValue(
            countLeaf(randomMoney(random), 1 + random.nextInt(3), 7, GOLD_DOMAINS),
            snapshot.projects(), context);
        if (counted.verdict() == Verdict.PASS) {
          assertThat(counted.evidenceIds()).isNotEmpty();
        }
        LeafResult certResult = CertificateEvaluator.evaluate(
            certLeaf("CMMI", 3), snapshot.certificates(), context);
        if (certResult.verdict() == Verdict.PASS) {
          assertThat(certResult.evidenceIds()).isNotEmpty();
        }

        ConditionNode tree = ConditionNode.branch(random.nextBoolean() ? "AND" : "OR",
            List.of(
                ConditionNode.leaf("AVG_ANNUAL_TURNOVER", "GTE",
                    randomMoney(random).toPlainString(), null, 3, null, null, null),
                ConditionNode.leaf("SIMILAR_PROJECTS_COUNT_VALUE", "GTE",
                    randomMoney(random).toPlainString(), 1, 7, GOLD_DOMAINS, null,
                    null)));
        LeafResult treeResult = ConditionTreeEvaluator.evaluate(treeJson(tree),
            leaf -> evaluator.evaluateLeaf(leaf, snapshot, context), mapper);
        if (treeResult.verdict() == Verdict.PASS) {
          assertThat(treeResult.evidenceIds()).isNotEmpty();
        }
      } catch (AssertionError e) {
        throw new AssertionError(
            "seed " + SEED + " iteration " + iter + " failed: " + e.getMessage(), e);
      }
    }
  }

  @Test
  void engineMatchesBigIntegerReference() {
    Random random = new Random(SEED);
    for (int iter = 0; iter < ITERATIONS; iter++) {
      try {
        List<FinancialRecord> records = List.of(
            fin(1L, FY_LABELS.get(0), FY_ENDS.get(0), randomMoney(random), null, true,
                101L),
            fin(2L, FY_LABELS.get(1), FY_ENDS.get(1), randomMoney(random), null, true,
                102L),
            fin(3L, FY_LABELS.get(2), FY_ENDS.get(2), randomMoney(random), null, true,
                103L));
        BigDecimal threshold = randomMoney(random);
        String operator = OPERATORS[random.nextInt(OPERATORS.length)];
        LeafResult turnover = FinancialEvaluator.evaluateTurnover(
            finLeaf("AVG_ANNUAL_TURNOVER", operator, threshold, 3),
            records, ctx(DEADLINE, E60));
        List<BigDecimal> values = records.stream()
            .map(FinancialRecord::getTurnover).toList();
        assertThat(turnover.verdict())
            .isEqualTo(referenceTurnover(values, threshold, 3, operator));

        int projectCount = 1 + random.nextInt(4);
        List<Project> projects = new ArrayList<>();
        for (int i = 0; i < projectCount; i++) {
          projects.add(proj(i + 1L, "Ref " + i, randomMoney(random),
              LocalDate.of(2025, 6, 30), DOMAINS[i % 2], 200L + i));
        }
        BigDecimal floor = randomMoney(random);
        String countOp = OPERATORS[random.nextInt(OPERATORS.length)];
        int needed = 1 + random.nextInt(projectCount + 1);
        LeafInput countInput = new LeafInput("SIMILAR_PROJECTS_COUNT_VALUE", countOp,
            floor.toPlainString(), floor, needed, 7, GOLD_DOMAINS, null, null);
        LeafResult counted = ProjectEvaluator.evaluateCountValue(
            countInput, projects, ctx(DEADLINE, E60));
        List<BigDecimal> contractValues = projects.stream()
            .map(Project::getContractValue).toList();
        assertThat(counted.verdict())
            .isEqualTo(referenceCount(contractValues, floor, needed, countOp));

        BigDecimal estimated = randomMoney(random);
        LeafResult rule = ProjectEvaluator.evaluate805040(
            ruleLeaf(7, GOLD_DOMAINS), projects, ctx(DEADLINE, estimated));
        assertThat(rule.verdict()).isEqualTo(reference805040(contractValues, estimated));
      } catch (AssertionError e) {
        throw new AssertionError(
            "seed " + SEED + " iteration " + iter + " failed: " + e.getMessage(), e);
      }
    }
  }

  private static Verdict referenceTurnover(List<BigDecimal> values, BigDecimal threshold,
      int count, String operator) {
    int scale = threshold.scale();
    for (BigDecimal value : values) {
      scale = Math.max(scale, value.scale());
    }
    BigInteger sum = BigInteger.ZERO;
    for (BigDecimal value : values) {
      sum = sum.add(value.setScale(scale).unscaledValue());
    }
    BigInteger bound = threshold.setScale(scale).unscaledValue()
        .multiply(BigInteger.valueOf(count));
    return applyOperator(sum.compareTo(bound), operator) ? Verdict.PASS : Verdict.FAIL;
  }

  private static Verdict referenceCount(List<BigDecimal> values, BigDecimal floor,
      int needed, String operator) {
    int scale = floor.scale();
    for (BigDecimal value : values) {
      scale = Math.max(scale, value.scale());
    }
    BigInteger bound = floor.setScale(scale).unscaledValue();
    int qualifying = 0;
    for (BigDecimal value : values) {
      if (applyOperator(value.setScale(scale).unscaledValue().compareTo(bound),
          operator)) {
        qualifying++;
      }
    }
    return qualifying >= needed ? Verdict.PASS : Verdict.FAIL;
  }

  private static Verdict reference805040(List<BigDecimal> values, BigDecimal estimated) {
    int scale = estimated.scale();
    for (BigDecimal value : values) {
      scale = Math.max(scale, value.scale());
    }
    List<BigInteger> desc = new ArrayList<>();
    for (BigDecimal value : values) {
      desc.add(value.setScale(scale).unscaledValue());
    }
    desc.sort(Comparator.reverseOrder());
    BigInteger base = estimated.setScale(scale).unscaledValue();
    BigInteger hundred = BigInteger.valueOf(100);
    if (desc.size() >= 1
        && desc.get(0).multiply(hundred).compareTo(base.multiply(BigInteger.valueOf(80)))
            >= 0) {
      return Verdict.PASS;
    }
    if (desc.size() >= 2
        && desc.get(1).multiply(hundred).compareTo(base.multiply(BigInteger.valueOf(50)))
            >= 0) {
      return Verdict.PASS;
    }
    if (desc.size() >= 3
        && desc.get(2).multiply(hundred).compareTo(base.multiply(BigInteger.valueOf(40)))
            >= 0) {
      return Verdict.PASS;
    }
    return Verdict.FAIL;
  }

  private static boolean applyOperator(int comparison, String operator) {
    return switch (operator) {
      case "GTE" -> comparison >= 0;
      case "GT" -> comparison > 0;
      case "LTE" -> comparison <= 0;
      case "LT" -> comparison < 0;
      default -> comparison == 0;
    };
  }

  @Test
  void decisionFollowsFailAndBidInvariants() {
    Random random = new Random(SEED);
    Verdict[] values = Verdict.values();
    for (int iter = 0; iter < ITERATIONS; iter++) {
      try {
        int count = 1 + random.nextInt(10);
        List<EvaluatedRequirement> results = new ArrayList<>();
        boolean mandatoryFail = false;
        int mandatoryCount = 0;
        for (int i = 0; i < count; i++) {
          boolean mandatory = random.nextBoolean();
          Verdict verdict = values[random.nextInt(values.length)];
          if (mandatory) {
            mandatoryCount++;
            mandatoryFail = mandatoryFail || verdict == Verdict.FAIL;
          }
          results.add(new EvaluatedRequirement((long) i, "M." + i, mandatory, verdict,
              "TEST", "{}", List.of()));
        }

        Decision decision = BidDecisionEngine.decide(results);

        if (mandatoryFail) {
          assertThat(decision.recommendation()).isEqualTo("NO_BID");
        }
        if ("BID".equals(decision.recommendation())) {
          assertThat(results.stream().filter(EvaluatedRequirement::mandatory)
              .map(EvaluatedRequirement::verdict).toList())
              .containsOnly(Verdict.PASS);
        }
        if (mandatoryCount == 0) {
          assertThat(decision.recommendation()).isEqualTo("INSUFFICIENT_DATA");
        }
      } catch (AssertionError e) {
        throw new AssertionError(
            "seed " + SEED + " iteration " + iter + " failed: " + e.getMessage(), e);
      }
    }
  }

  @Test
  void repeatedRunsAreIdentical() {
    Random random = new Random(SEED);
    for (int iter = 0; iter < ITERATIONS; iter++) {
      try {
        CompanySnapshot snapshot = new CompanySnapshot(randomFinancials(random),
            randomProjects(random), randomCertificates(random));
        EvaluationContext context = ctx(DEADLINE, E60);
        List<RequirementView> views = List.of(
            view("4.1", true, "AVG_ANNUAL_TURNOVER", "GTE", randomMoney(random), null, 3,
                List.of(), null, null, null),
            view("5.1", true, "OTHER", "GTE", null, null, null, List.of(), null, null,
                treeJson(v1ProjectsTree())),
            view("6.1", true, "CERTIFICATE_VALID", "GTE", null, null, null, List.of(),
                "CMMI", 3, null));

        RequirementEvaluator first = new RequirementEvaluator(new ObjectMapper());
        RequirementEvaluator second = new RequirementEvaluator(new ObjectMapper());
        for (int i = 0; i < views.size(); i++) {
          EvaluatedRequirement left = first.evaluateView((long) i, views.get(i),
              snapshot, context);
          EvaluatedRequirement right = second.evaluateView((long) i, views.get(i),
              snapshot, context);
          assertThat(right.verdict()).isEqualTo(left.verdict());
          assertThat(right.traceJson()).isEqualTo(left.traceJson());
        }
      } catch (AssertionError e) {
        throw new AssertionError(
            "seed " + SEED + " iteration " + iter + " failed: " + e.getMessage(), e);
      }
    }
  }
}
