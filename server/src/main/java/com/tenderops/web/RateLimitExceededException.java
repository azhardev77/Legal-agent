package com.tenderops.web;

public class RateLimitExceededException extends RuntimeException {
  public RateLimitExceededException() {
    super("Rate limit exceeded, please retry later");
  }
}
