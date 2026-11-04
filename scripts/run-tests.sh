#!/usr/bin/env bash
# TenderOps AI - run server + client test suites (logs to docs/test-logs/)
set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mkdir -p "$ROOT/docs/test-logs"
stamp="$(date +%Y%m%d-%H%M%S)"
failed=0
echo "=== server: mvn clean test ==="
serverLog="$ROOT/docs/test-logs/run-tests-server-$stamp.log"
if mvn -B -f "$ROOT/server/pom.xml" clean test >"$serverLog" 2>&1; then
  echo "server tests passed"
else
  failed=1; echo "SERVER TESTS FAILED (see $serverLog)"; tail -40 "$serverLog"
fi
echo "=== client: npm ci + npm test ==="
cd "$ROOT/client"
ciLog="$ROOT/docs/test-logs/run-tests-client-ci-$stamp.log"
if npm ci --no-audit --no-fund >"$ciLog" 2>&1; then
  clientLog="$ROOT/docs/test-logs/run-tests-client-$stamp.log"
  if npm test >"$clientLog" 2>&1; then
    echo "client tests passed"
  else
    failed=1; echo "CLIENT TESTS FAILED (see $clientLog)"; tail -40 "$clientLog"
  fi
else
  failed=1; echo "npm ci FAILED (see $ciLog)"; tail -40 "$ciLog"
fi
if [ "$failed" -ne 0 ]; then echo "RUN-TESTS: FAILED"; exit 1; else echo "RUN-TESTS: PASSED"; fi
