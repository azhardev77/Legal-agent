package com.tenderops.visual;

/** SAM 3.1 call failure; the caller falls back to the geometric detector. */
public class SamException extends RuntimeException {
  public SamException(String message) {
    super(message);
  }

  public SamException(String message, Throwable cause) {
    super(message, cause);
  }
}
