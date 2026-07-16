# ============================================================
# PMIS SQL Split Script: V1.0.0_project.sql -> sales / finance / project + literule migration
# Based on physical Mapper location after DDD refactor
# ============================================================

$sourceFile = "d:\Code\ydsz\ydsz\deploy\sql\modules\V1.0.0_project.sql"

# First, restore from V1.0.0.sql if the file was already overwritten
# We need the original content - let's read V1.0.0.sql and extract project tables
$masterFile = "d:\Code\ydsz\ydsz\deploy\sql\V1.0.0.sql"
$lines = Get-Content $sourceFile
$totalLines = $lines.Count
Write-Host "Source file: $totalLines lines"

# --- Table -> Module mapping ---
$salesTables = @(
    'pmis_project_opportunity',
    'pmis_project_opportunity_follow',
    'pmis_project_contract',
    'pmis_project_contract_supplement',
    'pmis_project_contract_change',
    'pmis_project_contract_template'
)

$financeTables = @(
    'pmis_project_invoice',
    'pmis_project_payment',
    'pmis_project_customer_credit',
    'pmis_project_expense',
    'pmis_project_revenue',
    'pmis_project_profit_snapshot',
    'pmis_project_profit_simulation',
    'pmis_project_reconcile_daily'
)

$literuleTables = @(
    'pmis_rule_execution_trace',
    'pmis_rule_decision_table',
    'pmis_rule_canary_bucket',
    'pmis_rule_scorecard',
    'pmis_rule_decision_tree',
    'pmis_rule_script',
    'pmis_rule_ab_policy',
    'pmis_rule_ab_rollback'
)

# --- Find all CREATE TABLE positions ---
# Each table block: starts at '-- ====...' separator before CREATE TABLE, ends at next '-- ====...' or section header
$tableBlocks = @()
$createTableLines = @()

for ($i = 0; $i -lt $totalLines; $i++) {
    if ($lines[$i] -match '^CREATE TABLE IF NOT EXISTS (\w+)') {
        $createTableLines += [PSCustomObject]@{ LineNum = $i; Table = $matches[1] }
    }
}

Write-Host "Found $($createTableLines.Count) CREATE TABLE statements"

# For each CREATE TABLE, find block start (search backwards for separator)
foreach ($ct in $createTableLines) {
    $blockStart = $ct.LineNum
    for ($j = $ct.LineNum - 1; $j -ge 0; $j--) {
        $line = $lines[$j]
        if ($line -match '^-- ={5,}' -and $line.Length -gt 10) {
            $blockStart = $j
            break
        }
    }
    $ct | Add-Member -NotePropertyName BlockStart -NotePropertyValue $blockStart
}

# Calculate block end for each table
for ($i = 0; $i -lt $createTableLines.Count; $i++) {
    if ($i -lt $createTableLines.Count - 1) {
        $nextStart = $createTableLines[$i + 1].BlockStart
        $end = $nextStart - 1
        # Trim trailing empty lines
        while ($end -gt $createTableLines[$i].LineNum -and [string]::IsNullOrWhiteSpace($lines[$end])) {
            $end--
        }
    } else {
        # Last table - go to end of file, but stop at section headers
        $end = $totalLines - 1
    }
    $createTableLines[$i] | Add-Member -NotePropertyName BlockEnd -NotePropertyValue $end
}

Write-Host "`nTable blocks:"
foreach ($ct in $createTableLines) {
    Write-Host "  $($ct.Table): lines $($ct.BlockStart)-$($ct.BlockEnd) ($($ct.BlockEnd - $ct.BlockStart + 1) lines)"
}

# --- Classify and collect lines ---
$salesContent = [System.Collections.ArrayList]@()
$financeContent = [System.Collections.ArrayList]@()
$projectContent = [System.Collections.ArrayList]@()
$literuleContent = [System.Collections.ArrayList]@()

foreach ($ct in $createTableLines) {
    $start = $ct.BlockStart
    $end = $ct.BlockEnd
    $blockLines = $lines[$start..$end]

    if ($salesTables -contains $ct.Table) {
        $salesContent.AddRange($blockLines)
        $salesContent.Add("")
    } elseif ($financeTables -contains $ct.Table) {
        $financeContent.AddRange($blockLines)
        $financeContent.Add("")
    } elseif ($literuleTables -contains $ct.Table) {
        $literuleContent.AddRange($blockLines)
        $literuleContent.Add("")
    } else {
        $projectContent.AddRange($blockLines)
        $projectContent.Add("")
    }
}

# --- Write Sales SQL ---
$salesHeader = @"
-- ============================================================
-- PMIS sales module SQL
-- 商务销售服务 (ydsz-sales, port 9010)
-- ============================================================
-- 本脚本 DDL 对应后端 sales 服务的 Mapper / DO,
--   物理 Mapper 实际所在模块即表归属。跨服务引用禁止直连,统一走
--   Feign Client (SalesDataClient / FinanceDataClient)。
--
-- 表归属依据: ydsz-sales/src/main/java/.../infra/mapper/
-- 表数量: $($salesTables.Count) 张
-- --------------------------------------------------------------------

"@
$salesFile = "d:\Code\ydsz\ydsz\deploy\sql\modules\V1.0.0_sales.sql"
$salesHeader | Out-File -FilePath $salesFile -Encoding UTF8
$salesContent -join "`n" | Out-File -FilePath $salesFile -Encoding UTF8 -Append
Write-Host "`nWrote $salesFile ($($salesContent.Count) lines)"

# --- Write Finance SQL ---
$financeHeader = @"
-- ============================================================
-- PMIS finance module SQL
-- 财务会计服务 (ydsz-finance, port 9011)
-- ============================================================
-- 本脚本 DDL 对应后端 finance 服务的 Mapper / DO,
--   物理 Mapper 实际所在模块即表归属。跨服务引用禁止直连,统一走
--   Feign Client (FinanceDataClient / SalesDataClient)。
--
-- 表归属依据: ydsz-finance/src/main/java/.../infra/mapper/
-- 表数量: $($financeTables.Count) 张
-- --------------------------------------------------------------------

"@
$financeFile = "d:\Code\ydsz\ydsz\deploy\sql\modules\V1.0.0_finance.sql"
$financeHeader | Out-File -FilePath $financeFile -Encoding UTF8
$financeContent -join "`n" | Out-File -FilePath $financeFile -Encoding UTF8 -Append
Write-Host "Wrote $financeFile ($($financeContent.Count) lines)"

# --- Write Project SQL (remaining) ---
$projectHeader = @"
-- ============================================================
-- PMIS project module SQL
-- 项目执行服务 (ydsz-project, port 9003)
-- ============================================================
-- 本脚本 DDL 对应后端 project 服务的 Mapper / DO,
--   物理 Mapper 实际所在模块即表归属。跨服务引用禁止直连,统一走
--   Feign Client (FinanceDataClient / SalesDataClient)。
--
-- 表归属依据: ydsz-project/src/main/java/.../infra/mapper/
-- 表数量: $($projectTables.Count) 张 (原 42 张表拆分后剩余)
-- --------------------------------------------------------------------
-- [P4 架构优化提示] 跨模块冗余字段：pmis_cost_allocation.employee_name、
--   pmis_cost_purchase.applicant_name / approver_name 等 *_name 字段为历史
--   冗余存储，原则上应通过 NameAssembler 实时解析，禁止在写入时同步冗余。
--   现有数据保留（兼容历史查询），新写入由 Java 端 NameAssembler 自动注入。
-- --------------------------------------------------------------------

"@
$projectFile = "d:\Code\ydsz\ydsz\deploy\sql\modules\V1.0.0_project.sql"
$projectHeader | Out-File -FilePath $projectFile -Encoding UTF8
$projectContent -join "`n" | Out-File -FilePath $projectFile -Encoding UTF8 -Append
Write-Host "Wrote $projectFile ($($projectContent.Count) lines)"

# --- Append literule tables to V1.0.0_literule.sql ---
$literuleFile = "d:\Code\ydsz\ydsz\deploy\sql\modules\V1.0.0_literule.sql"
$literuleAppend = @"

-- ============================================================
-- 以下表从 V1.0.0_project.sql 迁移 (2026-07-12 DDD 拆分)
-- 原 Mapper 在 project 模块, 现已迁移至 literule 模块
-- 表归属依据: ydsz-literule/src/main/java/.../mapper/
-- ============================================================

"@
$literuleAppend | Out-File -FilePath $literuleFile -Encoding UTF8 -Append
$literuleContent -join "`n" | Out-File -FilePath $literuleFile -Encoding UTF8 -Append
Write-Host "Appended to $literuleFile ($($literuleContent.Count) lines)"

Write-Host "`n=== Split Summary ==="
Write-Host "Sales:   $($salesTables.Count) tables -> V1.0.0_sales.sql"
Write-Host "Finance: $($financeTables.Count) tables -> V1.0.0_finance.sql"
Write-Host "Project: $($projectTables.Count) tables -> V1.0.0_project.sql"
Write-Host "Literule: $($literuleTables.Count) tables appended -> V1.0.0_literule.sql"
Write-Host "Total:   $($salesTables.Count + $financeTables.Count + $projectTables.Count + $literuleTables.Count) tables"
