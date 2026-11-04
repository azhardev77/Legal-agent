package com.tenderops.compliance;

import java.util.List;

/** The four verdict states (R3). Combines condition-tree children per section 6.6. */
public enum Verdict {
  PASS,
  FAIL,
  UNKNOWN,
  NEEDS_REVIEW;

  /** AND: any FAIL wins, then UNKNOWN, then NEEDS_REVIEW, else PASS. */
  public static Verdict andOf(List<Verdict> children) {
    if (children.contains(FAIL)) {
      return FAIL;
    }
    if (children.contains(UNKNOWN)) {
      return UNKNOWN;
    }
    if (children.contains(NEEDS_REVIEW)) {
      return NEEDS_REVIEW;
    }
    return PASS;
  }

  /** OR: any PASS wins, then NEEDS_REVIEW, then UNKNOWN, else FAIL. */
  public static Verdict orOf(List<Verdict> children) {
    if (children.contains(PASS)) {
      return PASS;
    }
    if (children.contains(NEEDS_REVIEW)) {
      return NEEDS_REVIEW;
    }
    if (children.contains(UNKNOWN)) {
      return UNKNOWN;
    }
    return FAIL;
  }
}
