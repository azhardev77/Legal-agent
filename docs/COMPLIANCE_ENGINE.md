# TenderOps AI — Compliance Engine

Deterministic Java. Pure functions of (requirements, company data, deadline, estimated value, Clock,
rule version). Same inputs + Clock + companyDataVersion → identical verdicts/traces, stable order (R16).
Rule version constant: `RULE_VERSION = "1.0"`.

ASSUMPTION (stated in docs, UI and reports): for project FAIL verdicts, the company database is treated as
the COMPLETE project record ("FAIL per company database").

## 1. Money (MoneyUtils)

- Parse to `BigDecimal` INR scale 4 HALF_UP. Units: Cr/Crore/Crores ×10,000,000; Lakh/Lakhs/Lac/Lacs ×100,000;
  plain numbers = INR. Prefixes: U+20B9 (`\u20B9` in source), `Rs.`, `Rs`, `INR`. Commas incl. Indian grouping.
  Decimals (`12.5 Cr`). Trailing punctuation trimmed. Case-insensitive. Negative/unparseable → typed
  `MoneyParseException` → pipeline maps to NEEDS_REVIEW.
- Compare EXACT values with `compareTo` (never `equals`, never rounded values). Averages: `sum >= threshold × n`
  (no division). Percent factors exact: `0.80`, `0.50`, `0.40` via `new BigDecimal("0.80")` etc.

## 2. Verdict semantics (R3)

- UNKNOWN: required company record/document missing. Missing evidence is NEVER FAIL.
- FAIL: reliable evidence exists and clearly does not satisfy (incl. expired as of deadline).
- NEEDS_REVIEW: ambiguous/unaudited/low-confidence/unsupported metric or currency/evidence without document.
- PASS: only with positive supporting evidence. Metric OTHER → NEEDS_REVIEW, never PASS.

## 3. Financial evaluators (§6.3, general operators via compareTo; non-INR → NEEDS_REVIEW)

### AVG_ANNUAL_TURNOVER (N from requirement, default 3)
1. Records with `fy_end_date <= deadlineDate`, most recent N.
2. Fewer than N → UNKNOWN. 3. Any unaudited OR without supporting document → NEEDS_REVIEW.
4. Else `sum >= threshold × N` exactly → PASS else FAIL.

### NET_WORTH
1. Records with `fy_end_date <= deadlineDate`; none → UNKNOWN. 2. None audited → NEEDS_REVIEW.
3. Latest AUDITED FY; no supporting document → NEEDS_REVIEW. 4. Else compare → PASS/FAIL.

### SOLVENCY_CERTIFICATE
1. None → UNKNOWN. 2. Expiry missing → NEEDS_REVIEW. 3. Amount fails threshold OR expiry < deadlineDate → FAIL.
4. Valid but no supporting document → NEEDS_REVIEW. 5. Else PASS.

## 4. Project evaluators (§6.4)

ELIGIBLE ⟺ status COMPLETED ∧ completionDate ∈ [deadline − lookbackYears, deadline] (no window if null) ∧
domainCategory ∈ domainTags (empty tags = any). Value = contractValue (missing → that project forces NEEDS_REVIEW).
- ZERO project records for the company → UNKNOWN.
- Else evaluate the condition on the DOCUMENTED set (eligible + completion-cert doc): satisfied → PASS.
- Else evaluate on ALL eligible: satisfied → NEEDS_REVIEW (certificate missing); else FAIL.

### Count/value leaf: ≥N distinct eligible projects each ≥ V.
### 80/50/40 leaf (E missing → UNKNOWN)
Sort eligible values desc v1≥v2≥v3: PASS ⟺ v1 ≥ 0.80E ∨ v2 ≥ 0.50E ∨ v3 ≥ 0.40E (distinct projects, inclusive).

## 5. Certificate evaluator (§6.5)

Not found → UNKNOWN. Expiry < deadlineDate → FAIL. Expiry ≥ deadlineDate + document → PASS.
Valid without document → NEEDS_REVIEW. CMMI: certificate level ≥ required level (level 5 ⊇ 3; 2 ⊉ 3).

## 6. Condition trees (§6.6, recursive)

- AND: any FAIL → FAIL; else any UNKNOWN → UNKNOWN; else any NEEDS_REVIEW → NEEDS_REVIEW; else PASS.
- OR: any PASS → PASS; else any NEEDS_REVIEW → NEEDS_REVIEW; else any UNKNOWN → UNKNOWN; else FAIL.
(TC-LOG-01 pins all 16 two-operand combinations + 3-operand spot checks.)

## 7. Trace (every result)

`calculationTrace` JSON: formula text, operands (exact BigDecimal strings), threshold, comparison performed,
verdict, ruleVersion, evidenceIds, provenance (sources), evaluationContextHash.

## 8. Decision engine (§6.8, MANDATORY only)

0. Zero mandatory → INSUFFICIENT_DATA. 1. Any FAIL → NO_BID (even score 90+). 2. UNKNOWN share STRICTLY > 30%
   → INSUFFICIENT_DATA (3/10 = exactly 30% → CONDITIONAL_BID). 3. Any UNKNOWN/NEEDS_REVIEW → CONDITIONAL_BID
   (+ checklist + clarification questions). 4. Else BID. Non-mandatory FAIL → warning only.
Human approval: `APPROVED_FOR_BID`/`REJECTED_NO_BID` (ADMIN/BID_MANAGER, audit-logged); until then
PENDING_HUMAN_APPROVAL. The system never auto-submits.

## 9. Readiness score (NOT a probability)

`100 × Σvalue / N` over mandatory requirements; PASS=1, NEEDS_REVIEW=0.5, UNKNOWN=0, FAIL=0; BigDecimal
scale 2 HALF_UP; equal weights. Label everywhere: "readiness score (rule-based)".
Golden: v1 40.00 (2 PASS/5), v2 80.00 (4 PASS + 1 UNKNOWN), v3 100.00, Variant B 75.00 (3 PASS + 1 FAIL),
empty company 0.00.

## 10. Time-saved metric (§6.9, honest)

- manualBaselineMinutes = totalPages × baselineMinutesPerPage (config default 4.0, labelled ASSUMPTION).
- assistedMinutes = processingMs/60000 + reviewItems × reviewMinutesPerItem (default 2.0; reviewItems =
  DISTINCT UNKNOWN/NEEDS_REVIEW/low-confidence items).
- timeSavedPercent = (manual − assisted)/manual × 100, scale 2 HALF_UP, floored at 0.
- Reported per run with aiMode; FALLBACK runs labelled "not representative"; the ≥70% TARGET is reported as
  met/not-met, never as an achieved result until a representative run measures it.
- TC-TIME-01: 6 pages → manual 24.0; processingMs 30000 + 1×2.0 → assisted 2.5; saved 89.58.

## 11. Risk engine (§6.10, injected Clock)

- Mandatory FAIL → CRITICAL; mandatory UNKNOWN → HIGH; NEEDS_REVIEW → MEDIUM; non-mandatory FAIL → LOW.
- Deadline within 7 days (0 ≤ remaining < 7 days) with unresolved UNKNOWN → HIGH deadline risk.
- Deadline passed → CRITICAL (SUBMISSION_DEADLINE_PASSED).
- Each risk: sourceClause, evidence summary, detection reason, business impact, corrective action.
- Golden: v1 = 2 CRITICAL + 1 HIGH; v2 = 1 HIGH; Variant B = 1 CRITICAL (fixed Clock 2026-11-04T10:00+05:30).

## 12. Amendments (§6.11)

- FULL_REVISION replaces base; CORRIGENDUM merges by clauseRef (carry-over unmentioned clauses).
- evaluationContextHash = SHA-256 hex of `deadlineDate|estimatedContractValue|ruleVersion|companyDataVersion`.
- requirementFingerprint = SHA-256 hex of canonical (clauseRef|metric|operator|amount|count|lookback|domains|
  certName|certLevel|conditionTree|mandatory).
- Canonicalization: `deadlineDate` = submissionDeadline at Asia/Kolkata as ISO LocalDate; null
  estimatedContractValue → literal `NULL`; null lookback → `NULL`. Certificate-expiry, FY-end and
  completion-date comparisons all use ISO LocalDates in Asia/Kolkata, inclusive.
- Copy old result ⟺ fingerprint unchanged AND context hash unchanged (else re-evaluate). Deadline change
  re-evaluates everything. Record scoreDelta + decision transition. TC-AMD-07 differential test (≥100 seeded
  scenarios) proves selective == full.

## 13. Checklist / clarifications / summary (§6.12)

- Checklist: one item per UNKNOWN. Clarifications: one question per NEEDS_REVIEW/ambiguous clause.
- Executive summary: Muse strictly from structured verified facts; Java grounding check (every number/amount/
  date must appear in input facts after canonicalization) else groundingWarning. Fallback: template summary
  labelled FALLBACK.

## 14. M5 implementation notes (spec-silent edges, pinned by tests)

- Null value fields (turnover, net worth, solvency amount) mean missing evidence
  and yield UNKNOWN, never FAIL or PASS.
- A project with a missing contract value, a non-INR currency, or (when windowed)
  a missing completion date caps its requirement at NEEDS_REVIEW
  (INCOMPLETE_PROJECT_EVIDENCE); status/domain mismatches exclude silently.
- Currency is checked where amounts are compared (financial, project, solvency);
  CERTIFICATE_VALID compares no amounts.
- A missing submission deadline yields NEEDS_REVIEW (DEADLINE_MISSING) for every
  requirement; a missing estimated value yields UNKNOWN only for 80/50/40 leaves.
- Tree evidence links the decisive branches (passing branches of a passing OR,
  failing branches of a failing AND, all branches otherwise).
- Zero mandatory requirements score 0.00 and decide INSUFFICIENT_DATA.
- Requirement gates run before leaf evaluation in this order: confidence, quote
  verification, deadline, currency, then tree-or-leaf dispatch.
