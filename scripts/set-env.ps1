<#
  Loads the environment variables the CareSync services need into the CURRENT PowerShell session.
  Run it by dot-sourcing it (note the leading ". ") in every terminal before starting a service:

      . .\scripts\set-env.ps1

  First run: generates random values and saves them to .env.local.ps1 (git-ignored, never commit it),
  so every terminal gets the SAME JWT_SECRET. Delete that file to generate new values.
  The database password is read from infra/docker-compose.yml, so it always matches the container.
#>
$root = Split-Path -Parent $PSScriptRoot
$file = Join-Path $root '.env.local.ps1'

function New-RandomHex([int]$Bytes = 32) {
    $buf = New-Object byte[] $Bytes
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($buf)
    $rng.Dispose()
    -join ($buf | ForEach-Object { $_.ToString('x2') })
}

if (-not (Test-Path $file)) {
    @(
        "`$env:JWT_SECRET = '$(New-RandomHex 32)'",
        "`$env:AUTH_MEMBER_PASSWORD = '$(New-RandomHex 12)'",
        "`$env:AUTH_ADJUDICATOR_PASSWORD = '$(New-RandomHex 12)'",
        "`$env:AUTH_ADMIN_PASSWORD = '$(New-RandomHex 12)'"
    ) | Set-Content -Path $file -Encoding ASCII
    Write-Host "Generated new local secrets in $file (git-ignored)."
}
. $file

$compose = Join-Path $root 'infra\docker-compose.yml'
$m = Select-String -Path $compose -Pattern 'POSTGRES_PASSWORD:\s*(\S+)' | Select-Object -First 1
if (-not $m) { throw "POSTGRES_PASSWORD not found in $compose" }
$env:SPRING_DATASOURCE_PASSWORD = $m.Matches[0].Groups[1].Value

Write-Host 'Environment ready: SPRING_DATASOURCE_PASSWORD, JWT_SECRET, AUTH_MEMBER_PASSWORD, AUTH_ADJUDICATOR_PASSWORD, AUTH_ADMIN_PASSWORD'
