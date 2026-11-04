package com.tenderops.common;

/** Typed PDF rejection with a machine-readable reason (mapped to 4xx by the web layer). */
public class PdfRejectedException extends RuntimeException {

  private final String reason;

  public PdfRejectedException(String reason, String message) {
    super(message);
    this.reason = reason;
  }

  public String reason() {
    return reason;
  }
}
