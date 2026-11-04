# TenderOps AI — Document Processing Pipeline

Stages: VALIDATE → PARSE → RENDER → VISUAL → EXTRACT → VERIFY → RISK → DECIDE.
Each stage is transactional per unit of work; events persist in the same transaction and broadcast AFTER_COMMIT.

## VALIDATE (PdfIngestionService + FileSafetyValidator)

1. Read bytes; enforce size limit (config `tenderops.limits.max-pdf-bytes`, default 25 MB) and page-count cap
   (default 200, checked after load).
2. Magic bytes must be `%PDF-`, else reject (TC-ING-01: non-PDF, corrupted, empty).
3. Reject encrypted PDFs (`Loader` reports encryption / password needed).
4. Reject PDFs with JavaScript / OpenAction / Launch actions: scan the catalog + page annotations + names
   dictionary for `/JS`, `/JavaScript`, `/OpenAction` with action type, `/Launch`.
5. SHA-256 of bytes; dedup per company (same bytes → return existing record, no reprocessing, `duplicate: true`).
6. Store under `data/storage/<sha256[0:2]>/<sha256>.pdf` (no path traversal: name derived from hash only).

## PARSE (position-sorted text + classifier)

1. `Loader.loadPDF`, per page extract text position-sorted (custom `PDFTextStripper` with
   `setSortByPosition(true)`), recording word boxes (PDFBox bottom-left points → normalized top-left [0,1]
   via `CoordinateUtils`, rotation-aware; TC-ING-05). Verified M3: `TextPosition.getYDirAdj()` is
   TOP-origin in unrotated user space at every page rotation, so the extractor flips it back
   (`PdfWordBoxExtractor`); vector scans track their own graphics state/CTM because PDFBox 3's bare
   `PDFStreamEngine` registers no state operators.
2. Classifier per page (in order):
   - embedded image covering > 60% of page area → SCANNED (or MIXED if extractable chars ≥ 50);
   - else extractable chars < 50 → SCANNED;
   - else U+FFFD ratio > 10% → MIXED;
   - else DIGITAL_TEXT.
3. Persist `document_pages` rows; emit PAGE_PARSED per page + STAGE_PROGRESS (page n of N).

## RENDER (PDFRenderer)

1. Render each page to PNG at 150 DPI default (config `tenderops.render.dpi`), stored under
   `data/storage/renders/<versionId>/p<n>.png`; record pixel size on the page row.
2. Render failures are per-page: mark `needs_human_review` with reason, continue (pipeline never crashes).

## VISUAL (detectors + content extraction)

1. Candidate pages: pages with images, signature/annexure keywords, or table-like layout (ruling lines).
   Non-candidate pages skip SAM calls (cost control) but still run the cheap geometric table scan.
2. For each (candidate page × prompt) with prompts `circular official stamp`, `handwritten signature`, `table`:
   - LIVE (key present): `Sam31Detector` → parse grammar → scale boxes (pixels → normalized) → regions.
   - Else/failure: `GeometricFallbackDetector` (§AI_INTEGRATION 3.5) → regions with FALLBACK_HEURISTIC.
3. Discard area > 40% (TC-VIS-11). Persist regions; emit REGION_DETECTED each.
4. Content extraction (§3.4): for OFFICIAL_STAMP + TABLE_REGION: crop (+2% padding) → store → region text /
   structured_table via PDFBOX_GEOMETRIC (DIGITAL_TEXT word clustering on y then x) or MUSE_VISION (scanned +
   verified image input) else needsHumanReview. Emit events; failures degrade, never crash.

## EXTRACT (requirements + metadata)

1. LIVE: `MuseSparkExtractor` over sandboxed page excerpts (R4) with strict JSON schema; validate; ≤2 retries
   with error feedback; quote verification (R5) replaces model quotes with exact raw spans; confidence < 0.80
   or quote mismatch flags NEEDS_REVIEW downstream (flags stored on the requirement).
2. FALLBACK: `RuleBasedFallbackExtractor` (§AI_INTEGRATION 3.4) producing the same schema; quotes are already
   exact spans (verified the same way for uniformity).
3. Tender metadata (reference, E, EMD, fee, deadline) parsed by Java from extractor raw texts (MoneyUtils +
   deadline parser for "15 December 2026, 17:00 IST" + variants). Deadline stored with +05:30.
4. Persist `tender_requirements` (+ fingerprint); emit REQUIREMENT_EXTRACTED each.
5. Table 4-A-style informational tables yield zero requirements (no requirement language → no clause; TC-VIS-10).

## VERIFY → RISK → DECIDE

See COMPLIANCE_ENGINE.md. VERIFY emits VERDICT_COMPUTED per requirement (matrix rows appear live), RISK emits
RISK_RAISED per risk, DECIDE emits DECISION_UPDATED once, then JOB_COMPLETED (or JOB_FAILED + STAGE_FAILED).

## Failure & recovery semantics

- Transient stage failure → job attempt++ with backoff (max 3) → JOB_FAILED with per-stage error (TC-JOB-01).
- Restart recovery: on boot, jobs stuck RUNNING past a heartbeat become QUEUED again (once) else FAILED.
- One active job per version: second submit rejected with 409 ProblemDetail (TC-JOB-02).
- Rollback: events written in a rolled-back transaction are never broadcast (TC-RT-04) because broadcast
  happens only in AFTER_COMMIT listeners.
