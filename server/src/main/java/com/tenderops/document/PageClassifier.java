package com.tenderops.document;

/**
 * Page classifier (§9): DIGITAL_TEXT unless extractable chars &lt; 50, or U+FFFD ratio &gt; 10%,
 * or one embedded image covers &gt; 60% of the page area (then SCANNED/MIXED).
 * Pages with almost no readable text are SCANNED; readable-but-degraded pages are MIXED.
 */
public final class PageClassifier {

  private PageClassifier() {}

  public static String classify(String text, double maxImageFraction) {
    String safe = text == null ? "" : text;
    long extractable = safe.codePoints().filter(c -> !Character.isWhitespace(c)).count();
    long replacements = safe.codePoints().filter(c -> c == 0xFFFD).count();
    double replacementRatio = replacements / (double) Math.max(1L, extractable + replacements);
    boolean degraded = extractable < 50 || replacementRatio > 0.10 || maxImageFraction > 0.60;
    if (!degraded) {
      return DocumentPage.DIGITAL_TEXT;
    }
    return extractable < 50 ? DocumentPage.SCANNED : DocumentPage.MIXED;
  }

  public static double replacementRatio(String text) {
    String safe = text == null ? "" : text;
    long extractable = safe.codePoints().filter(c -> !Character.isWhitespace(c)).count();
    long replacements = safe.codePoints().filter(c -> c == 0xFFFD).count();
    return replacements / (double) Math.max(1L, extractable + replacements);
  }
}
