package com.tenderops.compliance;

import java.util.Locale;

/** General comparison operators, applied to compareTo outcomes (never rounded values). */
public enum CompareOperator {
  GTE,
  GT,
  LTE,
  LT,
  EQ;

  public boolean test(int comparison) {
    return switch (this) {
      case GTE -> comparison >= 0;
      case GT -> comparison > 0;
      case LTE -> comparison <= 0;
      case LT -> comparison < 0;
      case EQ -> comparison == 0;
    };
  }

  public String symbol() {
    return switch (this) {
      case GTE -> ">=";
      case GT -> ">";
      case LTE -> "<=";
      case LT -> "<";
      case EQ -> "==";
    };
  }

  /** True for known codes; blank defaults to GTE. */
  public static boolean isKnown(String code) {
    if (code == null || code.isBlank()) {
      return true;
    }
    for (CompareOperator candidate : values()) {
      if (candidate.name().equals(code.trim().toUpperCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }

  /** Resolves a known code; blank defaults to GTE. Unknown codes throw. */
  public static CompareOperator of(String code) {
    if (code == null || code.isBlank()) {
      return GTE;
    }
    return CompareOperator.valueOf(code.trim().toUpperCase(Locale.ROOT));
  }
}
