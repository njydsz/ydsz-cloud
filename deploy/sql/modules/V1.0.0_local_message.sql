-- ============================================================
-- P0-4: 本地消息表（分布式事务 - 本地消息表模式）
-- ------------------------------------------------------------
-- 用于实现可靠消息投递：
--   1. 业务事务内将消息写入本地消息表（与业务数据在同一事务中提交）
--   2. 事务提交后，异步扫描消息表并通过 Feign/MQ 投递消息
--   3. 投递成功后标记为 DONE，失败重试至达到最大重试次数
-- ============================================================

CREATE TABLE IF NOT EXISTS pmis_local_message (
    id              BIGSERIAL       PRIMARY KEY,
    message_id      VARCHAR(64)     NOT NULL UNIQUE,  -- 消息唯一标识（UUID，用于幂等去重）
    message_type    VARCHAR(64)     NOT NULL,          -- 消息类型（NOTIFICATION / SYNC_PROJECT 等）
    target_service  VARCHAR(128)    NOT NULL,          -- 目标服务名
    target_endpoint VARCHAR(256)    NOT NULL,          -- 目标接口路径
    payload         TEXT            NOT NULL,          -- 消息体 JSON
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING',  -- PENDING / DONE / FAILED / DEAD
    retry_count     INTEGER         NOT NULL DEFAULT 0,
    max_retries     INTEGER         NOT NULL DEFAULT 5,
    next_retry_at   TIMESTAMP       NOT NULL DEFAULT NOW(),
    last_error      TEXT,
    tenant_id       VARCHAR(32),
    trace_id        VARCHAR(64),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- 索引：按状态和下次重试时间查询（调度器扫描使用）
CREATE INDEX IF NOT EXISTS idx_local_message_status_retry
    ON pmis_local_message (status, next_retry_at)
    WHERE status = 'PENDING' OR status = 'FAILED';

-- 索引：按消息类型查询
CREATE INDEX IF NOT EXISTS idx_local_message_type
    ON pmis_local_message (message_type);

-- 索引：按创建时间查询（清理历史数据使用）
CREATE INDEX IF NOT EXISTS idx_local_message_created
    ON pmis_local_message (created_at);

COMMENT ON TABLE  pmis_local_message IS '本地消息表（分布式事务 - 本地消息表模式）';
COMMENT ON COLUMN pmis_local_message.message_id     IS '消息唯一标识（UUID，用于消费端幂等去重）';
COMMENT ON COLUMN pmis_local_message.message_type   IS '消息类型（NOTIFICATION / SYNC_PROJECT / SYNC_USER 等）';
COMMENT ON COLUMN pmis_local_message.target_service IS '目标服务名（如 ydsz-pmis-message）';
COMMENT ON COLUMN pmis_local_message.target_endpoint IS '目标接口路径（如 /message/send）';
COMMENT ON COLUMN pmis_local_message.payload        IS '消息体 JSON';
COMMENT ON COLUMN pmis_local_message.status         IS '状态: PENDING / DONE / FAILED / DEAD';
COMMENT ON COLUMN pmis_local_message.retry_count    IS '已重试次数';
COMMENT ON COLUMN pmis_local_message.max_retries    IS '最大重试次数（默认 5）';
COMMENT ON COLUMN pmis_local_message.next_retry_at  IS '下次重试时间（指数退避）';
COMMENT ON COLUMN pmis_local_message.last_error     IS '最后错误信息';
