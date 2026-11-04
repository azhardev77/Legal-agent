# TenderOps AI — Deployment Guide

## Local run (development)

Prereqs: JDK 21, Maven 3.9+, Node 20+. `.env` present (copy `.env.example`, fill values; secrets stored in `.env`).

PowerShell:
```powershell
.\scripts\run-server.ps1   # :8080, dev profile (H2, seeder ON)
.\scripts\run-client.ps1   # :5173
```
Bash: `bash scripts/run-server.sh`, `bash scripts/run-client.sh`.

Demo login: `demo@tenderops.local`, password stored in `.env` (`DEMO_ADMIN_PASSWORD`).

## Postgres profile

```powershell
$env:SPRING_PROFILES_ACTIVE="postgres"
# set DB_URL/DB_USER/DB_PASSWORD (or edit application-postgres.yml defaults for compose)
mvn -B -f server/pom.xml spring-boot:run "-Dspring-boot.run.profiles=postgres"
```
Requires PostgreSQL 17 (compose provides it). UNVERIFIED while the Docker daemon is down.

## Docker Compose (postgres + server + client)

```powershell
docker compose config   # validate
docker compose up --build
```
UNVERIFIED while the Docker daemon is down (see TEST_REPORT.md for the current status).

## Production notes

- Profile `prod`: seeder OFF, Swagger OFF, JWT secret REQUIRED (fail-fast), H2 console OFF.
- `data/storage` must be a persistent volume; `.env` never committed; logs exclude secrets.
- LIVE AI needs `MODEL_API_KEY` set; without it the app runs fully in FALLBACK mode (labels shown).
