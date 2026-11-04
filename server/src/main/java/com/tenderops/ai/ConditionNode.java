package com.tenderops.ai;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AND/OR condition tree (§6.2, §6.6). A node is a branch iff {@code op} is set,
 * else a leaf (metric required). Canonical form sorts domain tags recursively so
 * fingerprints are stable regardless of model output order.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConditionNode(
    String op,
    List<ConditionNode> children,
    String metric,
    String operator,
    String amountText,
    Integer countValue,
    Integer lookbackYears,
    List<String> domainTags,
    String certificateName,
    Integer certificateLevel) {

  @JsonIgnore
  public boolean isBranch() {
    return op != null;
  }

  public static ConditionNode branch(String op, List<ConditionNode> children) {
    return new ConditionNode(op, List.copyOf(children), null, null, null, null, null, null,
        null, null);
  }

  public static ConditionNode leaf(String metric, String operator, String amountText,
      Integer countValue, Integer lookbackYears, List<String> domainTags,
      String certificateName, Integer certificateLevel) {
    return new ConditionNode(null, null, metric, operator, amountText, countValue,
        lookbackYears, domainTags == null ? null : List.copyOf(domainTags), certificateName,
        certificateLevel);
  }

  /** Canonical copy: domain tags sorted at every level. */
  public ConditionNode canonical() {
    List<String> tags = null;
    if (domainTags != null) {
      tags = new ArrayList<>(domainTags);
      Collections.sort(tags);
    }
    List<ConditionNode> kids = null;
    if (children != null) {
      kids = children.stream().map(ConditionNode::canonical).toList();
    }
    return new ConditionNode(op, kids, metric, operator, amountText, countValue,
        lookbackYears, tags, certificateName, certificateLevel);
  }
}
