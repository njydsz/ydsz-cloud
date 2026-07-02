-- =============================================================
-- V1.0.0_029__add_pmis_flow_timer.sql
-- 工作流定时器节点 + 边界定时器
--
-- P1-2: 工作流定时器
--   1. 中间定时器（intermediateTimer）: 流程到达此节点后等待指定时间再继续
--   2. 边界定时器（boundaryTimer）: 挂在 userTask 上，到达时间未完成则触发超时分支
--
-- 设计：
--   - pmis_flow_timer: 定时器实例表（每创建一个定时器节点实例时写入一行）
--   - timer_status: PENDING / FIRED / CANCELLED
--   - fire_at: 到点时间，scheduler 每 30s 扫描一次到点的 PENDING 记录并触发
--   - boundary_task_id: 边界定时器关联的 userTask ID
-- =============================================================

-- -------------------------------------------
-- 1. 定时器实例表
-- -------------------------------------------
DROP TABLE IF EXISTS pmis_flow_timer;
CREATE TABLE pmis_flow_timer (
    id                 BIGSERIAL PRIMARY KEY,
    tenant_id          BIGINT       NOT NULL,
    instance_id        BIGINT       NOT NULL,
    definition_id      BIGINT       NOT NULL,
    flow_code          VARCHAR(64)  NOT NULL,
    node_code          VARCHAR(64)  NOT NULL,
    node_name          VARCHAR(128),
    -- 中间定时器 INTERMEDIATE / 边界定时器 BOUNDARY
    timer_type         VARCHAR(16)  NOT NULL DEFAULT 'INTERMEDIATE',
    -- 边界定时器关联的 userTask
    boundary_task_id   BIGINT,
    -- 触发时间
    fire_at            TIMESTAMP    NOT NULL,
    -- CRON 表达式（可空，仅用于循环定时器）
    cycle              VARCHAR(64),
    -- 状态: PENDING / FIRED / CANCELLED
    timer_status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    -- 触发时间
    fired_at           TIMESTAMP,
    -- 取消原因（userTask 完成时关闭）
    cancel_reason      VARCHAR(255),
    provider_trace_id  VARCHAR(64),
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT     NOT NULL DEFAULT 0
);

COMMENT ON TABLE pmis_flow_timer IS '工作流定时器 - 中间定时器/边界定时器调度表';
COMMENT ON COLUMN pmis_flow_timer.timer_type IS 'INTERMEDIATE 中间定时器 / BOUNDARY 边界定时器';
COMMENT ON COLUMN pmis_flow_timer.timer_status IS 'PENDING 待执行 / FIRED 已触发 / CANCELLED 已取消';
COMMENT ON COLUMN pmis_flow_timer.fire_at IS '到点时间，扫描器按此字段选取待执行记录';

-- 索引：扫描器按 fire_at + status 选取
CREATE INDEX idx_pmis_flow_timer_scan ON pmis_flow_timer (timer_status, fire_at)
    WHERE deleted = 0;
-- 索引：实例维度查询
CREATE INDEX idx_pmis_flow_timer_instance ON pmis_flow_timer (instance_id, deleted);
-- 索引：边界定时器反向关联 userTask
CREATE INDEX idx_pmis_flow_timer_boundary ON pmis_flow_timer (boundary_task_id)
    WHERE boundary_task_id IS NOT NULL;

-- -------------------------------------------
-- 2. FlowNodeDO 扩展字段（流程设计时存到 ext 即可，无需新加列）
-- -------------------------------------------
-- 节点定时器配置由前端设计器写入 FlowNodeDO.ext JSON，格式：
--   {
--     "timerCycle": "PT5M",     // ISO 8601 duration（5 分钟）
--     "timerDate": "2026-07-02T10:00:00",  // 绝对时间
--     "isBoundary": true,       // 是否边界定时器
--     "attachedToUserTask": "node_xxx",  // 边界定时器挂接的 userTask
--     "boundaryAction": "INTERRUPT|CONTINUE"  // 边界触发后行为
--   }
--
-- 解析逻辑由 BpmnXmlParser.parseExtensionElements + FlowNodeDO.ext 处理，
-- 本 SQL 不增加新列，复用 ext JSON。

-- -------------------------------------------
-- 3. 注册定时器扫描器调度任务（PMIS Scheduler）
-- -------------------------------------------
INSERT INTO pmis_job (job_name, job_group, job_key, handler, cron_expression, status, remark, tenant_id)
VALUES (
    '工作流定时器扫描',
    'FLOW',
    'flowTimerScannerJob',
    'flowTimerScannerHandler',
    '0/30 * * * * ?',
    'NORMAL',
    'P1-2: 每 30s 扫描到点定时器，触发中间/边界定时器',
    1
) ON CONFLICT (job_key) DO NOTHING;
