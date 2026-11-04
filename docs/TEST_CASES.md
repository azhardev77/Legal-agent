# TenderOps AI — Test Cases (ground truth from MASTER_PROMPT §§5, 13)

Fixed Clock for golden tests: `2026-11-04T10:00+05:30`. Expected values below are ground truth; if any looks
arithmetically wrong, the PROMPT_DEFECT rule applies (record, keep test, mark FAILED, continue).

## MONEY (MoneyUtilsTest)

- TC-MON-01: "100 Cr"→1000000000.0000; "Rs. 25 Lakhs"→2500000.0000; "Rs. 1,00,00,000"→10000000.0000;
  "45 Crores"→450000000.0000; "12.5 Cr"→125000000.0000; "abc"→typed exception; plus rupee-sign-prefixed variants.
- TC-MON-02: "Rs. 1.2 Cr."→12000000.0000; "Rs 25 lakh"→2500000.0000; rupee-sign "50,000"→50000.0000;
  lowercase units; negative → exception.

## FINANCIAL (FinancialEvaluatorTest)

- TC-FIN-01: 98/105/112 Cr vs ≥100 Cr → avg 105 Cr PASS.
- TC-FIN-02: average exactly equal (scales 100 vs 100.0000) → PASS.
- TC-FIN-03 (ROUNDING TRAP): 1000000000.0000 + 1000000000.0000 + 999999999.9999 vs avg threshold
  1000000000 → FAIL (sum 2999999999.9999 < 3000000000).
- TC-FIN-04: only 2 audited years when 3 required → UNKNOWN.
- TC-FIN-05: one of 3 years unaudited → NEEDS_REVIEW.
- TC-FIN-06: net worth 35 Cr vs 30 Cr PASS; 29.9999 Cr FAIL; no record UNKNOWN.
- TC-FIN-07 (solvency): none UNKNOWN; 14.9999 Cr FAIL; 15 Cr + doc + valid expiry PASS; 15 Cr no doc
  NEEDS_REVIEW; expiry before deadline FAIL.
- TC-FIN-08: audited record without supporting document → NEEDS_REVIEW (turnover and net worth).
- TC-CUR-01: USD record or USD requirement → NEEDS_REVIEW (CURRENCY_UNSUPPORTED).

## PROJECTS (ProjectEvaluatorTest)

- TC-PRJ-01: golden v1 5.1 → FAIL. TC-PRJ-02: golden v2 5.1 → PASS.
- TC-PRJ-03: project outside lookback not counted. TC-PRJ-04: domain mismatch not counted.
- TC-PRJ-05: qualifying project without completion certificate → NEEDS_REVIEW (documented fails, full passes).
- TC-PRJ-06 (E=60 Cr): one 48 Cr PASS; two 30 Cr each PASS; three 24 Cr each PASS; single 47.9999 Cr FAIL.
- TC-PRJ-07: zero project records → UNKNOWN.
- TC-PRJ-08: non-COMPLETED not counted; value used is contractValue.

## CERTIFICATES (CertificateEvaluatorTest)

- TC-CRT-01: CMMI expiry 2026-03-31 vs deadline 2026-12-15 → FAIL.
- TC-CRT-02: missing → UNKNOWN. TC-CRT-03: expiry == deadline date → PASS.
- TC-CRT-04: valid, no doc → NEEDS_REVIEW. TC-CRT-05: OR(CMMI expired, ISO 9001 valid+doc) → PASS.
- TC-CRT-06: CMMI level 5 satisfies required 3; level 2 does not.

## LOGIC (ConditionTreeTest)

- TC-LOG-01: full AND/OR truth tables over {PASS,FAIL,UNKNOWN,NEEDS_REVIEW} per §6.6 (all 16 two-operand
  combos + 3-operand spot checks).

## DECISION/SCORE/TIME/RISK

- TC-DEC-01: 9 PASS + 1 FAIL mandatory → NO_BID, score 90.00.
- TC-DEC-02: 2/5 UNKNOWN (40%) → INSUFFICIENT_DATA. TC-DEC-03: 3/10 UNKNOWN (exactly 30%) → CONDITIONAL_BID.
- TC-DEC-04: only NEEDS_REVIEW → CONDITIONAL_BID. TC-DEC-05: all mandatory PASS → BID + PENDING_HUMAN_APPROVAL.
- TC-DEC-06: non-mandatory FAIL + all mandatory PASS → BID with warning.
- TC-DEC-07: zero mandatory → INSUFFICIENT_DATA.
- TC-SCR-01: golden scores 40.00 / 80.00 / 100.00 / Variant B 75.00 / empty company 0.00.
- TC-TIME-01: 6 pages, baseline 4.0, processingMs 30000, 1 review item ×2.0 → manual 24.0, assisted 2.5, saved 89.58.
- TC-RSK-01: golden risks (v1: 2 CRITICAL + 1 HIGH; v2: 1 HIGH; Variant B: 1 CRITICAL), fixed Clock.
- TC-RSK-02: Clock 2026-12-10T10:00+05:30 + unresolved UNKNOWN → HIGH deadline risk; Clock 2026-11-04 → none.
- TC-RSK-03: Clock 2026-12-16 (deadline passed) → CRITICAL.

## INGESTION (PdfIngestionServiceTest, CoordinateUtilsTest)

- TC-ING-01: reject non-PDF, corrupted, empty, encrypted, JS-bearing, oversized.
- TC-ING-02: duplicate SHA-256 returns existing record without reprocessing.
- TC-ING-03: seeded PDF extracted text contains "Clause 4.1" etc.
- TC-ING-04: image-only page classified SCANNED.
- TC-ING-05: normalized boxes in [0,1], top-left origin (heading near top has y0 < 0.2), incl. rotated page.

## VISUAL (VisualDetectionTest, RegionContentTest)

- TC-VIS-01: fallback finds stamp (P6), signature (P6), table (P3) with IoU ≥ 0.5 vs ground truth.
- TC-VIS-02: fallback regions labelled FALLBACK_HEURISTIC.
- TC-VIS-03: SAM adapter parses test-double responses (documented/probed grammar).
- TC-VIS-04: SAM 429/500/timeout → retries then fallback, pipeline continues, logged.
- TC-VIS-05: no API field claims signature validity or stamp authenticity.
- TC-VIS-06: P3 table crop → structured_table exactly 4 rows × 2 cols + cell texts, source PDFBOX_GEOMETRIC.
- TC-VIS-07: P6 stamp text equals "MSDC DEMO SEAL" (normalized); vision-derived text → NEEDS_REVIEW.
- TC-VIS-08: Muse image input unavailable on SCANNED page → needsHumanReview, dependent reqs NEEDS_REVIEW,
  pipeline continues, logged.
- TC-VIS-09: no UI text/field claims stamp/signature validity.
- TC-VIS-10: Table 4-A produces ZERO requirements. TC-VIS-11: area > 40% region discarded.

## AI (MuseExtractorTest, FallbackExtractorTest, QuoteVerifierTest, GroundingTest)

- TC-AI-01: Muse request uses model muse-spark-1.3, Bearer header, wrapped excerpts (via double).
- TC-AI-02: malformed JSON → 2 retries with error feedback → NEEDS_REVIEW.
- TC-AI-03: quote not in page text → NEEDS_REVIEW/QUOTE_NOT_VERIFIED.
- TC-AI-04: confidence < 0.80 → NEEDS_REVIEW; outside [0,1] rejected.
- TC-AI-05: amountText converted by Java; model-supplied numeric ignored.
- TC-AI-06: missing MODEL_API_KEY → fallback works on golden PDFs, aiMode=FALLBACK.
- TC-AI-07: timeout/429/5xx backoff. TC-AI-08: key never in logs/events/responses.
- TC-AI-09: grounding check flags a number absent from the facts.
- TC-AI-10: golden extraction (fallback + double Muse) yields exactly the 6 golden requirements with correct
  page/clauseRef + tender metadata.
- TC-GEN-01: Variant_B through FALLBACK yields exactly 4 mandatory requirements, correct thresholds + §5 verdicts.
- TC-QTE-01: normalization handles rupee vs "Rs.", en/em dashes, curly quotes, line-break whitespace; stores
  the exact raw PDF span.
- TC-SEC-01: injection text (+ closing-tag variant) → sandbox neutralizes; verdicts identical to clean run.

## SECURITY

- TC-SEC-02: unauthenticated 401; VIEWER cannot approve/edit (403); cross-company read blocked.
- TC-SEC-03: expired/tampered JWT rejected. TC-SEC-04: rate limit triggers.
- TC-SEC-05: ProblemDetail errors without stack traces.
- TC-SEC-06: audit log has no update/delete path + records before/after.
- TC-SEC-07: non-dev profile fails to start without JWT secret.

## AMENDMENT (AmendmentServiceTest)

- TC-AMD-01: diff reports exactly 5.1 + 6.1 MODIFIED, 0 inserted, 0 deleted.
- TC-AMD-02: engine evaluates only 5.1/6.1 (spy/counter), copies 4.1/4.2/6.2/8.1 (context unchanged).
- TC-AMD-03: NO_BID → CONDITIONAL_BID, delta +40.00.
- TC-AMD-04: company data changed → unchanged requirements re-evaluated, not copied.
- TC-AMD-05: synthetic deadline change detected AND everything re-evaluated (none copied).
- TC-AMD-06: synthetic insertion/deletion detected.
- TC-AMD-07: DIFFERENTIAL — golden + ≥100 seeded-random scenarios: selective == full exactly.

## AGENT / REPORTS / JOBS

- TC-AGT-01: multi-step tool loop, invalid args recovered, steps persisted with latency.
- TC-AGT-02: step/time limits enforced.
- TC-AGT-03: persisted verdict equals Java engine output even if the model (double) asserts otherwise.
- TC-RPT-01: PDF starts with %PDF-, ≥2 pages, every clause ref + verdict + risk; XLSX opens with POI, all rows.
- TC-JOB-01: stage progress persisted; transient failure retried; stuck job recovered after restart.
- TC-JOB-02: second job for same version rejected/queued, never concurrent.

## REAL-TIME (SpringBootTest RANDOM_PORT + HttpClient)

- TC-RT-01: stage-order events, strictly increasing seq. TC-RT-02: Last-Event-ID replay (only later, no dup/miss).
- TC-RT-03: stream without token 401; cross-company 403/404. TC-RT-04: rolled-back tx emits nothing.
- TC-RT-05: no secrets/raw prompts in payloads. TC-RT-06: emitter cleanup after disconnect.
- TC-RT-07: polling returns identical sequence. TC-RT-08: measured time-to-first-event reported.

## E2E

- TC-E2E-00 (API): register NEW user → empty company → load demo RFP → all 5 mandatory UNKNOWN →
  INSUFFICIENT_DATA, 0.00.
- TC-E2E-01 (API, mandatory): seeded demo login → load demo RFP (consume stream) → v1 NO_BID 40.00 →
  demo corrigendum → CONDITIONAL_BID 80.00 → add solvency + doc + Re-Verify → BID 100.00 pending approval →
  approve → PDF + XLSX download → audit log contains the events.
- TC-E2E-02: Playwright UI flow of the same (or NOT_RUN with reason).

## CLIENT (Vitest + RTL)

Matrix 4 states; trace money strings unchanged; page-chip navigation; overlay toggles; source badges; upload
validation; error state; 401 redirect; decision banner ×4; banned strings ("probability"/"chance of winning"/
stamp validity); TC-RT-09 incremental rows; TC-RT-10 reconnect + Last-Event-ID + dedupe; TC-RT-11 polling
fallback + POLLING indicator.

## INVARIANTS (seeded, ≥500 iterations, seed on failure)

- TC-INV-01: no UNKNOWN when evidence exists; no FAIL when missing.
- TC-INV-02: no PASS without ≥1 linked evidence id.
- TC-INV-03: financial/project verdicts equal an independent test-side reference (BigInteger/rational).
- TC-INV-04: mandatory FAIL ⇒ NO_BID; BID ⇒ all mandatory PASS.
- TC-INV-05: same inputs twice ⇒ identical results + traces (R16).
- TC-INV-06: score ∈ [0.00,100.00] and equals the documented formula.

## PROBABILITY/CONFIDENCE

- TC-PRB-01: confidences validated to [0,1]; score only by formula; no win-probability in API/UI strings.
- TC-PRB-02: BENCHMARK_RESULTS.md reports extraction confidence vs correctness (bins ≥0.80 / <0.80) +
  NEEDS_REVIEW routing; sample tiny, not statistically meaningful.
- TC-PRB-03: mandatory FALSE-PASS rate 0.00% on golden + Variant B.

## NEGATIVE/PERFORMANCE + REAL-WORLD

Per §13 lists: bad files, no key, timeouts, model failure, dupes, unauthorized, malicious content, missing
company info (→ UNKNOWN). Real measured: per-page processing, extraction, AI latency (LIVE only), verification,
API times, 5 parallel uploads, JVM memory, time-to-first-event. data/real/ runs reported without accuracy claims
(or "folder is empty").
