package com.tenderops.compliance;

import java.util.List;

/** One evaluated requirement with its serialized calculation trace. */
public record EvaluatedRequirement(Long requirementId, String clauseRef, boolean mandatory,
    Verdict verdict, String reasonCode, String traceJson, List<Long> evidenceIds) {
}
