-- ============================================================
-- V1.0.0_046  P0-1 BPMN 事件运行时 — 事件订阅表
-- ============================================================
-- 说明：为 BPMN 错误事件(errorEvent)和消息事件(messageEvent)提供运行时支持。
--   当流程推进到"事件捕获节点"(intermediateCatchEvent / boundaryEvent)时，
--   在本表插入一行 WAITING 记录，流程进入等待状态（不创建人工任务）。
--   外部系统通过 correlateMessage / throwError API 触发事件，
--   匹配到 WAITING 订阅后标记 COMPLETED 并推进流程到下游节点。
--
-- 事件类型：
--   MESSAGE — 消息事件（中间捕获 / 边界），通过 messageName + correlationKey 匹配
--   ERROR   — 错误事件（边界），通过 errorCode 匹配，由 serviceTask 或 API 抛出
--   SIGNAL  — 信号事件（预留，暂不实现运行时）
-- ============================================================

CREATE TABLE IF NOT EXISTS pmis_flow_event_subscription (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           BIGINT          NOT NULL DEFAULT 1,
    instance_id         BIGINT          NOT NULL,
    definition_id       BIGINT          NOT NULL,
    flow_code           VARCHAR(64)     NOT NULL,
    node_code           VARCHAR(64)     NOT NULL,
    node_name           VARCHAR(128),
    event_type          VARCHAR(16)     NOT NULL,   -- MESSAGE / ERROR / SIGNAL
    event_ref           VARCHAR(128),               -- messageRef / errorRef / signalRef
    correlation_key     VARCHAR(256),               -- 消息关联键（业务标识，可空）
    boundary_task_id    BIGINT,                     -- 边界事件关联的 userTask ID
    subscription_status VARCHAR(16)     NOT NULL DEFAULT 'WAITING', -- WAITING / COMPLETED / CANCELLED
    payload             TEXT,                       -- 触发时携带的业务数据 JSON
    triggered_at        TIMESTAMP,                  -- 实际触发时间
    trigger_source      VARCHAR(128),               -- 触发来源（API / SERVICE_TASK / BOUNDARY）
    cancel_reason       VARCHAR(256),               -- 取消原因
    -- 审计字段
    status              VARCHAR(16)     NOT NULL DEFAULT 'ENABLED',
    created_by          BIGINT,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    deleted             SMALLINT        NOT NULL DEFAULT 0,
    provider_trace_id   VARCHAR(64)
);

COMMENT ON TABLE pmis_flow_event_subscription IS '工作流事件订阅表 — BPMN 错误/消息事件运行时';
COMMENT ON COLUMN pmis_flow_event_subscription.event_type IS '事件类型: MESSAGE 消息 / ERROR 错误 / SIGNAL 信号';
COMMENT ON COLUMN pmis_flow_event_subscription.event_ref IS '事件引用标识（messageRef / errorRef / signalRef）';
COMMENT ON COLUMN pmis_flow_event_subscription.correlation_key IS '消息关联键，用于业务级消息匹配';
COMMENT ON COLUMN pmis_flow_event_subscription.boundary_task_id IS '边界事件关联的 userTask ID（中间事件为 NULL）';
COMMENT ON COLUMN pmis_flow_event_subscription.subscription_status IS '订阅状态: WAITING 等待中 / COMPLETED 已触发 / CANCELLED 已取消';

-- 索引：按实例查询等待中的订阅
CREATE INDEX IF NOT EXISTS idx_pfes_instance
    ON pmis_flow_event_subscription(instance_id)
    WHERE deleted = 0;

-- 索引：按事件类型+引用匹配（消息/错误触发时的主查询）
CREATE INDEX IF NOT EXISTS idx_pfes_event_match
    ON pmis_flow_event_subscription(tenant_id, event_type, event_ref, subscription_status)
    WHERE deleted = 0 AND subscription_status = 'WAITING';

-- 索引：按边界任务查询（userTask 完成时取消关联边界订阅）
CREATE INDEX IF NOT EXISTS idx_pfes_boundary
    ON pmis_flow_event_subscription(boundary_task_id)
    WHERE boundary_task_id IS NOT NULL AND deleted = 0;

-- 索引：按关联键匹配（业务消息精确匹配）
CREATE INDEX IF NOT EXISTS idx_pfes_correlation
    ON pmis_flow_event_subscription(tenant_id, correlation_key, subscription_status)
    WHERE deleted = 0 AND correlation_key IS NOT NULL;
