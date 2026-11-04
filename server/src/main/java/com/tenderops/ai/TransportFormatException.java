package com.tenderops.ai;

/** Meta API response envelope carries no usable output text (callers map to their errors). */
public class TransportFormatException extends RuntimeException {
  public TransportFormatException(String message) {
    super(message);
  }
}
