# TenderOps AI - run the client dev server (http://localhost:5173)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location "$Root\client"
if (-not (Test-Path "$Root\client\node_modules")) {
  & npm ci --no-audit --no-fund
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
& npm run dev
exit $LASTEXITCODE
