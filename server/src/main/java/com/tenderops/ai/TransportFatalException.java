package com.tenderops.ai;

/** Meta API rejected the call deterministically (4xx): no retry, caller falls back. */
public class TransportFatalException extends RuntimeException {
  public TransportFatalException(String message) {
    super(message);
  }
}
