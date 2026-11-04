package com.tenderops.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.tenderops.config.TenderopsProperties;
import com.tenderops.visual.AiCallLog;
import com.tenderops.visual.AiCallLogRepository;
import com.tenderops.visual.AiCallLogger;
import com.tenderops.visual.VisionRegionReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** Muse-vision region reader: table/text transcription, fenced JSON, honest degradation. */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "tenderops.jwt.secret=ApiTestDummySecret0123456789abcdefghij0123",
    "tenderops.meta-api.key=TESTKEY-VISION-1",
    "tenderops.meta-api.max-attempts=2",
    "tenderops.meta-api.backoff-initial-ms=10",
    "tenderops.meta-api.request-timeout-ms=1000",
    "tenderops.muse.image-input=true"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class MuseVisionRegionReaderTest {

  private static HttpServer server;
  private static final Deque<Behavior> script = new ArrayDeque<>();
  private static final List<String> captured = new ArrayList<>();

  private record Behavior(int status, String outputText) {
    static Behavior success(String outputText) {
      return new Behavior(200, outputText);
    }

    static Behavior failure(int status) {
      return new Behavior(status, null);
    }
  }

  static {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/responses", exchange -> {
        String body;
        try (var in = exchange.getRequestBody()) {
          body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Behavior behavior;
        synchronized (script) {
          behavior = script.isEmpty() ? Behavior.failure(500) : script.removeFirst();
          captured.add(body);
        }
        byte[] payload;
        if (behavior.status() == 200) {
          String json = "{\"output\":[{\"content\":[{\"text\":"
              + quoteJson(behavior.outputText()) + "}]}]}";
          payload = json.getBytes(StandardCharsets.UTF_8);
        } else {
          payload = "{\"error\":\"double failure\"}".getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(behavior.status(), payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
          out.write(payload);
        }
      });
      server.setExecutor(Executors.newCachedThreadPool());
      server.start();
    } catch (IOException e) {
      throw new IllegalStateException("Cannot start vision test double", e);
    }
  }

  private static String quoteJson(String raw) {
    return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r") + "\"";
  }

  @AfterAll
  static void stopDouble() {
    if (server != null) {
      server.stop(0);
    }
  }

  @DynamicPropertySource
  static void doubleUrl(DynamicPropertyRegistry registry) {
    registry.add("tenderops.meta-api.base-url",
        () -> "http://127.0.0.1:" + server.getAddress().getPort());
  }

  @Autowired
  private MuseVisionRegionReader reader;

  @Autowired
  private AiCallLogRepository callLogs;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private TenderopsProperties properties;

  @BeforeEach
  void resetDouble() {
    synchronized (script) {
      script.clear();
      captured.clear();
    }
  }

  private static void enqueue(Behavior behavior) {
    synchronized (script) {
      script.addLast(behavior);
    }
  }

  private static final byte[] CROP = new byte[] {(byte) 0x89, 'P', 'N', 'G'};

  @Test
  void tableTranscriptionParses() {
    enqueue(Behavior.success("{\"rows\": [[\"A\", \"B\"], [\"C\", \"D\"]]}"));
    Optional<VisionRegionReader.StructuredTable> table = reader.readTable(CROP);
    assertThat(table).isPresent();
    assertThat(table.get().rows())
        .isEqualTo(List.of(List.of("A", "B"), List.of("C", "D")));
    synchronized (script) {
      assertThat(captured.get(0)).contains("input_image");
      assertThat(captured.get(0)).contains("data:image/png;base64,");
    }
  }

  @Test
  void fencedTableJsonParses() {
    enqueue(Behavior.success("```json\n{\"rows\": [[\"X\"]]}\n```"));
    Optional<VisionRegionReader.StructuredTable> table = reader.readTable(CROP);
    assertThat(table).isPresent();
    assertThat(table.get().rows()).isEqualTo(List.of(List.of("X")));
  }

  @Test
  void malformedTableDegradesToEmpty() {
    enqueue(Behavior.success("no json here"));
    assertThat(reader.readTable(CROP)).isEmpty();
    List<AiCallLog> logs = callLogs.findByComponentOrderById("MUSE");
    assertThat(logs.stream().map(AiCallLog::getStatus).toList())
        .contains("UNUSABLE_CONTENT");
  }

  @Test
  void textTranscriptionTrims() {
    enqueue(Behavior.success("  MSDC DEMO SEAL  "));
    assertThat(reader.readText(CROP)).hasValue("MSDC DEMO SEAL");
  }

  @Test
  void httpFailureDegradesToEmpty() {
    enqueue(Behavior.failure(500));
    enqueue(Behavior.failure(500));
    assertThat(reader.readTable(CROP)).isEmpty();
  }

  @Test
  void missingKeyReturnsEmptyWithoutCalling() {
    TenderopsProperties.MetaApi meta = new TenderopsProperties.MetaApi(
        "http://127.0.0.1:1", "/responses", "", "muse-spark-1.3", "sam-3.1", 100, 100, 1, 10);
    TenderopsProperties noKey = new TenderopsProperties(null, null, null, null, meta,
        new TenderopsProperties.Muse(false, false, false), null, null, "1.0",
        "data/storage", null, null);
    MuseVisionRegionReader readerNoKey = new MuseVisionRegionReader(noKey, objectMapper,
        new AiCallLogger(callLogs), new MetaApiTransport(noKey));
    assertThat(readerNoKey.readTable(CROP)).isEmpty();
    assertThat(readerNoKey.readText(CROP)).isEmpty();
    synchronized (script) {
      assertThat(captured).isEmpty();
    }
  }
}
