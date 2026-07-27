"""补丁脚本：修复 cleanup-p2-migration-residue.py 漏掉的内容。"""
import pathlib

ROOT = pathlib.Path("d:/Code/ydsz/ydsz-pmis")

def read(p):
    return pathlib.Path(p).read_text(encoding="utf-8")

def write(p, content):
    pathlib.Path(p).write_text(content, encoding="utf-8")
    print(f"[OK] 写入 {p}")

# ============================================================
# 1) V1.0.0.sql: 删除 ydsz_job_slow_log 表
# ============================================================
sql = ROOT / "deploy/sql/V1.0.0.sql"
content = read(sql)

old_slow = """-- ============================================================================
-- [P6-3] 慢任务诊断日志表 ydsz_job_slow_log
-- ----------------------------------------------------------------------------
-- 当任务执行耗时超过 ydsz_job.slow_threshold_ms 时，自动记录到本表。
-- 与 ydsz_job_log 的区别：
--   - job_log 记录全部执行（RUNNING/SUCCESS/FAILED/TIMEOUT），用于审计
--   - slow_log 仅记录慢执行，用于性能趋势分析与优化决策
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
    created_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
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

if old_slow in content:
    content = content.replace(old_slow, "")
    print("[OK] 删除 ydsz_job_slow_log 表块 (V1.0.0.sql)")
else:
    print("[WARN] V1.0.0.sql ydsz_job_slow_log 块未匹配")

# (b) ydsz_job_version_history 表
old_ver = """-- ============================================================================
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
"""

if old_ver in content:
    content = content.replace(old_ver, "")
    print("[OK] 删除 ydsz_job_version_history 表 (V1.0.0.sql)")
else:
    print("[WARN] V1.0.0.sql ydsz_job_version_history 块未匹配")

write(sql, content)

# ============================================================
# 2) JobLog.java: is_slow 和 slowThresholdMs 字段 javadoc
# ============================================================
job_log = ROOT / "ydsz-backend/ydsz-cronjob/ydsz-cronjob-domain/src/main/java/com/njydsz/cronjob/domain/entity/JobLog.java"
text = read(job_log)

old_is_slow = """    /**
     * 慢任务标记（P2-1-merge：合并自 ydsz_job_slow_log）。
     *
     * <p>0=非慢 / 1=慢。由 {@code SlowTaskDetector} 在任务执行完成后
     * 根据 {@code slow_threshold_ms} 判定并标记，替代原独立 slow_log 表。
     */
    private Integer isSlow;
    /**
     * 慢任务阈值快照（毫秒，P2-1-merge）。
     *
     * <p>执行时从 {@code ydsz_job.slow_threshold_ms} 快照到日志记录，
     * NULL=未配置慢任务检测。快照保留执行时的阈值，避免后续修改 job 配置影响历史判定。
     */
    private Long slowThresholdMs;"""

new_is_slow = """    /**
     * 慢任务标记（0=非慢 / 1=慢）。
     *
     * <p>由 {@code SlowTaskDetector} 在任务执行完成后根据 {@code slow_threshold_ms} 判定并标记。
     */
    private Integer isSlow;
    /**
     * 慢任务阈值快照（毫秒）。
     *
     * <p>执行时从 {@code ydsz_job.slow_threshold_ms} 快照到日志记录，
     * NULL=未配置慢任务检测。快照保留执行时的阈值，避免后续修改 job 配置影响历史判定。
     */
    private Long slowThresholdMs;"""

if old_is_slow in text:
    text = text.replace(old_is_slow, new_is_slow)
    write(job_log, text)
else:
    print("[WARN] JobLog.java javadoc 未匹配")

# ============================================================
# 3) JobLogMapper.java: markSlow 方法 javadoc
# ============================================================
mapper = ROOT / "ydsz-backend/ydsz-cronjob/ydsz-cronjob-infra/src/main/java/com/njydsz/cronjob/infra/mapper/JobLogMapper.java"
text = read(mapper)

old_mark = """    /**
     * P2-1-merge: 标记指定日志为慢任务（is_slow=1, 快照 slow_threshold_ms）。
     *
     * <p>替代原 SlowTaskDetector 向 ydsz_job_slow_log 插入记录的逻辑。
     *
     * @param logId            任务日志 ID
     * @param slowThresholdMs  慢任务阈值快照（毫秒）
     * @return 受影响行数
     */"""

new_mark = """    /**
     * 标记指定日志为慢任务（is_slow=1, 快照 slow_threshold_ms）。
     *
     * @param logId            任务日志 ID
     * @param slowThresholdMs  慢任务阈值快照（毫秒）
     * @return 受影响行数
     */"""

if old_mark in text:
    text = text.replace(old_mark, new_mark)
    write(mapper, text)
else:
    print("[WARN] JobLogMapper markSlow javadoc 未匹配")

print("\n[DONE] 补丁完成")
