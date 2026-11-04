package com.tenderops.ai;

/**
 * Per-page requirement extraction. Live (Muse Spark) and fallback (rule-based) share this
 * contract; the service validates, verifies quotes, converts amounts and persists.
 */
public interface RequirementExtractor {

  Extraction.Result extract(String sourceDocument, int sourcePage, String pageText);
}
