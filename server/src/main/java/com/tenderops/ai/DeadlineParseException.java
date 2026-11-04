package com.tenderops.ai;

/** Tender deadline text is missing or unparseable. */
public class DeadlineParseException extends RuntimeException {
  public DeadlineParseException(String message) {
    super(message);
  }
}
