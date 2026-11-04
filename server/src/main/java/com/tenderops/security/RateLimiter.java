package com.tenderops.security;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Simple in-memory token-bucket rate limiter (per key, refilled per minute). */
@Component
public class RateLimiter {

  private final Clock clock;
  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

  public RateLimiter(Clock clock) {
    this.clock = clock;
  }

  public boolean tryConsume(String key, int permitsPerMinute) {
    if (permitsPerMinute <= 0) {
      return true;
    }
    return buckets.computeIfAbsent(key, k -> new Bucket(permitsPerMinute, clock.millis()))
        .tryConsume(permitsPerMinute, clock.millis());
  }

  /** Test support: drop all buckets. */
  public void resetAll() {
    buckets.clear();
  }

  private static final class Bucket {
    private double tokens;
    private long lastRefillMillis;

    Bucket(int permitsPerMinute, long nowMillis) {
      this.tokens = permitsPerMinute;
      this.lastRefillMillis = nowMillis;
    }

    synchronized boolean tryConsume(int permitsPerMinute, long nowMillis) {
      double elapsedMinutes = (nowMillis - lastRefillMillis) / 60000.0;
      lastRefillMillis = nowMillis;
      tokens = Math.min(permitsPerMinute, tokens + elapsedMinutes * permitsPerMinute);
      if (tokens >= 1.0) {
        tokens -= 1.0;
        return true;
      }
      return false;
    }
  }
}
