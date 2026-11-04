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
