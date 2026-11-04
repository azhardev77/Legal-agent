# TenderOps AI — Final Checklist (SELF-AUDIT §15.1)

Each row: ID, statement, check method, result, evidence. Results filled at M9 with real evidence paths.

| ID | Statement | Check method | Result | Evidence |
|---|---|---|---|---|
| CL-01 | Folder names are exactly server/ and client/ | dir listing | PENDING | |
| CL-02 | .env git-ignored and never committed | git history + check-ignore | PENDING | |
| CL-03 | No secret values in any log, doc or commit | grep | PENDING | |
| CL-04 | No task markers, fix-me markers, filler markers, JUnit-disable annotations, or Maven test-skip flags (exact tokens per MASTER_PROMPT H2) | grep | PENDING | |
| CL-05 | No double/float for money | grep + review | PENDING | |
| CL-06 | No money arithmetic in client | grep | PENDING | |
| CL-07 | Verdict paths use compareTo, no pre-comparison division | review + TC-FIN-03 | PENDING | |
| CL-08 | Four verdict states only; UNKNOWN only from missing evidence | TC-INV-01 + review | PENDING | |
| CL-09 | Every PASS has linked evidence ids | TC-INV-02 | PENDING | |
| CL-10 | Every requirement has sourcePage + verified quote or QUOTE_NOT_VERIFIED | TC-AI-03/10 | PENDING | |
| CL-11 | Sandbox wraps every model prompt containing PDF text | TC-AI-01 + TC-SEC-01 | PENDING | |
| CL-12 | aiMode recorded + displayed for every run | TC-E2E-01 + UI | PENDING | |
| CL-13 | No UI/API text claims probability, win chance, stamp/signature validity | grep + TC-VIS-05/09 | PENDING | |
| CL-14 | Human approval gate present, role-restricted, audit-logged | TC-SEC-02 + E2E-01 | PENDING | |
| CL-15 | Audit log has no update/delete path | TC-SEC-06 | PENDING | |
| CL-16 | JWT secret enforced in non-dev | TC-SEC-07 | PENDING | |
| CL-17 | Corrupt/encrypted/JS PDFs rejected | TC-ING-01 | PENDING | |
| CL-18 | SSE endpoint authenticated, resumable, cleaned up | TC-RT-01..08 | PENDING | |
| CL-19 | Frontend uses fetch-based SSE with polling fallback | TC-RT-09/10/11 | PENDING | |
| CL-20 | Every TC-ID maps to a real test + real result (or UNVERIFIED/NOT_RUN) | REQUIREMENTS_TRACEABILITY.md | PENDING | |
| CL-21 | scripts/verify-all works from clean checkout | run it | PENDING | |
| CL-22 | All docs match what was built | prompt audit §15.5 | PENDING | |
| CL-23 | Fallback extractor/detector never read golden/ground-truth data | grep + review | PENDING | |
| CL-24 | README commands were actually executed | test-logs | PENDING | |
