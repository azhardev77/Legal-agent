# TenderOps AI - run server + client test suites (logs to docs/test-logs/)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
New-Item -ItemType Directory -Force -Path "$Root\docs\test-logs" | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$failed = $false
Write-Host "=== server: mvn clean test ==="
$serverLog = "$Root\docs\test-logs\run-tests-server-$stamp.log"
& mvn -B -f "$Root\server\pom.xml" clean test > $serverLog 2>&1
if ($LASTEXITCODE -ne 0) { $failed = $true; Write-Host "SERVER TESTS FAILED (see $serverLog)"; Get-Content -Tail 40 $serverLog } else { Write-Host "server tests passed" }
Write-Host "=== client: npm ci + npm test ==="
Set-Location "$Root\client"
$ciLog = "$Root\docs\test-logs\run-tests-client-ci-$stamp.log"
& npm ci --no-audit --no-fund > $ciLog 2>&1
if ($LASTEXITCODE -ne 0) { $failed = $true; Write-Host "npm ci FAILED (see $ciLog)"; Get-Content -Tail 40 $ciLog }
else {
  $clientLog = "$Root\docs\test-logs\run-tests-client-$stamp.log"
  & npm test > $clientLog 2>&1
  if ($LASTEXITCODE -ne 0) { $failed = $true; Write-Host "CLIENT TESTS FAILED (see $clientLog)"; Get-Content -Tail 40 $clientLog } else { Write-Host "client tests passed" }
}
if ($failed) { Write-Host "RUN-TESTS: FAILED"; exit 1 } else { Write-Host "RUN-TESTS: PASSED" }
