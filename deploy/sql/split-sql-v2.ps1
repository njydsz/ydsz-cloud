# 数据库 SQL 按子模块拆分脚本 V2
# 按 SQL 语句级别解析，根据表名前缀分配到对应模块
$ErrorActionPreference = "Stop"

$sqlFile = "d:\Code\ydsz\ydsz-pmis\deploy\sql\V1.0.0.sql"
$outDir = "d:\Code\ydsz\ydsz-pmis\deploy\sql\modules"
if (!(Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

$lines = Get-Content $sqlFile -Encoding UTF8
$totalLines = $lines.Count
Write-Host "Total lines: $totalLines"

# 模块表名前缀映射（按优先级从高到低）
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

# 特殊 section 映射（仅用于不包含表名的 section）
$sectionModuleMap = @{
    '004' = 'workflow'; '023' = 'workflow'; '024' = 'common'; '025' = 'workflow'
    '026' = 'workflow'; '027' = 'common'; '028' = 'workflow'; '029' = 'workflow'
    '030' = 'workflow'; '033' = 'workflow'; '034' = 'workflow'; '037' = 'workflow'
    '038' = 'workflow'; '046' = 'workflow'; '048' = 'workflow'; '050' = 'workflow'
    '056' = 'common'; '058' = 'workflow'; '059' = 'workflow'; '060' = 'common'
    '063' = 'common'; '067' = 'workflow'; '067B' = 'workflow'; '067C' = 'workflow'
    '068' = 'workflow'; '069' = 'workflow'; '070' = 'workflow'; '071' = 'workflow'
    '005' = 'system'; '031' = 'system'; '036' = 'system'; '052' = 'system'
    '061' = 'system'; '062' = 'system'; '064' = 'system'; '065' = 'system'; '066' = 'system'
    '006' = 'cronjob'; '006e' = 'cronjob'; '032' = 'cronjob'; '035' = 'cronjob'
    '014' = 'userinfo'; '016' = 'userinfo'; '039' = 'userinfo'
    '041' = 'literule'; '042' = 'literule'; '043' = 'literule'; '044' = 'literule'
    '045' = 'literule'; '047' = 'literule'; '049' = 'literule'; '051' = 'literule'
    '053' = 'literule'; '055' = 'literule'
    '009' = 'project'; '010' = 'project'; '011' = 'project'; '012' = 'project'
    '013' = 'project'; '014_1' = 'project'; '015' = 'project'; '017' = 'project'
    '018' = 'project'; '019' = 'project'; '020' = 'project'; '021' = 'project'; '022' = 'project'
}

# 根据表名判断模块
function Get-TableModule($tableName) {
    if (!$tableName) { return $null }
    # 去掉分号、括号等
    $tableName = $tableName -replace '[^a-zA-Z0-9_].*', ''
    foreach ($modName in $moduleRules.Keys) {
        foreach ($prefix in $moduleRules[$modName]) {
            if ($tableName.StartsWith($prefix)) { return $modName }
        }
    }
    return $null
}

# 从一行 SQL 中提取表名
function Extract-TableName($line) {
    # CREATE TABLE IF NOT EXISTS pmis_xxx(
    if ($line -match 'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(pmis_\w+|undo_log)') { return $matches[1] }
    # CREATE INDEX ... ON pmis_xxx
    if ($line -match 'CREATE\s+(?:UNIQUE\s+)?INDEX\s+.*\s+ON\s+(pmis_\w+)') { return $matches[1] }
    # COMMENT ON TABLE pmis_xxx
    if ($line -match 'COMMENT\s+ON\s+TABLE\s+(pmis_\w+)\s') { return $matches[1] }
    # COMMENT ON COLUMN pmis_xxx.
    if ($line -match 'COMMENT\s+ON\s+COLUMN\s+(pmis_\w+)\.') { return $matches[1] }
    # ALTER TABLE pmis_xxx
    if ($line -match 'ALTER\s+TABLE\s+(?:IF\s+EXISTS\s+)?(pmis_\w+)') { return $matches[1] }
    # INSERT INTO pmis_xxx
    if ($line -match 'INSERT\s+INTO\s+(pmis_\w+)') { return $matches[1] }
    # CREATE TRIGGER ... ON pmis_xxx
    if ($line -match 'CREATE\s+(?:OR\s+REPLACE\s+)?TRIGGER\s+.*\s+(?:BEFORE|AFTER|INSTEAD\s+OF)\s+.*\s+ON\s+(pmis_\w+)') { return $matches[1] }
    return $null
}

# 从注释中提取 section ID
function Extract-SectionId($line) {
    if ($line -match '\[(\w+)\]') { return $matches[1] }
    return $null
}

# 判断一行是否是 section 标记行
function Is-SectionMarker($line) {
    return $line -match '^-- =+\s*\['
}

# ===== 解析模式 =====
# 将 SQL 文件分为三种内容：
# 1. Header: SET, BEGIN, CREATE EXTENSION, 注释（第一个 section 标记之前）
# 2. Statement blocks: 每个以 -- ==== 开头的 section 或独立语句
# 3. Footer: COMMIT 及其注释

# 找到所有 section 标记行
$sectionStarts = @()
for ($i = 0; $i -lt $totalLines; $i++) {
    if (Is-SectionMarker $lines[$i]) { $sectionStarts += $i }
}

# 找到 header 结束位置（第一个 section 之前）
$headerEnd = 0
if ($sectionStarts.Count -gt 0) {
    # 向上搜索到第一个非注释非空行
    $headerEnd = $sectionStarts[0]
    while ($headerEnd -gt 0 -and ($lines[$headerEnd - 1] -match '^\s*$' -or $lines[$headerEnd - 1] -match '^-- ')) {
        $headerEnd--
    }
}

# 也检查第一个 section 之前是否有非 section 的 SQL 语句
# 实际上 header 从第1行到第一个 section 标记前的所有内容
# 但第一个 section [001] 之前的 SET/BEGIN/CREATE EXTENSION 也是 header
# 而第一个 section [001] 内部包含了 dict_type/dict_item/role/permission/msg_notification 等多种模块的表

# 策略: 不按 section 分块, 而是逐行扫描, 识别每个"语句块"属于哪个模块
# 一个语句块 = 连续的注释 + CREATE TABLE / COMMENT / INDEX / ALTER / INSERT 等直到分号或空行

# 更好的策略: 按 section 分块, 但在 section 内部进一步按表名拆分
# 如果一个 section 内有多个模块的表, 则将该 section 的内容按表名分配

# 按模块初始化内容存储
$moduleContent = @{}
foreach ($mod in $moduleRules.Keys) {
    $moduleContent[$mod] = [System.Collections.ArrayList]::new()
}

# 处理 header (SET, BEGIN, CREATE EXTENSION 等) -> common
$headerLines = $lines[0..($sectionStarts[0] - 1)]
foreach ($line in $headerLines) {
    [void]$moduleContent['common'].Add($line)
}

# 处理每个 section 内部
for ($s = 0; $s -lt $sectionStarts.Count; $s++) {
    $startLine = $sectionStarts[$s]
    $endLine = if ($s -lt $sectionStarts.Count - 1) { $sectionStarts[$s + 1] - 1 } else { $totalLines - 1 }
    
    # 提取 section ID
    $sectionId = Extract-SectionId $lines[$startLine]
    $sectionFallback = $null
    if ($sectionId -and $sectionModuleMap.ContainsKey($sectionId)) {
        $sectionFallback = $sectionModuleMap[$sectionId]
    }
    
    # 在 section 内部按表名拆分
    # 一个"语句块" = 前导注释 + SQL 语句 (以分号结尾)
    $currentBlock = [System.Collections.ArrayList]::new()
    $currentTable = $null
    $currentModule = $null
    
    for ($i = $startLine; $i -le $endLine; $i++) {
        $line = $lines[$i]
        
        # 检查是否是新的 section 标记（第一次遇到时跳过，因为这是当前 section 的开始）
        if ($i -eq $startLine) {
            [void]$currentBlock.Add($line)
            continue
        }
        
        # 尝试从行中提取表名
        $tableName = Extract-TableName $line
        
        if ($tableName) {
            # 如果当前有未分配的 block, 先处理
            if ($currentBlock.Count -gt 0 -and $currentModule) {
                foreach ($bl in $currentBlock) { [void]$moduleContent[$currentModule].Add($bl) }
                $currentBlock.Clear()
            } elseif ($currentBlock.Count -gt 0 -and !$currentModule) {
                # block 还没有模块归属, 用新的表名确定
            }
            $currentTable = $tableName
            $currentModule = Get-TableModule $tableName
        }
        
        [void]$currentBlock.Add($line)
        
        # 检查语句是否结束（以分号结尾）
        if ($line -match ';\s*$') {
            # 语句结束, 确定 module
            if (!$currentModule -and $sectionFallback) {
                $currentModule = $sectionFallback
            }
            if (!$currentModule) {
                # 尝试从整个 block 文本中查找表名
                $blockText = $currentBlock -join "`n"
                $allTables = [regex]::Matches($blockText, '(pmis_\w+)')
                foreach ($m in $allTables) {
                    $mod = Get-TableModule $m.Value
                    if ($mod) { $currentModule = $mod; break }
                }
            }
            if (!$currentModule) { $currentModule = 'common' }
            
            # 将 block 分配到模块
            foreach ($bl in $currentBlock) { [void]$moduleContent[$currentModule].Add($bl) }
            $currentBlock.Clear()
            $currentTable = $null
            $currentModule = $null
        }
    }
    
    # 处理 section 末尾未结束的 block
    if ($currentBlock.Count -gt 0) {
        if (!$currentModule -and $sectionFallback) { $currentModule = $sectionFallback }
        if (!$currentModule) { $currentModule = 'common' }
        foreach ($bl in $currentBlock) { [void]$moduleContent[$currentModule].Add($bl) }
    }
}

# 处理 footer (COMMIT 等) -> common
# 从文件末尾搜索 COMMIT
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

# ===== 写入各模块 SQL 文件 =====
$moduleOrder = @('common', 'system', 'userinfo', 'project', 'cronjob', 'message', 'workflow', 'agent', 'literule')
$moduleTitles = @{
    'common'   = '公共基础 (字典/扩展/事务/触发器/字段统一)'
    'system'   = '系统管理 (配置/文件/审计/导出/索引调优)'
    'userinfo' = '用户信息 (认证/用户/组织/权限/资源/考勤)'
    'project'  = '项目管理 (商机/立项/合同/执行/财务/结项/售后/报表)'
    'cronjob'  = '定时任务 (作业/DAG/调度/告警/日志/配额)'
    'message'  = '消息中心 (通知/模板/回执/批量/灰度/偏好)'
    'workflow' = '工作流引擎 (定义/实例/委派/通知/DMN/集成/AI辅助)'
    'agent'    = 'AI Agent (Agent/编排/知识库/工具/人机协同)'
    'literule' = '规则引擎 (规则/决策表/评分卡/AB测试/变量)'
}

foreach ($mod in $moduleOrder) {
    $content = $moduleContent[$mod]
    if ($content.Count -eq 0) { continue }
    
    $outFile = "$outDir\V1.0.0_$mod.sql"
    $sb = [System.Text.StringBuilder]::new()
    
    [void]$sb.AppendLine("-- ====================================================================")
    [void]$sb.AppendLine("-- $($moduleTitles[$mod])")
    [void]$sb.AppendLine("-- Module: $mod")
    [void]$sb.AppendLine("-- Version: V1.0.0")
    [void]$sb.AppendLine("-- Target: PostgreSQL 18")
    [void]$sb.AppendLine("-- Description: 本文件由 deploy/sql/V1.0.0.sql 按模块拆分生成")
    [void]$sb.AppendLine("--   仅供单独初始化对应模块时使用; 完整初始化请使用 V1.0.0.sql")
    [void]$sb.AppendLine("--   或通过 psql 执行 V1.0.0_all.sql 引用脚本")
    [void]$sb.AppendLine("-- ====================================================================")
    [void]$sb.AppendLine("")
    
    foreach ($line in $content) {
        [void]$sb.AppendLine($line)
    }
    
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($outFile, $sb.ToString(), $utf8NoBom)
    Write-Host "  Written: $mod ($($content.Count) lines)"
}

# ===== 创建汇总引用脚本 =====
$refFile = "$outDir\V1.0.0_all.sql"
$refSb = [System.Text.StringBuilder]::new()
[void]$refSb.AppendLine("-- ====================================================================")
[void]$refSb.AppendLine("-- 南京云顶 PMIS 数据库初始化 - 模块引用脚本")
[void]$refSb.AppendLine("-- Version: V1.0.0")
[void]$refSb.AppendLine("-- Target: PostgreSQL 18")
[void]$refSb.AppendLine("-- Description: 本脚本按模块顺序引用各子模块 SQL 文件,")
[void]$refSb.AppendLine("--   等价于直接执行 deploy/sql/V1.0.0.sql")
[void]$refSb.AppendLine("-- Usage:")
[void]$refSb.AppendLine("--   psql ""host=... user=... dbname=... password=..."" -v ON_ERROR_STOP=1 -f modules/V1.0.0_all.sql")
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
[void]$refSb.AppendLine("-- ====================================================================")
[void]$refSb.AppendLine("-- All DDL has been applied. Commit the transaction.")
[void]$refSb.AppendLine("-- ====================================================================")
[void]$refSb.AppendLine("COMMIT;")
[void]$refSb.AppendLine("")

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($refFile, $refSb.ToString(), $utf8NoBom)
Write-Host "`n  Written: aggregate reference -> V1.0.0_all.sql"

# 统计
Write-Host "`n=== Split Summary ==="
$totalSplitLines = 0
foreach ($mod in $moduleOrder) {
    $cnt = $moduleContent[$mod].Count
    $totalSplitLines += $cnt
    Write-Host "  ${mod}: $cnt lines"
}
Write-Host "  Total split lines: $totalSplitLines (original: $totalLines)"

# 验证：检查每个模块中的表数
Write-Host "`n=== Table Count per Module ==="
foreach ($mod in $moduleOrder) {
    $content = $moduleContent[$mod] -join "`n"
    $tableCount = ([regex]::Matches($content, 'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(pmis_\w+|undo_log)')).Count
    Write-Host "  ${mod}: $tableCount tables"
}
