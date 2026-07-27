"""
清理 ydsz 仓库中所有迁移后残留的代码与 SQL 块。
- 删除 ydsz_job_sla / ydsz_job_slow_log / ydsz_job_version_history 在 SQL 中的孤儿块
- 清理 SQL / Java 中 P*-merge 等历史迁移注释
- 同步更新 ydsz-cronjob/README.md
"""
import pathlib

ROOT = pathlib.Path("d:/Code/ydsz/ydsz-pmis")

def read(p):
    return pathlib.Path(p).read_text(encoding="utf-8")

def write(p, content):
    pathlib.Path(p).write_text(content, encoding="utf-8")
    print(f"[OK] 写入 {p}")

# ============================================================
# 1) V1.0.0_cronjob.sql
# ============================================================
sql_cronjob = ROOT / "deploy/sql/modules/V1.0.0_cronjob.sql"
content = read(sql_cronjob)

# (a) 删除 ydsz_job_slow_log 表 + 索引 (行 591-661)
old_slow = """-- ============================================================================
-- [P6-3] 慢任务诊断日志表 ydsz_job_slow_log（已废弃 — P2-1-merge 合并到 ydsz_job_log.is_slow）
-- ----------------------------------------------------------------------------
-- [DEPRECATED] 本表已废弃，慢任务标记已合并到 ydsz_job_log.is_slow 字段。
-- ydsz_job_log 新增 is_slow (0/1) 和 slow_threshold_ms (快照) 字段，
-- 配合部分索引 idx_pjl_slow 替代独立 slow_log 表。
-- 原有逻辑由 SlowTaskDetector 标记 is_slow=1 而非插入独立表。
-- 保留本表 DDL 仅用于存量数据迁移参考，新部署可跳过创建。
-- ============================================================================
CREATE TABLE IF NOT EXISTS ydsz_job_slow_log(
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    job_id            VARCHAR(20)         NOT NULL,
    job_key           VARCHAR(128)   NOT NULL,
    log_id            VARCHAR(20)         NOT NULL,
    duration_ms       BIGINT         NOT NULL,
    slow_threshold_ms BIGINT         NOT NULL,
    params_json       TEXT,
    error_message     TEXT,
    trace_id          VARCHAR(20),
    tenant_id         VARCHAR(20)         NOT NULL DEFAULT '1',
    created_by        VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT       NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_pjsl_duration_pos    CHECK (duration_ms > 0),
    CONSTRAINT ck_pjsl_threshold_pos   CHECK (slow_threshold_ms > 0),
    CONSTRAINT ck_pjsl_deleted_enum   CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE ydsz_job_slow_log IS '慢任务诊断日志（P6-3）: 仅记录执行耗时超过 slow_threshold_ms 的任务，用于性能分析';

COMMENT ON COLUMN ydsz_job_slow_log.id IS '主键 ID';

COMMENT ON COLUMN ydsz_job_slow_log.job_id IS '任务 ID（关联 ydsz_job.id）';

COMMENT ON COLUMN ydsz_job_slow_log.job_key IS '任务 KEY（冗余,避免连表）';

COMMENT ON COLUMN ydsz_job_slow_log.log_id IS '关联 ydsz_job_log.id（原始终端执行日志）';

COMMENT ON COLUMN ydsz_job_slow_log.duration_ms IS '本次执行耗时（毫秒）';

COMMENT ON COLUMN ydsz_job_slow_log.slow_threshold_ms IS '慢任务阈值（毫秒，来自 ydsz_job.slow_threshold_ms）';

COMMENT ON COLUMN ydsz_job_slow_log.params_json IS '执行参数 JSON（冗余自 job_log,便于独立分析）';

COMMENT ON COLUMN ydsz_job_slow_log.error_message IS '异常信息（如慢且有异常,冗余自 job_log）';

COMMENT ON COLUMN ydsz_job_slow_log.trace_id IS '链路追踪 ID（关联分布式链路）';

COMMENT ON COLUMN ydsz_job_slow_log.tenant_id IS '租户 ID（单租户部署默认 1）';

COMMENT ON COLUMN ydsz_job_slow_log.created_by IS '创建人 ID';

COMMENT ON COLUMN ydsz_job_slow_log.created_at IS '记录时间';

COMMENT ON COLUMN ydsz_job_slow_log.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN ydsz_job_slow_log.updated_at IS '最后修改时间';

COMMENT ON COLUMN ydsz_job_slow_log.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

-- [INLINE-OPT] job_id 索引（按任务查慢日志）
CREATE INDEX IF NOT EXISTS idx_pjsl_job_id
    ON ydsz_job_slow_log (job_id) WHERE deleted = 0;

-- [INLINE-OPT] 创建时间索引（按时间范围查慢日志趋势）
CREATE INDEX IF NOT EXISTS idx_pjsl_created
    ON ydsz_job_slow_log (created_at DESC) WHERE deleted = 0;
"""

assert old_slow in content, "ydsz_job_slow_log 块未找到"
content = content.replace(old_slow, "")
print("[OK] 删除 ydsz_job_slow_log 表块 (V1.0.0_cronjob.sql)")

# (b) 删除 ydsz_job_sla 孤儿 COMMENT 块 (行 1307-1337)
old_sla_comments = """-- ============================================================================

COMMENT ON TABLE ydsz_job_sla IS '任务 SLA 管理表: 定义最大执行时长/失败率/成功率约束, 违约时告警';

COMMENT ON COLUMN ydsz_job_sla.id IS '主键 ID';

COMMENT ON COLUMN ydsz_job_sla.job_id IS '任务 ID';

COMMENT ON COLUMN ydsz_job_sla.job_key IS '任务 KEY(冗余)';

COMMENT ON COLUMN ydsz_job_sla.max_duration_ms IS '最大执行时长(毫秒), 超过则违约';

COMMENT ON COLUMN ydsz_job_sla.max_fail_rate IS '最大失败率(%), 超过则违约';

COMMENT ON COLUMN ydsz_job_sla.min_success_rate IS '最小成功率(%), 低于则违约';

COMMENT ON COLUMN ydsz_job_sla.alert_level IS '告警级别: INFO / WARNING / CRITICAL';

COMMENT ON COLUMN ydsz_job_sla.enabled IS '是否启用: 0 禁用 / 1 启用';

COMMENT ON COLUMN ydsz_job_sla.created_by IS '创建人 ID';

COMMENT ON COLUMN ydsz_job_sla.created_at IS '创建时间';

COMMENT ON COLUMN ydsz_job_sla.updated_by IS '修改人 ID';

COMMENT ON COLUMN ydsz_job_sla.updated_at IS '修改时间';

COMMENT ON COLUMN ydsz_job_sla.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

-- ============================================================================
"""

assert old_sla_comments in content, "ydsz_job_sla COMMENT 块未找到"
content = content.replace(old_sla_comments, "")
print("[OK] 删除 ydsz_job_sla 孤儿 COMMENT 块 (V1.0.0_cronjob.sql)")

# (c) 删除 ydsz_job_version_history 表 (行 1339-1384)
old_ver = """-- ============================================================================
-- [P2-7] 任务版本历史表 ydsz_job_version_history（已废弃 — P1-6-merge 合并到 ydsz_job_history）
-- ----------------------------------------------------------------------------
-- [DEPRECATED] 本表已废弃，版本历史已合并到 ydsz_job_history（新增 change_type / before_snapshot 字段）。
-- 保留本表 DDL 仅用于存量数据迁移参考，新部署可跳过创建。
-- ============================================================================
CREATE TABLE IF NOT EXISTS ydsz_job_version_history(
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    job_id            VARCHAR(20)         NOT NULL,
    job_key           VARCHAR(128)   NOT NULL,
    version           INTEGER        NOT NULL,
    change_type       VARCHAR(32)    NOT NULL,
    before_snapshot   TEXT,
    after_snapshot    TEXT,
    change_remark     VARCHAR(512),
    changed_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    changed_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_pjvh_change_type_enum CHECK (change_type IN ('CREATE', 'UPDATE', 'DELETE')),
    CONSTRAINT ck_pjvh_version_pos CHECK (version >= 1)
);

COMMENT ON TABLE ydsz_job_version_history IS '任务版本历史表: 每次任务定义变更时记录版本快照, 支持回溯和差异对比';

COMMENT ON COLUMN ydsz_job_version_history.id IS '主键 ID';

COMMENT ON COLUMN ydsz_job_version_history.job_id IS '任务 ID';

COMMENT ON COLUMN ydsz_job_version_history.job_key IS '任务 KEY(冗余)';

COMMENT ON COLUMN ydsz_job_version_history.version IS '版本号';

COMMENT ON COLUMN ydsz_job_version_history.change_type IS '变更类型: CREATE / UPDATE / DELETE';

COMMENT ON COLUMN ydsz_job_version_history.before_snapshot IS '变更前快照 JSON';

COMMENT ON COLUMN ydsz_job_version_history.after_snapshot IS '变更后快照 JSON';

COMMENT ON COLUMN ydsz_job_version_history.change_remark IS '变更说明';

COMMENT ON COLUMN ydsz_job_version_history.changed_by IS '变更人 ID';

COMMENT ON COLUMN ydsz_job_version_history.changed_at IS '变更时间';

CREATE INDEX IF NOT EXISTS idx_pjvh_job_id
    ON ydsz_job_version_history (job_id, version DESC);

-- ============================================================================
"""

assert old_ver in content, "ydsz_job_version_history 表块未找到"
content = content.replace(old_ver, "")
print("[OK] 删除 ydsz_job_version_history 表 (V1.0.0_cronjob.sql)")

# (d) 清理 P*-merge 字段注释
merge_fixes = [
    # job_log.is_slow 注释
    (
        "    -- [P2-1-merge] 慢任务标记: 0=非慢 / 1=慢（合并自 ydsz_job_slow_log, 由 SlowTaskDetector 标记）\n",
        "    -- 慢任务标记: 0=非慢 / 1=慢（由 SlowTaskDetector 定期扫描并标记）\n",
    ),
    # job_log slow 索引注释
    (
        "-- [P2-1-merge] 慢任务部分索引: 替代 ydsz_job_slow_log 独立表, 直接从 job_log 查询慢任务\n",
        "-- 慢任务部分索引: 直接从 job_log 查询慢任务\n",
    ),
    # job_history 标题注释
    (
        "-- [P1-6] 任务配置历史版本表 ydsz_job_history（合并原 ydsz_job_version_history）\n",
        "-- 任务配置历史版本表 ydsz_job_history\n",
    ),
    (
        "-- 合并了原 ydsz_job_version_history 的 change_type / before_snapshot 能力，\n",
        "",
    ),
    # job_history 字段 merge 注释
    (
        "    -- [P1-6-merge] 变更类型: CREATE / UPDATE / DELETE（原 ydsz_job_version_history.change_type）\n",
        "    -- 变更类型: CREATE / UPDATE / DELETE\n",
    ),
    (
        "    -- [P1-6-merge] 变更前快照 JSON（原 ydsz_job_version_history.before_snapshot; CREATE 时为 NULL）\n",
        "    -- 变更前快照 JSON（CREATE 时为 NULL）\n",
    ),
    (
        "    -- [P1-6-merge] 变更说明（原 ydsz_job_version_history.change_remark）\n",
        "    -- 变更说明\n",
    ),
    # job_alert_rule.source_type
    (
        "    -- [P2-2-merge] 规则来源: MANUAL 手动创建(默认) / SLA 由SLA规则自动生成(合并自 ydsz_job_sla)\n",
        "    -- 规则来源: MANUAL 手动创建(默认) / SLA 由SLA规则自动生成\n",
    ),
    # ydsz_job.slow_threshold_ms 字段说明（指向废弃表）
    (
        "    -- [P6-3] 慢任务阈值（毫秒, NULL 不检测慢任务; 超过此值记入 ydsz_job_slow_log）\n",
        "    -- 慢任务阈值（毫秒, NULL 不检测慢任务; 超过此值由 SlowTaskDetector 标记 is_slow=1）\n",
    ),
    (
        "COMMENT ON COLUMN ydsz_job.slow_threshold_ms IS '慢任务阈值(毫秒, NULL 不检测慢任务; 执行耗时超过此值记入 ydsz_job_slow_log)';\n",
        "COMMENT ON COLUMN ydsz_job.slow_threshold_ms IS '慢任务阈值(毫秒, NULL 不检测慢任务; 执行耗时超过此值由 SlowTaskDetector 标记 is_slow=1)';\n",
    ),
    # export_record P0-3 merge 注释
    (
        "-- P0-3 合并：原 ydsz_report_export_record 已并入 ydsz_export_record，\n",
        "-- 异步导出记录表\n",
    ),
]
for old, new in merge_fixes:
    if old in content:
        content = content.replace(old, new)
        print(f"[OK] 修复 P*-merge 注释: {old[:60]}...")

write(sql_cronjob, content)

# ============================================================
# 2) V1.0.0.sql 中的 V1.0.0_cronjob.sql 副本
# ============================================================
sql_main = ROOT / "deploy/sql/V1.0.0.sql"
content = read(sql_main)

# (a) ydsz_job_slow_log 表块 (V1.0.0.sql 1860-1912)
old_slow_v1 = """-- ============================================================================
-- [P6-3] 慢任务诊断日志表 ydsz_job_slow_log
-- ----------------------------------------------------------------------------
-- 当任务执行耗时超过 ydsz_job.slow_threshold_ms 时，自动记录到本表。
-- 由 SlowTaskDetector 定期扫描 job_log 并写入，不影响任务执行主流程。
-- ============================================================================
CREATE TABLE IF NOT EXISTS ydsz_job_slow_log(
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    job_id            VARCHAR(20)         NOT NULL,
    job_key           VARCHAR(128)   NOT NULL,
    log_id            VARCHAR(20)         NOT NULL,
    duration_ms       BIGINT         NOT NULL,
    slow_threshold_ms BIGINT         NOT NULL,
    params_json       TEXT,
    error_message     TEXT,
    trace_id          VARCHAR(20),
    tenant_id         VARCHAR(20)         NOT NULL DEFAULT '1',
    created_by        VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT       NOT NULL DEFAULT 0
);

COMMENT ON TABLE ydsz_job_slow_log IS '慢任务诊断日志（P6-3）: 仅记录执行耗时超过 slow_threshold_ms 的任务，用于性能分析';
COMMENT ON COLUMN ydsz_job_slow_log.id IS '主键 ID';
COMMENT ON COLUMN ydsz_job_slow_log.job_id IS '任务 ID（关联 ydsz_job.id）';
COMMENT ON COLUMN ydsz_job_slow_log.job_key IS '任务 KEY（冗余,避免连表）';
COMMENT ON COLUMN ydsz_job_slow_log.log_id IS '关联 ydsz_job_log.id（原始终端执行日志）';
COMMENT ON COLUMN ydsz_job_slow_log.duration_ms IS '本次执行耗时（毫秒）';
COMMENT ON COLUMN ydsz_job_slow_log.slow_threshold_ms IS '慢任务阈值（毫秒，来自 ydsz_job.slow_threshold_ms）';
COMMENT ON COLUMN ydsz_job_slow_log.params_json IS '执行参数 JSON（冗余自 job_log,便于独立分析）';
COMMENT ON COLUMN ydsz_job_slow_log.error_message IS '异常信息（如慢且有异常,冗余自 job_log）';
COMMENT ON COLUMN ydsz_job_slow_log.trace_id IS '链路追踪 ID（关联分布式链路）';
COMMENT ON COLUMN ydsz_job_slow_log.tenant_id IS '租户 ID（单租户部署默认 1）';
COMMENT ON COLUMN ydsz_job_slow_log.created_by IS '创建人 ID';
COMMENT ON COLUMN ydsz_job_slow_log.created_at IS '记录时间';
COMMENT ON COLUMN ydsz_job_slow_log.updated_by IS '最后修改人 ID';
COMMENT ON COLUMN ydsz_job_slow_log.updated_at IS '最后修改时间';
COMMENT ON COLUMN ydsz_job_slow_log.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

-- [INLINE-OPT] job_id 索引（按任务查慢日志）
CREATE INDEX IF NOT EXISTS idx_pjsl_job_id
    ON ydsz_job_slow_log (job_id) WHERE deleted = 0;
-- [INLINE-OPT] 创建时间索引（按时间范围查慢日志趋势）
CREATE INDEX IF NOT EXISTS idx_pjsl_created
    ON ydsz_job_slow_log (created_at DESC) WHERE deleted = 0;
"""

if old_slow_v1 in content:
    content = content.replace(old_slow_v1, "")
    print("[OK] 删除 ydsz_job_slow_log 表块 (V1.0.0.sql)")
else:
    print("[WARN] V1.0.0.sql 中 ydsz_job_slow_log 表块未找到")

# (b) ydsz_job_sla 孤儿 COMMENT 块 (V1.0.0.sql 2447-2464)
old_sla_v1 = """-- ============================================================================

COMMENT ON TABLE ydsz_job_sla IS '任务 SLA 管理表: 定义最大执行时长/失败率/成功率约束, 违约时告警';
COMMENT ON COLUMN ydsz_job_sla.id IS '主键 ID';
COMMENT ON COLUMN ydsz_job_sla.job_id IS '任务 ID';
COMMENT ON COLUMN ydsz_job_sla.job_key IS '任务 KEY(冗余)';
COMMENT ON COLUMN ydsz_job_sla.max_duration_ms IS '最大执行时长(毫秒), 超过则违约';
COMMENT ON COLUMN ydsz_job_sla.max_fail_rate IS '最大失败率(%), 超过则违约';
COMMENT ON COLUMN ydsz_job_sla.min_success_rate IS '最小成功率(%), 低于则违约';
COMMENT ON COLUMN ydsz_job_sla.alert_level IS '告警级别: INFO / WARNING / CRITICAL';
COMMENT ON COLUMN ydsz_job_sla.enabled IS '是否启用: 0 禁用 / 1 启用';
COMMENT ON COLUMN ydsz_job_sla.created_by IS '创建人 ID';
COMMENT ON COLUMN ydsz_job_sla.created_at IS '创建时间';
COMMENT ON COLUMN ydsz_job_sla.updated_by IS '修改人 ID';
COMMENT ON COLUMN ydsz_job_sla.updated_at IS '修改时间';
COMMENT ON COLUMN ydsz_job_sla.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

-- ============================================================================
"""

if old_sla_v1 in content:
    content = content.replace(old_sla_v1, "")
    print("[OK] 删除 ydsz_job_sla 孤儿 COMMENT 块 (V1.0.0.sql)")
else:
    print("[WARN] V1.0.0.sql 中 ydsz_job_sla COMMENT 块未找到")

# (c) ydsz_job_version_history 表 (V1.0.0.sql 2466-2499)
old_ver_v1 = """-- ============================================================================
-- [P2-7] 任务版本历史表 ydsz_job_version_history
-- ----------------------------------------------------------------------------
-- 每次任务定义变更时记录一条版本快照，支持版本回溯和差异对比。
-- ============================================================================
CREATE TABLE IF NOT EXISTS ydsz_job_version_history(
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    job_id            VARCHAR(20)         NOT NULL,
    job_key           VARCHAR(128)   NOT NULL,
    version           INTEGER        NOT NULL,
    change_type       VARCHAR(32)    NOT NULL,
    before_snapshot   TEXT,
    after_snapshot    TEXT,
    change_remark     VARCHAR(512),
    changed_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    changed_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ydsz_job_version_history IS '任务版本历史表: 每次任务定义变更时记录版本快照, 支持回溯和差异对比';
COMMENT ON COLUMN ydsz_job_version_history.id IS '主键 ID';
COMMENT ON COLUMN ydsz_job_version_history.job_id IS '任务 ID';
COMMENT ON COLUMN ydsz_job_version_history.job_key IS '任务 KEY(冗余)';
COMMENT ON COLUMN ydsz_job_version_history.version IS '版本号';
COMMENT ON COLUMN ydsz_job_version_history.change_type IS '变更类型: CREATE / UPDATE / DELETE';
COMMENT ON COLUMN ydsz_job_version_history.before_snapshot IS '变更前快照 JSON';
COMMENT ON COLUMN ydsz_job_version_history.after_snapshot IS '变更后快照 JSON';
COMMENT ON COLUMN ydsz_job_version_history.change_remark IS '变更说明';
COMMENT ON COLUMN ydsz_job_version_history.changed_by IS '变更人 ID';
COMMENT ON COLUMN ydsz_job_version_history.changed_at IS '变更时间';

CREATE INDEX IF NOT EXISTS idx_pjvh_job_id
    ON ydsz_job_version_history (job_id, version DESC);
"""

if old_ver_v1 in content:
    content = content.replace(old_ver_v1, "")
    print("[OK] 删除 ydsz_job_version_history 表 (V1.0.0.sql)")
else:
    print("[WARN] V1.0.0.sql 中 ydsz_job_version_history 表块未找到")

# (d) V1.0.0.sql 字段注释同步修复
for old, new in merge_fixes:
    if old in content and old != new:
        content = content.replace(old, new)
        print(f"[OK] V1.0.0.sql 修复 P*-merge 注释: {old[:60]}...")

write(sql_main, content)

# ============================================================
# 3) Java 代码迁移注释清理
# ============================================================
java_fixes = [
    # JobAlertRule.java 行 81
    (
        "ydsz-backend/ydsz-cronjob/ydsz-cronjob-domain/src/main/java/com/njydsz/cronjob/domain/entity/JobAlertRule.java",
        "    /** 规则来源: MANUAL 手动创建(默认) / SLA 由SLA规则自动生成(P2-2-merge 合并自 ydsz_job_sla) */\n",
        "    /** 规则来源: MANUAL 手动创建(默认) / SLA 由SLA规则自动生成 */\n",
    ),
    # JobAlertRuleMapper.java 行 71-74
    (
        "ydsz-backend/ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobAlertRuleMapper.java",
        "     * <p>用于 SLA CRUD 代理查询：通过 alert_rule 表管理 SLA 规则，\n     * 替代原 ydsz_job_sla 独立表查询。\n",
        "     * <p>用于 SLA CRUD 代理查询：通过 alert_rule 表管理 SLA 规则。\n",
    ),
    # JobLog.java 行 87 + 90 (连续两行)
    (
        "ydsz-backend/ydsz-cronjob/ydsz-cronjob-domain/src/main/java/com/njydsz/cronjob/domain/entity/JobLog.java",
        "     * 慢任务标记（P2-1-merge：合并自 ydsz_job_slow_log）。\n     * 根据 {@code slow_threshold_ms} 判定并标记，替代原独立 slow_log 表。\n",
        "     * 慢任务标记（0/1），由 {@code SlowTaskDetector} 定期扫描并标记。\n",
    ),
    # JobHistory.java 行 46
    (
        "ydsz-backend/ydsz-cronjob/ydsz-cronjob-domain/src/main/java/com/njydsz/cronjob/domain/entity/JobHistory.java",
        "    /** 变更类型: CREATE / UPDATE / DELETE（合并自原 ydsz_job_version_history） */\n",
        "    /** 变更类型: CREATE / UPDATE / DELETE */\n",
    ),
    # JobLogMapper.java 行 85 (P2-1-merge LEFT JOIN)
    (
        "ydsz-backend/ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobLogMapper.java",
        "     * <p>P2-1-merge: 原通过 LEFT JOIN ydsz_job_slow_log 过滤已记录的 log_id,\n",
        "     * <p>原通过 LEFT JOIN 慢日志表过滤已记录 log_id，\n",
    ),
    # JobLogMapper.java 行 113-115
    (
        "ydsz-backend/ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobLogMapper.java",
        "     * P2-1-merge: 标记指定日志为慢任务（is_slow=1, 快照 slow_threshold_ms）。\n     * <p>替代原 SlowTaskDetector 向 ydsz_job_slow_log 插入记录的逻辑。\n",
        "     * 标记指定日志为慢任务（is_slow=1, 快照 slow_threshold_ms）。\n",
    ),
    # JobHistoryService.java 行 78
    (
        "ydsz-backend/ydsz-cronjob/ydsz-cronjob-server/src/main/java/com/njydsz/cronjob/server/service/JobHistoryService.java",
        "     * 记录版本变更快照（合并自原 JobVersionService.recordVersionChange）。\n",
        "     * 记录版本变更快照。\n",
    ),
    # JobServiceImpl.java 行 119
    (
        "ydsz-backend/ydsz-cronjob/ydsz-cronjob-server/src/main/java/com/njydsz/cronjob/server/service/impl/JobServiceImpl.java",
        "     * 同时合并了原 JobVersionService 的版本变更记录能力（recordVersionChange），\n",
        "     * 同时记录版本变更快照，\n",
    ),
    # JobHistoryServiceImpl.java 行 29
    (
        "ydsz-backend/ydsz-cronjob/ydsz-cronjob-server/src/main/java/com/njydsz/cronjob/server/service/impl/JobHistoryServiceImpl.java",
        " * 任务配置历史版本服务实现（P1-6 任务版本管理，合并原 JobVersionService）。\n",
        " * 任务配置历史版本服务实现（P1-6 任务版本管理）。\n",
    ),
    # SlowTaskDetector.java javadoc 清理 P2-1-merge 变更说明整段
    (
        "ydsz-backend/ydsz-cronjob/ydsz-cronjob-server/src/main/java/com/njydsz/cronjob/server/core/SlowTaskDetector.java",
        """/**
 * 慢任务诊断扫描器（P6-3, P2-1-merge 重构）。
 *
 * <p>仅当 {@code ydsz.cronjob.leader.enabled=true} 且当前节点是 Leader 时启用。
 * 定时（默认 30s）扫描 {@code ydsz_job_log} 中已结束（SUCCESS/FAILED/TIMEOUT）
 * 且耗时超过 {@code ydsz_job.slow_threshold_ms} 的记录，标记 {@code is_slow=1}。
 *
 * <h3>P2-1-merge 变更说明</h3>
 * <p>原实现将慢任务记录写入独立的 {@code ydsz_job_slow_log} 表。
 * 现已合并到 {@code ydsz_job_log.is_slow} 字段（0/1）和 {@code slow_threshold_ms} 快照，
 * 消除了独立表及 LEFT JOIN 幂等检查。查询慢任务直接通过部分索引 {@code idx_pjl_slow} 完成。
 *
 * <h3>设计要点</h3>
""",
        """/**
 * 慢任务诊断扫描器。
 *
 * <p>仅当 {@code ydsz.cronjob.leader.enabled=true} 且当前节点是 Leader 时启用。
 * 定时（默认 30s）扫描 {@code ydsz_job_log} 中已结束（SUCCESS/FAILED/TIMEOUT）
 * 且耗时超过 {@code ydsz_job.slow_threshold_ms} 的记录，标记 {@code is_slow=1}。
 *
 * <h3>设计要点</h3>
""",
    ),
    # JobPostDTO.java 行 69
    (
        "ydsz-backend/ydsz-cronjob/ydsz-cronjob-domain/src/main/java/com/njydsz/cronjob/domain/dto/post/JobPostDTO.java",
        '@Schema(description = "慢任务阈值（毫秒，P6-3）：null 不检测慢任务；执行耗时超过此值时由 SlowTaskDetector 标记 JobLog.is_slow=1")\n',
        '@Schema(description = "慢任务阈值（毫秒）：null 不检测慢任务；执行耗时超过此值时由 SlowTaskDetector 标记 JobLog.is_slow=1")\n',
    ),
    # JobPutDTO.java 行 73
    (
        "ydsz-backend/ydsz-cronjob/ydsz-cronjob-domain/src/main/java/com/njydsz/cronjob/domain/dto/put/JobPutDTO.java",
        '@Schema(description = "慢任务阈值（毫秒，P6-3）：null 不检测慢任务；执行耗时超过此值时由 SlowTaskDetector 标记 JobLog.is_slow=1")\n',
        '@Schema(description = "慢任务阈值（毫秒）：null 不检测慢任务；执行耗时超过此值时由 SlowTaskDetector 标记 JobLog.is_slow=1")\n',
    ),
    # JobSaveDTO.java 行 80
    (
        "ydsz-backend/ydsz-cronjob/ydsz-cronjob-domain/src/main/java/com/njydsz/cronjob/domain/dto/JobSaveDTO.java",
        '@Schema(description = "慢任务阈值（毫秒，P6-3）：null 不检测慢任务；执行耗时超过此值时由 SlowTaskDetector 标记 JobLog.is_slow=1")\n',
        '@Schema(description = "慢任务阈值（毫秒）：null 不检测慢任务；执行耗时超过此值时由 SlowTaskDetector 标记 JobLog.is_slow=1")\n',
    ),
]

for rel, old, new in java_fixes:
    p = ROOT / rel
    if not p.exists():
        print(f"[WARN] 文件不存在: {rel}")
        continue
    text = read(p)
    if old in text:
        text = text.replace(old, new)
        write(p, text)
    else:
        print(f"[SKIP] 已在 {rel} 中未匹配: {old[:60]}...")

# ============================================================
# 4) ydsz-cronjob/README.md
# ============================================================
readme = ROOT / "ydsz-backend/ydsz-cronjob/README.md"
text = read(readme)
old = "| | `ydsz_job_version_history` | 任务版本历史（回滚） |\n"
if old in text:
    text = text.replace(old, "")
    print("[OK] README 删除 ydsz_job_version_history 行")
old = "| | `ydsz_job_slow_log` | 慢执行记录 |\n"
if old in text:
    text = text.replace(old, "")
    print("[OK] README 删除 ydsz_job_slow_log 行")
# 表数量从 20 改为 18
text = text.replace("**20 张表**", "**18 张表**")
text = text.replace("任务定义/调度/执行/日志/告警/告警规则/版本历史/SLA/节点/分片关系/统计/产物/胶水代码/租户配额/DAG",
                    "任务定义/调度/执行/日志/告警/告警规则/历史/节点/分片关系/统计/产物/胶水代码/租户配额/DAG")
write(readme, text)

print("\n[DONE] 清理完成")
