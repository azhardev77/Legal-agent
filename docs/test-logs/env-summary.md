# Step A environment summary (observed 2026-11-04, Windows)

| Tool | Observed | Required | Status |
|---|---|---|---|
| java | OpenJDK 21.0.11 LTS (Red Hat build 21.0.11+10-LTS) | JDK 21 | OK |
| mvn | Apache Maven 3.9.6, home C:\apache-maven-3.9.6 | Maven 3.9+ | OK (see note) |
| node | v24.21.0 | Node 20+ | OK |
| npm | 11.19.0 | with Node 20+ | OK |
| docker client | 29.9.2 | present | OK binary |
| docker daemon | NOT running (cannot connect to npipe docker_engine) | needed for postgres profile + compose run | UNAVAILABLE now; postgres/compose-run UNVERIFIED unless daemon starts |
| git | 2.55.0.windows.5 | present | OK |
| python | 3.14.8 | n/a (utility) | present |
| openssl | not installed | n/a | absent; secrets generated via Node crypto (CSPRNG) |
| MODEL_API_KEY | empty | n/a | ALL AI components run in FALLBACK mode |

Notes:
- `mvn` prints "Access is denied." on stderr but exits 0 and reports its version; treated as cosmetic (likely a
  config-file read). Raw output in env-mvn.txt.
- `docker --version` prints a config.json "Access is denied" warning; the daemon itself is unreachable.
  Raw output in env-docker.txt.
- PowerShell runs in ConstrainedLanguage mode in this sandbox: no .NET crypto calls; .env secrets were
  generated with Node `crypto.randomBytes` (OS CSPRNG) and written directly to `.env` without display.
