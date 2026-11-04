# TenderOps AI — Tech Stack

Fixed by MASTER_PROMPT §4. Each item is justified below; nothing unneeded is added.

## Server (server/)

| Technology | Version | Why |
|---|---|---|
| Java | 21 (LTS) | Required; records, virtual threads, HttpClient for Meta API calls |
| Spring Boot | 3.4.3 | web, data-jpa, validation, security, actuator |
| spring.threads.virtual.enabled | true | Agent parallel tools + SSE emitters on virtual threads |
| Apache PDFBox | 3.0.4 | PDF parsing, rendering, report generation (`Loader.loadPDF`, `PDFRenderer`) |
| Flyway | Boot-managed | Single schema source; migrations run on H2 and PostgreSQL |
| H2 | Boot-managed | Default/dev/test DB in PostgreSQL-compatibility mode (no Docker needed) |
| PostgreSQL driver | Boot-managed | `postgres` profile (PostgreSQL 17 via compose) |
| springdoc-openapi | 2.x | OpenAPI docs (required deliverable surface) |
| Apache POI | 5.x (ooxml) | XLSX checklist generation |
| jjwt | 0.12.x (api/impl/jackson) | JWT issue/validation (HS256) |
| JUnit 5 + Mockito + AssertJ | Boot-managed | Unit/integration tests |
| spring-boot-starter-test | Boot-managed | MockMvc, @SpringBootTest |
| Jackson | Boot-managed | JSON + TEXT-column AttributeConverters |

Decisions:
- PDF report is generated with PDFBox directly. OpenHTMLToPDF is NOT used (PDFBox 2.x conflict risk).
- No OCR library (out of scope). Scanned pages without verified Muse image input → needsHumanReview.
- JSON columns stored as TEXT via Jackson AttributeConverter (works identically on H2 and PostgreSQL;
  avoids jsonb/H2-JSON dialect split). Single decision, documented here.
- No testcontainers: H2 covers `mvn clean test`; the postgres profile is tested only if a Docker daemon exists.
- No Spring resource-server: jjwt keeps JWT logic explicit and small.

## Client (client/)

| Technology | Version | Why |
|---|---|---|
| React | 18 | Required |
| TypeScript | 5.x | Required (strict) |
| Vite | 6.x | Required dev/build |
| React Router | 6/7 | Required routing |
| TanStack Query | 5.x | Required server-state + live invalidation |
| Vitest + React Testing Library | latest | Required component tests |
| Plain CSS | — | No design effort; single stylesheet |

- No Tailwind (plain CSS is enough for a functional UI).
- Money displayed as strings; no money arithmetic in the client (CL-06 greps enforce this).
- Playwright attempted for TC-E2E-02; if browsers cannot install → NOT_RUN with reason.

## Infra

- Dockerfiles (server multi-stage Maven build; client nginx or node-serve static) + docker-compose.yml
  (postgres 17 + server + client). Compose run verified only if a daemon exists.
- Profiles: default/dev/test → H2; `postgres` → PostgreSQL 17; `prod` → postgres + seeder OFF + JWT secret required.

## Fonts (R14)

- Generated PDFs and report PDFs embed a bundled open-license TrueType font containing U+20B9, loaded via
  `PDType0Font.load`, glyph presence verified programmatically at startup/test, stored under
  `server/src/main/resources/fonts/`. If no such font can be obtained, generated PDFs write "Rs." and this
  decision is documented in M2's evidence. (Update at M2 with the actual outcome.)
