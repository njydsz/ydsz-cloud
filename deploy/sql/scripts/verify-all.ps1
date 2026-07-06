#requires -Version 5.1
<#
.SYNOPSIS
  Run all P1/P2 verification checks against the current V1.0.0.sql
#>
param(
    [string]$SqlDir = "d:\Code\ydsz\ydsz-pmis\deploy\sql\scripts"
)

$ErrorActionPreference = "Continue"
$scripts = @(
    "check-provider-trace-index.ps1",
    "check-table-comment.ps1"
)

Write-Host "=========================================="
Write-Host "P1/P2 verification suite"
Write-Host "=========================================="
Write-Host "SQL dir: $SqlDir"
Write-Host "Generated at: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host ""

$failed = 0
foreach ($s in $scripts) {
    $path = Join-Path $SqlDir $s
    if (-not (Test-Path $path)) {
        Write-Host "[SKIP] $s (not found)"
        continue
    }
    Write-Host "----------------------------------------"
    Write-Host "[RUN ] $s"
    Write-Host "----------------------------------------"
    & powershell -NoProfile -ExecutionPolicy Bypass -File $path
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[FAIL] $s"
        $failed++
    }
    Write-Host ""
}

Write-Host "=========================================="
if ($failed -eq 0) {
    Write-Host "All checks PASSED"
} else {
    Write-Host "Some checks FAILED: $failed"
}
Write-Host "=========================================="
exit $failed
