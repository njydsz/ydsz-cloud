-- ============================================================================
-- ydsz-cloud: Outbox 事务表 DDL (PostgreSQL 16+)
-- ============================================================================
-- 功能：Transactional Outbox 模式存储领域事件，保障消息可靠投递
-- 规范：
--   - 使用 PostgreSQL ENUM 管理状态字段
--   - Snowflake ID (BIGINT) 应用层生成
--   - 禁止物理外键，逻辑外键加索引
--   - 布尔型软删除 (deleted)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 类型定义
-- ----------------------------------------------------------------------------

-- Outbox 消息状态枚举
DO $$ BEGIN
    CREATE TYPE ydsz_outbox_status AS ENUM (
        'PENDING',       -- 待投递
        'PROCESSING',    -- 处理中（已被实例 claim）
        'SENT',          -- 已投递成功
        'DEAD_LETTER'    -- 死信（超过最大重试次数）
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- ----------------------------------------------------------------------------
-- 2. 主表
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ydsz_outbox (
    -- ========== 业务主键 ==========
    id                  VARCHAR(64)     NOT NULL,  -- Snowflake ID（应用层生成）

    -- ========== 聚合根信息 ==========
    aggregate_type      VARCHAR(128)    NOT NULL,  -- 聚合根类型（如 Order / User / Project）
    aggregate_id        VARCHAR(128)    NOT NULL,  -- 聚合根 ID（如订单号、用户 ID）

    -- ========== 事件信息 ==========
    event_type          VARCHAR(128)    NOT NULL,  -- 事件类型（如 OrderCreated）
    payload             TEXT            NOT NULL,  -- 事件负载（JSON 字符串，最大 4MB）
    headers             JSONB,                      -- 扩展头（JSON 格式，用于路由/追踪）

    -- ========== 投递控制 ==========
    status              ydsz_outbox_status NOT NULL DEFAULT 'PENDING',
    retry_count         INTEGER         NOT NULL DEFAULT 0,    -- 当前重试次数
    max_retries         INTEGER         NOT NULL DEFAULT 5,    -- 最大重试次数
    next_retry_at       TIMESTAMPTZ,                            -- 下次重试时间（指数退避）
    priority            INTEGER         NOT NULL DEFAULT 5,    -- 优先级（0-9，9 最高）
    error_message       TEXT,                                    -- 最后一次失败的错误信息

    -- ========== 上下文 ==========
    tenant_id           VARCHAR(64),     -- 租户 ID（多租户隔离）
    trace_id            VARCHAR(64),     -- 链路追踪 ID（W3C traceparent）
    deduplication_id    VARCHAR(64),     -- 幂等去重 ID（下游消费端去重）

    -- ========== Schema ==========
    schema_version      VARCHAR(32)     NOT NULL DEFAULT 'v1.0.0',  -- 事件 Schema 版本
    content_type        VARCHAR(128),    -- 内容类型（如 application/vnd.ydsz.order.v1+json）

    -- ========== 时间戳 ==========
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    sent_at             TIMESTAMPTZ,     -- 投递成功时间
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE,  -- 软删除标记

    -- ========== 主键 ==========
    CONSTRAINT pk_ydsz_outbox PRIMARY KEY (id)
);

-- ----------------------------------------------------------------------------
-- 3. 索引
-- ----------------------------------------------------------------------------

-- 核心查询索引：轮询待投递消息（按优先级降序、创建时间升序）
-- 使用部分索引仅覆盖 PENDING 状态的行，大幅减少索引体积
CREATE INDEX IF NOT EXISTS idx_ydsz_outbox_pending
    ON ydsz_outbox (priority DESC, created_at ASC)
    WHERE status = 'PENDING' AND deleted = FALSE;

-- 待投递消息的下次重试时间索引（支持超时回收查询）
CREATE INDEX IF NOT EXISTS idx_ydsz_outbox_retry
    ON ydsz_outbox (next_retry_at)
    WHERE status = 'PENDING' AND deleted = FALSE;

-- PROCESSING 状态超时回收索引
CREATE INDEX IF NOT EXISTS idx_ydsz_outbox_processing
    ON ydsz_outbox (updated_at)
    WHERE status = 'PROCESSING' AND deleted = FALSE;

-- 已投递消息清理索引
CREATE INDEX IF NOT EXISTS idx_ydsz_outbox_sent_at
    ON ydsz_outbox (sent_at)
    WHERE status = 'SENT' AND deleted = FALSE;

-- 租户隔离索引（启用租户隔离时用于多租户查询）
CREATE INDEX IF NOT EXISTS idx_ydsz_outbox_tenant
    ON ydsz_outbox (tenant_id, status)
    WHERE deleted = FALSE;

-- 幂等去重索引
CREATE INDEX IF NOT EXISTS idx_ydsz_outbox_dedup
    ON ydsz_outbox (deduplication_id)
    WHERE deduplication_id IS NOT NULL
      AND status IN ('PENDING', 'PROCESSING')
      AND deleted = FALSE;

-- 聚合根查询索引（支持按聚合根 ID 查询事件历史）
CREATE INDEX IF NOT EXISTS idx_ydsz_outbox_aggregate
    ON ydsz_outbox (aggregate_type, aggregate_id, created_at DESC)
    WHERE deleted = FALSE;

-- ----------------------------------------------------------------------------
-- 4. 注释
-- ----------------------------------------------------------------------------

COMMENT ON TABLE ydsz_outbox IS '事务性 Outbox 表：存储领域事件，保障业务写操作与事件投递的事务一致性';
COMMENT ON COLUMN ydsz_outbox.id IS '消息唯一标识（Snowflake ID，应用层生成）';
COMMENT ON COLUMN ydsz_outbox.aggregate_type IS '聚合根类型（如 Order, User, Project）';
COMMENT ON COLUMN ydsz_outbox.aggregate_id IS '聚合根 ID（如订单号、用户 ID）';
COMMENT ON COLUMN ydsz_outbox.event_type IS '事件类型（如 OrderCreated, UserDisabled）';
COMMENT ON COLUMN ydsz_outbox.payload IS '事件负载 JSON 字符串（最大 4MB）';
COMMENT ON COLUMN ydsz_outbox.headers IS '扩展头 JSON（用于路由、追踪）';
COMMENT ON COLUMN ydsz_outbox.status IS '投递状态: PENDING/PROCESSING/SENT/DEAD_LETTER';
COMMENT ON COLUMN ydsz_outbox.retry_count IS '当前重试次数';
COMMENT ON COLUMN ydsz_outbox.max_retries IS '最大重试次数（超过后进入 DEAD_LETTER）';
COMMENT ON COLUMN ydsz_outbox.next_retry_at IS '下次允许重试的时间（指数退避计算）';
COMMENT ON COLUMN ydsz_outbox.priority IS '投递优先级（0-9，9 最高）';
COMMENT ON COLUMN ydsz_outbox.error_message IS '最后一次投递失败的错误消息';
COMMENT ON COLUMN ydsz_outbox.tenant_id IS '租户 ID（多租户隔离，可选）';
COMMENT ON COLUMN ydsz_outbox.trace_id IS '链路追踪 ID（W3C traceparent 协议）';
COMMENT ON COLUMN ydsz_outbox.deduplication_id IS '幂等去重 ID（用于下游消费端去重）';
COMMENT ON COLUMN ydsz_outbox.schema_version IS '事件 Schema 版本号（如 v1.0.0）';
COMMENT ON COLUMN ydsz_outbox.content_type IS '事件内容类型（RFC 6838  MIME 类型）';
COMMENT ON COLUMN ydsz_outbox.created_at IS '记录创建时间';
COMMENT ON COLUMN ydsz_outbox.updated_at IS '记录最后更新时间';
COMMENT ON COLUMN ydsz_outbox.sent_at IS '投递成功时间（投递网关确认成功）';
COMMENT ON COLUMN ydsz_outbox.deleted IS '软删除标记（TRUE 表示已删除，定期物理清理）';
