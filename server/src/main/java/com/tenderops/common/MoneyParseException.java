package com.tenderops.common;

/** Typed failure for unparseable or negative money text; the pipeline maps this to NEEDS_REVIEW. */
public class MoneyParseException extends RuntimeException {
  public MoneyParseException(String message) {
    super(message);
  }
}
