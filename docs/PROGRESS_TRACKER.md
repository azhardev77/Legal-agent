# TenderOps AI — Progress Tracker (resume memory)

Statuses: NOT_STARTED, IN_PROGRESS, BLOCKED, IMPLEMENTED, TESTING, PASSED, FAILED, UNVERIFIED, NOT_RUN.
Rule: continue from the first item not PASSED. Git-commit after every passing milestone (never .env, never
data/storage). After context loss: re-read MASTER_PROMPT.md + this file.

## Current position

- Step 0.1 bootstrap: PASSED (P created, MASTER_PROMPT.md saved, .gitignore/.env/.env.example, 12 scripts,
  initial commit d926134; handbook read-only recorded in REQUIREMENTS.md).
- Step A environment: PASSED (JDK 21.0.11, Maven 3.9.6, Node 24.21.0, npm 11.19.0, git 2.55.0; docker daemon
  DOWN; MODEL_API_KEY EMPTY → FALLBACK mode; evidence docs/test-logs/env-*).
- Step B docs: PASSED (20 files). Step C gate: PASSED (logged below). Next: M1 Foundation.
- M1 Foundation: PASSED (server 57/57, client build+test green). M2 Demo bootstrap: PASSED (server 69/69
  green, m2-gate3.log). Next: M3 ingestion + visual (no MODEL_API_KEY → probe NOT_RUN, FALLBACK).
- MODEL_API_KEY is EMPTY: every AI component runs in FALLBACK mode. LIVE was not tested.

## Feature matrix

Columns: REQ(doc) ARCH DOCS | SRV CLI API | UNIT INT E2E NEG | SEC PERF ACC — Status

| Feature | Docs | Server | Client | Tests | Status |
|---|---|---|---|---|---|
| M1 foundation (profiles, security, utils) | PROJECT_OVERVIEW..PROGRESS_TRACKER | PASSED | PASSED | 57/57 + client smoke | PASSED |
| M2 demo bootstrap (seeder, PDFs, font) | §5 restated in REQUIREMENTS | PASSED | — | 12/12 new, 69/69 total | PASSED |
| M3 ingestion + visual | DOCUMENT_PROCESSING_PIPELINE, AI_INTEGRATION | NOT_STARTED | — | TC-ING/VIS NOT_STARTED | NOT_STARTED |
| M4 AI extraction | AI_INTEGRATION | NOT_STARTED | — | TC-AI/QTE/GEN/SEC-01 NOT_STARTED | NOT_STARTED |
| M5 compliance core | COMPLIANCE_ENGINE | NOT_STARTED | — | golden+INV written FIRST | NOT_STARTED |
| M6 workflows/realtime/amend/agent/report | REALTIME_DESIGN, API_SPEC | NOT_STARTED | — | TC-AMD/AGT/RPT/JOB/RT/E2E NOT_STARTED | NOT_STARTED |
| M7 client | FRONTEND_ARCHITECTURE | — | NOT_STARTED | component + RT-09/10/11 NOT_STARTED | NOT_STARTED |
| M8 infra | DEPLOYMENT_GUIDE | NOT_STARTED | NOT_STARTED | E2E-02 attempt | NOT_STARTED |
| M9 verification + self-audit | TEST_REPORT etc. | NOT_STARTED | NOT_STARTED | MUT/INV/PRB + checklist | NOT_STARTED |

## Step C doc validation gate (log)

PASSED 2026-11-04. Cross-checked all 20 docs against each other and MASTER_PROMPT.md §§1–16:
- Golden arithmetic re-verified: v1 40.00 (2/5 PASS), v2 80.00 (4 PASS + 1 UNKNOWN), v3 100.00,
  Variant B 75.00 (3 PASS + 1 FAIL), empty 0.00, TC-TIME-01 89.58, TC-DEC-03 strict >30% rule, 80/50/40
  boundaries (48/30/24 Cr, inclusive), OR-table outcomes for v2 5.1/6.1. No PROMPT_DEFECT found.
- API paths (API_SPECIFICATION) match scripts (run-live-check → /auth/login + /demo/live-check),
  REALTIME_DESIGN (SSE + ?afterSeq=N), DATABASE_DESIGN (all tables/columns used by endpoints), and
  FRONTEND_ARCHITECTURE (9 routes). Demo endpoints profile-gated consistently.
- Context-hash/fingerprint canonicalization was underspecified → fixed in COMPLIANCE_ENGINE.md
  (Asia/Kolkata deadlineDate, NULL literals, inclusive LocalDate comparisons).
- Pinned versions verified on Maven Central: spring-boot-starter-parent 3.4.3 (200 OK) and
  pdfbox 3.0.4 (200 OK). No version-substitution risk.
- Sections 2/5/6 never relaxed. Proceeding to M1 automatically per gate rule.

## Build-hygiene notes (tooling lessons, not product behavior)

- Byte-exactness: file transports are byte-exact, but the author's emission of backslash runs,
  unicode escapes and long random strings is untrustworthy (MoneyUtils RUPEE arrived as ESC[H;
  the .ps1 PNG blob arrived as a bracketed marker while rendered output misleadingly showed a full
  blob). Protocol: Java/needs-escape content uses ASCII marker tokens + python chr()-based
  replacement; every such file is asserted by docs/test-logs/verify_bytes.py (exit 0 required);
  long random strings are length/charset-guarded, never trusted from rendered output.
- Bracketed tokens (e.g. [BASE64_..._1]) may render expanded in tool output: trust ordinals/lengths.
- write_file can spuriously fail with "already exists" (os 183); retry succeeds. Verify batch file
  presence after each write batch.
- Banned grep tokens (H2/CL-04 exact strings) must never appear in authored files; MASTER_PROMPT.md
  keeps them verbatim and is excluded from the M9 grep with this documented reason.

## Milestone log

| Milestone | Result | Evidence | Commit |
|---|---|---|---|
| Bootstrap | PASSED | initial commit | d926134 |
| Step A | PASSED | docs/test-logs/env-* | (with docs commit) |
| Step B+C docs + gate | PASSED | 20 docs + this log | (docs commit) |
| M1 Foundation | PASSED | server 57/57 green (m1-gate-green.log), client build+test green | (M1 commit) |
| M2 Demo bootstrap | PASSED | server 69/69 green (m2-gate3.log, MVN_EXIT 0): 6/2/2-page PDFs, golden clauses, §5 seed, idempotent | (M2 commit) |
| M3..M9 | NOT_STARTED | | |

M2 failure-fix loop (all fixed, none in tests' ground truth): (1) Hibernate validate rejected
company_documents.sha256 VARCHAR mapping — V1 DDL is CHAR(64); entity fixed to CHAR(64). V1 already
defines the full M2 schema (no V2 migration needed). (2) StorageService dual constructors confused
Spring DI — @Autowired added to the primary. (3) Generator-test phrase assertions broke on layout
line-wraps — test now collapses whitespace (content check, TC-ING-03).

## Sandbox build mechanics (this session only; user scripts run plain mvn/npm)

- Shell runs as `muse-sbx-r1`; repo lives under `C:\Users\white\...`. Sandboxed java.exe CANNOT
  canonicalize (toRealPath) or ZipFS-close jars there, and cannot use sockets at all; esbuild cannot
  read parent dirs. Consequence: ALL `mvn`/`npm run build`/`npm test` commands in this session must
  run with `require_escalated` (auto-approved here), else they fail for sandbox reasons, not code reasons.
- Maven is fully offline: `-o "-Dmaven.repo.local=<P>\.m2repo"` (quotes REQUIRED for -D key=value args;
  unquoted -D is silently dropped). `.m2repo` = copy of the pre-seeded `~/.m2` cache minus broken
  `_remote.repositories` tracking, plus regenerated installed-form markers; gitignored.
- Direct Maven network is denied; the sandbox proxy port rotates per shell invocation, so no static
  proxy config is viable. Current flags are deterministic and offline.
- npm needs per-command proxy env (`npm_config_proxy/https_proxy` from `$env:HTTPS_PROXY`) plus a
  project-local cache (`--cache <P>\.npm-cache`, gitignored). package-lock.json IS committed (ci needs it).
- Dependency pins forced by offline-cache gaps (documented in pom + TECH_STACK.md): surefire 3.5.6
  (Boot's 3.5.2 lacks maven-surefire-common), JUnit 5.12.2 (cache lacks platform-launcher 1.11.4).
- Flyway V1 uses `TIMESTAMP WITH TIME ZONE` (H2 has no TIMESTAMPTZ type, even in PG mode).
- A concurrent process (likely an IDE as another user) also compiles into server/target; escalated
  `clean` wins deterministically. Evidence: docs/test-logs/m1-*.log, hello/ probes.
