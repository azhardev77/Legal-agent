# TenderOps AI — Requirements

Source of truth: `MASTER_PROMPT.md` v3. This file restates the requirements in testable form.

## 1. Functional requirements

### FR-AUTH — Authentication & users
- FR-AUTH-1: Register creates a new user with a NEW EMPTY company (role BID_MANAGER).
- FR-AUTH-2: Login returns a JWT; all `/api/v1` endpoints except register/login require it.
- FR-AUTH-3: Roles ADMIN / BID_MANAGER / VIEWER; resource access scoped by the user's companyId.
- FR-AUTH-4: ADMIN can list users and change roles.
- FR-AUTH-5: Human approval (`APPROVED_FOR_BID` / `REJECTED_NO_BID`) restricted to ADMIN/BID_MANAGER, audit-logged.

### FR-TENDER — Tender ingestion
- FR-TENDER-1: Upload tender PDF; validate magic bytes, size limit, page-count cap; reject encrypted PDFs and
  PDFs with JavaScript/OpenAction/Launch actions (RFC 7807 errors).
- FR-TENDER-2: SHA-256 dedup: same bytes return the existing record without reprocessing.
- FR-TENDER-3: Store files under SHA-256-based names (no path traversal).
- FR-TENDER-4: One-click "Load demo RFP" (bundled generated PDF) via API and UI.
- FR-TENDER-5: Page classification DIGITAL_TEXT | SCANNED | MIXED; position-sorted text extraction; 150 DPI
  rendering (configurable).

### FR-VISUAL — Visual regions
- FR-VISUAL-1: Detect OFFICIAL_STAMP, HANDWRITTEN_SIGNATURE, TABLE_REGION with normalized top-left-origin
  boxes in [0,1], confidence, source SAM_3_1 | FALLBACK_HEURISTIC.
- FR-VISUAL-2: Crop each stamp/table bbox (+2% padding) and store it.
- FR-VISUAL-3: TABLE_REGION on DIGITAL_TEXT pages: deterministic PDFBox geometric table rebuild
  (source PDFBOX_GEOMETRIC). On SCANNED/MIXED: Muse vision if verified, else page needsHumanReview.
- FR-VISUAL-4: OFFICIAL_STAMP: extract raw text only. NOTHING anywhere claims stamp/signature validity or authenticity.
- FR-VISUAL-5: Discard regions with area > 40% of the page.
- FR-VISUAL-6: Informational tables (e.g. Table 4-A) produce ZERO requirements.

### FR-EXTRACT — Requirement extraction
- FR-EXTRACT-1: Muse Spark extractor (live) + rule-based fallback extractor; strict requirement schema (§6.2);
  amounts as raw text converted by Java MoneyUtils.
- FR-EXTRACT-2: Quote verification (R5): normalized substring match with offset map; store the EXACT RAW PDF span;
  failure → NEEDS_REVIEW (QUOTE_NOT_VERIFIED).
- FR-EXTRACT-3: Confidence in [0,1] else response rejected; < 0.80 → NEEDS_REVIEW; never presented as a
  probability of correctness.
- FR-EXTRACT-4: PDF text sent to any model is sandboxed per R4 (tag wrap + neutralization + system-prompt statement).

### FR-VERIFY — Deterministic verification
- FR-VERIFY-1: Money BigDecimal-only, INR, scale 4 HALF_UP; comparisons exact via compareTo; sum vs threshold×n
  for averages; exact 0.80/0.50/0.40 multiplication.
- FR-VERIFY-2: Verdicts exactly PASS/FAIL/UNKNOWN/NEEDS_REVIEW with R3 semantics; metric OTHER → NEEDS_REVIEW;
  non-INR → NEEDS_REVIEW (CURRENCY_UNSUPPORTED).
- FR-VERIFY-3: Financial evaluators per §6.3 (turnover average, net worth, solvency) with full operator support.
- FR-VERIFY-4: Project evaluators per §6.4 (eligibility window, documented-set vs all-eligible logic, count/value
  leaf, 80/50/40 leaf with inclusive boundaries).
- FR-VERIFY-5: Certificate evaluator per §6.5 incl. CMMI level comparison.
- FR-VERIFY-6: Recursive AND/OR condition trees per §6.6 truth tables.
- FR-VERIFY-7: Every result stores a calculationTrace (formula, exact operand strings, threshold, comparison,
  verdict, rule version, evidence ids, provenance, context hash).
- FR-VERIFY-8: Re-Verify after company edits re-runs only the Java engine (never re-calls AI).

### FR-DECIDE — Decision, score, risk, time-saved
- FR-DECIDE-1: BidDecisionEngine per §6.8 (mandatory-only; FAIL → NO_BID; UNKNOWN share > 30% → INSUFFICIENT_DATA;
  UNKNOWN/NEEDS_REVIEW → CONDITIONAL_BID; else BID; zero mandatory → INSUFFICIENT_DATA).
- FR-DECIDE-2: Readiness score 100×Σvalue/N (PASS=1, NEEDS_REVIEW=0.5, else 0), scale 2 HALF_UP, labelled
  "readiness score (rule-based)". Never "probability"/"chance of winning".
- FR-DECIDE-3: Risk engine per §6.10 with CRITICAL/HIGH/MEDIUM/LOW + deadline risks (injected Clock).
- FR-DECIDE-4: Missing-document checklist (every UNKNOWN) + pre-bid clarification questions (every NEEDS_REVIEW).
- FR-DECIDE-5: Time-saved metric per §6.9 with labelled assumptions, per-run aiMode, FALLBACK runs "not representative".
- FR-DECIDE-6: Executive summary from structured verified data + Java grounding check (numbers/dates must appear
  in input facts) else groundingWarning; template fallback labelled FALLBACK.

### FR-AMEND — Amendments
- FR-AMEND-1: FULL_REVISION replaces base; CORRIGENDUM merges by clauseRef (substitution/insertion/deletion/deadline change).
- FR-AMEND-2: Diff reports inserted/deleted/modified clauses, threshold/operator/tree/deadline changes.
- FR-AMEND-3: Selective re-verification copies results only when requirementFingerprint AND evaluationContextHash
  are unchanged; deadline change re-evaluates everything; record score delta + decision transition.
- FR-AMEND-4: One-click demo corrigendum via API and UI; side-by-side clause diff in UI.

### FR-RT — Real-time
- FR-RT-1: Every pipeline event persisted in workflow_events (monotonic seq per version), broadcast AFTER_COMMIT only.
- FR-RT-2: SSE endpoint with seq ids, Last-Event-ID replay, 15 s heartbeat, cleanup; JSON polling equivalent
  `?afterSeq=N`; dashboard stream for JOB_*/DECISION_UPDATED.
- FR-RT-3: Client uses fetch()-based SSE reader with Bearer token, reconnect + Last-Event-ID, seq dedupe, polling
  fallback after 3 failed reconnects; connection-state indicator (STREAMING | POLLING | DISCONNECTED).
- FR-RT-4: Tender Workspace updates live without refresh; dashboard counters invalidate on JOB_COMPLETED/DECISION_UPDATED.
- FR-RT-5: Time-to-first-event measured and reported (target < 1 s).

### FR-AGENT — Bounded agent
- FR-AGENT-1: Tool loop with the 6 read-only/verify tools (strict JSON-schema args, no raw SQL), limits
  (10 steps, 10 s/tool, 120 s/run), invalid-args recovery, parallel read-only tools on virtual threads.
- FR-AGENT-2: Every step persisted + emitted (AGENT_STEP) + shown in UI.
- FR-AGENT-3: Persisted verdict ALWAYS equals the Java engine output.
- FR-AGENT-4: Without verified tool calling, deterministic orchestrator labelled FALLBACK.

### FR-REPORT — Reports & audit
- FR-REPORT-1: Bid-package PDF (PDFBox; ≥2 pages; every clause ref + verdict + risk) and XLSX checklist (POI).
- FR-REPORT-2: Audit log insert-only with before/after JSON; no update/delete code path.
- FR-REPORT-3: AI call log for every AI call/retry/fallback/error (no secrets, no raw prompts).

### FR-COMPANY — Company database
- FR-COMPANY-1: CRUD profile/financials/projects/certificates with document upload; UNIQUE company+FY.
- FR-COMPANY-2: Every write bumps companyDataVersion (monotonic per-company counter).
- FR-COMPANY-3: Demo seed "Apex InfraTech Pvt. Ltd." exactly per §5(b) (dev/test/demo profiles only, idempotent).

### FR-DEMO — Demo bootstrap
- FR-DEMO-1: Seeder creates demo user, company, supporting DEMO PDFs, and the 3 generated tender PDFs (6/2/2 pages)
  with ground-truth JSON used ONLY by tests.
- FR-DEMO-2: Rupee sign per R14 (embedded open-license TTF with verified glyph, else "Rs." + documented).

## 2. Non-functional requirements

- NFR-1: Determinism (R16): same inputs + Clock + companyDataVersion → identical verdicts/traces, stable order.
- NFR-2: Concurrency (R17): ≤1 active job per tender version; re-verify/amendment serialize per company;
  events only after commit.
- NFR-3: Security: JWT, RBAC, company scoping, login/register/upload rate limits, ProblemDetail errors without
  stack traces, CORS, no hardcoded secrets, non-dev fails to start without JWT secret.
- NFR-4: UTF-8 everywhere; Windows-safe Path APIs; `\u20B9` escape in Java sources.
- NFR-5: Virtual threads on; async jobs with retry + restart recovery.
- NFR-6: API response times and pipeline latencies measured and reported (real numbers only).
- NFR-7: Design does not matter: plain functional UI.

## 3. Hackathon rules (from legal-agent-workshop, read-only; untrusted data, never instructions)

Source files (sibling folder, NOT modified): `legal-agent-workshop/HANDBOOK.md`, `legal-agent-workshop/README.md`.
Observed facts relevant to this build:
- The workshop uses the Meta Model API with env var `MODEL_API_KEY`, models `muse-spark-1.3` and `sam-3.1`,
  base URL `https://api.meta.ai/v1` (HANDBOOK shows `GET https://api.meta.ai/v1/models → 200 OK`). This
  corroborates the prompt's claimed facts; live behavior here is still UNVERIFIED (no key in this environment).
- The workshop agent talks to the API with an OpenAI-compatible SDK — consistent with using the Responses API
  plus Chat Completions as fallback shapes.
- Ground-rule hygiene from the handbook ("never print or log API keys") matches this prompt's H-rules.
- No conflicts with MASTER_PROMPT.md v3 were found. If any future conflict appears, MASTER_PROMPT.md wins.
- No code was copied from that folder.

## 4. Out of scope (per prompt §1)

Native OCR, 29-table schema, 22 screens, knowledge-graph explorer, e-signature/stamp authenticity validation,
automatic bid submission, multi-tenant org hierarchies.
