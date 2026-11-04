#!/usr/bin/env bash
# TenderOps AI - run the client dev server (http://localhost:5173)
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/client"
if [ ! -d "$ROOT/client/node_modules" ]; then
  npm ci --no-audit --no-fund
fi
npm run dev
