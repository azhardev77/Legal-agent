package com.tenderops.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * INR money parsing/formatting (R1, section 6.1). BigDecimal only, storage/display scale 4 HALF_UP.
 * The rupee sign is written as a unicode escape in source (Windows-safe rule).
 */
public final class MoneyUtils {

  private static final BigDecimal CRORE = new BigDecimal("10000000");
  private static final BigDecimal LAKH = new BigDecimal("100000");
  private static final String RUPEE = "\u20B9";
  private static final Pattern UNIT_SUFFIX =
      Pattern.compile("(?i)\\s*(crores?|cr|lakhs?|lacs?)\\.?\\s*$");
  private static final Pattern PREFIX =
      Pattern.compile("(?i)^(\u20B9|rs\\.?|inr)\\s*");
  private static final Pattern NUMERIC = Pattern.compile("\\d+(\\.\\d+)?");

  private MoneyUtils() {}

  /**
   * Parses raw amount text (e.g. "Rs. 100 Cr", "Rs 50,000", "12.5 Cr") to INR scale 4.
   * Negative or unparseable input throws {@link MoneyParseException}.
   */
  public static BigDecimal parse(String text) {
    if (text == null || text.trim().isEmpty()) {
      throw new MoneyParseException("Amount text is missing");
    }
    String s = text.trim().replaceAll("[\\s.]+$", "");
    if (s.isEmpty()) {
      throw new MoneyParseException("Amount text is missing: '" + text + "'");
    }
    if (s.contains("-")) {
      throw new MoneyParseException("Negative amounts are not supported: '" + text + "'");
    }
    BigDecimal multiplier = BigDecimal.ONE;
    Matcher unit = UNIT_SUFFIX.matcher(s);
    if (unit.find()) {
      String u = unit.group(1).toLowerCase();
      multiplier = u.startsWith("cr") ? CRORE : LAKH;
      s = s.substring(0, unit.start()).trim().replaceAll("[\\s.]+$", "");
    }
    s = PREFIX.matcher(s).replaceFirst("").trim();
    s = s.replace(",", "").replaceAll("\\s+", "");
    if (!NUMERIC.matcher(s).matches()) {
      throw new MoneyParseException("Unparseable amount text: '" + text + "'");
    }
    return new BigDecimal(s).multiply(multiplier).setScale(4, RoundingMode.HALF_UP);
  }

  /** Storage/display scale (R1): scale 4, HALF_UP. */
  public static BigDecimal toStorageScale(BigDecimal value) {
    return value.setScale(4, RoundingMode.HALF_UP);
  }

  /** JSON/API string form of money (plain, scale 4). */
  public static String toStringValue(BigDecimal value) {
    return toStorageScale(value).toPlainString();
  }
}
