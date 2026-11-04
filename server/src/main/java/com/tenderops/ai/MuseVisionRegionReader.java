package com.tenderops.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenderops.config.TenderopsProperties;
import com.tenderops.visual.AiCallLogger;
import com.tenderops.visual.VisionRegionReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Muse-vision region reader (§3.4/§3.6): transcribes table crops and stamp crops on
 * SCANNED/MIXED pages when image input is available. Unavailable or unusable output
 * degrades to empty (caller flags needsHumanReview); never throws on model failures.
 */
@Component
public class MuseVisionRegionReader implements VisionRegionReader {

  private static final Logger LOG = LoggerFactory.getLogger(MuseVisionRegionReader.class);

  private final TenderopsProperties properties;
  private final ObjectMapper objectMapper;
  private final AiCallLogger callLogger;
  private final MetaApiTransport transport;

  public MuseVisionRegionReader(TenderopsProperties properties, ObjectMapper objectMapper,
      AiCallLogger callLogger, MetaApiTransport transport) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.callLogger = callLogger;
    this.transport = transport;
  }

  @Override
  public Optional<StructuredTable> readTable(byte[] cropPng) {
    Optional<String> output = transcribe("muse-vision:table", cropPng,
        "Transcribe ONLY the visible table cells as JSON {\"rows\": [[cell, ...], ...]} "
            + "in reading order. Raw cell text, no guessing, no extra keys.");
    if (output.isEmpty()) {
      return Optional.empty();
    }
    try {
      JsonNode root = objectMapper.readTree(unfence(output.get()));
      JsonNode rows = root.get("rows");
      if (rows == null || !rows.isArray() || rows.isEmpty()) {
        logUnusable("muse-vision:table", "no rows array");
        return Optional.empty();
      }
      List<List<String>> table = new ArrayList<>();
      for (JsonNode row : rows) {
        if (row == null || !row.isArray()) {
          logUnusable("muse-vision:table", "row is not an array");
          return Optional.empty();
        }
        List<String> cells = new ArrayList<>();
        for (JsonNode cell : row) {
          cells.add(cell.isTextual() ? cell.asText() : cell.toString());
        }
        table.add(cells);
      }
      return Optional.of(new StructuredTable(table));
    } catch (IOException e) {
      logUnusable("muse-vision:table", "not JSON: " + oneLine(e));
      return Optional.empty();
    }
  }

  @Override
  public Optional<String> readText(byte[] cropPng) {
    Optional<String> output = transcribe("muse-vision:text", cropPng,
        "Transcribe ONLY the raw visible text in this crop. No commentary, no guessing.");
    if (output.isEmpty() || output.get().isBlank()) {
      return Optional.empty();
    }
    return Optional.of(output.get().trim());
  }

  private Optional<String> transcribe(String purpose, byte[] cropPng, String prompt) {
    String key = properties.metaApi().key();
    if (key == null || key.isBlank() || !properties.muse().imageInput()
        || cropPng == null || cropPng.length == 0) {
      return Optional.empty();
    }
    String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(cropPng);
    Map<String, Object> textPart = new LinkedHashMap<>();
    textPart.put("type", "input_text");
    textPart.put("text", prompt);
    Map<String, Object> imagePart = new LinkedHashMap<>();
    imagePart.put("type", "input_image");
    imagePart.put("image_url", dataUrl);
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("type", "message");
    message.put("role", "user");
    message.put("content", List.of(textPart, imagePart));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", properties.metaApi().museModel());
    body.put("input", List.of(message));
    String bodyJson;
    try {
      bodyJson = objectMapper.writeValueAsString(body);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot serialize vision request", e);
    }
    String url = properties.metaApi().baseUrl() + properties.metaApi().responsesPath();
    MetaApiTransport.AttemptListener listener = outcome -> callLogger.log(null, "MUSE",
        properties.metaApi().museModel(), purpose, outcome.status(), outcome.attempt(),
        outcome.latencyMs(), "OK".equals(outcome.status()) ? "LIVE" : "FALLBACK",
        outcome.error());
    try {
      String responseBody = transport.postJson(url, key, bodyJson,
          properties.metaApi().requestTimeoutMs(), properties.metaApi().maxAttempts(),
          properties.metaApi().backoffInitialMs(), listener);
      return Optional.of(MetaApiTransport.extractOutputText(objectMapper, responseBody));
    } catch (TransportFailedException | TransportFatalException | TransportFormatException e) {
      LOG.info("Vision {} unavailable: {}", purpose, e.getMessage());
      return Optional.empty();
    }
  }

  private void logUnusable(String purpose, String error) {
    callLogger.log(null, "MUSE", properties.metaApi().museModel(), purpose, "UNUSABLE_CONTENT",
        1, null, "FALLBACK", error);
  }

  private String unfence(String output) {
    String trimmed = output.trim();
    if (trimmed.startsWith("```")) {
      int newline = trimmed.indexOf('\n');
      trimmed = newline < 0 ? "" : trimmed.substring(newline + 1);
      if (trimmed.endsWith("```")) {
        trimmed = trimmed.substring(0, trimmed.length() - 3);
      }
    }
    return trimmed.trim();
  }

  private String oneLine(IOException e) {
    String message = e.getMessage();
    if (message == null) {
      return "unreadable";
    }
    String first = message.lines().findFirst().orElse("unreadable");
    return first.length() <= 200 ? first : first.substring(0, 200);
  }
}
