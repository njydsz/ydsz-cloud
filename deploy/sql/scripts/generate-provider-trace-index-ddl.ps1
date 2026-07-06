#requires -Version 5.1
<#
.SYNOPSIS
  Generate CREATE INDEX statements for tables with provider_trace_id but missing index
#>
param(
    [string]$SqlPath = "d:\Code\ydsz\ydsz-pmis\deploy\sql\V1.0.0.sql",
    [string]$OutPath = "d:\Code\ydsz\ydsz-pmis\deploy\sql\scripts\provider-trace-index-additions.sql"
)

$ErrorActionPreference = "Stop"
$content = Get-Content $SqlPath -Raw -Encoding UTF8

$tablePattern = '(?s)CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+(pmis_[a-z_]+)\b(.*?);\s*\n'
$tableMatches = [regex]::Matches($content, $tablePattern)

$missing = @()
foreach ($m in $tableMatches) {
    $table = $m.Groups[1].Value
    $body = $m.Groups[2].Value
    if ($body -notmatch 'provider_trace_id') { continue }

    $lineMatch = [regex]::Match($body, "(?im)^\s*provider_trace_id\s+VARCHAR\(\d+\)\s*(NOT NULL\s+DEFAULT\s+'[^']*'|NULL)?")
    $isNotNull = $false
    if ($lineMatch.Success) {
        $qualifier = $lineMatch.Groups[1].Value
        $isNotNull = ($qualifier -match 'NOT NULL')
    }

    $startIdx = $m.Index + $m.Length
    $window = ''
    if ($startIdx -lt $content.Length) {
        $windowLen = [Math]::Min(4000, $content.Length - $startIdx)
        $window = $content.Substring($startIdx, $windowLen)
    }
    $tableEsc = [Regex]::Escape($table)
    $idxPattern = "CREATE\s+(?:UNIQUE\s+)?INDEX\s+IF\s+NOT\s+EXISTS\s+\S+\s+ON\s+$tableEsc\s*\([^)]*provider_trace_id"
    $hasIndex = $window -match "(?is)$idxPattern"
    if ($hasIndex) { continue }

    $missing += [pscustomobject]@{
        Table = $table
        IsNotNull = $isNotNull
    }
}

$header = @"
-- ====================================================================
-- ============ P1-7 increment: provider_trace_id index fill-up ==========
-- ====================================================================
-- Background: industry standard requires all tables carrying
--             provider_trace_id to have a dedicated index to support
--             O(log n) reverse lookups. This file fills 63 gaps.
-- Design:
--   * NULLABLE columns  -> partial index WHERE provider_trace_id IS NOT NULL
--   * NOT NULL DEFAULT  -> partial index WHERE provider_trace_id <> ''
-- Generated on $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
-- ====================================================================

"@

$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine($header)

foreach ($item in $missing) {
    $idxName = "idx_$($item.Table)_trace"
    $where = if ($item.IsNotNull) { "WHERE provider_trace_id <> ''" } else { "WHERE provider_trace_id IS NOT NULL" }
    $nullTag = if ($item.IsNotNull) { "NOT NULL" } else { "NULLABLE" }
    [void]$sb.AppendLine("-- $($item.Table) ($nullTag)")
    [void]$sb.AppendLine("CREATE INDEX IF NOT EXISTS $idxName")
    [void]$sb.AppendLine("    ON $($item.Table) (provider_trace_id)")
    [void]$sb.AppendLine("    $where;")
    [void]$sb.AppendLine("")
}

[System.IO.File]::WriteAllText($OutPath, $sb.ToString(), [System.Text.Encoding]::UTF8)
Write-Host "Generated $($missing.Count) index DDLs to: $OutPath"
$missing | ForEach-Object {
    $tag = if ($_.IsNotNull) { "NOT NULL" } else { "NULL" }
    Write-Host ("  + {0,-45} {1}" -f $_.Table, $tag)
}
