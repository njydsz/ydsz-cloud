# ====================================================================
# V1.0.0.sql 业务 ID 字段统一 VARCHAR(20) 改造脚本 (v2 - 精简版)
# ====================================================================
# 大厂规范: 所有主键 ID + 业务 ID 一律 VARCHAR(20),
#           由应用层 SnowflakeIdGenerator 生成,不再依赖数据库自增。
#
# 改造范围:
#   ✓  id BIGSERIAL PRIMARY KEY                  -> id VARCHAR(20) PRIMARY KEY
#   ✓  tenant_id BIGINT                          -> VARCHAR(20)
#   ✓  created_by / updated_by BIGINT            -> VARCHAR(20)
#   ✓  所有 _id 业务外键 BIGINT                   -> VARCHAR(20)
#   ✓  biz_id / business_id / workflow_id /      -> VARCHAR(20) (原 VARCHAR(64))
#      assignee_id / msg_id / trace_id / user_id
#   ✗  provider_trace_id VARCHAR(64)             保留(三方)
#   ✗  open_id / union_id / corp_id /            保留(三方 IM)
#      agent_id VARCHAR(128)
#   ✗  process_instance_id VARCHAR(128)          保留(工作流跨服务)
#   ✗  attachment_id VARCHAR(64)                 保留(文件 UUID)
#   ✗  source_id VARCHAR(64)                     保留(三方数据源)
# ====================================================================

param(
    [string]$SqlPath = "deploy\sql\V1.0.0.sql",
    [string]$BackupPath = "deploy\sql\V1.0.0.sql.bak.p3-1-v2"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $BackupPath)) {
    Copy-Item -Path $SqlPath -Destination $BackupPath -Force
    Write-Host "[OK] 已备份到 $BackupPath"
}

$content = Get-Content $SqlPath -Raw -Encoding UTF8
$totalChanged = 0

# ====================================================================
# Step 1: 主键 id BIGSERIAL PRIMARY KEY -> VARCHAR(20) PRIMARY KEY
# ====================================================================
$pattern1 = '(?m)^(\s*)id(\s+)BIGSERIAL(\s+)PRIMARY KEY(\s*,)'
$count1 = ([regex]::Matches($content, $pattern1)).Count
$content = [regex]::Replace($content, $pattern1, '${1}id${2}VARCHAR(20)${3}PRIMARY KEY${4}')
$msg1 = '[Step 1] id BIGSERIAL PRIMARY KEY -> VARCHAR(20): {0} 处' -f $count1
Write-Host $msg1

# ====================================================================
# Step 2: 业务 ID 列 BIGINT -> VARCHAR(20)
#   匹配: 行内 缩进 + 列名 + 空白 + BIGINT + 空白 + (NOT NULL|PRIMARY KEY|DEFAULT|,)
# ====================================================================
$businessIdColumns = @(
    'tenant_id', 'created_by', 'updated_by',
    'user_id', 'dept_id', 'department_id', 'employee_id', 'customer_id',
    'applicant_id', 'approver_id', 'assignee_id', 'owner_id',
    'initiator_id', 'leader_id', 'reviewer_id', 'evaluator_id',
    'operator_id', 'subscriber_id', 'auth_id', 'assignor_id',
    'trigger_user_id', 'cc_user_id', 'target_id', 'delegate_user_id',
    'owner_user_id', 'business_dept_id', 'role_id',
    'parent_id', 'instance_id', 'task_id', 'contract_id',
    'definition_id', 'opportunity_id', 'pool_id',
    'initiation_id', 'boundary_task_id', 'warranty_id',
    'approval_id', 'biz_id', 'source_id', 'file_id', 'pm_id',
    'standard_id', 'wbs_task_id', 'invoice_id', 'subscription_id',
    'ticket_id', 'branch_id', 'uploader_id', 'reporter_id',
    'contract_file_id', 'position_id', 'author_id'
)
$count2 = 0
foreach ($col in $businessIdColumns) {
    $colEscaped = [regex]::Escape($col)
    # PowerShell 中 , 是数组操作符,这里把整个模式包成单引号 + 反引号转义竖线
    $p = '(?m)^(\s+)' + $colEscaped + '(\s+)BIGINT(\s*)(?:,|NOT NULL|PRIMARY KEY|DEFAULT)'
    $r = '${1}' + $col + '${2}VARCHAR(20)${3}${4}'
    $m = [regex]::Matches($content, $p)
    if ($m.Count -gt 0) {
        $content = [regex]::Replace($content, $p, $r)
        $count2 += $m.Count
    }
}
Write-Host ('[Step 2] 业务 ID BIGINT -> VARCHAR(20): {0} 处' -f $count2)

# ====================================================================
# Step 3: biz_id/business_id/workflow_id/assignee_id/msg_id/trace_id/user_id
#         VARCHAR(64) -> VARCHAR(20)
# ====================================================================
$varchar64Columns = @('biz_id', 'business_id', 'workflow_id', 'assignee_id', 'msg_id', 'trace_id', 'user_id')
$count3 = 0
foreach ($col in $varchar64Columns) {
    $colEscaped = [regex]::Escape($col)
    $p = '(?m)^(\s+)' + $colEscaped + '(\s+)VARCHAR\(64\)(\s*)(?:,|NOT NULL|PRIMARY KEY|DEFAULT)'
    $r = '${1}' + $col + '${2}VARCHAR(20)${3}${4}'
    $m = [regex]::Matches($content, $p)
    if ($m.Count -gt 0) {
        $content = [regex]::Replace($content, $p, $r)
        $count3 += $m.Count
    }
}
Write-Host "[Step 3] VARCHAR(64) -> VARCHAR(20): $count3 处"

# ====================================================================
# Step 4: tenant_id 的 DEFAULT 0/1 改成 '1' (雪花 ID 字符串)
# ====================================================================
$pattern4 = "(?m)^(\s+tenant_id\s+VARCHAR\(20\)\s+NOT NULL\s+DEFAULT\s+)\d+"
$count4 = ([regex]::Matches($content, $pattern4)).Count
$content = [regex]::Replace($content, $pattern4, "${1}'1'")
Write-Host "[Step 4] tenant_id DEFAULT 0 -> '1': $count4 处"

# ====================================================================
# Step 5: created_by / updated_by DEFAULT 0 -> '0' (雪花 ID 占位)
# ====================================================================
$pattern5 = "(?m)^(\s+(created_by|updated_by)\s+VARCHAR\(20\)\s+NOT NULL\s+DEFAULT\s+)\d+"
$count5 = ([regex]::Matches($content, $pattern5)).Count
$content = [regex]::Replace($content, $pattern5, "${1}'0'")
Write-Host "[Step 5] created_by/updated_by DEFAULT 0 -> '0': $count5 处"

# ====================================================================
# Step 6: 校验残留 (用于人工 review)
# ====================================================================
$remainingBigserial = ([regex]::Matches($content, 'BIGSERIAL')).Count
$remainingBigint = ([regex]::Matches($content, '(?m)^\s+\w+_id\s+BIGINT')).Count
Write-Host ""
Write-Host "===================================================================="
Write-Host "残留检查: BIGSERIAL=$remainingBigserial 处, 业务ID BIGINT=$remainingBigint 处"
Write-Host "===================================================================="

# ====================================================================
# Step 7: 写回
# ====================================================================
$content | Set-Content -Path $SqlPath -Encoding UTF8 -NoNewline
$total = $count1 + $count2 + $count3 + $count4 + $count5
Write-Host ""
Write-Host "[DONE] V1.0.0.sql 累计修改 $total 处,已写回 $SqlPath"
Write-Host "[BACKUP] 原文件: $BackupPath"
