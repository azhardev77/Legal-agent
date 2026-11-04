package com.tenderops.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Token-bucket units with a manual clock: fully deterministic, no sleeps (TC-SEC-04 unit part). */
class RateLimiterTest {

  static final class ManualClock extends Clock {
    private final AtomicLong millis = new AtomicLong(1_000_000L);

    void advance(long ms) {
      millis.addAndGet(ms);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public long millis() {
      return millis.get();
    }

    @Override
    public Instant instant() {
      return Instant.ofEpochMilli(millis.get());
    }
  }

  @Test
  void bucketExhaustsAndRefillsOnClockAdvance() {
    ManualClock clock = new ManualClock();
    RateLimiter limiter = new RateLimiter(clock);
    assertThat(limiter.tryConsume("k1", 3)).isTrue();
    assertThat(limiter.tryConsume("k1", 3)).isTrue();
    assertThat(limiter.tryConsume("k1", 3)).isTrue();
    assertThat(limiter.tryConsume("k1", 3)).isFalse();
    assertThat(limiter.tryConsume("other-key", 3)).isTrue();
    clock.advance(19_999);
    assertThat(limiter.tryConsume("k1", 3)).isFalse();
    clock.advance(2);
    assertThat(limiter.tryConsume("k1", 3)).isTrue();
    assertThat(limiter.tryConsume("k1", 3)).isFalse();
    limiter.resetAll();
    assertThat(limiter.tryConsume("k1", 3)).isTrue();
  }

  @Test
  void refillCapsAtBucketSize() {
    ManualClock clock = new ManualClock();
    RateLimiter limiter = new RateLimiter(clock);
    clock.advance(3600_000L);
    assertThat(limiter.tryConsume("capped", 2)).isTrue();
    assertThat(limiter.tryConsume("capped", 2)).isTrue();
    assertThat(limiter.tryConsume("capped", 2)).isFalse();
  }
}
