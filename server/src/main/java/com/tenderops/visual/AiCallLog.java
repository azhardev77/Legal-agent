package com.tenderops.visual;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ai_call_logs")
public class AiCallLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "run_id")
  private Long runId;

  @Column(nullable = false, length = 16)
  private String component;

  @Column(nullable = false, length = 64)
  private String model;

  @Column(nullable = false, length = 128)
  private String purpose;

  @Column(nullable = false, length = 24)
  private String status;

  @Column(nullable = false)
  private int attempt;

  @Column(name = "latency_ms")
  private Long latencyMs;

  @Column(name = "ai_mode", nullable = false, length = 16)
  private String aiMode;

  @Column(name = "error_text", columnDefinition = "TEXT")
  private String errorText;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected AiCallLog() {}

  public AiCallLog(Long runId, String component, String model, String purpose, String status,
      int attempt, Long latencyMs, String aiMode, String errorText) {
    this.runId = runId;
    this.component = component;
    this.model = model;
    this.purpose = purpose;
    this.status = status;
    this.attempt = attempt;
    this.latencyMs = latencyMs;
    this.aiMode = aiMode;
    this.errorText = errorText;
    this.createdAt = OffsetDateTime.now();
  }

  public Long getId() { return id; }
  public Long getRunId() { return runId; }
  public String getComponent() { return component; }
  public String getModel() { return model; }
  public String getPurpose() { return purpose; }
  public String getStatus() { return status; }
  public int getAttempt() { return attempt; }
  public Long getLatencyMs() { return latencyMs; }
  public String getAiMode() { return aiMode; }
  public String getErrorText() { return errorText; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
