#!/usr/bin/env bash
# TenderOps AI - run the golden flow in LIVE mode and print aiMode per component.
# Requires: server running (scripts/run-server), MODEL_API_KEY set in .env for LIVE.
set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
BASE="http://localhost:8080"
if [ -f "$ROOT/.env" ]; then set -a; . "$ROOT/.env"; set +a; fi
if [ -z "${MODEL_API_KEY:-}" ]; then
  echo "MODEL_API_KEY is empty: LIVE mode is not possible. Every AI component runs in FALLBACK mode."
  echo "Set MODEL_API_KEY in .env, restart the server, and re-run for a LIVE check."
  exit 2
fi
LOGIN_JSON="$(printf '{"email":"demo@tenderops.local","password":"%s"}' "${DEMO_ADMIN_PASSWORD:-}")"
TOKEN="$(curl -s -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' -d "$LOGIN_JSON" | node -e "let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{try{console.log(JSON.parse(s).accessToken||'')}catch(e){console.log('')}})")"
if [ -z "$TOKEN" ]; then echo "live-check failed: login did not return a token (is the server running?)"; exit 1; fi
RESULT="$(curl -s -X POST "$BASE/api/v1/demo/live-check" -H "Authorization: Bearer $TOKEN" --max-time 600)"
echo "LIVE CHECK RESULT"
echo "$RESULT" | node -e "let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{const r=JSON.parse(s);console.log('MUSE aiMode: '+r.museMode);console.log('SAM aiMode: '+r.samMode);console.log('decision: '+r.decision+' score: '+r.readinessScore);console.log(JSON.stringify(r,null,2))})"
