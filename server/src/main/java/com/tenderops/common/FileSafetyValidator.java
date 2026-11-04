package com.tenderops.common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Byte-level upload guards: magic bytes, size cap, SHA-256 naming (TC-ING-01/02). */
public final class FileSafetyValidator {

  private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};

  private FileSafetyValidator() {}

  public static void requireNonEmpty(byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      throw new PdfRejectedException("EMPTY", "Uploaded file is empty");
    }
  }

  public static void requirePdfMagic(byte[] bytes) {
    requireNonEmpty(bytes);
    if (bytes.length < PDF_MAGIC.length) {
      throw new PdfRejectedException("NOT_PDF", "File is not a PDF (too short)");
    }
    for (int i = 0; i < PDF_MAGIC.length; i++) {
      if (bytes[i] != PDF_MAGIC[i]) {
        throw new PdfRejectedException("NOT_PDF", "File is not a PDF (bad magic bytes)");
      }
    }
  }

  public static void requireSizeWithin(byte[] bytes, long maxBytes) {
    if (bytes != null && bytes.length > maxBytes) {
      throw new PdfRejectedException("TOO_LARGE",
          "File exceeds the " + maxBytes + " byte limit");
    }
  }

  public static String sha256Hex(byte[] bytes) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        sb.append(Character.forDigit((b >> 4) & 0xF, 16));
        sb.append(Character.forDigit(b & 0xF, 16));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
