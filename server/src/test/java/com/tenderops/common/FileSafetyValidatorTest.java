package com.tenderops.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** TC-ING-01 (byte-level part) and TC-ING-02 (hashing part). */
class FileSafetyValidatorTest {

  @Test
  void validPdfMagicPasses() {
    FileSafetyValidator.requirePdfMagic("%PDF-1.7 rest".getBytes(StandardCharsets.US_ASCII));
  }

  @Test
  void nonPdfMagicRejected() {
    assertThatThrownBy(() -> FileSafetyValidator.requirePdfMagic(
        "Hello world".getBytes(StandardCharsets.US_ASCII)))
        .isInstanceOf(PdfRejectedException.class)
        .hasFieldOrPropertyWithValue("reason", "NOT_PDF");
  }

  @Test
  void emptyAndNullRejected() {
    assertThatThrownBy(() -> FileSafetyValidator.requirePdfMagic(new byte[0]))
        .isInstanceOf(PdfRejectedException.class);
    assertThatThrownBy(() -> FileSafetyValidator.requirePdfMagic(null))
        .isInstanceOf(PdfRejectedException.class);
    assertThatThrownBy(() -> FileSafetyValidator.requirePdfMagic(new byte[]{'%', 'P'}))
        .isInstanceOf(PdfRejectedException.class);
  }

  @Test
  void oversizeRejected() {
    assertThatThrownBy(() -> FileSafetyValidator.requireSizeWithin(new byte[6], 5))
        .isInstanceOf(PdfRejectedException.class)
        .hasFieldOrPropertyWithValue("reason", "TOO_LARGE");
    FileSafetyValidator.requireSizeWithin(new byte[5], 5);
  }

  @Test
  void sha256HexMatchesKnownVector() {
    assertThat(FileSafetyValidator.sha256Hex("abc".getBytes(StandardCharsets.US_ASCII)))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }
}
