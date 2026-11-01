<#
  Demo client for CareSync: log in through the gateway, then submit claims for specs/03 examples E1, E3, E5, E7.
  Needs the gateway (8080), auth (8081) and claims (8082) services running.
  Reads the member password from AUTH_MEMBER_PASSWORD; set GATEWAY_SECURITY_ENABLED=false on the gateway
  and pass -NoAuth to skip the login step.
#>
param(
    [string]$Gateway = 'http://127.0.0.1:8080',
    [switch]$NoAuth
)
$ErrorActionPreference = 'Stop'

function Invoke-Api([string]$Method, [string]$Path, [string]$Body, [string]$Token) {
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    try {
        $r = Invoke-WebRequest -UseBasicParsing -Method $Method -Uri "$Gateway$Path" -Headers $headers `
            -ContentType 'application/json' -Body $Body -TimeoutSec 20
        return [pscustomobject]@{ Status = [int]$r.StatusCode; Body = $r.Content }
    } catch {
        $resp = $_.Exception.Response
        if (-not $resp) { throw "Cannot reach $Gateway - are the services running? ($($_.Exception.Message))" }
        $text = (New-Object IO.StreamReader($resp.GetResponseStream())).ReadToEnd()
        return [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = $text }
    }
}

$token = $null
if (-not $NoAuth) {
    if (-not $env:AUTH_MEMBER_PASSWORD) { throw 'Set AUTH_MEMBER_PASSWORD (same value the auth service was started with).' }
    $login = Invoke-Api POST '/api/v1/auth/token' (@{ username = 'member1'; password = $env:AUTH_MEMBER_PASSWORD } | ConvertTo-Json)
    if ($login.Status -ne 200) { throw "Login failed with HTTP $($login.Status)" }
    $token = ($login.Body | ConvertFrom-Json).accessToken
    Write-Host 'Logged in as member1 (token not printed).'
}

function Submit([string]$Label, [string]$Member, [int]$DaysAgo, [string]$Amount, [string]$Category) {
    $body = @{
        memberId = $Member; providerId = 'PRV-44102'
        serviceDate = (Get-Date).AddDays(-$DaysAgo).ToString('yyyy-MM-dd')
        billedAmount = $Amount; benefitCategory = $Category
    } | ConvertTo-Json
    $r = Invoke-Api POST '/api/v1/claims' $body $token
    Write-Host ("`n{0}  ->  HTTP {1}" -f $Label, $r.Status)
    Write-Host $r.Body
    if ($r.Status -eq 201) {
        $ref = ($r.Body | ConvertFrom-Json).claimReference
        $g = Invoke-Api GET "/api/v1/claims/$ref" $null $token
        Write-Host ("GET {0}  ->  HTTP {1}" -f $ref, $g.Status)
    }
}

# Each example uses its own member so the 24-hour duplicate rule (R6) does not interfere between runs.
$run = Get-Random -Minimum 10000 -Maximum 99999
Submit 'E1 in-network primary care 150.00 (expect 120.00 / 30.00)' "MEM-$run" 6   '150.00' 'IN_NETWORK_PRIMARY_CARE'
Submit 'E3 preventive 200.00 (expect 200.00 / 0.00)'                "MEM-$($run+1)" 6 '200.00' 'PREVENTIVE_SCREENING'
Submit 'E5 below minimum 4.99 (expect 400 Validation Failed)'      "MEM-$($run+2)" 1 '4.99'   'GENERAL_MEDICAL'
Submit 'E7 timely filing exceeded (expect DENIED)'                 "MEM-$($run+3)" 400 '100.00' 'IN_NETWORK_PRIMARY_CARE'

Write-Host "`nNext: open http://localhost:8090 (Kafka UI) -> Topics -> claim-lifecycle-events -> Messages,"
Write-Host 'and look for [AUDIT NOTIFICATION] lines in the care-notification-service console.'