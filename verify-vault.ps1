<#
.SYNOPSIS
    Zero-Config Vault - Automated End-to-End Verification Test Suite
.DESCRIPTION
    Executes a comprehensive battery of operational integration tests against
    a running Zero-Config Vault instance (REST APIs, Security, Crypto, GitOps, Diffing, Stats).
.PARAMETER BaseUrl
    Base URL of the Vault backend (default: http://localhost:8080)
.PARAMETER ApiKey
    Standard API Key for read/write operations (default: sk-dev-key-001)
.PARAMETER AdminKey
    Admin API Key for privileged operations (default: sk-admin-master-key)
#>

[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$ApiKey = "sk-dev-key-001",
    [string]$AdminKey = "sk-admin-master-key"
)

$ErrorActionPreference = "Continue"

$totalTests = 0
$passedTests = 0
$failedTests = 0

function Write-TestHeader($title) {
    Write-Host "`n=================================================================" -ForegroundColor Cyan
    Write-Host "  $title" -ForegroundColor Cyan
    Write-Host "=================================================================" -ForegroundColor Cyan
}

function Report-Result($testName, $success, $details = "") {
    $script:totalTests++
    if ($success) {
        $script:passedTests++
        Write-Host "  [PASS] " -ForegroundColor Green -NoNewline
        Write-Host "$testName" -ForegroundColor White
        if ($details) {
            Write-Host "         $details" -ForegroundColor DarkGray
        }
    } else {
        $script:failedTests++
        Write-Host "  [FAIL] " -ForegroundColor Red -NoNewline
        Write-Host "$testName" -ForegroundColor Yellow
        if ($details) {
            Write-Host "         ERROR: $details" -ForegroundColor Red
        }
    }
}

Write-TestHeader "Zero-Config Vault: Automated Verification Suite"
Write-Host "Target Server : $BaseUrl" -ForegroundColor Gray
Write-Host "API Key       : $($ApiKey.Substring(0, [Math]::Min(10, $ApiKey.Length)))..." -ForegroundColor Gray
Write-Host "Started At    : $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Gray

# ------------------------------------------------------------------------------
# Test 1: API Docs & Swagger OpenAPI Health
# ------------------------------------------------------------------------------
try {
    $response = Invoke-RestMethod -Uri "$BaseUrl/v3/api-docs" -Method Get -TimeoutSec 10
    $hasOpenApi = ($response.openapi -ne $null) -or ($response.swagger -ne $null)
    Report-Result "Test 1: OpenAPI Documentation Endpoint (/v3/api-docs)" $hasOpenApi "API Docs healthy, version: $($response.info.version)"
} catch {
    Report-Result "Test 1: OpenAPI Documentation Endpoint (/v3/api-docs)" $false $_.Exception.Message
}

# ------------------------------------------------------------------------------
# Test 2: Security Filter - Unauthorized Access Rejection (HTTP 401)
# ------------------------------------------------------------------------------
try {
    $res = Invoke-RestMethod -Uri "$BaseUrl/api/v1/vault/stats" -Method Get -TimeoutSec 5
    Report-Result "Test 2: Security Filter Rejects Missing API Key (HTTP 401)" $false "Expected 401 but request succeeded"
} catch {
    $statusCode = 0
    if ($_.Exception.Response) {
        $statusCode = [int]$_.Exception.Response.StatusCode
    }
    $is401 = ($statusCode -eq 401)
    Report-Result "Test 2: Security Filter Rejects Missing API Key (HTTP 401)" $is401 "Captured HTTP $statusCode Unauthorized"
}

# ------------------------------------------------------------------------------
# Test 3: Create Initial Configuration with 'secure_settings' (v1)
# ------------------------------------------------------------------------------
$appName = "e2e-test-app-$([System.Guid]::NewGuid().ToString().Substring(0, 8))"
$envName = "production"
$secretValue = "super-secret-vault-token-xyz987"

$initialPayload = @{
    applicationName = $appName
    environment = $envName
    payload = @{
        server = @{
            port = 8080
            host = "internal.prod.node"
        }
        database = @{
            host = "postgres-primary"
            port = 5432
            name = "e2e_db"
        }
        secure_settings = @{
            jwt_signing_key = $secretValue
            stripe_api_key = "sk_live_e2e_secret_123"
        }
    }
    tags = @{
        e2e = "true"
        tier = "integration"
    }
} | ConvertTo-Json -Depth 10

$v1Hash = $null
try {
    $headers = @{
        "X-API-Key" = $ApiKey
        "Content-Type" = "application/json"
    }
    $createRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/vault/config" -Method Post -Headers $headers -Body $initialPayload
    $v1Hash = $createRes.documentHash
    $isV1Valid = ($createRes.version -eq 1) -and ($v1Hash.Length -eq 128)
    Report-Result "Test 3: Create Revision v1 with SHA-512 Hash" $isV1Valid "v1 created, Hash: $($v1Hash.Substring(0, 16))... (Length: $($v1Hash.Length))"
} catch {
    Report-Result "Test 3: Create Revision v1 with SHA-512 Hash" $false $_.Exception.Message
}

# ------------------------------------------------------------------------------
# Test 4: Retrieve Configuration & Verify Transparent Decryption
# ------------------------------------------------------------------------------
try {
    $headers = @{ "X-API-Key" = $ApiKey }
    $getRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/vault/config/$appName/$envName" -Method Get -Headers $headers
    $decryptedMatch = ($getRes.payload.secure_settings.jwt_signing_key -eq $secretValue)
    Report-Result "Test 4: Retrieve Active Configuration & Transparent Decryption" $decryptedMatch "secure_settings decrypted successfully"
} catch {
    Report-Result "Test 4: Retrieve Active Configuration & Transparent Decryption" $false $_.Exception.Message
}

# ------------------------------------------------------------------------------
# Test 5: Dot-Notation Path Traversal
# ------------------------------------------------------------------------------
try {
    $headers = @{ "X-API-Key" = $ApiKey }
    $searchRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/vault/config/$appName/$envName/search?path=database.host" -Method Get -Headers $headers
    $extracted = $searchRes.value
    $pathMatch = ($extracted -eq "postgres-primary")
    Report-Result "Test 5: Dot-Notation Path Traversal ('database.host')" $pathMatch "Extracted value: $extracted"
} catch {
    Report-Result "Test 5: Dot-Notation Path Traversal ('database.host')" $false $_.Exception.Message
}

# ------------------------------------------------------------------------------
# Test 6: Multi-Format Config Export (YAML & ENV)
# ------------------------------------------------------------------------------
try {
    $headers = @{ "X-API-Key" = $ApiKey }
    $yamlRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/vault/config/$appName/$envName/export?format=yaml" -Method Get -Headers $headers
    $envRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/vault/config/$appName/$envName/export?format=env" -Method Get -Headers $headers
    $hasYaml = ($yamlRes -match "database:") -or ($yamlRes -match "server:")
    $hasEnv = ($envRes -match "server.port=") -or ($envRes -match "database.host=") -or ($envRes -match "SERVER_PORT=")
    Report-Result "Test 6: Multi-Format Exporter (YAML & .env)" ($hasYaml -and $hasEnv) "Exported YAML & ENV representations verified"
} catch {
    Report-Result "Test 6: Multi-Format Exporter (YAML & .env)" $false $_.Exception.Message
}

# ------------------------------------------------------------------------------
# Test 7: Temporal Version Increment (v2)
# ------------------------------------------------------------------------------
$updatePayload = @{
    applicationName = $appName
    environment = $envName
    payload = @{
        server = @{
            port = 9090
            host = "internal.prod.node"
        }
        database = @{
            host = "postgres-primary"
            port = 5432
            name = "e2e_db"
        }
        feature_flags = @{
            enable_redis_cache = $true
            rate_limit_per_sec = 500
        }
        secure_settings = @{
            jwt_signing_key = $secretValue
            stripe_api_key = "sk_live_e2e_secret_MODIFIED_456"
        }
    }
} | ConvertTo-Json -Depth 10

$v2Hash = $null
try {
    $headers = @{
        "X-API-Key" = $ApiKey
        "Content-Type" = "application/json"
    }
    $v2Res = Invoke-RestMethod -Uri "$BaseUrl/api/v1/vault/config" -Method Post -Headers $headers -Body $updatePayload
    $v2Hash = $v2Res.documentHash
    $isV2Valid = ($v2Res.version -eq 2) -and ($v2Hash -ne $v1Hash)
    Report-Result "Test 7: GitOps Temporal Immutability Increment (v2)" $isV2Valid "v2 created, Version: $($v2Res.version), Hash distinct from v1"
} catch {
    Report-Result "Test 7: GitOps Temporal Immutability Increment (v2)" $false $_.Exception.Message
}

# ------------------------------------------------------------------------------
# Test 8: Structural Delta & Diff Calculation (v1 vs v2)
# ------------------------------------------------------------------------------
try {
    $headers = @{ "X-API-Key" = $ApiKey }
    $diffRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/vault/config/$appName/$envName/diff?v1=1&v2=2" -Method Get -Headers $headers
    $hasDiff = ($diffRes.added -ne $null) -or ($diffRes.modified -ne $null)
    Report-Result "Test 8: Structural Diff Engine (/diff?v1=1&v2=2)" $hasDiff "Detected modifications and additions between revisions"
} catch {
    Report-Result "Test 8: Structural Diff Engine (/diff?v1=1&v2=2)" $false $_.Exception.Message
}

# ------------------------------------------------------------------------------
# Test 9: Atomic Rollback Engine (v2 -> Rollback to v1 creates v3)
# ------------------------------------------------------------------------------
try {
    $headers = @{ "X-API-Key" = $ApiKey }
    $rollbackRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/vault/config/$appName/$envName/rollback/1" -Method Post -Headers $headers
    $isV3 = ($rollbackRes.version -eq 3)
    $portRestored = ($rollbackRes.payload.server.port -eq 8080)
    Report-Result "Test 9: Atomic Rollback Engine (Target v1 -> Creates v3)" ($isV3 -and $portRestored) "Version 3 spawned with server.port restored to 8080"
} catch {
    Report-Result "Test 9: Atomic Rollback Engine (Target v1 -> Creates v3)" $false $_.Exception.Message
}

# ------------------------------------------------------------------------------
# Test 10: Aggregation Pipeline Telemetry (/stats)
# ------------------------------------------------------------------------------
try {
    $headers = @{ "X-API-Key" = $ApiKey }
    $statsRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/vault/stats" -Method Get -Headers $headers
    $statsValid = ($statsRes.totalApplications -gt 0) -and ($statsRes.totalVersions -gt 0)
    Report-Result "Test 10: Aggregation Pipeline System Telemetry (/stats)" $statsValid "Applications: $($statsRes.totalApplications), Versions: $($statsRes.totalVersions), AuditLogs: $($statsRes.auditLogCount)"
} catch {
    Report-Result "Test 10: Aggregation Pipeline System Telemetry (/stats)" $false $_.Exception.Message
}

# ------------------------------------------------------------------------------
# Summary
# ------------------------------------------------------------------------------
Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host "  Verification Summary" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  Total Tests Executed : $totalTests" -ForegroundColor White
Write-Host "  Passed Tests         : $passedTests" -ForegroundColor Green
if ($failedTests -gt 0) {
    Write-Host "  Failed Tests         : $failedTests" -ForegroundColor Red
    exit 1
} else {
    Write-Host "  All verification checks passed with 100% success!" -ForegroundColor Green
    exit 0
}
