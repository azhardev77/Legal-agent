package com.tenderops.ai;

/** Muse Spark extraction exhausted retries/validation: the caller must fall back. */
public class MuseFailedException extends ExtractionException {
  public MuseFailedException(String message) {
    super(message);
  }

  public MuseFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
