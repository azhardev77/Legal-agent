package com.tenderops.compliance;

/** Name/domain normalization without regex: uppercase ASCII alphanumerics only. */
final class TextNorms {

  private TextNorms() {
  }

  static String alphaNumUpper(String value) {
    if (value == null) {
      return null;
    }
    StringBuilder out = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c >= 'a' && c <= 'z') {
        out.append((char) (c - 32));
      } else if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
        out.append(c);
      }
    }
    return out.toString();
  }

  /** Fail-closed INR check: null or foreign currency is not INR. */
  static boolean isInr(String currency) {
    return currency != null && currency.length() == 3
        && (currency.charAt(0) == 'I' || currency.charAt(0) == 'i')
        && (currency.charAt(1) == 'N' || currency.charAt(1) == 'n')
        && (currency.charAt(2) == 'R' || currency.charAt(2) == 'r');
  }
}
