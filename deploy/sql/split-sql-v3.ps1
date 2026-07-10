# SQL Split Script V3 - split by statement-level parsing
$ErrorActionPreference = "Stop"

$sqlFile = "d:\Code\ydsz\ydsz-pmis\deploy\sql\V1.0.0.sql"
$outDir = "d:\Code\ydsz\ydsz-pmis\deploy\sql\modules"
if (!(Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

$lines = Get-Content $sqlFile -Encoding UTF8
$totalLines = $lines.Count
Write-Host "Total lines: $totalLines"

# Module prefix rules (ordered by priority)
$moduleRules = [ordered]@{
    'literule'  = @('pmis_rule_')
    'agent'     = @('pmis_agent_')
    'workflow'  = @('pmis_flow_')
    'cronjob'   = @('pmis_job_', 'pmis_tenant_quota')
    'message'   = @('pmis_msg_')
    'project'   = @(
        'pmis_project_', 'pmis_execution_', 'pmis_cost_', 'pmis_profit_',
        'pmis_finance_', 'pmis_rate_internal', 'pmis_resource_assignment',
        'pmis_bench_record', 'pmis_warranty', 'pmis_ops_ticket',
        'pmis_satisfaction', 'pmis_alert_dispatch', 'pmis_reconcile_daily',
        'pmis_billable_utilization_snapshot'
    )
    'userinfo'  = @(
        'pmis_role', 'pmis_permission', 'pmis_user_role', 'pmis_role_permission',
        'pmis_department', 'pmis_position', 'pmis_job_level',
        'pmis_employee', 'pmis_part_time_rate', 'pmis_outsource_rate',
        'pmis_user_account', 'pmis_user_2fa', 'pmis_user_session',
        'pmis_login_audit', 'pmis_data_export_audit', 'pmis_sensitive_operation',
        'pmis_attendance', 'pmis_overtime', 'pmis_leave'
    )
    'system'    = @(
        'pmis_config', 'pmis_operation_log', 'pmis_operation_log_default',
        'pmis_file', 'pmis_export_record', 'pmis_report_subscription',
        'pmis_meta_schema_version'
    )
    'common'    = @(
        'pmis_dict_type', 'pmis_dict_item', 'pmis_dict_version', 'undo_log'
    )
}

# Section fallback mapping
$sectionModuleMap = @{
    '004'='workflow';'023'='workflow';'024'='common';'025'='workflow'
    '026'='workflow';'027'='common';'028'='workflow';'029'='workflow'
    '030'='workflow';'033'='workflow';'034'='workflow';'037'='workflow'
    '038'='workflow';'046'='workflow';'048'='workflow';'050'='workflow'
    '056'='common';'058'='workflow';'059'='workflow';'060'='common'
    '063'='common';'067'='workflow';'067B'='workflow';'067C'='workflow'
    '068'='workflow';'069'='workflow';'070'='workflow';'071'='workflow'
    '005'='system';'031'='system';'036'='system';'052'='system'
    '061'='system';'062'='system';'064'='system';'065'='system';'066'='system'
    '006'='cronjob';'006e'='cronjob';'032'='cronjob';'035'='cronjob'
    '014'='userinfo';'016'='userinfo';'039'='userinfo'
    '041'='literule';'042'='literule';'043'='literule';'044'='literule'
    '045'='literule';'047'='literule';'049'='literule';'051'='literule'
    '053'='literule';'055'='literule'
    '009'='project';'010'='project';'011'='project';'012'='project'
    '013'='project';'014_1'='project';'015'='project';'017'='project'
    '018'='project';'019'='project';'020'='project';'021'='project';'022'='project'
}

function Get-TableModule($tableName) {
    if (!$tableName) { return $null }
    $tableName = $tableName -replace '[^a-zA-Z0-9_].*', ''
    foreach ($modName in $moduleRules.Keys) {
        foreach ($prefix in $moduleRules[$modName]) {
            if ($tableName.StartsWith($prefix)) { return $modName }
        }
    }
    return $null
}

function Extract-TableName($line) {
    if ($line -match 'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(pmis_\w+|undo_log)') { return $matches[1] }
    if ($line -match 'CREATE\s+(?:UNIQUE\s+)?INDEX\s+.*\s+ON\s+(pmis_\w+)') { return $matches[1] }
    if ($line -match 'COMMENT\s+ON\s+TABLE\s+(pmis_\w+)\s') { return $matches[1] }
    if ($line -match 'COMMENT\s+ON\s+COLUMN\s+(pmis_\w+)\.') { return $matches[1] }
    if ($line -match 'ALTER\s+TABLE\s+(?:IF\s+EXISTS\s+)?(pmis_\w+)') { return $matches[1] }
    if ($line -match 'INSERT\s+INTO\s+(pmis_\w+)') { return $matches[1] }
    if ($line -match 'CREATE\s+(?:OR\s+REPLACE\s+)?TRIGGER\s+.*\s+(?:BEFORE|AFTER|INSTEAD\s+OF)\s+.*\s+ON\s+(pmis_\w+)') { return $matches[1] }
    return $null
}

# Find section markers
$sectionStarts = @()
for ($i = 0; $i -lt $totalLines; $i++) {
    if ($lines[$i] -match '^-- =+\s*\[') { $sectionStarts += $i }
}
Write-Host "Found $($sectionStarts.Count) section markers"

# Init module content
$moduleContent = @{}
foreach ($mod in $moduleRules.Keys) {
    $moduleContent[$mod] = [System.Collections.ArrayList]::new()
}

# Header -> common
$headerEnd = $sectionStarts[0]
for ($i = 0; $i -lt $headerEnd; $i++) {
    [void]$moduleContent['common'].Add($lines[$i])
}

# Process each section
for ($s = 0; $s -lt $sectionStarts.Count; $s++) {
    $startLine = $sectionStarts[$s]
    $endLine = if ($s -lt $sectionStarts.Count - 1) { $sectionStarts[$s + 1] - 1 } else { $totalLines - 1 }
    
    # Extract section ID for fallback
    $sectionId = $null
    if ($lines[$startLine] -match '\[(\w+)\]') { $sectionId = $matches[1] }
    $sectionFallback = $null
    if ($sectionId -and $sectionModuleMap.ContainsKey($sectionId)) {
        $sectionFallback = $sectionModuleMap[$sectionId]
    }
    
    # Parse within section by statement blocks
    $currentBlock = [System.Collections.ArrayList]::new()
    $currentModule = $null
    
    for ($i = $startLine; $i -le $endLine; $i++) {
        $line = $lines[$i]
        
        if ($i -eq $startLine) {
            [void]$currentBlock.Add($line)
            continue
        }
        
        $tableName = Extract-TableName $line
        if ($tableName) {
            if ($currentBlock.Count -gt 0 -and $currentModule) {
                foreach ($bl in $currentBlock) { [void]$moduleContent[$currentModule].Add($bl) }
                $currentBlock.Clear()
            }
            $currentModule = Get-TableModule $tableName
        }
        
        [void]$currentBlock.Add($line)
        
        if ($line -match ';\s*$') {
            if (!$currentModule -and $sectionFallback) { $currentModule = $sectionFallback }
            if (!$currentModule) {
                $blockText = $currentBlock -join "`n"
                $allTables = [regex]::Matches($blockText, '(pmis_\w+)')
                foreach ($m in $allTables) {
                    $mod = Get-TableModule $m.Value
                    if ($mod) { $currentModule = $mod; break }
                }
            }
            if (!$currentModule) { $currentModule = 'common' }
            
            foreach ($bl in $currentBlock) { [void]$moduleContent[$currentModule].Add($bl) }
            $currentBlock.Clear()
            $currentModule = $null
        }
    }
    
    if ($currentBlock.Count -gt 0) {
        if (!$currentModule -and $sectionFallback) { $currentModule = $sectionFallback }
        if (!$currentModule) { $currentModule = 'common' }
        foreach ($bl in $currentBlock) { [void]$moduleContent[$currentModule].Add($bl) }
    }
}

# Footer (COMMIT) -> common
for ($i = $totalLines - 1; $i -ge 0; $i--) {
    if ($lines[$i] -match '^COMMIT;') {
        $commitStart = $i
        while ($commitStart -gt 0 -and ($lines[$commitStart - 1] -match '^-- ' -or $lines[$commitStart - 1] -match '^\s*$')) {
            $commitStart--
        }
        for ($j = $commitStart; $j -lt $totalLines; $j++) {
            [void]$moduleContent['common'].Add($lines[$j])
        }
        break
    }
}

# Write module files
$moduleOrder = @('common','system','userinfo','project','cronjob','message','workflow','agent','literule')
$moduleTitles = @{
    'common'='Common Base (Dict/Ext/Tx/Trigger)'
    'system'='System Mgmt (Config/File/Audit/Export)'
    'userinfo'='User Info (Auth/User/Org/Perm/Resource/HR)'
    'project'='Project Mgmt (Opp/Init/Contract/Exec/Fin/Close/AfterSales)'
    'cronjob'='Cron Job (Job/DAG/Schedule/Alert/Log/Quota)'
    'message'='Message Center (Notif/Template/Receipt/Batch/Canary)'
    'workflow'='Workflow Engine (Def/Instance/Delegate/Notify/DMN/Integ)'
    'agent'='AI Agent (Agent/Orch/Knowledge/Tool/HitL)'
    'literule'='Rule Engine (Rule/Decision/Scorecard/ABTest/Var)'
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

foreach ($mod in $moduleOrder) {
    $content = $moduleContent[$mod]
    if ($content.Count -eq 0) { continue }
    
    $outFile = "$outDir\V1.0.0_$mod.sql"
    $sb = [System.Text.StringBuilder]::new()
    
    [void]$sb.AppendLine("-- ====================================================================")
    [void]$sb.AppendLine("-- $($moduleTitles[$mod])")
    [void]$sb.AppendLine("-- Module: $mod | Version: V1.0.0 | Target: PostgreSQL 18")
    [void]$sb.AppendLine("-- Generated from deploy/sql/V1.0.0.sql")
    [void]$sb.AppendLine("-- ====================================================================")
    [void]$sb.AppendLine("")
    
    foreach ($line in $content) { [void]$sb.AppendLine($line) }
    
    [System.IO.File]::WriteAllText($outFile, $sb.ToString(), $utf8NoBom)
    Write-Host "  Written: $mod ($($content.Count) lines)"
}

# Write aggregate reference script
$refFile = "$outDir\V1.0.0_all.sql"
$refSb = [System.Text.StringBuilder]::new()
[void]$refSb.AppendLine("-- ====================================================================")
[void]$refSb.AppendLine("-- PMIS DB Init - Module Reference Script")
[void]$refSb.AppendLine("-- Version: V1.0.0 | Target: PostgreSQL 18")
[void]$refSb.AppendLine("-- Usage: psql -v ON_ERROR_STOP=1 -f modules/V1.0.0_all.sql")
[void]$refSb.AppendLine("-- ====================================================================")
[void]$refSb.AppendLine("")
[void]$refSb.AppendLine("SET client_min_messages = WARNING;")
[void]$refSb.AppendLine("SET search_path = public, pg_catalog;")
[void]$refSb.AppendLine("BEGIN;")
[void]$refSb.AppendLine("")
foreach ($mod in $moduleOrder) {
    $modFile = "V1.0.0_$mod.sql"
    if (Test-Path "$outDir\$modFile") {
        [void]$refSb.AppendLine("-- ==== $($moduleTitles[$mod]) ====")
        [void]$refSb.AppendLine("\i $modFile")
        [void]$refSb.AppendLine("")
    }
}
[void]$refSb.AppendLine("COMMIT;")
[void]$refSb.AppendLine("")

[System.IO.File]::WriteAllText($refFile, $refSb.ToString(), $utf8NoBom)
Write-Host "`n  Written: aggregate reference -> V1.0.0_all.sql"

# Summary
Write-Host "`n=== Split Summary ==="
$totalSplitLines = 0
foreach ($mod in $moduleOrder) {
    $cnt = $moduleContent[$mod].Count
    $totalSplitLines += $cnt
    $content = $moduleContent[$mod] -join "`n"
    $tableCount = ([regex]::Matches($content, 'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(pmis_\w+|undo_log)')).Count
    Write-Host "  ${mod}: $cnt lines, $tableCount tables"
}
Write-Host "  Total: $totalSplitLines lines (original: $totalLines)"
