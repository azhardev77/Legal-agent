package com.tenderops.visual;

/** Transient SAM failure (timeout/connection/429/5xx after retries): fall back + log. */
public class SamUnavailableException extends SamException {
  public SamUnavailableException(String message) {
    super(message);
  }

  public SamUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
