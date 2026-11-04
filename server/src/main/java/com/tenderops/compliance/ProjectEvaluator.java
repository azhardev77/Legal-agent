package com.tenderops.compliance;

import com.tenderops.company.Project;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic project evaluators (section 6.4). FAIL verdicts are per company
 * database: the database is treated as the complete project record.
 */
public final class ProjectEvaluator {

  private static final String PROVENANCE = "company_projects";
  private static final BigDecimal P80 = new BigDecimal("0.80");
  private static final BigDecimal P50 = new BigDecimal("0.50");
  private static final BigDecimal P40 = new BigDecimal("0.40");

  private ProjectEvaluator() {
  }

  public static LeafResult evaluateCountValue(LeafInput input, List<Project> projects,
      EvaluationContext context) {
    if (projects.isEmpty()) {
      return new TraceBuilder(Verdict.UNKNOWN, "NO_PROJECT_RECORDS",
          "no project records for the company", context).build(List.of(), PROVENANCE);
    }
    if (input.lookbackYears() != null && input.lookbackYears() < 0) {
      return new TraceBuilder(Verdict.NEEDS_REVIEW, "AMBIGUOUS_WINDOW",
          "lookback years must not be negative", context).build(List.of(), PROVENANCE);
    }
    if (input.amountValue() == null) {
      return new TraceBuilder(Verdict.NEEDS_REVIEW, "AMOUNT_MISSING",
          "threshold amount is missing", context).build(List.of(), PROVENANCE);
    }
    if (input.countValue() == null || input.countValue() <= 0) {
      return new TraceBuilder(Verdict.NEEDS_REVIEW, "COUNT_MISSING",
          "required project count is missing", context).build(List.of(), PROVENANCE);
    }
    Partition partition = partition(input, projects, context.deadlineDate());
    CompareOperator operator = CompareOperator.of(input.operator());
    String formula = countFormula(input, operator);
    List<Project> documentedQualifying =
        qualifying(partition.documented(), input.amountValue(), operator);
    if (documentedQualifying.size() >= input.countValue()) {
      if (!partition.unvalued().isEmpty()) {
        return new TraceBuilder(Verdict.NEEDS_REVIEW, "INCOMPLETE_PROJECT_EVIDENCE",
            formula + " holds on documented projects but "
                + partition.unvalued().size()
                + " eligible project(s) lack usable values",
            context).put("operands", operands(partition.documented()))
            .put("threshold", input.amountValue().toPlainString())
            .put("comparison", documentedQualifying.size() + " of " + input.countValue()
                + " required (documented)")
            .build(ids(partition.documented()), PROVENANCE);
      }
      return new TraceBuilder(Verdict.PASS, "CONDITION_MET", formula, context)
          .put("operands", operands(partition.documented()))
          .put("threshold", input.amountValue().toPlainString())
          .put("comparison", documentedQualifying.size() + " of " + input.countValue()
              + " required (documented)")
          .build(ids(documentedQualifying), PROVENANCE);
    }
    List<Project> fullQualifying =
        qualifying(partition.valued(), input.amountValue(), operator);
    if (fullQualifying.size() >= input.countValue()) {
      return new TraceBuilder(Verdict.NEEDS_REVIEW, "MISSING_COMPLETION_CERTIFICATE",
          formula + " holds only when undocumented projects are counted", context)
          .put("operands", operands(partition.valued()))
          .put("threshold", input.amountValue().toPlainString())
          .put("comparison", fullQualifying.size() + " of " + input.countValue()
              + " required (all eligible)")
          .build(ids(fullQualifying), PROVENANCE);
    }
    if (!partition.unvalued().isEmpty()) {
      return new TraceBuilder(Verdict.NEEDS_REVIEW, "INCOMPLETE_PROJECT_EVIDENCE",
          formula + " cannot be decided: " + partition.unvalued().size()
              + " eligible project(s) lack usable values",
          context).put("operands", operands(partition.valued()))
          .put("threshold", input.amountValue().toPlainString())
          .build(ids(partition.valued()), PROVENANCE);
    }
    return new TraceBuilder(Verdict.FAIL, "CONDITION_NOT_MET",
        formula + " (per company database)", context)
        .put("operands", operands(partition.valued()))
        .put("threshold", input.amountValue().toPlainString())
        .put("comparison", fullQualifying.size() + " of " + input.countValue()
            + " required (all eligible)")
        .build(ids(partition.valued()), PROVENANCE);
  }

  public static LeafResult evaluate805040(LeafInput input, List<Project> projects,
      EvaluationContext context) {
    if (projects.isEmpty()) {
      return new TraceBuilder(Verdict.UNKNOWN, "NO_PROJECT_RECORDS",
          "no project records for the company", context).build(List.of(), PROVENANCE);
    }
    if (input.lookbackYears() != null && input.lookbackYears() < 0) {
      return new TraceBuilder(Verdict.NEEDS_REVIEW, "AMBIGUOUS_WINDOW",
          "lookback years must not be negative", context).build(List.of(), PROVENANCE);
    }
    BigDecimal estimated = context.estimatedValue();
    if (estimated == null || estimated.compareTo(BigDecimal.ZERO) <= 0) {
      return new TraceBuilder(Verdict.UNKNOWN, "ESTIMATED_VALUE_MISSING",
          "tender has no usable estimated contract value", context)
          .build(List.of(), PROVENANCE);
    }
    Partition partition = partition(input, projects, context.deadlineDate());
    Branch documented = branch(partition.documented(), estimated);
    if (documented.satisfied()) {
      if (!partition.unvalued().isEmpty()) {
        return ruleTrace(Verdict.NEEDS_REVIEW, "INCOMPLETE_PROJECT_EVIDENCE",
            partition.documented(), estimated, documented,
            " holds on documented projects but " + partition.unvalued().size()
                + " eligible project(s) lack usable values",
            context, ids(partition.documented()));
      }
      return ruleTrace(Verdict.PASS, "CONDITION_MET", partition.documented(), estimated,
          documented, "", context, documented.projectIds());
    }
    Branch full = branch(partition.valued(), estimated);
    if (full.satisfied()) {
      return ruleTrace(Verdict.NEEDS_REVIEW, "MISSING_COMPLETION_CERTIFICATE",
          partition.valued(), estimated, full,
          " holds only when undocumented projects are counted", context,
          full.projectIds());
    }
    if (!partition.unvalued().isEmpty()) {
      return ruleTrace(Verdict.NEEDS_REVIEW, "INCOMPLETE_PROJECT_EVIDENCE",
          partition.valued(), estimated, full,
          " cannot be decided: " + partition.unvalued().size()
              + " eligible project(s) lack usable values",
          context, ids(partition.valued()));
    }
    return ruleTrace(Verdict.FAIL, "CONDITION_NOT_MET", partition.valued(), estimated,
        full, " (per company database)", context, ids(partition.valued()));
  }

  private static LeafResult ruleTrace(Verdict verdict, String reason,
      List<Project> considered, BigDecimal estimated, Branch branch, String suffix,
      EvaluationContext context, List<Long> evidence) {
    return new TraceBuilder(verdict, reason,
        "80/50/40 over " + considered.size() + " eligible project(s)" + suffix, context)
        .put("operands", operands(considered))
        .put("estimatedValue", estimated.toPlainString())
        .put("branch80", estimated.multiply(P80).toPlainString())
        .put("branch50", estimated.multiply(P50).toPlainString())
        .put("branch40", estimated.multiply(P40).toPlainString())
        .put("comparison", branch.describe(estimated))
        .build(evidence, PROVENANCE);
  }

  private static String countFormula(LeafInput input, CompareOperator operator) {
    return "at least " + input.countValue() + " distinct eligible project(s) each "
        + operator.symbol() + " " + input.amountValue().toPlainString();
  }

  private record Partition(List<Project> valued, List<Project> documented,
      List<Project> unvalued) {
  }

  private static Partition partition(LeafInput input, List<Project> projects,
      LocalDate deadline) {
    List<Project> valued = new ArrayList<>();
    List<Project> unvalued = new ArrayList<>();
    for (Project project : projects) {
      if (!"COMPLETED".equalsIgnoreCase(project.getStatus())) {
        continue;
      }
      if (!domainMatches(input.domainTags(), project.getDomainCategory())) {
        continue;
      }
      if (input.lookbackYears() != null) {
        if (project.getCompletionDate() == null) {
          unvalued.add(project);
          continue;
        }
        LocalDate earliest = deadline.minusYears(input.lookbackYears());
        if (project.getCompletionDate().isBefore(earliest)
            || project.getCompletionDate().isAfter(deadline)) {
          continue;
        }
      }
      if (project.getContractValue() == null
          || !TextNorms.isInr(project.getCurrency())) {
        unvalued.add(project);
        continue;
      }
      valued.add(project);
    }
    List<Project> documented = new ArrayList<>();
    for (Project project : valued) {
      if (project.getCompletionCertDocId() != null) {
        documented.add(project);
      }
    }
    return new Partition(valued, documented, unvalued);
  }

  private static boolean domainMatches(List<String> tags, String domain) {
    if (tags == null || tags.isEmpty()) {
      return true;
    }
    String project = TextNorms.alphaNumUpper(domain);
    if (project == null || project.isEmpty()) {
      return false;
    }
    for (String tag : tags) {
      if (project.equals(TextNorms.alphaNumUpper(tag))) {
        return true;
      }
    }
    return false;
  }

  private static List<Project> qualifying(List<Project> projects, BigDecimal threshold,
      CompareOperator operator) {
    List<Project> out = new ArrayList<>();
    for (Project project : projects) {
      if (operator.test(project.getContractValue().compareTo(threshold))) {
        out.add(project);
      }
    }
    return out;
  }

  private record Branch(boolean satisfied, List<Long> projectIds, List<String> values) {
    String describe(BigDecimal estimated) {
      StringBuilder out = new StringBuilder();
      String[] branches = {"80", "50", "40"};
      BigDecimal[] factors = {P80, P50, P40};
      for (int i = 0; i < 3; i++) {
        if (i > 0) {
          out.append("; ");
        }
        out.append("v").append(i + 1).append(" ");
        if (i < values.size()) {
          out.append(values.get(i)).append(" vs ")
              .append(estimated.multiply(factors[i]).toPlainString());
        } else {
          out.append("missing vs ")
              .append(estimated.multiply(factors[i]).toPlainString());
        }
        out.append(" (").append(branches[i]).append("%)");
      }
      return out.toString();
    }
  }

  private static Branch branch(List<Project> projects, BigDecimal estimated) {
    List<Project> desc = new ArrayList<>(projects);
    desc.sort(Comparator.comparing(Project::getContractValue, Comparator.reverseOrder()));
    List<String> values = new ArrayList<>();
    for (Project project : desc) {
      values.add(project.getContractValue().toPlainString());
    }
    if (desc.size() >= 1
        && desc.get(0).getContractValue().compareTo(estimated.multiply(P80)) >= 0) {
      return new Branch(true, List.of(desc.get(0).getId()), values);
    }
    if (desc.size() >= 2
        && desc.get(1).getContractValue().compareTo(estimated.multiply(P50)) >= 0) {
      return new Branch(true, List.of(desc.get(0).getId(), desc.get(1).getId()), values);
    }
    if (desc.size() >= 3
        && desc.get(2).getContractValue().compareTo(estimated.multiply(P40)) >= 0) {
      return new Branch(true,
          List.of(desc.get(0).getId(), desc.get(1).getId(), desc.get(2).getId()), values);
    }
    return new Branch(false, List.of(), values);
  }

  private static List<String> operands(List<Project> projects) {
    List<BigDecimal> values = new ArrayList<>();
    for (Project project : projects) {
      values.add(project.getContractValue());
    }
    values.sort(Comparator.reverseOrder());
    List<String> out = new ArrayList<>();
    for (BigDecimal value : values) {
      out.add(value.toPlainString());
    }
    return out;
  }

  private static List<Long> ids(List<Project> projects) {
    List<Long> ids = new ArrayList<>();
    for (Project project : projects) {
      ids.add(project.getId());
    }
    return ids;
  }
}
