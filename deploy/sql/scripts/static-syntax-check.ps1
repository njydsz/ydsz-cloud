#requires -Version 5.1
<#
.SYNOPSIS
  Static syntax sanity check for V1.0.0.sql
#>
param(
    [string]$SqlPath = "d:\Code\ydsz\ydsz-pmis\deploy\sql\V1.0.0.sql"
)

$ErrorActionPreference = "Stop"
$content = Get-Content $SqlPath -Raw -Encoding UTF8

$issues = @()

# 1) Section header presence
$requiredSections = @(
    '\[060\]', '\[061\]', '\[062\]', '\[063\]', '\[064\]', '\[065\]', '\[066\]'
)
foreach ($s in $requiredSections) {
    if ($content -notmatch $s) {
        $issues += "Missing section header: $s"
    }
}

# 2) CREATE OR REPLACE VIEW WITH clause (cross-line, security_invoker)
$viewWithPattern = '(?ms)CREATE\s+OR\s+REPLACE\s+VIEW\s+(\w+)[\s\S]{0,80}?WITH\s*\(\s*security_invoker'
$viewWith = [regex]::Matches($content, $viewWithPattern)
$viewAll = [regex]::Matches($content, 'CREATE\s+OR\s+REPLACE\s+VIEW\s+(\w+)')
$viewWithoutWith = $viewAll | ForEach-Object { $_.Groups[1].Value } | Where-Object {
    $name = $_
    -not ($viewWith | Where-Object { $_.Groups[1].Value -eq $name })
}
Write-Host "Views total: $($viewAll.Count)  with security_invoker: $($viewWith.Count)"
foreach ($v in $viewWithoutWith) { $issues += "View $v missing WITH (security_invoker=...)" }

# 3) CREATE INDEX balance
$createIdx = ([regex]::Matches($content, 'CREATE\s+(?:UNIQUE\s+)?INDEX\s+IF\s+NOT\s+EXISTS', 'IgnoreCase')).Count
$createIdxSemi = ([regex]::Matches($content, 'CREATE\s+(?:UNIQUE\s+)?INDEX\s+IF\s+NOT\s+EXISTS[\s\S]+?;\s*\n', 'IgnoreCase')).Count
Write-Host "CREATE INDEX: $createIdx  (with terminal ';' and newline: $createIdxSemi)"

# 4) CREATE TABLE balance
$createTbl = ([regex]::Matches($content, 'CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+pmis_', 'IgnoreCase')).Count
Write-Host "CREATE TABLE pmis_*: $createTbl"

# 5) CREATE OR REPLACE VIEW count
$createView = ([regex]::Matches($content, 'CREATE\s+OR\s+REPLACE\s+VIEW\s+pmis_', 'IgnoreCase')).Count
Write-Host "CREATE OR REPLACE VIEW pmis_*: $createView"

# 6) BEGIN/COMMIT
$beginCnt  = ([regex]::Matches($content, '(?im)^\s*BEGIN\s*;')).Count
$commitCnt = ([regex]::Matches($content, '(?im)^\s*COMMIT\s*;')).Count
Write-Host "BEGIN: $beginCnt  COMMIT: $commitCnt"

# 7) DDL stat has semicolon at end (sample last 10000 chars)
$tail = $content.Substring([Math]::Max(0, $content.Length - 10000))
$tailSemi = ([regex]::Matches($tail, ';\s*\n')).Count
$tailLines = ($tail -split "`n").Count
Write-Host "Tail 10000 chars: lines=$tailLines semicolons=$tailSemi"

# 8) Report
Write-Host ""
Write-Host "=== Result ==="
if ($issues.Count -eq 0) {
    Write-Host "No structural issues found"
} else {
    foreach ($i in $issues) { Write-Host "  - $i" }
}
