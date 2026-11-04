# TenderOps AI — Security and Privacy

## Authentication & authorization

- JWT (jjwt 0.12.x, HS256): `POST /auth/register`, `POST /auth/login` → `{accessToken, ...}`.
  Claims: sub=userId, companyId, role, exp (config, default 12 h). Filter validates signature + expiry;
  expired/tampered → 401 (TC-SEC-03).
- Roles: ADMIN (user mgmt, approval), BID_MANAGER (edit, approve), VIEWER (read-only). Approval endpoint
  rejects VIEWER with 403 (TC-SEC-02).
- Company scoping: every tender/company read checks `resource.companyId == caller.companyId`; cross-company
  access → 404 (no existence leak) (TC-SEC-02).

## Secrets (R9)

- `MODEL_API_KEY`, JWT secret, DB password come from environment/`.env` only. `.env` is git-ignored and was
  never committed (CL-02). Non-dev profiles fail fast at startup without a JWT secret (TC-SEC-07).
- Secrets are never logged, never appear in events/payloads/AI logs (TC-AI-08, TC-RT-05, CL-03 greps).

## Input & file safety

- Uploads: magic bytes, size + page caps, encrypted/JS-bearing PDF rejection (TC-ING-01); SHA-256 storage
  names (no path traversal); multipart restricted to `application/pdf` (+ client-side check).
- PDF text is UNTRUSTED: sandboxed before any model call (R4); prompt-injection MUST NOT change verdicts
  (TC-SEC-01). Model output is untrusted: schema-validated, quotes re-verified, amounts re-parsed, confidence
  range-checked.

## Hardening notes (threat-model review, M1)

- JWT role staleness: role changes take effect on next login (role rides the JWT). Sensitive actions
  (human approval) re-validate the role from the database instead of trusting the claim (implemented at M6).
- Register always creates BID_MANAGER; the role is never client-settable (no self-promotion). An ADMIN
  cannot change their own role (no accidental lockout).
- Login failures return a uniform 401 (no user enumeration). Register returns 409 for a taken email,
  which inherently reveals registration; accepted standard tradeoff, documented here.
- Rate buckets: login keyed by IP+email (counts failures too, checked before password verification);
  register keyed by IP after schema validation (malformed requests are cheap and uncounted).
  In-memory buckets are correct for a single instance (this deployment); multi-instance would need a
  shared store.
- Uploads (M3/M6): MIME allowlist + magic bytes + size/page caps + SHA-256 storage names; no path traversal.
- LLM output is untrusted input: schema-validated, quotes re-verified against raw text, amounts re-parsed,
  confidence range-checked; never into SQL/shell/paths (see AI_INTEGRATION.md sandboxing).

## Abuse controls

- In-memory token-bucket rate limits on login/register/upload (TC-SEC-04).
- Errors are RFC 7807 ProblemDetail without stack traces or internals (TC-SEC-05).
- CORS locked to the client origin (config).
- Swagger UI enabled in dev only.

## Audit & privacy

- Audit log insert-only with before/after JSON (R11, TC-SEC-06); covers auth, company writes, approvals,
  uploads, amendments, report downloads.
- AI call logs exclude secrets and raw prompts (R10).
- Demo data is synthetic and labelled (is_demo / "DEMO DOCUMENT"); no real company data is seeded.
