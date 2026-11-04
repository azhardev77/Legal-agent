package com.tenderops.ai;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * R5 quote verification: the model's quote must be a substring of the page's extracted text
 * after normalization (NFKC, whitespace collapsed, dash/quote variants unified, rupee sign
 * and "Rs."/"Rs" unified, case-insensitive). On success returns the EXACT RAW PDF SPAN
 * (never the model's string) via an offset map; on failure the requirement is flagged
 * NEEDS_REVIEW (QUOTE_NOT_VERIFIED) by the caller.
 */
public final class QuoteVerifier {

  public record VerifiedQuote(String rawSpan, int rawStart, int rawEnd) {}

  private QuoteVerifier() {}

  /** Full normalization for matching (no offsets). */
  public static String normalizeForMatch(String text) {
    if (text == null) {
      return "";
    }
    return restOf(Normalizer.normalize(text, Normalizer.Form.NFKC), null);
  }

  /**
   * Verifies the model quote against the page text. First normalized occurrence wins
   * (deterministic). Returns the exact raw span on success.
   */
  public static Optional<VerifiedQuote> verify(String pageText, String modelQuote) {
    if (pageText == null || modelQuote == null) {
      return Optional.empty();
    }
    String wanted = normalizeForMatch(modelQuote);
    if (wanted.isBlank()) {
      return Optional.empty();
    }
    String nfkc = Normalizer.normalize(pageText, Normalizer.Form.NFKC);
    int[] nfkcToRaw = nfkcOffsetMap(pageText, nfkc);
    List<Integer> finalToNfkc = new ArrayList<>();
    String normalized = restOf(nfkc, finalToNfkc);
    int at = normalized.indexOf(wanted);
    if (at < 0) {
      return Optional.empty();
    }
    int rawStart = nfkcToRaw[finalToNfkc.get(at)];
    int lastFinal = at + wanted.length() - 1;
    int rawEnd = nfkcToRawEnd(nfkc, nfkcToRaw, finalToNfkc.get(lastFinal));
    if (rawEnd > rawStart && rawEnd < pageText.length()
        && Character.isHighSurrogate(pageText.charAt(rawEnd - 1))
        && Character.isLowSurrogate(pageText.charAt(rawEnd))) {
      rawEnd++;
    }
    if (rawStart < 0 || rawEnd > pageText.length() || rawEnd <= rawStart) {
      return Optional.empty();
    }
    return Optional.of(new VerifiedQuote(pageText.substring(rawStart, rawEnd), rawStart,
        rawEnd));
  }

  /**
   * Offset map: NFKC index (UTF-16) to raw index (UTF-16 start of the attributed raw
   * code point). Identity fast path; prefix-diff slow path for NFKC-active text.
   */
  private static int[] nfkcOffsetMap(String raw, String nfkc) {
    if (nfkc.equals(raw)) {
      int[] identity = new int[raw.length() + 1];
      for (int i = 0; i <= raw.length(); i++) {
        identity[i] = i;
      }
      return identity;
    }
    int[] map = new int[nfkc.length() + 1];
    String prev = "";
    int[] prevMap = new int[1];
    int i = 0;
    while (i < raw.length()) {
      int cp = raw.codePointAt(i);
      int next = i + Character.charCount(cp);
      String current = Normalizer.normalize(raw.substring(0, next), Normalizer.Form.NFKC);
      int lcp = commonPrefixUnits(prev, current);
      int[] nextMap = new int[current.length() + 1];
      System.arraycopy(prevMap, 0, nextMap, 0, Math.min(lcp, prevMap.length - 1));
      int attributed = i;
      for (int j = lcp; j < prev.length() && j < prevMap.length; j++) {
        attributed = Math.min(attributed, prevMap[j]);
      }
      for (int j = lcp; j <= current.length(); j++) {
        nextMap[j] = attributed;
      }
      prev = current;
      prevMap = nextMap;
      i = next;
    }
    System.arraycopy(prevMap, 0, map, 0, Math.min(map.length, prevMap.length));
    return map;
  }

  /** Raw end (exclusive UTF-16) for an NFKC index: start of the next raw code point run. */
  private static int nfkcToRawEnd(String nfkc, int[] nfkcToRaw, int nfkcIndex) {
    int start = nfkcToRaw[nfkcIndex];
    int j = nfkcIndex + 1;
    while (j < nfkc.length() && nfkcToRaw[j] == start) {
      j++;
    }
    if (j <= nfkc.length()) {
      int end = nfkcToRaw[Math.min(j, nfkcToRaw.length - 1)];
      if (end > start) {
        return end;
      }
    }
    return Math.min(start + 1, Integer.MAX_VALUE);
  }

  private static int commonPrefixUnits(String a, String b) {
    int n = Math.min(a.length(), b.length());
    int i = 0;
    while (i < n && a.charAt(i) == b.charAt(i)) {
      i++;
    }
    if (i > 0 && i < n + 1 && Character.isLowSurrogate(a.charAt(i - 1))
        && i < a.length() && i < b.length()) {
      i--;
    }
    return i;
  }

  /**
   * Rest-transforms after NFKC: dash/quote/rupee unification, Rs-forms, whitespace
   * collapse, trim, lowercase. Records final-to-input offsets when {@code offsets} is
   * non-null (one entry per emitted char: input UTF-16 index).
   */
  private static String restOf(String input, List<Integer> offsets) {
    StringBuilder out = new StringBuilder(input.length());
    List<Integer> map = offsets == null ? null : offsets;
    int i = 0;
    boolean pendingSpace = false;
    while (i < input.length()) {
      int cp = input.codePointAt(i);
      int cpLen = Character.charCount(cp);
      if (Character.isWhitespace(cp)) {
        pendingSpace = true;
        i += cpLen;
        continue;
      }
      String emit;
      int consume = cpLen;
      if (isDash(cp)) {
        emit = "-";
      } else if (isSingleQuote(cp)) {
        emit = "'";
      } else if (isDoubleQuote(cp)) {
        emit = "\"";
      } else if (cp == 0x20B9) {
        emit = "rs";
      } else if ((cp == 'r' || cp == 'R') && rsFormAt(input, i)) {
        emit = "rs";
        consume = rsFormLength(input, i);
      } else {
        emit = new String(Character.toChars(cp)).toLowerCase(Locale.ROOT);
      }
      if (pendingSpace && out.length() > 0) {
        out.append(' ');
        if (map != null) {
          map.add(i);
        }
      }
      pendingSpace = false;
      for (int k = 0; k < emit.length(); k++) {
        out.append(emit.charAt(k));
        if (map != null) {
          map.add(i);
        }
      }
      i += consume;
    }
    return out.toString();
  }

  private static boolean isDash(int cp) {
    return cp == 0x2010 || cp == 0x2011 || cp == 0x2012 || cp == 0x2013 || cp == 0x2014
        || cp == 0x2015 || cp == 0x2212 || cp == 0xFE58 || cp == 0xFE63 || cp == 0xFF0D;
  }

  private static boolean isSingleQuote(int cp) {
    return cp == 0x2018 || cp == 0x2019 || cp == 0x201A || cp == 0x201B || cp == 0x2032
        || cp == 0x02BC;
  }

  private static boolean isDoubleQuote(int cp) {
    return cp == 0x201C || cp == 0x201D || cp == 0x201E || cp == 0x201F || cp == 0x2033
        || cp == 0x2036;
  }

  /** "Rs"/"Rs." (any case) with word boundary before and space/digit/punct/end after. */
  private static boolean rsFormAt(String input, int index) {
    if (index > 0) {
      int prev = input.codePointBefore(index);
      if (Character.isLetterOrDigit(prev)) {
        return false;
      }
    }
    int i = index + 1;
    if (i >= input.length()) {
      return false;
    }
    int second = input.codePointAt(i);
    if (second != 's' && second != 'S') {
      return false;
    }
    i += Character.charCount(second);
    if (i < input.length() && input.charAt(i) == '.') {
      i++;
    }
    if (i >= input.length()) {
      return true;
    }
    int next = input.codePointAt(i);
    return Character.isWhitespace(next) || Character.isDigit(next) || next == ','
        || next == '.' || next == ';' || next == ':';
  }

  private static int rsFormLength(String input, int index) {
    int i = index + 1 + Character.charCount(input.codePointAt(index + 1));
    if (i < input.length() && input.charAt(i) == '.') {
      i++;
    }
    return i - index;
  }
}
