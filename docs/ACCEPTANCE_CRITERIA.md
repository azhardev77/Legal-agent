# TenderOps AI — Acceptance Criteria

## Functional (each has TC-IDs in TEST_CASES.md)

- AC-01: Golden v1 → NO_BID 40.00 with verdicts 4.1 PASS, 4.2 PASS, 5.1 FAIL, 6.1 FAIL, 6.2 UNKNOWN, 8.1 PASS;
  risks 2 CRITICAL + 1 HIGH (TC-E2E-01, TC-SCR-01, TC-RSK-01).
- AC-02: + Corrigendum → CONDITIONAL_BID 80.00, delta +40.00, 5.1/6.1 PASS (TC-AMD-01/02/03).
- AC-03: + solvency cert + Re-Verify → BID 100.00 PENDING_HUMAN_APPROVAL; approval works (TC-E2E-01).
- AC-04: Empty company → all mandatory UNKNOWN, INSUFFICIENT_DATA 0.00 (TC-E2E-00).
- AC-05: Variant B → NO_BID 75.00, 3.1 FAIL (TC-GEN-01).
- AC-06: Workspace updates live over SSE without refresh; reconnect + polling fallback work (TC-RT-*, TC-RT-09/10/11).
- AC-07: Reports: PDF (%PDF-, ≥2 pp, all refs/verdicts/risks), XLSX (all rows) (TC-RPT-01).
- AC-08: FALSE-PASS rate 0.00% on golden + Variant B (TC-PRB-03).
- AC-09: aiMode labelled on every run; FALLBACK never presented as LIVE (H3, CL-12).
- AC-10: Time-saved reported with assumptions + aiMode; FALLBACK "not representative" (TC-TIME-01, §6.9).

## Quality gates (§14)

- AC-11: `scripts/verify-all` from clean: server 0 failures, client build 0 errors + tests pass; skipped = 0.
- AC-12: TEST_REPORT.md + BENCHMARK_RESULTS.md contain measured values only, with UNVERIFIED/NOT_RUN reasons.
- AC-13: FINAL_CHECKLIST CL-01..CL-24 each evidenced PASS/FAIL/UNVERIFIED/NOT_RUN.
- AC-14: Mutations TC-MUT-01..08 all caught (or test gap added + re-run).
- AC-15: No TODO/FIXME/placeholder/@Disabled/-DskipTests; no secret in logs/docs/commits; money BigDecimal-only;
  client money strings only.
