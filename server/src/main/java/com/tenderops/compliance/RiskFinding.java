package com.tenderops.compliance;

/** One deterministic risk finding (section 6.10). Deadline risks carry no source clause. */
public record RiskFinding(String severity, String riskCode, String sourceClause,
    String evidenceSummary, String detectionReason, String businessImpact,
    String correctiveAction) {
}
