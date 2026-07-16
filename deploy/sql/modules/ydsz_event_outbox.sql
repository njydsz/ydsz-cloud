-- =====================================================
-- ydsz Outbox 事件表 DDL
-- 模块: ydsz-common-event
-- 数据库: PostgreSQL (兼容 MySQL 8.x / Oracle 19c+)
-- =====================================================

-- Outbox 事件表
CREATE TABLE IF NOT EXISTS ydsz_outbox (
    id                  VARCHAR(64)   NOT NULL,
    aggregate_id        VARCHAR(128)  NOT NULL,
    aggregate_type      VARCHAR(128)  NOT NULL,
    event_type          VARCHAR(128)  NOT NULL,
    payload             TEXT          NOT NULL,
    headers             TEXT,
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    retry_count         INT           NOT NULL DEFAULT 0,
    max_retries         INT           NOT NULL DEFAULT 5,
    next_retry_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at             TIMESTAMP,
    error_message       TEXT,

    -- P0-1: 租户隔离
    tenant_id           VARCHAR(64),

    -- P0-3: 幂等去重
    deduplication_id    VARCHAR(64),

    -- P1-1: 事件版本化和 Schema 演进
    schema_version      VARCHAR(32)   DEFAULT 'v1.0.0',
    content_type        VARCHAR(128),

    -- P1-2: 优先级（0-9，9 最高，默认 5）
    priority            INT           NOT NULL DEFAULT 5,

    -- P3-3: 链路追踪 ID
    trace_id            VARCHAR(64),

    PRIMARY KEY (id)
);

-- 索引：按状态 + 重试时间查询（轮询主索引）
CREATE INDEX IF NOT EXISTS idx_outbox_status_retry
    ON ydsz_outbox (status, next_retry_at);

-- 索引：按优先级 + 创建时间排序（轮询时使用）
CREATE INDEX IF NOT EXISTS idx_outbox_priority_created
    ON ydsz_outbox (status, priority DESC, created_at);

-- 索引：按聚合根查询
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate
    ON ydsz_outbox (aggregate_type, aggregate_id);

-- 索引：按事件类型查询
CREATE INDEX IF NOT EXISTS idx_outbox_event_type
    ON ydsz_outbox (event_type);

-- 索引：按租户查询
CREATE INDEX IF NOT EXISTS idx_outbox_tenant
    ON ydsz_outbox (tenant_id, status);

-- 索引：按幂等去重 ID 查询
CREATE INDEX IF NOT EXISTS idx_outbox_dedup
    ON ydsz_outbox (deduplication_id);

-- 索引：按创建时间排序（清理时使用）
CREATE INDEX IF NOT EXISTS idx_outbox_created_at
    ON ydsz_outbox (created_at);

-- 注释（PostgreSQL 语法）
COMMENT ON TABLE  ydsz_outbox IS 'PMIS Outbox 事件表 - 事务性 Outbox 模式';
COMMENT ON COLUMN ydsz_outbox.id IS '主键 UUID';
COMMENT ON COLUMN ydsz_outbox.aggregate_id IS '聚合根 ID';
COMMENT ON COLUMN ydsz_outbox.aggregate_type IS '聚合根类型';
COMMENT ON COLUMN ydsz_outbox.event_type IS '事件类型';
COMMENT ON COLUMN ydsz_outbox.payload IS '事件负载 JSON';
COMMENT ON COLUMN ydsz_outbox.headers IS '扩展头 JSON';
COMMENT ON COLUMN ydsz_outbox.status IS '状态: PENDING/PROCESSING/SENT/DEAD_LETTER';
COMMENT ON COLUMN ydsz_outbox.retry_count IS '重试次数';
COMMENT ON COLUMN ydsz_outbox.max_retries IS '最大重试次数';
COMMENT ON COLUMN ydsz_outbox.next_retry_at IS '下次重试时间';
COMMENT ON COLUMN ydsz_outbox.sent_at IS '投递成功时间';
COMMENT ON COLUMN ydsz_outbox.error_message IS '最后错误信息';
COMMENT ON COLUMN ydsz_outbox.tenant_id IS '租户 ID（多租户隔离）';
COMMENT ON COLUMN ydsz_outbox.deduplication_id IS '幂等去重 ID（下游消费端去重）';
COMMENT ON COLUMN ydsz_outbox.schema_version IS '事件 Schema 版本号';
COMMENT ON COLUMN ydsz_outbox.content_type IS '内容类型';
COMMENT ON COLUMN ydsz_outbox.priority IS '优先级（0-9，9 最高）';
COMMENT ON COLUMN ydsz_outbox.trace_id IS '链路追踪 ID';
