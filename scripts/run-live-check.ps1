# TenderOps AI - run the golden flow in LIVE mode and print aiMode per component.
# Requires: server running (scripts/run-server), MODEL_API_KEY set in .env for LIVE.
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
$base = "http://localhost:8080"
$modelKey = ""
$demoPwd = ""
if (Test-Path "$Root\.env") {
  Get-Content "$Root\.env" | ForEach-Object {
    $line = $_.Trim()
    if ($line.StartsWith("MODEL_API_KEY=")) { $modelKey = $line.Substring(14).Trim() }
    if ($line.StartsWith("DEMO_ADMIN_PASSWORD=")) { $demoPwd = $line.Substring(20).Trim() }
  }
}
if ([string]::IsNullOrEmpty($modelKey)) {
  Write-Host "MODEL_API_KEY is empty: LIVE mode is not possible. Every AI component runs in FALLBACK mode."
  Write-Host "Set MODEL_API_KEY in .env, restart the server, and re-run for a LIVE check."
  exit 2
}
try {
  $loginBody = @{ email = "demo@tenderops.local"; password = $demoPwd } | ConvertTo-Json
  $login = Invoke-RestMethod -Method Post -Uri "$base/api/v1/auth/login" -ContentType "application/json" -Body $loginBody -TimeoutSec 30
  $headers = @{ Authorization = ("Bearer " + $login.accessToken) }
  $check = Invoke-RestMethod -Method Post -Uri "$base/api/v1/demo/live-check" -Headers $headers -TimeoutSec 600
  Write-Host "LIVE CHECK RESULT"
  Write-Host ("MUSE aiMode: " + $check.museMode)
  Write-Host ("SAM aiMode: " + $check.samMode)
  Write-Host ("decision: " + $check.decision + " score: " + $check.readinessScore)
  $check | ConvertTo-Json -Depth 6
}
catch {
  Write-Host ("live-check failed: " + $_.Exception.Message)
  exit 1
}
