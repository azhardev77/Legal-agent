package com.tenderops.visual;

import org.springframework.stereotype.Service;

/** Persists ai_call_logs rows (R10): every AI call, retry, fallback and error. No secrets. */
@Service
public class AiCallLogger {

  private static final int MAX_ERROR = 2000;

  private final AiCallLogRepository repository;

  public AiCallLogger(AiCallLogRepository repository) {
    this.repository = repository;
  }

  public AiCallLog log(Long runId, String component, String model, String purpose,
      String status, int attempt, Long latencyMs, String aiMode, String errorText) {
    String error = errorText == null ? null
        : errorText.length() <= MAX_ERROR ? errorText : errorText.substring(0, MAX_ERROR);
    return repository.save(new AiCallLog(runId, component, model, purpose, status, attempt,
        latencyMs, aiMode, error));
  }
}
