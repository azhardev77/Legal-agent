package com.tenderops.compliance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenderops.ai.ConditionNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Recursive AND/OR evaluation (section 6.6). Evidence links the decisive branches:
 * passing branches for a passing OR, failing branches for a failing AND,
 * every branch otherwise. Malformed trees are NEEDS_REVIEW, never an exception.
 */
public final class ConditionTreeEvaluator {

  private ConditionTreeEvaluator() {
  }

  public static LeafResult evaluate(String treeJson, Function<LeafInput, LeafResult> leaves,
      ObjectMapper mapper) {
    final ConditionNode root;
    try {
      root = mapper.readValue(treeJson, ConditionNode.class);
    } catch (JsonProcessingException | IllegalArgumentException e) {
      return invalid();
    }
    if (root == null) {
      return invalid();
    }
    return evaluateNode(root, leaves);
  }

  private static LeafResult evaluateNode(ConditionNode node,
      Function<LeafInput, LeafResult> leaves) {
    if (!node.isBranch()) {
      LeafInput input = new LeafInput(node.metric(), node.operator(), node.amountText(),
          null, node.countValue(), node.lookbackYears(),
          node.domainTags() == null ? List.of() : List.copyOf(node.domainTags()),
          node.certificateName(), node.certificateLevel());
      LeafResult result = leaves.apply(input);
      return result == null ? invalid() : result;
    }
    if ((!"AND".equals(node.op()) && !"OR".equals(node.op()))
        || node.children() == null || node.children().isEmpty()) {
      return invalid();
    }
    boolean and = "AND".equals(node.op());
    List<LeafResult> children = new ArrayList<>();
    for (ConditionNode child : node.children()) {
      if (child == null) {
        return invalid();
      }
      children.add(evaluateNode(child, leaves));
    }
    List<Verdict> verdicts = new ArrayList<>();
    for (LeafResult child : children) {
      verdicts.add(child.verdict());
    }
    Verdict verdict = and ? Verdict.andOf(verdicts) : Verdict.orOf(verdicts);
    List<LeafResult> decisive = decisive(children, verdict, and);
    List<String> names = new ArrayList<>();
    for (LeafResult child : children) {
      names.add(child.verdict().name());
    }
    List<Map<String, Object>> childTraces = new ArrayList<>();
    for (LeafResult child : children) {
      childTraces.add(child.trace());
    }
    List<Long> evidence = new ArrayList<>();
    for (LeafResult child : decisive) {
      evidence.addAll(child.evidenceIds());
    }
    evidence = evidence.stream().filter(id -> id != null).distinct().sorted().toList();
    String reason = and ? "TREE_AND" : "TREE_OR";
    Map<String, Object> trace = new LinkedHashMap<>();
    trace.put("formula", node.op() + "(" + String.join(", ", names) + ")");
    trace.put("verdict", verdict.name());
    trace.put("reasonCode", reason);
    trace.put("children", childTraces);
    trace.put("evidenceIds", evidence);
    trace.put("provenance", "condition_tree");
    return new LeafResult(verdict, reason, Collections.unmodifiableMap(trace), evidence);
  }

  private static List<LeafResult> decisive(List<LeafResult> children, Verdict verdict,
      boolean and) {
    if (!and && verdict == Verdict.PASS) {
      return children.stream().filter(child -> child.verdict() == Verdict.PASS).toList();
    }
    if (and && verdict == Verdict.FAIL) {
      return children.stream().filter(child -> child.verdict() == Verdict.FAIL).toList();
    }
    return children;
  }

  private static LeafResult invalid() {
    Map<String, Object> trace = new LinkedHashMap<>();
    trace.put("formula", "condition tree is missing or malformed");
    trace.put("verdict", Verdict.NEEDS_REVIEW.name());
    trace.put("reasonCode", "TREE_INVALID");
    trace.put("evidenceIds", List.of());
    trace.put("provenance", "condition_tree");
    return new LeafResult(Verdict.NEEDS_REVIEW, "TREE_INVALID",
        Collections.unmodifiableMap(trace), List.of());
  }
}
