package com.tenderops.compliance;

import java.util.ArrayList;
import java.util.List;

/**
 * Bid decision over mandatory requirements only (section 6.8). Any FAIL forces
 * NO_BID; an UNKNOWN share strictly above 30% is INSUFFICIENT_DATA (exact
 * integer comparison, no division). Non-mandatory FAILs only add warnings.
 */
public final class BidDecisionEngine {

  private BidDecisionEngine() {
  }

  public static Decision decide(List<EvaluatedRequirement> results) {
    List<EvaluatedRequirement> mandatory = new ArrayList<>();
    for (EvaluatedRequirement result : results) {
      if (result.mandatory()) {
        mandatory.add(result);
      }
    }
    List<String> warnings = new ArrayList<>();
    for (EvaluatedRequirement result : results) {
      if (!result.mandatory() && result.verdict() == Verdict.FAIL) {
        warnings.add("Non-mandatory requirement " + result.clauseRef()
            + " failed: bonus marks at risk.");
      }
    }
    if (mandatory.isEmpty()) {
      return new Decision("INSUFFICIENT_DATA", "PENDING_HUMAN_APPROVAL", warnings);
    }
    long failed = 0;
    long unknown = 0;
    long review = 0;
    for (EvaluatedRequirement result : mandatory) {
      if (result.verdict() == Verdict.FAIL) {
        failed++;
      } else if (result.verdict() == Verdict.UNKNOWN) {
        unknown++;
      } else if (result.verdict() == Verdict.NEEDS_REVIEW) {
        review++;
      }
    }
    if (failed > 0) {
      return new Decision("NO_BID", "PENDING_HUMAN_APPROVAL", warnings);
    }
    if (unknown * 10 > (long) mandatory.size() * 3) {
      return new Decision("INSUFFICIENT_DATA", "PENDING_HUMAN_APPROVAL", warnings);
    }
    if (unknown > 0 || review > 0) {
      return new Decision("CONDITIONAL_BID", "PENDING_HUMAN_APPROVAL", warnings);
    }
    return new Decision("BID", "PENDING_HUMAN_APPROVAL", warnings);
  }
}
