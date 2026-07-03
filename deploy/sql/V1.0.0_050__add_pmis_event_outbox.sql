-- ============================================================
-- V1.0.0_050  P2-1 可靠消息投递 — 事件 Outbox 表
-- ============================================================
-- 说明：本地消息表（Outbox Pattern），保证业务事务与消息投递的最终一致性。
--   工作流关键事件（任务创建/通过/驳回/转办/委派/催办/超时/实例终止等）
--   在主事务内写入本表（同表同事务原子性），事务提交后由扫描任务异步投递
--   到 NotificationClient / NotificationPushClient / MessageFeignClient。
--
-- 工作流：
--   1. 主事务内：INSERT outbox (status=PENDING)
--   2. 事务提交后：@TransactionalEventListener(AFTER_COMMIT) 触发，但实际投递由扫描任务做
--   3. 扫描任务（@Scheduled 30s）：查 status=PENDING AND next_retry_at <= NOW()
--      → 调 Feign 投递 → 成功标 SENT，失败 retry_count++，超过阈值标 DEAD
--   4. DEAD 行由管理员人工重投（后台入口）
--
-- 状态机：PENDING → SENT（成功）/ DEAD（超过最大重试次数）
-- ============================================================

CREATE TABLE IF NOT EXISTS pmis_event_outbox (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           BIGINT          NOT NULL DEFAULT 1,
    -- 事件标识
    event_type          VARCHAR(64)     NOT NULL,               -- TASK_CREATED / TASK_COMPLETED / INSTANCE_TERMINATED 等
    biz_type            VARCHAR(64)     NOT NULL,               -- 业务类型: WORKFLOW_TASK / WORKFLOW_INSTANCE / WORKFLOW_CC
    biz_id              BIGINT,                                 -- 业务 ID（taskId / instanceId）
    instance_id         BIGINT,                                 -- 流程实例 ID（便于按实例查询）
    task_id             BIGINT,                                 -- 任务 ID（便于按任务查询）
    -- 消息内容
    payload             TEXT            NOT NULL,               -- JSON 载荷（接收方解析）
    target_channels     VARCHAR(128),                           -- 投递通道: IN_APP / IM / EMAIL / SMS（逗号分隔，空表示按 event_type 默认）
    target_user_ids     VARCHAR(512),                           -- 接收用户 ID 列表（逗号分隔，空表示由 payload 自行决定）
    -- 投递状态
    status              VARCHAR(16)     NOT NULL DEFAULT 'PENDING', -- PENDING / SENT / DEAD
    retry_count         INT             NOT NULL DEFAULT 0,
    max_retries         INT             NOT NULL DEFAULT 5,     -- 默认最大重试 5 次
    next_retry_at       TIMESTAMP       NOT NULL DEFAULT NOW(), -- 下次重试时间（指数退避）
    sent_at             TIMESTAMP,                              -- 实际投递成功时间
    error_msg           VARCHAR(1024),                          -- 最近一次失败原因
    -- 链路追踪
    provider_trace_id   VARCHAR(64),
    -- 审计字段
    created_by          BIGINT,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    deleted             SMALLINT        NOT NULL DEFAULT 0
);

COMMENT ON TABLE pmis_event_outbox IS '事件 Outbox 表 — 可靠消息投递（P2-1 阶段一）';
COMMENT ON COLUMN pmis_event_outbox.event_type IS '事件类型: TASK_CREATED / TASK_COMPLETED / INSTANCE_TERMINATED 等';
COMMENT ON COLUMN pmis_event_outbox.biz_type IS '业务类型: WORKFLOW_TASK / WORKFLOW_INSTANCE / WORKFLOW_CC';
COMMENT ON COLUMN pmis_event_outbox.payload IS 'JSON 载荷，由接收方解析';
COMMENT ON COLUMN pmis_event_outbox.target_channels IS '投递通道: IN_APP / IM / EMAIL / SMS（逗号分隔）';
COMMENT ON COLUMN pmis_event_outbox.target_user_ids IS '接收用户 ID 列表（逗号分隔）';
COMMENT ON COLUMN pmis_event_outbox.status IS '投递状态: PENDING 待投递 / SENT 已投递 / DEAD 死信';
COMMENT ON COLUMN pmis_event_outbox.retry_count IS '已重试次数';
COMMENT ON COLUMN pmis_event_outbox.max_retries IS '最大重试次数（默认 5）';
COMMENT ON COLUMN pmis_event_outbox.next_retry_at IS '下次重试时间（指数退避：30s/60s/120s/300s/600s）';
COMMENT ON COLUMN pmis_event_outbox.error_msg IS '最近一次失败原因';

-- 索引：扫描任务主查询（status=PENDING AND next_retry_at <= NOW()）
CREATE INDEX IF NOT EXISTS idx_peo_pending_scan
    ON pmis_event_outbox(status, next_retry_at)
    WHERE deleted = 0 AND status = 'PENDING';

-- 索引：按业务类型+业务 ID 查询（幂等校验：同一业务事件不重复入箱）
CREATE INDEX IF NOT EXISTS idx_peo_biz
    ON pmis_event_outbox(biz_type, biz_id)
    WHERE deleted = 0;

-- 索引：按实例查询（流程实例下所有事件）
CREATE INDEX IF NOT EXISTS idx_peo_instance
    ON pmis_event_outbox(instance_id)
    WHERE deleted = 0 AND instance_id IS NOT NULL;

-- 索引：按 trace_id 查询（链路追踪）
CREATE INDEX IF NOT EXISTS idx_peo_trace
    ON pmis_event_outbox(provider_trace_id)
    WHERE deleted = 0 AND provider_trace_id IS NOT NULL;
