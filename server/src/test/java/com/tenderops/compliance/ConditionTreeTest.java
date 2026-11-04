package com.tenderops.compliance;

import static com.tenderops.compliance.ComplianceFixtures.treeJson;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenderops.ai.ConditionNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** TC-LOG-01: full AND/OR truth tables over the four verdicts, plus nesting and errors. */
class ConditionTreeTest {

  private static LeafResult canned(Verdict verdict, List<Long> evidenceIds) {
    return new LeafResult(verdict, "STUB", Map.of("verdict", verdict.name()), evidenceIds);
  }

  private static LeafResult eval(String op, Verdict... verdicts) {
    Map<String, Verdict> byMetric = new LinkedHashMap<>();
    List<ConditionNode> kids = new ArrayList<>();
    int index = 0;
    for (Verdict verdict : verdicts) {
      String metric = "METRIC_" + index++;
      byMetric.put(metric, verdict);
      kids.add(ConditionNode.leaf(metric, "GTE", null, null, null, null, null, null));
    }
    String json = treeJson(ConditionNode.branch(op, kids));
    return ConditionTreeEvaluator.evaluate(json,
        leaf -> canned(byMetric.get(leaf.metric()), List.of()), new ObjectMapper());
  }

  private static Verdict andTable(Verdict left, Verdict right) {
    return switch (left) {
      case PASS -> right;
      case FAIL -> Verdict.FAIL;
      case UNKNOWN -> right == Verdict.FAIL ? Verdict.FAIL : Verdict.UNKNOWN;
      case NEEDS_REVIEW -> switch (right) {
        case FAIL -> Verdict.FAIL;
        case UNKNOWN -> Verdict.UNKNOWN;
        default -> Verdict.NEEDS_REVIEW;
      };
    };
  }

  private static Verdict orTable(Verdict left, Verdict right) {
    return switch (left) {
      case PASS -> Verdict.PASS;
      case FAIL -> right;
      case UNKNOWN -> switch (right) {
        case PASS -> Verdict.PASS;
        case NEEDS_REVIEW -> Verdict.NEEDS_REVIEW;
        default -> Verdict.UNKNOWN;
      };
      case NEEDS_REVIEW -> switch (right) {
        case PASS -> Verdict.PASS;
        default -> Verdict.NEEDS_REVIEW;
      };
    };
  }

  @Test
  void andTruthTable() {
    for (Verdict left : Verdict.values()) {
      for (Verdict right : Verdict.values()) {
        assertThat(eval("AND", left, right).verdict())
            .as("AND of %s and %s", left, right)
            .isEqualTo(andTable(left, right));
      }
    }
  }

  @Test
  void orTruthTable() {
    for (Verdict left : Verdict.values()) {
      for (Verdict right : Verdict.values()) {
        assertThat(eval("OR", left, right).verdict())
            .as("OR of %s and %s", left, right)
            .isEqualTo(orTable(left, right));
      }
    }
  }

  @Test
  void threeOperandSpots() {
    assertThat(eval("AND", Verdict.PASS, Verdict.UNKNOWN, Verdict.NEEDS_REVIEW).verdict())
        .isEqualTo(Verdict.UNKNOWN);
    assertThat(eval("AND", Verdict.FAIL, Verdict.UNKNOWN, Verdict.NEEDS_REVIEW).verdict())
        .isEqualTo(Verdict.FAIL);
    assertThat(eval("OR", Verdict.FAIL, Verdict.UNKNOWN, Verdict.NEEDS_REVIEW).verdict())
        .isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(eval("OR", Verdict.FAIL, Verdict.FAIL, Verdict.UNKNOWN).verdict())
        .isEqualTo(Verdict.UNKNOWN);
    assertThat(eval("OR", Verdict.PASS, Verdict.FAIL, Verdict.UNKNOWN).verdict())
        .isEqualTo(Verdict.PASS);
  }

  @Test
  void nestedBranchesCombineRecursively() {
    ConditionNode inner = ConditionNode.branch("OR", List.of(
        ConditionNode.leaf("A", "GTE", null, null, null, null, null, null),
        ConditionNode.leaf("B", "GTE", null, null, null, null, null, null)));
    ConditionNode root = ConditionNode.branch("AND", List.of(inner,
        ConditionNode.leaf("C", "GTE", null, null, null, null, null, null)));
    Map<String, Verdict> verdicts =
        Map.of("A", Verdict.PASS, "B", Verdict.FAIL, "C", Verdict.UNKNOWN);

    LeafResult result = ConditionTreeEvaluator.evaluate(treeJson(root),
        leaf -> canned(verdicts.get(leaf.metric()), List.of()), new ObjectMapper());

    assertThat(result.verdict()).isEqualTo(Verdict.UNKNOWN);
  }

  @Test
  void singleLeafRootEvaluatesTheLeaf() {
    ConditionNode root = ConditionNode.leaf("A", "GTE", null, null, null, null, null, null);

    LeafResult result = ConditionTreeEvaluator.evaluate(treeJson(root),
        leaf -> canned(Verdict.PASS, List.of(9L)), new ObjectMapper());

    assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    assertThat(result.evidenceIds()).containsExactly(9L);
  }

  @Test
  void orEvidenceComesFromPassingBranches() {
    ConditionNode root = ConditionNode.branch("OR", List.of(
        ConditionNode.leaf("A", "GTE", null, null, null, null, null, null),
        ConditionNode.leaf("B", "GTE", null, null, null, null, null, null)));

    LeafResult result = ConditionTreeEvaluator.evaluate(treeJson(root), leaf -> {
      if ("A".equals(leaf.metric())) {
        return canned(Verdict.FAIL, List.of(2L));
      }
      return canned(Verdict.PASS, List.of(1L));
    }, new ObjectMapper());

    assertThat(result.verdict()).isEqualTo(Verdict.PASS);
    assertThat(result.evidenceIds()).containsExactly(1L);
  }

  @Test
  void andEvidenceComesFromFailingBranches() {
    ConditionNode root = ConditionNode.branch("AND", List.of(
        ConditionNode.leaf("A", "GTE", null, null, null, null, null, null),
        ConditionNode.leaf("B", "GTE", null, null, null, null, null, null)));

    LeafResult result = ConditionTreeEvaluator.evaluate(treeJson(root), leaf -> {
      if ("A".equals(leaf.metric())) {
        return canned(Verdict.FAIL, List.of(2L));
      }
      return canned(Verdict.PASS, List.of(1L));
    }, new ObjectMapper());

    assertThat(result.verdict()).isEqualTo(Verdict.FAIL);
    assertThat(result.evidenceIds()).containsExactly(2L);
  }

  @Test
  void unanimousBranchesKeepAllEvidence() {
    ConditionNode allPass = ConditionNode.branch("AND", List.of(
        ConditionNode.leaf("A", "GTE", null, null, null, null, null, null),
        ConditionNode.leaf("B", "GTE", null, null, null, null, null, null)));
    ConditionNode allFail = ConditionNode.branch("OR", List.of(
        ConditionNode.leaf("A", "GTE", null, null, null, null, null, null),
        ConditionNode.leaf("B", "GTE", null, null, null, null, null, null)));

    LeafResult pass = ConditionTreeEvaluator.evaluate(treeJson(allPass), leaf -> {
      if ("A".equals(leaf.metric())) {
        return canned(Verdict.PASS, List.of(2L));
      }
      return canned(Verdict.PASS, List.of(1L));
    }, new ObjectMapper());
    LeafResult fail = ConditionTreeEvaluator.evaluate(treeJson(allFail), leaf -> {
      if ("A".equals(leaf.metric())) {
        return canned(Verdict.FAIL, List.of(2L));
      }
      return canned(Verdict.FAIL, List.of(1L));
    }, new ObjectMapper());

    assertThat(pass.evidenceIds()).containsExactly(1L, 2L);
    assertThat(fail.evidenceIds()).containsExactly(1L, 2L);
  }

  @Test
  void malformedTreeNeedsReview() {
    LeafResult result = ConditionTreeEvaluator.evaluate("not a tree",
        leaf -> canned(Verdict.PASS, List.of()), new ObjectMapper());

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("TREE_INVALID");
  }

  @Test
  void unknownOperatorNeedsReview() {
    ConditionNode root = ConditionNode.branch("XOR", List.of(
        ConditionNode.leaf("A", "GTE", null, null, null, null, null, null)));

    LeafResult result = ConditionTreeEvaluator.evaluate(treeJson(root),
        leaf -> canned(Verdict.PASS, List.of()), new ObjectMapper());

    assertThat(result.verdict()).isEqualTo(Verdict.NEEDS_REVIEW);
    assertThat(result.reasonCode()).isEqualTo("TREE_INVALID");
  }
}
