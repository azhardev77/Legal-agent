package com.tenderops.ai;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses tender deadline raw texts (e.g. "15 December 2026, 17:00 IST") to an
 * {@link OffsetDateTime} at +05:30 (R13). Date-only texts mean end of that day (23:59 IST,
 * documented assumption). Unrecognized zone suffixes default to IST.
 */
public final class DeadlineParser {

  private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
  private static final LocalTime END_OF_DAY = LocalTime.of(23, 59);

  private static final Pattern ZONE_SUFFIX =
      Pattern.compile("(?i)\\s+(IST|UTC|GMT|\\+05:?30)\\s*$");
  private static final Pattern DATE_TIME =
      Pattern.compile("(?i)(\\d{1,2})\\s+([A-Za-z]+)\\s+(\\d{4})\\s*,?\\s+(\\d{1,2}):(\\d{2})");

  private static final List<DateTimeFormatter> DATE_ONLY = List.of(
      new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMMM yyyy")
          .toFormatter(Locale.ENGLISH),
      new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d-MMM-yyyy")
          .toFormatter(Locale.ENGLISH),
      new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMM yyyy")
          .toFormatter(Locale.ENGLISH),
      DateTimeFormatter.ofPattern("d/M/yyyy"),
      DateTimeFormatter.ISO_LOCAL_DATE);

  private static final DateTimeFormatter MONTH_NAME = new DateTimeFormatterBuilder()
      .parseCaseInsensitive().appendPattern("MMMM").toFormatter(Locale.ENGLISH);
  private static final DateTimeFormatter MONTH_ABBR = new DateTimeFormatterBuilder()
      .parseCaseInsensitive().appendPattern("MMM").toFormatter(Locale.ENGLISH);

  private DeadlineParser() {}

  public static OffsetDateTime parse(String text) {
    if (text == null || text.isBlank()) {
      throw new DeadlineParseException("Deadline text is missing");
    }
    String trimmed = text.trim();
    Matcher zone = ZONE_SUFFIX.matcher(trimmed);
    ZoneOffset offset = ZoneOffset.of("+05:30");
    if (zone.find()) {
      String suffix = zone.group(1).toUpperCase(Locale.ROOT);
      if (suffix.equals("UTC") || suffix.equals("GMT")) {
        offset = ZoneOffset.UTC;
      }
      trimmed = trimmed.substring(0, zone.start()).trim();
    }
    Matcher dateTime = DATE_TIME.matcher(trimmed);
    if (dateTime.find()) {
      int day = Integer.parseInt(dateTime.group(1));
      int month = monthNumber(dateTime.group(2), text);
      int year = Integer.parseInt(dateTime.group(3));
      int hour = Integer.parseInt(dateTime.group(4));
      int minute = Integer.parseInt(dateTime.group(5));
      try {
        return OffsetDateTime.of(year, month, day, hour, minute, 0, 0, offset)
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));
      } catch (Exception e) {
        throw new DeadlineParseException("Invalid deadline: '" + text + "'");
      }
    }
    for (DateTimeFormatter format : DATE_ONLY) {
      try {
        LocalDate date = LocalDate.parse(trimmed, format);
        return java.time.ZonedDateTime.of(date, END_OF_DAY, IST).toOffsetDateTime()
            .withOffsetSameInstant(ZoneOffset.of("+05:30"));
      } catch (DateTimeParseException e) {
        // Try the next format.
      }
    }
    throw new DeadlineParseException("Unparseable deadline: '" + text + "'");
  }

  private static int monthNumber(String name, String text) {
    try {
      return java.time.Month.from(MONTH_NAME.parse(name.trim())).getValue();
    } catch (DateTimeParseException e) {
      try {
        return java.time.Month.from(MONTH_ABBR.parse(name.trim())).getValue();
      } catch (DateTimeParseException e2) {
        throw new DeadlineParseException("Bad month in deadline: '" + text + "'");
      }
    }
  }
}
