# TenderOps AI — Risk Register

| ID | Risk | Impact | Status | Mitigation / Evidence |
|---|---|---|---|---|
| RSK-REG-01 | MODEL_API_KEY empty → no LIVE calls possible | High | OPEN | FALLBACK everywhere; adapters built to docs-verified shapes; LIVE UNVERIFIED; switching = set key + run-live-check |
| RSK-REG-02 | Docker daemon not running | Medium | OPEN | H2 default profile; postgres/compose-run UNVERIFIED unless daemon starts; retry at M8 |
| RSK-REG-03 | `mvn` prints "Access is denied" on stderr | Low | OPEN | Exits 0, version OK; cosmetic; watch for real failures in logs |
| RSK-REG-04 | ConstrainedLanguage PowerShell in sandbox | Low | OPEN | Avoid .NET calls in commands; Node/Python for crypto; user-run scripts use full language |
| RSK-REG-05 | No official Java SAM decoder | Medium | OPEN | Parse boxes per documented grammar (mandatory); store raw mask; raster decode best-effort/documented |
| RSK-REG-06 | Live API shapes drift from docs | Medium | OPEN | Config-driven adapters + probe script + test doubles; record probe results when key exists |
| RSK-REG-07 | Playwright browsers uninstallable | Low | OPEN | Attempt at M8; else TC-E2E-02 NOT_RUN with reason |
| RSK-REG-08 | R14 font unobtainable offline | Low | CLOSED (fallback) | Offline search found no open-license TTF with U+20B9 (see TECH_STACK.md). "Rs." fallback active + pinned by RsFallbackTest; MoneyUtils/quote-verify accept both forms. |
| RSK-REG-09 | Time/effort shortage | High | OPEN | Finish earlier milestones completely; no half-built critical workflows |

PROMPT_DEFECT entries (if any golden value proves arithmetically wrong): none at Step B. Rule: keep the test,
record arithmetic here, mark FAILED in the report, continue.
