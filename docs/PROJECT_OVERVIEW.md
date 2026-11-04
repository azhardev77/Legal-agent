# TenderOps AI — Project Overview

## What this is

TenderOps AI ("Autonomous Tender & Compliance Processing Engine") is a hackathon build for the theme
"Build for Your World". It attacks one bottleneck: manually parsing complex government/enterprise tender PDFs
delays bidding. The product flow:

1. Upload a tender PDF (or load the bundled demo RFP with one click).
2. The server segments/extracts text, tables, stamps and signatures (SAM 3.1 via the Meta Model API, with a
   geometric fallback when no key/API is available).
3. Muse Spark extracts tender requirements with source page + exact quote (with a rule-based fallback extractor).
4. A **deterministic Java engine** verifies each requirement against the company's internal database using
   `BigDecimal`, producing verdicts (PASS / FAIL / UNKNOWN / NEEDS_REVIEW), risks, a missing-document checklist,
   a readiness score, a Bid/No-Bid recommendation and an executive summary.
5. A human approves or rejects; reports (PDF, XLSX) are generated.
6. The React UI shows all of this **live** via server-sent events as the pipeline runs.

## Core principle (non-negotiable)

The LLM only extracts and explains. It **never** decides a verdict, score or Bid/No-Bid. All verdicts come from
deterministic Java code. AI never authorizes submission; human approval is mandatory.

## Repository layout

```
tenderops-ai/
  MASTER_PROMPT.md        # single source of truth (this build follows v3)
  README.md               # run commands (PowerShell + bash), demo walkthrough
  .env / .env.example     # local secrets (never committed) / key template
  docker-compose.yml      # postgres + server + client
  scripts/                # run-server, run-client, run-tests, verify-all, run-live-check, probe-meta-api (.ps1 + .sh)
  docs/                   # documentation-first package + reports + test-logs/
  data/samples/           # generated demo PDFs (v1 RFP, corrigendum, Variant B)
  data/real/              # optional user-supplied real tenders (robustness runs only, no golden claims)
  data/storage/           # runtime files (gitignored): uploads, renders, crops
  server/                 # Java 21 + Spring Boot 3.4.3 (base package com.tenderops)
  client/                 # React 18 + TypeScript + Vite
```

There are exactly two app folders: `server/` and `client/`. No `backend/` or `frontend/` folders exist.

## How honesty is enforced

- Every AI run records `aiMode` per component (`MUSE = LIVE|FALLBACK`, `SAM = LIVE|FALLBACK`). LIVE requires a real
  HTTP 2xx from `api.meta.ai` whose output was actually used. Shown in UI and reports.
- No fallback/mock is ever presented as a real model integration.
- The 70% manual-review-time reduction is a TARGET until measured per the documented formula.
- Anything not run here (Docker daemon down, no live key, Playwright browsers) is marked UNVERIFIED/NOT_RUN, never PASSED.
- Raw command outputs live under `docs/test-logs/`; report numbers come only from those.

## Current run mode

`MODEL_API_KEY` is empty in this environment, so every AI component runs in FALLBACK mode here:
rule-based requirement extraction and geometric visual detection. LIVE was not tested (see `docs/LIVE_CONFORMANCE.md`).
Switching to LIVE is: set the key in `.env`, restart the server, run `scripts/run-live-check`.
