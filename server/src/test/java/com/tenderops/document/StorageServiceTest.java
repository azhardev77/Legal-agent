package com.tenderops.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** StorageService: sha256 naming, roundtrip, traversal rejection. */
class StorageServiceTest {

  @TempDir
  Path temp;

  @Test
  void storeAndLoadRoundtrip() {
    StorageService storage = new StorageService(temp);
    byte[] bytes = "hello storage".getBytes(StandardCharsets.UTF_8);
    String relative = storage.store(bytes, ".pdf");
    assertThat(relative).matches("[0-9a-f]{2}/[0-9a-f]{64}\\.pdf");
    assertThat(storage.load(relative)).isEqualTo(bytes);
  }

  @Test
  void traversalPathsRejected() {
    StorageService storage = new StorageService(temp);
    assertThatThrownBy(() -> storage.load("../../outside.txt"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
