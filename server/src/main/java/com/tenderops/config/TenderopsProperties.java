package com.tenderops.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tenderops")
public record TenderopsProperties(
    Jwt jwt,
    RateLimit rateLimit,
    Limits limits,
    Render render,
    MetaApi metaApi,
    Muse muse,
    Sam sam,
    TimeSaved timeSaved,
    String ruleVersion,
    String storageDir,
    Seeder seeder,
    Cors cors) {

  public record Jwt(String secret, int expiryHours) {}

  public record RateLimit(int loginPerMinute, int registerPerMinute, int uploadPerMinute) {}

  public record Limits(long maxPdfBytes, int maxPdfPages) {}

  public record Render(int dpi) {}

  public record MetaApi(
      String baseUrl,
      String responsesPath,
      String key,
      String museModel,
      String samModel,
      int connectTimeoutMs,
      int requestTimeoutMs,
      int maxAttempts,
      int backoffInitialMs) {}

  public record Muse(boolean structuredOutput, boolean toolCalling, boolean imageInput) {}

  public record Sam(String maskFormat) {}

  public record TimeSaved(double baselineMinutesPerPage, double reviewMinutesPerItem) {}

  public record Seeder(boolean enabled) {}

  public record Cors(String allowedOrigins) {}
}
