package com.tenderops.compliance;

import java.util.List;
import java.util.Map;

/**
 * One leaf/branch outcome. The trace map uses insertion order (stable JSON for R16)
 * and carries formula, operands, threshold, comparison, verdict, ruleVersion,
 * evidenceIds, provenance and contextHash per section 6.7.
 */
public record LeafResult(Verdict verdict, String reasonCode, Map<String, Object> trace,
    List<Long> evidenceIds) {
}
