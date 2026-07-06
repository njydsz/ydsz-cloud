#requires -Version 5.1
<#
.SYNOPSIS
  Remove residual [SKIPPED-CLEANUP] and [SKIPPED-FWD-REF] comments from V1.0.0.sql
.DESCRIPTION
  P1-6 cleanup: strips historical comments left by the prior merge generator.
  Two patterns handled:
    1. Single-line "-- [SKIPPED-CLEANUP] DROP TABLE ..."  (entire line removed)
    2. Multi-line "-- [SKIPPED-CLEANUP] DELETE FROM ..." block  (entire block removed)
  Backups the original file to V1.0.0.sql.p1-6.bak.
#>
param(
    [string]$SqlPath = "d:\Code\ydsz\ydsz-pmis\deploy\sql\V1.0.0.sql"
)

$ErrorActionPreference = "Stop"
$content = Get-Content $SqlPath -Raw -Encoding UTF8

# backup
$bak = "$SqlPath.p1-6.bak"
Copy-Item $SqlPath $bak -Force
Write-Host "Backup: $bak"

# count before
$beforeCleanup = ([regex]::Matches($content, '\[SKIPPED-CLEANUP\]')).Count
$beforeFwdRef  = ([regex]::Matches($content, '\[SKIPPED-FWD-REF\]')).Count
Write-Host "Before: SKIPPED-CLEANUP=$beforeCleanup  SKIPPED-FWD-REF=$beforeFwdRef"

# strategy 1: remove single-line "[SKIPPED-*] DROP TABLE/VIEW ..." comments
$content = [regex]::Replace(
    $content,
    "(?m)^[ \t]*--\s*\[SKIPPED-(?:CLEANUP|FWD-REF)\]\s+(?:DROP\s+(?:TABLE|VIEW)\s+IF\s+EXISTS\s+\S+;|ANALYZE\s+\S+;)\s*\r?\n",
    ""
)

# strategy 2: remove [SKIPPED-CLEANUP] DELETE FROM ... multi-line block
# Pattern: -- [SKIPPED-CLEANUP] <statement...>;
# Greedy until first standalone ");" on its own line
$content = [regex]::Replace(
    $content,
    "(?ms)^[ \t]*--\s*\[SKIPPED-CLEANUP\]\s+DELETE\s+FROM\s+\S+\s+WHERE\s+[^\n]+(?:\r?\n[ \t]*--\s*\[SKIPPED-CLEANUP\][^\n]*)*",
    ""
)

# strategy 3: remove [SKIPPED-FWD-REF] multi-line CREATE INDEX block
$content = [regex]::Replace(
    $content,
    "(?ms)^[ \t]*--\s*\[SKIPPED-FWD-REF\]\s+CREATE\s+INDEX\s+IF\s+NOT\s+EXISTS\s+\S+\s*\r?\n[ \t]*--\s*\[SKIPPED-FWD-REF\][^\n]*\r?\n(?:[ \t]*--\s*\[SKIPPED-FWD-REF\][^\n]*\r?\n)*",
    ""
)

# strategy 4: any remaining "P1-6: [SKIPPED-CLEANUP] ..." inline tags - demote to P1-6 documentation
$content = [regex]::Replace(
    $content,
    "(?m)^[ \t]*--\s*P1-6:\s+\[SKIPPED-CLEANUP\]\s+(.*)$",
    '-- P1-6: 已废弃(无需 DROP), 标记保留以记录历史:$1'
)

# count after
$afterCleanup = ([regex]::Matches($content, '\[SKIPPED-CLEANUP\]')).Count
$afterFwdRef  = ([regex]::Matches($content, '\[SKIPPED-FWD-REF\]')).Count
Write-Host "After : SKIPPED-CLEANUP=$afterCleanup  SKIPPED-FWD-REF=$afterFwdRef"
Write-Host "Removed: CLEANUP=$($beforeCleanup - $afterCleanup)  FWD-REF=$($beforeFwdRef - $afterFwdRef)"

# write back
[System.IO.File]::WriteAllText($SqlPath, $content, [System.Text.Encoding]::UTF8)
Write-Host "Updated: $SqlPath"
