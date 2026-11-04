package com.tenderops.visual;

/** SAM output does not match the expected grammar: fail fast to fallback (no retry). */
public class SamFormatException extends SamException {
  public SamFormatException(String message) {
    super(message);
  }
}
