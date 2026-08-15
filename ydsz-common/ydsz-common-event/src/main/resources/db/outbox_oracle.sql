-- ============================================================================
-- ydsz-cloud: Outbox 事务表 DDL (Oracle 19c+)
-- ============================================================================
-- 功能：Transactional Outbox 模式存储领域事件，保障消息可靠投递
-- 规范：
--   - CHECK 约束校验状态字段
--   - Snowflake ID (VARCHAR2) 应用层生成
--   - 禁止物理外键，逻辑外键加索引
--   - 布尔型软删除 (deleted)
-- 注意：Oracle 不支持部分索引（Partial Index），使用标准索引替代
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 主表
-- ----------------------------------------------------------------------------

CREATE TABLE ydsz_outbox (
    -- ========== 业务主键 ==========
    id                  VARCHAR2(64)    NOT NULL,  -- Snowflake ID（应用层生成）

    -- ========== 聚合根信息 ==========
    aggregate_type      VARCHAR2(128)   NOT NULL,  -- 聚合根类型
    aggregate_id        VARCHAR2(128)   NOT NULL,  -- 聚合根 ID

    -- ========== 事件信息 ==========
    event_type          VARCHAR2(128)   NOT NULL,  -- 事件类型
    payload             CLOB            NOT NULL,  -- 事件负载（JSON，最大 4MB）
    headers             CLOB,                      -- 扩展头（JSON，Oracle 21c+ 可用 JSON 类型）

    -- ========== 投递控制 ==========
    status              VARCHAR2(32)    NOT NULL
                                        DEFAULT 'PENDING'
                                        CHECK (status IN ('PENDING','PROCESSING','SENT','DEAD_LETTER')),
    retry_count         NUMBER(10)      NOT NULL DEFAULT 0,    -- 当前重试次数
    max_retries         NUMBER(10)      NOT NULL DEFAULT 5,    -- 最大重试次数
    next_retry_at       TIMESTAMP(3),                           -- 下次重试时间
    priority            NUMBER(10)      NOT NULL DEFAULT 5,    -- 优先级（0-9）
    error_message       CLOB,                                    -- 错误信息

    -- ========== 上下文 ==========
    tenant_id           VARCHAR2(64),    -- 租户 ID
    trace_id            VARCHAR2(64),    -- 链路追踪 ID
    deduplication_id    VARCHAR2(64),    -- 幂等去重 ID

    -- ========== Schema ==========
    schema_version      VARCHAR2(32)    NOT NULL DEFAULT 'v1.0.0',
    content_type        VARCHAR2(128),   -- 内容类型

    -- ========== 时间戳 ==========
    created_at          TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at             TIMESTAMP(3),    -- 投递成功时间
    deleted             NUMBER(1)       NOT NULL DEFAULT 0
                                        CHECK (deleted IN (0, 1)),  -- 软删除标记

    -- ========== 主键 ==========
    CONSTRAINT pk_ydsz_outbox PRIMARY KEY (id)
);

-- ----------------------------------------------------------------------------
-- 2. 注释
-- ----------------------------------------------------------------------------

COMMENT ON TABLE ydsz_outbox IS 'Transactional Outbox table for reliable domain event delivery';
COMMENT ON COLUMN ydsz_outbox.id IS 'Message unique identifier (Snowflake ID)';
COMMENT ON COLUMN ydsz_outbox.aggregate_type IS 'Aggregate root type (e.g., Order, User)';
COMMENT ON COLUMN ydsz_outbox.aggregate_id IS 'Aggregate root ID';
COMMENT ON COLUMN ydsz_outbox.event_type IS 'Event type (e.g., OrderCreated)';
COMMENT ON COLUMN ydsz_outbox.payload IS 'Event payload JSON (max 4MB)';
COMMENT ON COLUMN ydsz_outbox.headers IS 'Extension headers (JSON)';
COMMENT ON COLUMN ydsz_outbox.status IS 'Status: PENDING/PROCESSING/SENT/DEAD_LETTER';
COMMENT ON COLUMN ydsz_outbox.retry_count IS 'Current retry count';
COMMENT ON COLUMN ydsz_outbox.max_retries IS 'Max retries before dead letter';
COMMENT ON COLUMN ydsz_outbox.next_retry_at IS 'Next retry time (exponential backoff)';
COMMENT ON COLUMN ydsz_outbox.priority IS 'Priority (0-9, 9=highest)';
COMMENT ON COLUMN ydsz_outbox.error_message IS 'Last failure error message';
COMMENT ON COLUMN ydsz_outbox.tenant_id IS 'Tenant ID for multi-tenancy';
COMMENT ON COLUMN ydsz_outbox.trace_id IS 'W3C traceparent trace ID';
COMMENT ON COLUMN ydsz_outbox.deduplication_id IS 'Idempotent deduplication ID';
COMMENT ON COLUMN ydsz_outbox.schema_version IS 'Event schema version';
COMMENT ON COLUMN ydsz_outbox.content_type IS 'Content type (RFC 6838 MIME)';
COMMENT ON COLUMN ydsz_outbox.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN ydsz_outbox.updated_at IS 'Last update timestamp';
COMMENT ON COLUMN ydsz_outbox.sent_at IS 'Successful delivery timestamp';
COMMENT ON COLUMN ydsz_outbox.deleted IS 'Soft delete flag (0=active, 1=deleted)';

-- ----------------------------------------------------------------------------
-- 3. 索引
-- ----------------------------------------------------------------------------

-- 轮询待投递消息索引
CREATE INDEX idx_ydsz_outbox_pending
    ON ydsz_outbox (status, priority DESC, created_at ASC);

-- 下次重试时间索引
CREATE INDEX idx_ydsz_outbox_retry
    ON ydsz_outbox (status, next_retry_at);

-- PROCESSING 超时回收索引
CREATE INDEX idx_ydsz_outbox_processing
    ON ydsz_outbox (status, updated_at);

-- 已投递清理索引
CREATE INDEX idx_ydsz_outbox_sent_at
    ON ydsz_outbox (status, sent_at);

-- 租户隔离索引
CREATE INDEX idx_ydsz_outbox_tenant
    ON ydsz_outbox (tenant_id, status);

-- 幂等去重索引
CREATE INDEX idx_ydsz_outbox_dedup
    ON ydsz_outbox (deduplication_id, status);

-- 聚合根查询索引
CREATE INDEX idx_ydsz_outbox_aggregate
    ON ydsz_outbox (aggregate_type, aggregate_id, created_at DESC);

-- ----------------------------------------------------------------------------
-- 4. 触发器：自动更新 updated_at（Oracle 标准做法）
-- ----------------------------------------------------------------------------

CREATE OR REPLACE TRIGGER trg_ydsz_outbox_updated_at
BEFORE UPDATE ON ydsz_outbox
FOR EACH ROW
BEGIN
    :NEW.updated_at := CURRENT_TIMESTAMP;
END;
/
