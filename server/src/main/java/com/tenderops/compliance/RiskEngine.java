package com.tenderops.compliance;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Deterministic risk mapping (section 6.10) with an injected clock for deadlines. */
public final class RiskEngine {

  private RiskEngine() {
  }

  public static List<RiskFinding> assess(List<EvaluatedRequirement> results,
      OffsetDateTime deadline, Clock clock) {
    List<RiskFinding> risks = new ArrayList<>();
    for (EvaluatedRequirement result : results) {
      if (result.mandatory() && result.verdict() == Verdict.FAIL) {
        risks.add(new RiskFinding("CRITICAL", "MANDATORY_REQUIREMENT_FAILED",
            result.clauseRef(),
            "Clause " + result.clauseRef()
                + " failed verification against the company database.",
            "The deterministic engine returned FAIL on supporting evidence.",
            "Bidding while a mandatory requirement fails risks disqualification.",
            "Remedy the gap in the company database or prepare a no-bid rationale."));
      } else if (result.mandatory() && result.verdict() == Verdict.UNKNOWN) {
        risks.add(new RiskFinding("HIGH", "MISSING_DOCUMENTATION", result.clauseRef(),
            "Clause " + result.clauseRef()
                + " cannot be verified: a required record or document is missing.",
            "The deterministic engine returned UNKNOWN for missing evidence.",
            "Unverifiable mandatory requirements block a confident bid.",
            "Collect the missing record or document listed in the checklist."));
      } else if (result.verdict() == Verdict.NEEDS_REVIEW) {
        risks.add(new RiskFinding("MEDIUM", "NEEDS_HUMAN_REVIEW", result.clauseRef(),
            "Clause " + result.clauseRef() + " needs a person to review ambiguous or "
                + "incomplete evidence.",
            "The deterministic engine returned NEEDS_REVIEW.",
            "Unresolved ambiguity can surface as a compliance gap later.",
            "Review the flagged evidence and attach a clear supporting document."));
      } else if (!result.mandatory() && result.verdict() == Verdict.FAIL) {
        risks.add(new RiskFinding("LOW", "NON_MANDATORY_FAILED", result.clauseRef(),
            "Desirable clause " + result.clauseRef() + " is not satisfied.",
            "The deterministic engine returned FAIL on a non-mandatory requirement.",
            "Bonus marks for this clause are at risk.",
            "Decide whether the bonus is worth pursuing before the deadline."));
      }
    }
    if (deadline != null) {
      OffsetDateTime now = OffsetDateTime.now(clock);
      if (now.isAfter(deadline)) {
        risks.add(new RiskFinding("CRITICAL", "SUBMISSION_DEADLINE_PASSED", null,
            "The submission deadline " + deadline + " has passed.",
            "The injected clock reads after the tender deadline.",
            "A bid can no longer be submitted for this tender.",
            "Stop bidding work on this tender and archive the run."));
      } else {
        boolean unresolvedUnknown = false;
        for (EvaluatedRequirement result : results) {
          if (result.mandatory() && result.verdict() == Verdict.UNKNOWN) {
            unresolvedUnknown = true;
            break;
          }
        }
        if (unresolvedUnknown) {
          Duration remaining = Duration.between(now, deadline);
          if (!remaining.isNegative() && remaining.toDays() < 7) {
            risks.add(new RiskFinding("HIGH", "SUBMISSION_DEADLINE_APPROACHING", null,
                "The submission deadline is within 7 days and mandatory items are "
                    + "still unverified.",
              "Less than 7 days remain with unresolved UNKNOWN requirements.",
              "Running out of time to collect missing documentation.",
              "Prioritize the missing-document checklist immediately."));
          }
        }
      }
    }
    return risks;
  }
}
