# TenderOps AI — Frontend Architecture

client/: React 18 + TypeScript (strict) + Vite, React Router, TanStack Query, plain CSS. No money arithmetic
in the client — money renders as received strings (CL-06).

## Routes (9 screens)

| Route | Screen |
|---|---|
| /login, /register | Auth (401 anywhere redirects to /login) |
| / | Dashboard: real aggregates, time-saved + aiMode, live counters |
| /tenders | List + upload (type/size validation, duplicate warning) + "Load demo RFP" |
| /tenders/:id/workspace | Tender Workspace (primary screen, §10.4) |
| /tenders/:id/amendments | Corrigendum upload/one-click demo, side-by-side diff, transition + delta |
| /company | Company DB CRUD + document upload + Re-Verify |
| /tenders/:id/agent | Agent trace (live steps, tools, latency, aiMode) |
| /tenders/:id/package | Bid package: summary, checklist, questions, PDF/XLSX, approval sign-off |
| /audit, /settings | Audit log; ADMIN user list/roles |

Every screen: loading, empty, error states.

## Tender Workspace layout

- Left: page-image viewer (`/pages/{n}/image`) with toggleable overlays per region type (each badge shows
  SAM_3_1 vs FALLBACK_HEURISTIC), extracted table/stamp text panel beside the overlay with content-source
  badge (MUSE_VISION | PDFBOX_GEOMETRIC), highlighted source quote for the selected requirement.
- Right: LIVE stage stepper + activity feed; compliance matrix (4 visually distinct states PASS/FAIL/UNKNOWN/
  NEEDS_REVIEW); expandable calculation trace (money strings unchanged); source-page chip jumping the viewer;
  risk register; missing-document checklist; decision banner (4 decisions); readiness score "(rule-based)";
  time-saved badge + aiMode; connection-state indicator.

## Data layer (src/api, src/hooks)

- `apiClient`: fetch wrapper (base URL, Bearer injection, 401 redirect, ProblemDetail parsing).
- TanStack Query per resource; SSE events invalidate queries (`matrix`, `risks`, `decision`, dashboard stats
  on JOB_COMPLETED/DECISION_UPDATED).
- `useEventStream(versionId)`: fetch()-based SSE reader (ReadableStream parser):
  - sends `Authorization: Bearer`, parses `id:/event:/data:`, heartbeats ignored;
  - reconnect with backoff + `Last-Event-ID`; dedupe by seq (TC-RT-10);
  - after 3 failed reconnects → polling `?afterSeq=N` every 2 s (TC-RT-11); indicator updates.
- TC-RT-09: simulated SSE events make matrix rows appear incrementally (no refresh).

## Banned strings (grep-enforced in tests)

No UI text may contain "probability", "chance of winning", or any claim of stamp/signature validity
(TC-VIS-09 + client tests). Readiness score is always labelled "(rule-based)".

## Component tests (Vitest + RTL)

Matrix 4 states; trace drawer money strings; page-chip navigation; overlay toggles; source badges; upload
validation; error state; 401 redirect; decision banner ×4; banned-string scan; TC-RT-09/10/11.
