package com.tenderops.visual;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenderops.config.TenderopsProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Live SAM 3.1 detector via the Meta Responses API (one request per candidate page × noun
 * phrase). Retries 429/5xx/timeouts with backoff honoring Retry-After, then throws
 * {@link SamUnavailableException} so the caller falls back. Results cached by
 * (image SHA-256, page, prompt). Built to the docs-verified shape; live behavior UNVERIFIED.
 */
@Component
public class Sam31Detector implements VisualRegionDetector {

  private static final Logger LOG = LoggerFactory.getLogger(Sam31Detector.class);

  public static final List<Prompt> PROMPTS = List.of(
      new Prompt("circular official stamp", VisualRegion.OFFICIAL_STAMP),
      new Prompt("handwritten signature", VisualRegion.HANDWRITTEN_SIGNATURE),
      new Prompt("table", VisualRegion.TABLE_REGION));

  public record Prompt(String phrase, String regionType) {}

  private final TenderopsProperties properties;
  private final ObjectMapper objectMapper;
  private final AiCallLogger callLogger;
  private final HttpClient httpClient;
  private final Map<String, List<Detection>> cache = new ConcurrentHashMap<>();

  public Sam31Detector(TenderopsProperties properties, ObjectMapper objectMapper,
      AiCallLogger callLogger) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.callLogger = callLogger;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(properties.metaApi().connectTimeoutMs()))
        .build();
  }

  @Override
  public List<Detection> detect(PDDocument doc, int pageIndex0, byte[] renderedPng,
      int imageWidth, int imageHeight, String pageSha256) throws IOException {
    String key = properties.metaApi().key();
    if (key == null || key.isBlank()) {
      throw new SamUnavailableException("MODEL_API_KEY is empty; SAM unavailable");
    }
    List<Detection> out = new ArrayList<>();
    for (Prompt prompt : PROMPTS) {
      String cacheKey = pageSha256 + "|" + pageIndex0 + "|" + prompt.phrase();
      List<Detection> cached = cache.get(cacheKey);
      if (cached != null) {
        out.addAll(cached);
        continue;
      }
      List<Detection> found =
          detectPrompt(prompt, renderedPng, imageWidth, imageHeight, key);
      cache.put(cacheKey, found);
      out.addAll(found);
    }
    return out;
  }

  private List<Detection> detectPrompt(Prompt prompt, byte[] renderedPng, int imageWidth,
      int imageHeight, String key) {
    String purpose = "sam-detect:" + prompt.phrase();
    int maxAttempts = Math.max(1, properties.metaApi().maxAttempts());
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      long start = System.currentTimeMillis();
      try {
        String outputText = post(prompt.phrase(), renderedPng, key, attempt);
        long latency = System.currentTimeMillis() - start;
        List<SamOutputParser.SamBox> boxes =
            SamOutputParser.parse(outputText, imageWidth, imageHeight);
        callLogger.log(null, "SAM", properties.metaApi().samModel(), purpose, "OK", attempt,
            latency, "LIVE", null);
        boolean keepMasks = !"none".equalsIgnoreCase(properties.sam().maskFormat());
        List<Detection> detections = new ArrayList<>();
        for (SamOutputParser.SamBox box : boxes) {
          detections.add(new Detection(prompt.regionType(), box.x0(), box.y0(), box.x1(),
              box.y1(), box.confidence(), VisualRegion.SOURCE_SAM,
              keepMasks ? box.maskRef() : null));
        }
        return detections;
      } catch (SamFormatException e) {
        long latency = System.currentTimeMillis() - start;
        callLogger.log(null, "SAM", properties.metaApi().samModel(), purpose, "FORMAT_ERROR",
            attempt, latency, "FALLBACK", e.getMessage());
        throw e;
      } catch (RetryableSamException e) {
        long latency = System.currentTimeMillis() - start;
        callLogger.log(null, "SAM", properties.metaApi().samModel(), purpose, e.status(),
            attempt, latency, "FALLBACK", e.getMessage());
        if (attempt >= maxAttempts) {
          throw new SamUnavailableException(
              "SAM retries exhausted for '" + prompt.phrase() + "': " + e.getMessage(), e);
        }
        sleepBeforeRetry(e.retryAfterMs(), attempt);
      }
    }
    throw new SamUnavailableException("SAM retries exhausted for '" + prompt.phrase() + "'");
  }

  private String post(String phrase, byte[] renderedPng, String key, int attempt)
      throws RetryableSamException {
    String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(renderedPng);
    Map<String, Object> imagePart = new LinkedHashMap<>();
    imagePart.put("type", "input_image");
    imagePart.put("image_url", dataUrl);
    Map<String, Object> textPart = new LinkedHashMap<>();
    textPart.put("type", "input_text");
    textPart.put("text", phrase);
    Map<String, Object> message = new LinkedHashMap<>();
    message.put("type", "message");
    message.put("role", "user");
    message.put("content", List.of(textPart, imagePart));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", properties.metaApi().samModel());
    body.put("input", List.of(message));
    String json;
    try {
      json = objectMapper.writeValueAsString(body);
    } catch (IOException e) {
      throw new SamException("Cannot serialize SAM request", e);
    }
    String url = properties.metaApi().baseUrl() + properties.metaApi().responsesPath();
    HttpRequest request = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofMillis(properties.metaApi().requestTimeoutMs()))
        .header("Authorization", "Bearer " + key)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build();
    HttpResponse<String> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (HttpTimeoutException e) {
      throw new RetryableSamException("TIMEOUT", 0, "SAM request timed out", e);
    } catch (IOException e) {
      throw new RetryableSamException("CONNECTION_ERROR", 0, e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SamUnavailableException("SAM call interrupted", e);
    }
    int status = response.statusCode();
    if (status >= 200 && status < 300) {
      return extractOutputText(response.body());
    }
    if (status == 429 || status >= 500) {
      long retryAfterMs = retryAfterMs(response);
      throw new RetryableSamException("HTTP_" + status, retryAfterMs,
          "SAM HTTP " + status + " (attempt " + attempt + ")");
    }
    throw new SamException("SAM HTTP " + status + ": " + abbreviate(response.body()));
  }

  private long retryAfterMs(HttpResponse<String> response) {
    try {
      return response.headers().firstValue("Retry-After").map(value -> {
        long seconds = Long.parseLong(value.trim());
        return Math.min(30_000, Math.max(0, seconds * 1000));
      }).orElse(0L);
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  private void sleepBeforeRetry(long retryAfterMs, int attempt) {
    long backoff = (long) properties.metaApi().backoffInitialMs() * (1L << (attempt - 1));
    long jitter = ThreadLocalRandom.current().nextLong(0, 101);
    long waitMs = Math.max(retryAfterMs, backoff + jitter);
    try {
      Thread.sleep(waitMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SamUnavailableException("SAM backoff interrupted", e);
    }
  }

  /** Tolerantly extracts model output text from a Responses-API JSON payload. */
  private String extractOutputText(String body) {
    JsonNode root;
    try {
      root = objectMapper.readTree(body);
    } catch (IOException e) {
      throw new SamFormatException("SAM response is not JSON");
    }
    List<String> chunks = new ArrayList<>();
    JsonNode output = root.get("output");
    if (output != null && output.isArray()) {
      for (JsonNode item : output) {
        JsonNode content = item.get("content");
        if (content != null && content.isArray()) {
          for (JsonNode part : content) {
            JsonNode text = part.get("text");
            if (text != null && text.isTextual()) {
              chunks.add(text.asText());
            }
          }
        }
      }
    }
    if (chunks.isEmpty() && root.has("output_text") && root.get("output_text").isTextual()) {
      chunks.add(root.get("output_text").asText());
    }
    if (chunks.isEmpty()) {
      LOG.warn("SAM response carried no output text");
      throw new SamFormatException("SAM response carries no output text");
    }
    return String.join("\n", chunks);
  }

  private String abbreviate(String body) {
    if (body == null) {
      return "";
    }
    return body.length() <= 200 ? body : body.substring(0, 200) + "...";
  }

  private static final class RetryableSamException extends RuntimeException {
    private final String status;
    private final long retryAfterMs;

    RetryableSamException(String status, long retryAfterMs, String message) {
      super(message);
      this.status = status;
      this.retryAfterMs = retryAfterMs;
    }

    RetryableSamException(String status, long retryAfterMs, String message, Throwable cause) {
      super(message, cause);
      this.status = status;
      this.retryAfterMs = retryAfterMs;
    }

    String status() {
      return status;
    }

    long retryAfterMs() {
      return retryAfterMs;
    }
  }
}
