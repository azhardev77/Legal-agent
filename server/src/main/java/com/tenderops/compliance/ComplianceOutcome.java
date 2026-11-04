package com.tenderops.compliance;

import java.math.BigDecimal;
import java.util.List;

/** In-memory outcome of one evaluation run; persistence and events belong to M6. */
public record ComplianceOutcome(List<EvaluatedRequirement> results, Decision decision,
    BigDecimal readinessScore, List<RiskFinding> risks, String contextHash,
    String ruleVersion, long companyDataVersion) {
}
