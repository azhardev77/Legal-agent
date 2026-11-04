package com.tenderops.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * R14 outcome pin: no open-license rupee font is obtainable offline, so generated PDFs must use
 * "Rs." text and contain no U+20B9 byte sequence. If fonts/ ever gains a TTF, replace this test
 * with a glyph-presence assertion plus TTF-path coverage (see TECH_STACK.md).
 */
class RsFallbackTest {

  @TempDir
  Path temp;

  @Test
  void noBundledFontAndNoRupeeBytesInOutput() throws Exception {
    Path fontsDir = Paths.get("src/main/resources/fonts");
    if (Files.exists(fontsDir)) {
      try (var stream = Files.list(fontsDir)) {
        assertThat(stream.filter(p -> p.toString().endsWith(".ttf")).toList()).isEmpty();
      }
    }
    TenderPdfGenerator generator = new TenderPdfGenerator(new ObjectMapper());
    generator.generateSamples(temp);
    byte[] v1 = Files.readAllBytes(temp.resolve(TenderPdfGenerator.V1_FILE));
    assertThat(contains(v1, "Rs".getBytes(StandardCharsets.US_ASCII))).isTrue();
    assertThat(contains(v1, new byte[]{(byte) 0xE2, (byte) 0x82, (byte) 0xB9})).isFalse();
  }

  private static boolean contains(byte[] haystack, byte[] needle) {
    outer:
    for (int i = 0; i + needle.length <= haystack.length; i++) {
      for (int j = 0; j < needle.length; j++) {
        if (haystack[i + j] != needle[j]) {
          continue outer;
        }
      }
      return true;
    }
    return false;
  }
}
