# 数据库 SQL 按子模块拆分脚本
# 将 deploy/sql/V1.0.0.sql 拆分为各子模块独立脚本 + 保留汇总脚本
$ErrorActionPreference = "Stop"

$sqlFile = "d:\Code\ydsz\ydsz-pmis\deploy\sql\V1.0.0.sql"
$outDir = "d:\Code\ydsz\ydsz-pmis\deploy\sql\modules"
if (!(Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

# 读取所有行
$lines = Get-Content $sqlFile -Encoding UTF8
$totalLines = $lines.Count
Write-Host "Total lines: $totalLines"

# 定义模块及表名前缀映射（按优先级从高到低）
# 格式: 模块名 -> 匹配规则列表
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
        'pmis_config', 'pmis_operation_log', 'pmis_file',
        'pmis_export_record', 'pmis_report_subscription', 'pmis_meta_schema_version'
    )
    'common'    = @(
        'pmis_dict_type', 'pmis_dict_item', 'pmis_dict_version',
        'undo_log'
    )
}

# 特殊 section 映射（按 section 编号）
$sectionModuleMap = @{
    '001' = 'common'     # init pmis schema (dict, extensions, SET, BEGIN)
    '004' = 'workflow'   # init pmis workflow schema
    '005' = 'system'     # init pmis file schema
    '006' = 'cronjob'    # init pmis job schema
    '006e' = 'cronjob'   # P7-2 租户级配额
    '009' = 'project'    # init pmis project schema
    '010' = 'project'    # init pmis execution schema
    '011' = 'project'    # init pmis batch8 schema
    '012' = 'project'    # init pmis finance schema
    '013' = 'project'    # init pmis evm schema
    '014_1' = 'project'  # init pmis resource bench schema
    '014' = 'userinfo'   # init pmis admin full perm
    '015' = 'project'    # init pmis cockpit views
    '016' = 'userinfo'   # init pmis security
    '017' = 'project'    # init pmis after sales schema
    '018' = 'project'    # init pmis smart p4 2 schema
    '019' = 'project'    # init pmis alert thresholds
    '020' = 'project'    # init pmis billable utilization snapshot
    '021' = 'project'    # register pmis smart jobs
    '022' = 'project'    # init pmis alert templates
    '023' = 'workflow'   # init pmis flow engine
    '024' = 'common'     # add version to core tables
    '025' = 'workflow'   # add pmis flow audit log
    '026' = 'workflow'   # add pmis flow cc
    '027' = 'common'     # init undo log
    '028' = 'workflow'   # add flow gap columns
    '029' = 'workflow'   # add pmis flow timer
    '030' = 'workflow'   # add pmis flow delegate auth
    '031' = 'system'     # init report subscription
    '032' = 'cronjob'    # register report jobs
    '033' = 'workflow'   # add pmis flow weight
    '034' = 'workflow'   # add pmis flow sla reminder
    '035' = 'cronjob'    # register consistency job
    '036' = 'system'     # init export record
    '037' = 'workflow'   # init pmis flow archive
    '038' = 'workflow'   # add pmis flow canary
    '039' = 'userinfo'   # init pmis attendance schema
    '041' = 'literule'   # init pmis literule schema
    '042' = 'literule'   # init pmis rule test case
    '043' = 'literule'   # add rule lifecycle and trace
    '044' = 'literule'   # add decision table
    '045' = 'literule'   # add decision table hit policy
    '046' = 'workflow'   # add pmis flow event subscription
    '047' = 'literule'   # add rule canary
    '048' = 'workflow'   # add pmis flow run task priority
    '049' = 'literule'   # add rule status check
    '050' = 'workflow'   # add pmis flow notify outbox
    '051' = 'literule'   # init rule scorecard tree script
    '052' = 'system'     # index tuning (system-wide)
    '053' = 'literule'   # add rule tenant id
    '055' = 'literule'   # init rule variable def
    '056' = 'common'     # add tenant id to base tables
    '058' = 'workflow'   # init pmis flow third party
    '059' = 'workflow'   # init pmis flow dmn
    '060' = 'common'     # field type unification
    '061' = 'system'     # merge export tables
    '062' = 'system'     # monthly partitioning for audit logs
    '063' = 'common'     # tg_set_updated_at trigger
    '064' = 'system'     # P1-7 provider_trace_id 索引补齐
    '065' = 'system'     # pmis_meta_schema_version
    '066' = 'system'     # P3 性能/安全/审计 增强设计预留
    '067' = 'workflow'   # P1-2 工作流通知模板表
    '067B' = 'workflow'  # P1-6 工作流 Webhook 订阅表
    '067C' = 'workflow'  # P1-7 工作流通知偏好表
    '068' = 'workflow'   # P1-6 工作流审批附件表
    '069' = 'workflow'   # P2-1 委派沟通记录表
    '070' = 'workflow'   # P2-2 流程评论多级回复表
    '071' = 'workflow'   # P3-3 AI 推荐审批人反馈记录表
}

# 判断一段 SQL 属于哪个模块
function Get-BlockModule($blockText) {
    # 1. 先尝试通过 section 标记判断
    $sectionMatch = [regex]::Match($blockText, '\[(\w+)\]')
    if ($sectionMatch.Success) {
        $sectionId = $sectionMatch.Groups[1].Value
        # 去掉前导零
        $cleanId = $sectionId -replace '^0+', ''
        if ($sectionModuleMap.ContainsKey($sectionId)) {
            return $sectionModuleMap[$sectionId]
        }
        if ($sectionModuleMap.ContainsKey($cleanId)) {
            return $sectionModuleMap[$cleanId]
        }
    }
    
    # 2. 通过表名前缀判断
    foreach ($modName in $moduleRules.Keys) {
        foreach ($prefix in $moduleRules[$modName]) {
            if ($blockText -match [regex]::Escape($prefix)) {
                return $modName
            }
        }
    }
    
    return 'common'  # 默认归入 common
}

# 解析 SQL 文件结构：
# - Header: 从第1行到第一个 section 标记之前（SET, BEGIN, extensions, comments）
# - Blocks: 每个 section 标记到下一个 section 标记之间
# - Footer: 最后一个 section 之后的内容（COMMIT 等）

# 找到所有 section 标记的行号
$sectionMarkers = @()
for ($i = 0; $i -lt $totalLines; $i++) {
    if ($lines[$i] -match '^-- =+\s*\[\w+\]') {
        $sectionMarkers += $i
    }
}

Write-Host "Found $($sectionMarkers.Count) section markers"

# 提取 header（第一个 section 之前的所有内容）
$headerEnd = if ($sectionMarkers.Count -gt 0) { $sectionMarkers[0] - 1 } else { 30 }
# 向上搜索找到空行（section marker 前的注释行）
while ($headerEnd -gt 0 -and $lines[$headerEnd] -match '^\s*$') { $headerEnd-- }
# 回退到包含注释的位置
while ($headerEnd -gt 0 -and $lines[$headerEnd] -match '^-- ') { $headerEnd-- }
if ($lines[$headerEnd] -match '^\s*$') { $headerEnd++ }

$headerLines = $lines[0..($headerEnd - 1)]
Write-Host "Header: lines 1 to $headerEnd ($($headerLines.Count) lines)"

# 按模块初始化内容存储
$moduleContent = @{}
foreach ($mod in $moduleRules.Keys) {
    $moduleContent[$mod] = [System.Collections.ArrayList]::new()
}

# 处理每个 section block
for ($s = 0; $s -lt $sectionMarkers.Count; $s++) {
    $startLine = $sectionMarkers[$s]
    $endLine = if ($s -lt $sectionMarkers.Count - 1) { $sectionMarkers[$s + 1] - 1 } else { $totalLines - 1 }
    
    # 提取 block 内容
    $blockLines = $lines[$startLine..$endLine]
    $blockText = $blockLines -join "`n"
    
    # 判断模块
    $modName = Get-BlockModule $blockText
    
    # 添加到对应模块
    foreach ($line in $blockLines) {
        [void]$moduleContent[$modName].Add($line)
    }
}

# 提取 footer（最后一个 section 之后的内容中的 COMMIT 行）
$footerLines = @()
$lastSectionEnd = if ($sectionMarkers.Count -gt 0) { 
    if ($s -lt $sectionMarkers.Count) { $sectionMarkers[-1] } else { $sectionMarkers[-1] }
} else { 0 }
# 从文件末尾搜索 COMMIT
for ($i = $totalLines - 1; $i -ge 0; $i--) {
    if ($lines[$i] -match '^COMMIT;') {
        # 向上搜索找到注释开始
        $commitStart = $i
        while ($commitStart -gt 0 -and ($lines[$commitStart - 1] -match '^-- ' -or $lines[$commitStart - 1] -match '^\s*$')) {
            $commitStart--
        }
        $footerLines = $lines[$commitStart..($totalLines - 1)]
        break
    }
}

Write-Host "Footer: $($footerLines.Count) lines"

# 写入各模块 SQL 文件
$moduleOrder = @('common', 'system', 'userinfo', 'project', 'cronjob', 'message', 'workflow', 'agent', 'literule')
$moduleTitles = @{
    'common'   = '公共基础 (字典/扩展/事务/触发器)'
    'system'   = '系统管理 (配置/文件/审计/导出)'
    'userinfo' = '用户信息 (认证/用户/组织/权限/资源/考勤)'
    'project'  = '项目管理 (商机/立项/合同/执行/财务/结项/售后)'
    'cronjob'  = '定时任务 (作业/DAG/调度/告警/日志)'
    'message'  = '消息中心 (通知/模板/回执/批量/灰度)'
    'workflow' = '工作流引擎 (定义/实例/委派/通知/DMN/集成)'
    'agent'    = 'AI Agent (Agent/编排/知识库/工具/人机协同)'
    'literule' = '规则引擎 (规则/决策表/评分卡/AB测试)'
}

foreach ($mod in $moduleOrder) {
    $content = $moduleContent[$mod]
    if ($content.Count -eq 0) { continue }
    
    $outFile = "$outDir\V1.0.0_$mod.sql"
    $sb = [System.Text.StringBuilder]::new()
    
    # 模块文件头
    [void]$sb.AppendLine("-- ====================================================================")
    [void]$sb.AppendLine("-- $moduleTitles[$mod]")
    [void]$sb.AppendLine("-- Module: $mod")
    [void]$sb.AppendLine("-- Version: V1.0.0")
    [void]$sb.AppendLine("-- Target: PostgreSQL 18")
    [void]$sb.AppendLine("-- Description: 本文件由 deploy/sql/V1.0.0.sql 拆分生成")
    [void]$sb.AppendLine("--   仅供单独初始化对应模块时使用; 完整初始化请使用 V1.0.0.sql")
    [void]$sb.AppendLine("-- ====================================================================")
    [void]$sb.AppendLine("")
    
    # 模块内容
    foreach ($line in $content) {
        [void]$sb.AppendLine($line)
    }
    
    # 写入文件
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($outFile, $sb.ToString(), $utf8NoBom)
    Write-Host "  Written: $mod ($($content.Count) lines) -> V1.0.0_$mod.sql"
}

# 创建汇总脚本（保留原文件的完整副本，但更新头部说明）
$aggFile = "d:\Code\ydsz\ydsz-pmis\deploy\sql\V1.0.0.sql"
# 原文件保持不变，但创建一个新的 \i 引用脚本
$refFile = "$outDir\V1.0.0_all.sql"
$refSb = [System.Text.StringBuilder]::new()
[void]$refSb.AppendLine("-- ====================================================================")
[void]$refSb.AppendLine("-- 南京云顶 PMIS 数据库初始化 - 模块引用脚本")
[void]$refSb.AppendLine("-- Version: V1.0.0")
[void]$refSb.AppendLine("-- Target: PostgreSQL 18")
[void]$refSb.AppendLine("-- Description: 本脚本按模块顺序引用各子模块 SQL 文件")
[void]$refSb.AppendLine("--   等价于直接执行 deploy/sql/V1.0.0.sql")
[void]$refSb.AppendLine("-- Usage:")
[void]$refSb.AppendLine("--   psql ""host=... user=... dbname=... password=..."" -v ON_ERROR_STOP=1 -f V1.0.0_all.sql")
[void]$refSb.AppendLine("-- ====================================================================")
[void]$refSb.AppendLine("")
[void]$refSb.AppendLine("SET client_min_messages = WARNING;")
[void]$refSb.AppendLine("SET search_path = public, pg_catalog;")
[void]$refSb.AppendLine("BEGIN;")
[void]$refSb.AppendLine("")
foreach ($mod in $moduleOrder) {
    $modFile = "V1.0.0_$mod.sql"
    if (Test-Path "$outDir\$modFile") {
        [void]$refSb.AppendLine("-- ==== $moduleTitles[$mod] ====")
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
