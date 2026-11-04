package com.tenderops.document;

import com.tenderops.common.FileSafetyValidator;
import com.tenderops.config.TenderopsProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * File storage under the configured storage dir. Files are addressed by SHA-256-derived names
 * ({@code <aa>/<sha256><ext>}), so stored names can never traverse paths.
 */
@Service
public class StorageService {

  private final Path root;

  @Autowired
  public StorageService(TenderopsProperties properties) {
    this(Paths.get(properties.storageDir()));
  }

  public StorageService(Path root) {
    this.root = root.toAbsolutePath().normalize();
  }

  /** Stores bytes, returns the relative storage path ({@code aa/sha256.ext}). */
  public String store(byte[] bytes, String extension) {
    String sha256 = FileSafetyValidator.sha256Hex(bytes);
    String relative = sha256.substring(0, 2) + "/" + sha256 + extension;
    Path target = root.resolve(relative).normalize();
    if (!target.startsWith(root)) {
      throw new IllegalArgumentException("Storage path escapes the storage root");
    }
    try {
      Files.createDirectories(target.getParent());
      Files.write(target, bytes);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot write to storage", e);
    }
    return relative;
  }

  /** Loads bytes by relative storage path; rejects any path escaping the root. */
  public byte[] load(String relativePath) {
    Path target = root.resolve(relativePath).normalize();
    if (!target.startsWith(root)) {
      throw new IllegalArgumentException("Storage path escapes the storage root");
    }
    try {
      return Files.readAllBytes(target);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read from storage: " + relativePath, e);
    }
  }

  public Path root() {
    return root;
  }
}
