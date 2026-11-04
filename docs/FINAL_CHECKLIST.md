# TenderOps AI — Final Checklist (SELF-AUDIT §15.1)

Each row: ID, statement, check method, result, evidence. Results filled at M9 with real evidence paths.

| ID | Statement | Check method | Result | Evidence |
|---|---|---|---|---|
| CL-01 | Folder names are exactly server/ and client/ | dir listing | TODO@M9 | |
| CL-02 | .env git-ignored and never committed | git history + check-ignore | TODO@M9 | |
| CL-03 | No secret values in any log, doc or commit | grep | TODO@M9 | |
| CL-04 | No TODO/FIXME/placeholder/@Disabled/-DskipTests | grep | TODO@M9 | |
| CL-05 | No double/float for money | grep + review | TODO@M9 | |
| CL-06 | No money arithmetic in client | grep | TODO@M9 | |
| CL-07 | Verdict paths use compareTo, no pre-comparison division | review + TC-FIN-03 | TODO@M9 | |
| CL-08 | Four verdict states only; UNKNOWN only from missing evidence | TC-INV-01 + review | TODO@M9 | |
| CL-09 | Every PASS has linked evidence ids | TC-INV-02 | TODO@M9 | |
| CL-10 | Every requirement has sourcePage + verified quote or QUOTE_NOT_VERIFIED | TC-AI-03/10 | TODO@M9 | |
| CL-11 | Sandbox wraps every model prompt containing PDF text | TC-AI-01 + TC-SEC-01 | TODO@M9 | |
| CL-12 | aiMode recorded + displayed for every run | TC-E2E-01 + UI | TODO@M9 | |
| CL-13 | No UI/API text claims probability, win chance, stamp/signature validity | grep + TC-VIS-05/09 | TODO@M9 | |
| CL-14 | Human approval gate present, role-restricted, audit-logged | TC-SEC-02 + E2E-01 | TODO@M9 | |
| CL-15 | Audit log has no update/delete path | TC-SEC-06 | TODO@M9 | |
| CL-16 | JWT secret enforced in non-dev | TC-SEC-07 | TODO@M9 | |
| CL-17 | Corrupt/encrypted/JS PDFs rejected | TC-ING-01 | TODO@M9 | |
| CL-18 | SSE endpoint authenticated, resumable, cleaned up | TC-RT-01..08 | TODO@M9 | |
| CL-19 | Frontend uses fetch-based SSE with polling fallback | TC-RT-09/10/11 | TODO@M9 | |
| CL-20 | Every TC-ID maps to a real test + real result (or UNVERIFIED/NOT_RUN) | REQUIREMENTS_TRACEABILITY.md | TODO@M9 | |
| CL-21 | scripts/verify-all works from clean checkout | run it | TODO@M9 | |
| CL-22 | All docs match what was built | prompt audit §15.5 | TODO@M9 | |
| CL-23 | Fallback extractor/detector never read golden/ground-truth data | grep + review | TODO@M9 | |
| CL-24 | README commands were actually executed | test-logs | TODO@M9 | |
