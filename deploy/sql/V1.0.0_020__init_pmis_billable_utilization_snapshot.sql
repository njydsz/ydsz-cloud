-- ====================================================================
-- V1.0.0_020  可计费利用率快照表
--
--  说明：可计费利用率（BillableUtilization）由 scheduler 每日计算后
--        持久化到本表，驾驶舱 / 排行榜 / 趋势分析均直接读快照，
--        避免每次实时聚合 pmis_execution_time_entry 大表。
--
--  写入路径：ydsz-pmis-scheduler 模块的
--           BillableUtilizationJobHandler#execute
--  读取路径：CockpitReportService / AdvancedReportService /
--           BillableUtilizationController
--
--  键设计：(period, employee_id) 唯一，
--         PostgreSQL UPSERT ON CONFLICT 保证幂等。
-- ====================================================================

CREATE TABLE IF NOT EXISTS pmis_billable_utilization_snapshot (
    id               BIGSERIAL PRIMARY KEY,
    period           VARCHAR(7)  NOT NULL,                          -- yyyy-MM
    employee_id      BIGINT      NOT NULL,
    employee_name    VARCHAR(64) DEFAULT '',
    level_code       VARCHAR(16) DEFAULT '',                        -- L1-L18
    department       VARCHAR(64) DEFAULT '',
    total_hours      NUMERIC(12,2) NOT NULL DEFAULT 0,             -- 全部工时
    billable_hours   NUMERIC(12,2) NOT NULL DEFAULT 0,             -- 可计费
    overtime_hours   NUMERIC(12,2) NOT NULL DEFAULT 0,
    leave_hours      NUMERIC(12,2) NOT NULL DEFAULT 0,
    training_hours   NUMERIC(12,2) NOT NULL DEFAULT 0,
    bench_hours      NUMERIC(12,2) NOT NULL DEFAULT 0,             -- 闲置
    utilization_pct  NUMERIC(6,4)  NOT NULL DEFAULT 0,             -- 0-1
    grade            VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',        -- EXCELLENT/GOOD/NORMAL/WARN/CRITICAL
    range_from       DATE         NOT NULL,
    range_to         DATE         NOT NULL,
    snapshot_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source           VARCHAR(16)  NOT NULL DEFAULT 'SCHEDULER',    -- SCHEDULER / MANUAL / RETRO
    tenant_id        BIGINT       DEFAULT 0,
    deleted          SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uq_billable_period_emp UNIQUE (period, employee_id, deleted)
);

CREATE INDEX IF NOT EXISTS idx_billable_period
    ON pmis_billable_utilization_snapshot (period, deleted);

CREATE INDEX IF NOT EXISTS idx_billable_department
    ON pmis_billable_utilization_snapshot (department, period)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_billable_grade
    ON pmis_billable_utilization_snapshot (grade, period)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_billable_range
    ON pmis_billable_utilization_snapshot (range_from, range_to)
    WHERE deleted = 0;

COMMENT ON TABLE  pmis_billable_utilization_snapshot IS '可计费利用率快照（scheduler 每日计算）';
COMMENT ON COLUMN pmis_billable_utilization_snapshot.period IS '统计周期 yyyy-MM';
COMMENT ON COLUMN pmis_billable_utilization_snapshot.utilization_pct IS '利用率 0-1';
COMMENT ON COLUMN pmis_billable_utilization_snapshot.grade IS '考核等级 EXCELLENT/GOOD/NORMAL/WARN/CRITICAL';
