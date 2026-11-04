package com.tenderops.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java grounding check (§6.12): every number/currency amount/date in generated text must
 * appear in the input facts after canonicalization, else it is reported ungrounded.
 */
public final class GroundingChecker {

  private static final Pattern NUMBER_LIKE = Pattern.compile(
      "(?i)(?:rs\\.?|inr|\\$|USD)?\\s*\\d[\\d,]*\\.?\\d*\\s*(?:crores?|cr|lakhs?|lacs?|%)?");
  private static final Pattern DATE_WORDY =
      Pattern.compile("(?i)\\d{1,2}\\s+[A-Za-z]+\\s+\\d{4}");
  private static final Pattern DATE_ISO = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

  private GroundingChecker() {}

  /** Returns the summary tokens with no match in the canonicalized facts (empty = grounded). */
  public static List<String> ungroundedNumbers(String summary, List<String> facts) {
    List<String> missing = new ArrayList<>();
    if (summary == null || summary.isBlank()) {
      return missing;
    }
    String canonFacts = canonical(String.join("\n", facts));
    for (String token : tokens(summary)) {
      if (!canonFacts.contains(canonical(token))) {
        missing.add(token);
      }
    }
    return missing;
  }

  private static List<String> tokens(String summary) {
    List<String> tokens = new ArrayList<>();
    Matcher numbers = NUMBER_LIKE.matcher(summary);
    while (numbers.find()) {
      String token = numbers.group().trim().replaceAll("[.\\s]+$", "");
      if (!token.isEmpty() && token.matches("(?s).*\\d.*")) {
        tokens.add(token);
      }
    }
    Matcher wordy = DATE_WORDY.matcher(summary);
    while (wordy.find()) {
      tokens.add(wordy.group().trim());
    }
    Matcher iso = DATE_ISO.matcher(summary);
    while (iso.find()) {
      tokens.add(iso.group().trim());
    }
    return tokens;
  }

  private static String canonical(String text) {
    return text.replace(String.valueOf((char) 0x20B9), "rs")
        .toLowerCase(Locale.ROOT).replace(",", "").replaceAll("\\s+", " ").trim()
        .replace("rs.", "rs").replace("rupees", "rs").replace("rupee", "rs");
  }
}
