package com.tenderops.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/** Deadline raw-text parsing (golden datetimes exact; date-only = end of day IST). */
class DeadlineParserTest {

  @Test
  void goldenDeadlinesParseExactly() {
    assertThat(DeadlineParser.parse("15 December 2026, 17:00 IST"))
        .isEqualTo(OffsetDateTime.parse("2026-12-15T17:00+05:30"));
    assertThat(DeadlineParser.parse("20 December 2026, 17:00 IST"))
        .isEqualTo(OffsetDateTime.parse("2026-12-20T17:00+05:30"));
    assertThat(DeadlineParser.parse("15 December 2026 17:00 IST"))
        .isEqualTo(OffsetDateTime.parse("2026-12-15T17:00+05:30"));
  }

  @Test
  void dateOnlyMeansEndOfDayIst() {
    assertThat(DeadlineParser.parse("15 December 2026"))
        .isEqualTo(OffsetDateTime.parse("2026-12-15T23:59+05:30"));
    assertThat(DeadlineParser.parse("15-Dec-2026"))
        .isEqualTo(OffsetDateTime.parse("2026-12-15T23:59+05:30"));
    assertThat(DeadlineParser.parse("2026-12-15"))
        .isEqualTo(OffsetDateTime.parse("2026-12-15T23:59+05:30"));
  }

  @Test
  void utcSuffixConvertsToIstOffset() {
    assertThat(DeadlineParser.parse("15 December 2026, 11:30 UTC"))
        .isEqualTo(OffsetDateTime.parse("2026-12-15T17:00+05:30"));
  }

  @Test
  void garbageThrowsTypedException() {
    assertThatThrownBy(() -> DeadlineParser.parse("sometime next week"))
        .isInstanceOf(DeadlineParseException.class);
    assertThatThrownBy(() -> DeadlineParser.parse(""))
        .isInstanceOf(DeadlineParseException.class);
    assertThatThrownBy(() -> DeadlineParser.parse(null))
        .isInstanceOf(DeadlineParseException.class);
  }
}
