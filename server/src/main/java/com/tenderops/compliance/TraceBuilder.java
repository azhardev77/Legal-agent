package com.tenderops.compliance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds section-6.7 traces with deterministic key order and sorted evidence ids. */
final class TraceBuilder {

  private final Verdict verdict;
  private final String reasonCode;
  private final Map<String, Object> map = new LinkedHashMap<>();

  TraceBuilder(Verdict verdict, String reasonCode, String formula,
      EvaluationContext context) {
    this.verdict = verdict;
    this.reasonCode = reasonCode;
    map.put("formula", formula);
    map.put("verdict", verdict.name());
    map.put("reasonCode", reasonCode);
    map.put("ruleVersion", context.ruleVersion());
    map.put("contextHash", context.contextHash());
  }

  TraceBuilder put(String key, Object value) {
    map.put(key, value);
    return this;
  }

  LeafResult build(List<Long> evidenceIds, String provenance) {
    List<Long> sorted = new ArrayList<>();
    if (evidenceIds != null) {
      for (Long id : evidenceIds) {
        if (id != null) {
          sorted.add(id);
        }
      }
    }
    Collections.sort(sorted);
    map.put("evidenceIds", sorted);
    map.put("provenance", provenance);
    return new LeafResult(verdict, reasonCode,
        Collections.unmodifiableMap(new LinkedHashMap<>(map)), List.copyOf(sorted));
  }
}
