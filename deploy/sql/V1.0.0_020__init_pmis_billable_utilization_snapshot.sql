-- ====================================================================
-- V1.0.0_020  可计费利用率快照表
--
--  说明：可计费利用率（BillableUtilization）由 scheduler 每日计算后
--        持久化到本表，驾驶舱 / 排行榜 / 趋势分析均直接读快照，
--        避免每次实时聚合 pmis_execution_time_entry 大表。
--
--  写入路径：ydsz-pmis-cronjob 模块的
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

COMMENT ON TABLE  pmis_billable_utilization_snapshot IS '可计费利用率快照表: cronjob 每日计算并持久化,驾驶舱/排行榜/趋势分析均读快照,避免实时聚合大表';
COMMENT ON COLUMN pmis_billable_utilization_snapshot.period IS '统计周期: 格式 yyyy-MM,例如 2026-06';
COMMENT ON COLUMN pmis_billable_utilization_snapshot.employee_id IS '员工 ID';
COMMENT ON COLUMN pmis_billable_utilization_snapshot.employee_name IS '员工姓名（冗余）';
COMMENT ON COLUMN pmis_billable_utilization_snapshot.level_code IS '职级: L1-L18';
COMMENT ON COLUMN pmis_billable_utilization_snapshot.department IS '所属部门: 冗余字段';
COMMENT ON COLUMN pmis_billable_utilization_snapshot.total_hours IS '全部工时(小时)';
COMMENT ON COLUMN pmis_billable_utilization_snapshot.billable_hours IS '可计费工时(小时): 仅 billable=1 的工时';
COMMENT ON COLUMN pmis_billable_utilization_snapshot.overtime_hours IS '加班工时(小时)';
COMMENT ON COLUMN pmis_billable_utilization_snapshot.leave_hours IS '请假工时(小时)';
COMMENT ON COLUMN pmis_billable_utilization_snapshot.training_hours IS '培训工时(小时): training window 30 天上限';
COMMENT ON COLUMN pmis_billable_utilization_snapshot.bench_hours IS 'Bench 闲置工时(小时)';
COMMENT ON COLUMN pmis_billable_utilization_snapshot.utilization_pct IS '可计费利用率: 0-1,billable_hours / total_hours';
COMMENT ON COLUMN pmis_billable_utilization_snapshot.grade IS '考核等级: EXCELLENT 优秀(>=0.9) / GOOD 良好(>=0.75) / NORMAL 正常(>=0.6) / WARN 警告(>=0.4) / CRITICAL 严重(<0.4)';
COMMENT ON COLUMN pmis_billable_utilization_snapshot.range_from IS '统计区间开始日期';
COMMENT ON COLUMN pmis_billable_utilization_snapshot.range_to IS '统计区间结束日期';
COMMENT ON COLUMN pmis_billable_utilization_snapshot.snapshot_at IS '快照生成时间';
COMMENT ON COLUMN pmis_billable_utilization_snapshot.source IS '数据来源: SCHEDULER 定时任务 / MANUAL 手动 / RETRO 追溯重算';
COMMENT ON COLUMN pmis_billable_utilization_snapshot.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_billable_utilization_snapshot.deleted IS '逻辑删除: 0=未删除,1=已删除';
