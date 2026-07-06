#requires -Version 5.1
<#
.SYNOPSIS
  Check COMMENT ON TABLE coverage in V1.0.0.sql
#>
param(
    [string]$SqlPath = "d:\Code\ydsz\ydsz-pmis\deploy\sql\V1.0.0.sql"
)

$ErrorActionPreference = "Stop"
$content = Get-Content $SqlPath -Raw -Encoding UTF8

# Find all CREATE TABLE IF NOT EXISTS
$tablePattern = '(?ms)CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+(pmis_[a-z_]+)\b'
$tableMatches = [regex]::Matches($content, $tablePattern)

# Find all COMMENT ON TABLE statements
$commentPattern = '(?m)^\s*COMMENT\s+ON\s+TABLE\s+(pmis_[a-z_]+)\s+IS'
$commentMatches = [regex]::Matches($content, $commentPattern)

$tables = @{}
foreach ($m in $tableMatches) {
    $tables[$m.Groups[1].Value] = $false
}
foreach ($m in $commentMatches) {
    if ($tables.ContainsKey($m.Groups[1].Value)) {
        $tables[$m.Groups[1].Value] = $true
    }
}

$total = $tables.Count
$covered = ($tables.Values | Where-Object { $_ }).Count
$missing = $tables.Keys | Where-Object { -not $tables[$_] }

Write-Host "=== Summary ==="
Write-Host ("Tables total   : {0}" -f $total)
Write-Host ("With COMMENT  : {0}" -f $covered)
Write-Host ("Without COMMENT: {0}" -f ($total - $covered))
Write-Host ("Coverage: {0:N1}%%" -f (100.0 * $covered / $total))
Write-Host ""
Write-Host "=== Missing COMMENT ON TABLE ==="
$missing | ForEach-Object { Write-Host "  - $_" }
