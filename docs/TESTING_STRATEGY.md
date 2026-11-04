# TenderOps AI — Testing Strategy

## Principles (H1/H2)

- No claim without a run test seen passing. Every report number comes from a real command; raw logs in
  `docs/test-logs/`.
- Never tune tests to code output; never delete/skip/@Disabled/weaken; never `-DskipTests`. Skipped = 0.
- Test doubles (JDK HttpServer) live only in `src/test`, named as doubles; they NEVER produce LIVE aiMode.
- Ground-truth JSON is read only by tests, never by `src/main` (CL-23 greps + review).
- Fixed Clock `2026-11-04T10:00+05:30` (Asia/Kolkata) for ALL golden tests (R8).
- Invariants: seeded-random, ≥500 iterations each, seed printed on failure.
- Mutation sanity (§15.2) proves the tests can fail; survivors become new tests.

## Layers

| Layer | Where | What |
|---|---|---|
| Unit | server `*Test`, client `*.test.ts` | MoneyUtils, evaluators, trees, decision, score, risk, time-saved, sanitizer, quote verify, coordinates, SAM parser, diff, fingerprints |
| Integration (slice) | `@SpringBootTest` H2 | Ingestion, pipeline stages, fallback extractor on generated PDFs, detectors, agent loop vs double, reports, jobs |
| API E2E | `@SpringBootTest(RANDOM_PORT)` + HttpClient | TC-E2E-00/01 full golden journeys; real SSE stream tests TC-RT-* |
| Component | Vitest + RTL | Matrix, traces, viewer, overlays, banners, upload, 401, banned strings, TC-RT-09/10/11 |
| Browser E2E | Playwright | TC-E2E-02 attempted; NOT_RUN with reason if browsers uninstallable |
| Differential | JUnit seeded | TC-AMD-07 (≥100 scenarios): selective == full |
| Invariants | JUnit seeded | TC-INV-* (≥500 iterations each) |
| Negative/perf | JUnit + scripts | bad files, no key, timeouts, dupes, malicious content; real latency numbers |

## Commands

- Server: `mvn -B clean test` (exit 0, 0 failures). Surefire `-Dfile.encoding=UTF-8`.
- Client: `npm ci --no-audit --no-fund`, `npm run build` (0 errors), `npm test` (vitest run).
- Everything: `scripts/verify-all` (clean checkout, logs to docs/test-logs/).
- Live (needs key + running server): `scripts/run-live-check`; LIVE results go to docs/LIVE_CONFORMANCE.md,
  never asserted in `mvn test` (non-deterministic).

## Traceability

Every TC-ID maps to test file/method + last real result in docs/REQUIREMENTS_TRACEABILITY.md (M9).
M5 writes golden + invariant tests FIRST (engine-level fixtures independent of AI; pipeline-level via fallback).
