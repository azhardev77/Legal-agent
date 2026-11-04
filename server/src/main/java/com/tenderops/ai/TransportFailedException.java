package com.tenderops.ai;

/** Meta API call failed after retries, or was interrupted (caller falls back). */
public class TransportFailedException extends RuntimeException {
  public TransportFailedException(String message) {
    super(message);
  }

  public TransportFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
