# TenderOps AI - run the server (dev profile, http://localhost:8080)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
if (Test-Path "$Root\.env") {
  Get-Content "$Root\.env" | ForEach-Object {
    $line = $_.Trim()
    if ($line -ne "" -and -not $line.StartsWith("#") -and $line.Contains("=")) {
      $name = $line.Substring(0, $line.IndexOf("=")).Trim()
      $value = $line.Substring($line.IndexOf("=") + 1).Trim()
      [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
  }
}
& mvn -B -f "$Root\server\pom.xml" spring-boot:run "-Dspring-boot.run.profiles=dev"
exit $LASTEXITCODE
