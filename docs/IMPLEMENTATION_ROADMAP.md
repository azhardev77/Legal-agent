# TenderOps AI — Implementation Roadmap

Milestones M1–M9 per MASTER_PROMPT §12, test-first. Each milestone gate needs real command output before moving on.
This file doubles as the implementation plan (`tasks/plan.md` equivalent; the §16 folder structure fixes `docs/`
as the location). Per-feature status lives in PROGRESS_TRACKER.md.

## Dependency graph

```
M1 foundation (pom/vite, profiles, migrations, security, utils, scripts)
 └─ M2 demo bootstrap (seeder, PDF generators, font, ground truth)
     ├─ M3 ingestion + visual (probe first if key; classifier, renderer, SAM/fallback, content extraction)
     │   └─ M4 AI extraction (Muse adapter, validation, sandbox, quote verify, fallback extractor)
     │       └─ M5 compliance core (golden + invariant tests FIRST, then engines/decision/score/risk/time)
     │           └─ M6 workflows, realtime, amendments, agent, reports (TC-E2E-00/01 here)
     │               └─ M7 client (fetch SSE, 9 screens, component tests)
     │                   └─ M8 infra (Dockerfiles, compose, OpenAPI, README, Playwright attempt)
     │                       └─ M9 verification + self-audit (verify-all, mutations, benchmark, traceability)
```

Vertical slices (each leaves the system working): M1 slice (auth works end-to-end) → M2 slice (demo data
loads) → M3 slice (upload → pages → regions) → M4 slice (upload → requirements) → M5 slice (requirements →
verdicts/decision, engine-tested) → M6 slice (full pipeline + live events + amendments + reports) → M7 slice
(UI on real API) → M8/M9 (ship + prove).

## Task slices (S/M/L per planning guidance; L split before starting)

### M1 Foundation (gate: utility unit tests + auth tests green)
- M1.1 (M): server pom (Boot 3.4.3, PDFBox 3.0.4, Flyway, jjwt 0.12.x, POI, springdoc, H2/PG), profiles yml,
  Flyway V1 (all tables), Jackson money config, virtual threads.
- M1.2 (M): security (JWT service/filter, RBAC, rate bucket, company scoping), ProblemDetail handler, audit.
- M1.3 (M): common utils (MoneyUtils, CoordinateUtils, PromptSanitizer, FileSafetyValidator) + unit tests.
- M1.4 (S): client scaffold (Vite+React+TS+Router+Query+Vitest), apiClient, auth screens.
- CHECKPOINT M1: `mvn -B clean test` green; `npm run build` + `npm test` green; commit.

### M2 Demo bootstrap (gate: 6/2/2 pages; golden clauses in extracted text; seed matches §5)
- M2.1 (M): font acquisition + R14 verification (or documented "Rs." fallback).
- M2.2 (L→split): PDF generators (v1 6pp, corrigendum 2pp, Variant B 2pp, DEMO support docs) + ground-truth JSON.
- M2.3 (M): DemoDataSeeder (user/company/projects/certs/docs) idempotent, non-prod only.
- M2.4 (S): expected.json golden fixture (tests only).
- CHECKPOINT M2: M2 gate tests green; commit.

### M3 Ingestion + visual (gate: TC-ING-*, TC-VIS-*)
- M3.0 (S): if key → run probe-meta-api (L1), record results, set flags. (No key here → record NOT RUN.)
- M3.1 (M): ingestion (validate/dedup/store), classifier, renderer.
- M3.2 (M): SAM adapter + grammar parser + cache + retry; test doubles.
- M3.3 (M): geometric fallback detector; content extraction (crops, PDFBOX_GEOMETRIC tables, stamp text).
- CHECKPOINT M3: TC-ING/VIS green; commit.

### M4 AI extraction (gate: TC-AI-*, TC-QTE-*, TC-GEN-01, TC-SEC-01)
- M4.1 (M): Muse adapter (schema, retries, confidence/amount rules) + doubles.
- M4.2 (M): sandbox + quote verifier (offset map, raw-span store).
- M4.3 (M): rule-based fallback extractor (+amendment phrases); TC-GEN-01 proves generality.
- CHECKPOINT M4: gate green; commit.

### M5 Compliance core (gate: TC-MON/FIN/CUR/PRJ/CRT/LOG/DEC/SCR/TIME/RSK/INV)
- M5.0 (M): WRITE golden + invariant tests FIRST (engine fixtures + fallback-pipeline level).
- M5.1 (M): MoneyUtils completion + financial/project/certificate evaluators + trees.
- M5.2 (M): decision engine + score + risk + time-saved + checklist/questions + summary grounding.
- CHECKPOINT M5: gate green (incl. 500-iteration invariants); commit.

### M6 Workflows/realtime/amendments/agent/reports (gate: TC-AMD/AGT/RPT/JOB/RT + E2E-00/01)
- M6.1 (M): jobs + stage runner + recovery + event store + AFTER_COMMIT broadcast + SSE + polling.
- M6.2 (M): amendment diff + merge + selective re-verify.
- M6.3 (M): agent tools + bounded loop + orchestrator fallback.
- M6.4 (M): PDF/XLSX reports + approval + dashboard stats + audit wiring.
- M6.5 (M): TC-E2E-00/01 end-to-end API journeys.
- CHECKPOINT M6: gate green; commit.

### M7 Client (gate: build 0 errors, npm test pass, component tests)
- M7.1 (M): fetch SSE reader + hooks + dashboard + tenders list/upload.
- M7.2 (L→split): Tender Workspace (viewer/overlays/matrix/traces/risks/decision).
- M7.3 (M): amendments, company DB, agent trace, bid package, audit/settings.
- M7.4 (S): banned-string + 401 + error-state tests.
- CHECKPOINT M7: gate green; commit.

### M8 Infra (gate: compose config valid; run verified or UNVERIFIED)
- Dockerfiles, compose, OpenAPI polish, README, .env.example, script hardening; Playwright attempt.
- CHECKPOINT M8: gate evidence recorded; commit.

### M9 Verification + self-audit
- verify-all from clean; TEST_REPORT; BENCHMARK_RESULTS; REQUIREMENTS_TRACEABILITY; mutations TC-MUT-*;
  INV/PRB runs; FINAL_CHECKLIST rows evidenced; SELF_AUDIT_REPORT; FINAL_DELIVERY_REPORT; DEMO_SCRIPT.

## Risks & mitigations (detail: RISK_REGISTER.md)

- No MODEL_API_KEY → FALLBACK everywhere; LIVE UNVERIFIED. Mitigation: docs-verified adapter shapes + doubles.
- Docker daemon down → postgres/compose-run UNVERIFIED. Mitigation: H2 default; retry daemon at M8.
- Playwright browsers → attempt; NOT_RUN with reason if blocked.
- Time shortage → finish earlier milestones completely (autonomy contract); never half-built critical paths.
