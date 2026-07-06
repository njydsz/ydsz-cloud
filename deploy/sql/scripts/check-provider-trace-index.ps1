#requires -Version 5.1
<#
.SYNOPSIS
  Analyze provider_trace_id index coverage in V1.0.0.sql (v2 - fixed)
#>
param(
    [string]$SqlPath = "d:\Code\ydsz\ydsz-pmis\deploy\sql\V1.0.0.sql"
)

$ErrorActionPreference = "Stop"
$content = Get-Content $SqlPath -Raw -Encoding UTF8

# Find all CREATE TABLE blocks (multiline, lazy match up to closing );  )
$tablePattern = '(?s)CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+(pmis_[a-z_]+)\b.*?;\s*\n'
$tableMatches = [regex]::Matches($content, $tablePattern)

$results = @()
foreach ($m in $tableMatches) {
    $table = $m.Groups[1].Value
    $body = $m.Value
    if ($body -notmatch 'provider_trace_id') { continue }

    # Search the ENTIRE file content (not just the next window) for any
    # CREATE INDEX targeting this table on provider_trace_id.
    $tableEsc = [Regex]::Escape($table)
    $idxPattern = "CREATE\s+(?:UNIQUE\s+)?INDEX\s+IF\s+NOT\s+EXISTS\s+\S+\s+ON\s+$tableEsc\s*\([^)]*provider_trace_id"
    $hasIndex = $content -match "(?is)$idxPattern"

    $results += [pscustomobject]@{
        Table = $table
        HasIndex = $hasIndex
    }
}

$withIndex = $results | Where-Object { $_.HasIndex }
$withoutIndex = $results | Where-Object { -not $_.HasIndex }

Write-Host "=== Summary ==="
Write-Host ("Tables with provider_trace_id field: {0}" -f $results.Count)
Write-Host ("With index     : {0}" -f $withIndex.Count)
Write-Host ("Without index  : {0}" -f $withoutIndex.Count)
Write-Host ""
Write-Host "=== MISSING index on provider_trace_id ==="
$withoutIndex | ForEach-Object { Write-Host ("  - {0}" -f $_.Table) }
Write-Host ""
Write-Host "=== OK ==="
$withIndex | ForEach-Object { Write-Host ("  + {0}" -f $_.Table) }
