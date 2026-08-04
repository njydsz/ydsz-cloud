-- =====================================================================
-- V1.0.1 - 慢 SQL 治理：索引补充 + 大表分区（P1-6）
--
-- 说明：
--   1. 本脚本为增量版本示例，演示"只追加不修改"的版本化规则
--   2. 索引依据 20 个业务域的高频查询模式补充（代码审计整理）
--   3. 分区表改造示例：ydsz_flow_history 按月 RANGE 分区
--
-- ROLLBACK:
--   DROP INDEX IF EXISTS idx_project_opportunity_status;
--   ...
--   DROP TABLE IF EXISTS ydsz_flow_history;
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 商机/立项/合同高频查询索引
-- ---------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_project_opportunity_status
    ON ydsz_project_opportunity (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_project_initiation_status
    ON ydsz_project_initiation (status, initiated_at DESC);

CREATE INDEX IF NOT EXISTS idx_project_contract_project_id
    ON ydsz_project_contract (project_id);

CREATE INDEX IF NOT EXISTS idx_project_contract_status
    ON ydsz_project_contract (status, sign_date DESC);

-- ---------------------------------------------------------------------
-- 2. EVM / 成本 / 收入分析查询索引（报表场景）
-- ---------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_evm_measure_project_month
    ON ydsz_evm_measure (project_id, measure_month);

CREATE INDEX IF NOT EXISTS idx_cost_allocation_project
    ON ydsz_cost_allocation (project_id, period);

CREATE INDEX IF NOT EXISTS idx_project_revenue_project
    ON ydsz_project_revenue (project_id, recognize_date);

-- ---------------------------------------------------------------------
-- 3. 工作流实例/任务高频查询索引
-- ---------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_flow_instance_status
    ON ydsz_flow_instance (flow_code, status, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_flow_task_assignee
    ON ydsz_flow_task (assignee_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_flow_task_instance_id
    ON ydsz_flow_task (instance_id);

-- ---------------------------------------------------------------------
-- 4. 消息中心：按用户查未读
-- ---------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_msg_log_recipient
    ON ydsz_msg_log (recipient_id, status, created_at DESC);

-- ---------------------------------------------------------------------
-- 5. 定时任务：调度查询
-- ---------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_job_definition_group
    ON ydsz_job_definition (job_group, status);

-- =====================================================================
-- 大表分区改造：ydsz_flow_history 按月 RANGE 分区
-- ---------------------------------------------------------------------
-- 注意：实际执行时需先评估存量数据，若表已有数据需使用
--   CREATE TABLE ... PARTITION OF 方式建分区并迁移，或新建分区表后
--   通过 INSERT INTO ... SELECT 迁移。生产环境建议：
--   1. 低峰期创建分区表结构（新表 ydsz_flow_history_v2）
--   2. 使用 pg_partman 或 CronJob 自动创建未来分区
--   3. 数据迁移完成后原子切换表名
-- =====================================================================
CREATE TABLE IF NOT EXISTS ydsz_flow_history (
    id            BIGINT GENERATED ALWAYS AS IDENTITY,
    instance_id   BIGINT NOT NULL,
    flow_code     VARCHAR(64) NOT NULL,
    node_key      VARCHAR(64),
    action        VARCHAR(32) NOT NULL,          -- APPROVE / REJECT / TRANSFER / DELEGATE ...
    operator_id   BIGINT,
    comment       TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- 当月分区示例（后续按月由定时任务自动创建）
-- CREATE TABLE IF NOT EXISTS ydsz_flow_history_y2026m08
--     PARTITION OF ydsz_flow_history
--     FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
--
-- 索引：分区表索引需在分区上分别创建
-- CREATE INDEX IF NOT EXISTS idx_flow_history_flow_code ON ydsz_flow_history_y2026m08 (flow_code);
-- CREATE INDEX IF NOT EXISTS idx_flow_history_instance_id ON ydsz_flow_history_y2026m08 (instance_id);

-- ---------------------------------------------------------------------
-- 归档建议（配合 pg_partman）：
--   CREATE EXTENSION IF NOT EXISTS pg_partman;
--   SELECT partman.create_parent('public.ydsz_flow_history', 'created_at', 'native', 'monthly');
--   UPDATE partman.part_config SET retention = '12 month', retention_keep_table = false
--     WHERE parent_table = 'public.ydsz_flow_history';
-- =====================================================================
