#!/usr/bin/env bash
# TenderOps AI - run the server (dev profile, http://localhost:8080)
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
if [ -f "$ROOT/.env" ]; then set -a; . "$ROOT/.env"; set +a; fi
mvn -B -f "$ROOT/server/pom.xml" spring-boot:run "-Dspring-boot.run.profiles=dev"
