package com.tenderops.ai;

/** Extraction failed for a page (live failures fall back to the rule-based extractor). */
public class ExtractionException extends RuntimeException {
  public ExtractionException(String message) {
    super(message);
  }

  public ExtractionException(String message, Throwable cause) {
    super(message, cause);
  }
}
