-- ============================================================================
-- ydsz-cloud: Outbox 事务表 DDL (MySQL 8.0+)
-- ============================================================================
-- 功能：Transactional Outbox 模式存储领域事件，保障消息可靠投递
-- 规范：
--   - 使用 ENUM 管理状态字段
--   - Snowflake ID (BIGINT) 应用层生成
--   - 禁止物理外键，逻辑外键加索引
--   - 布尔型软删除 (deleted)
-- 字符集：utf8mb4
-- ============================================================================

CREATE TABLE IF NOT EXISTS ydsz_outbox (
    -- ========== 业务主键 ==========
    id                  VARCHAR(64)     NOT NULL COMMENT '消息唯一标识（Snowflake ID）',

    -- ========== 聚合根信息 ==========
    aggregate_type      VARCHAR(128)    NOT NULL COMMENT '聚合根类型（如 Order, User）',
    aggregate_id        VARCHAR(128)    NOT NULL COMMENT '聚合根 ID',

    -- ========== 事件信息 ==========
    event_type          VARCHAR(128)    NOT NULL COMMENT '事件类型（如 OrderCreated）',
    payload             MEDIUMTEXT      NOT NULL COMMENT '事件负载 JSON（最大 4MB）',
    headers             JSON                        COMMENT '扩展头（用于路由/追踪）',

    -- ========== 投递控制 ==========
    status              ENUM('PENDING','PROCESSING','SENT','DEAD_LETTER')
                                        NOT NULL DEFAULT 'PENDING' COMMENT '投递状态',
    retry_count         INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '当前重试次数',
    max_retries         INT UNSIGNED    NOT NULL DEFAULT 5 COMMENT '最大重试次数',
    next_retry_at       DATETIME(3)             COMMENT '下次重试时间（指数退避）',
    priority            INT UNSIGNED    NOT NULL DEFAULT 5 COMMENT '优先级（0-9，9 最高）',
    error_message       TEXT                     COMMENT '最后一次失败的错误信息',

    -- ========== 上下文 ==========
    tenant_id           VARCHAR(64)              COMMENT '租户 ID（多租户隔离）',
    trace_id            VARCHAR(64)              COMMENT '链路追踪 ID',
    deduplication_id    VARCHAR(64)              COMMENT '幂等去重 ID',

    -- ========== Schema ==========
    schema_version      VARCHAR(32)     NOT NULL DEFAULT 'v1.0.0' COMMENT '事件 Schema 版本',
    content_type        VARCHAR(128)             COMMENT '内容类型（MIME）',

    -- ========== 时间戳 ==========
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    sent_at             DATETIME(3)              COMMENT '投递成功时间',
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE COMMENT '软删除标记',

    -- ========== 约束与主键 ==========
    PRIMARY KEY (id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='事务性 Outbox 表：存储领域事件，保障业务写操作与事件投递的事务一致性';

-- ----------------------------------------------------------------------------
-- 索引
-- ----------------------------------------------------------------------------

-- 轮询待投递消息索引
CREATE INDEX idx_ydsz_outbox_pending
    ON ydsz_outbox (status, priority DESC, created_at ASC)
    WHERE deleted = FALSE;

-- 下次重试时间索引
CREATE INDEX idx_ydsz_outbox_retry
    ON ydsz_outbox (status, next_retry_at)
    WHERE deleted = FALSE;

-- PROCESSING 超时回收索引
CREATE INDEX idx_ydsz_outbox_processing
    ON ydsz_outbox (status, updated_at)
    WHERE deleted = FALSE;

-- 已投递清理索引
CREATE INDEX idx_ydsz_outbox_sent_at
    ON ydsz_outbox (status, sent_at)
    WHERE deleted = FALSE;

-- 租户隔离索引
CREATE INDEX idx_ydsz_outbox_tenant
    ON ydsz_outbox (tenant_id, status)
    WHERE deleted = FALSE;

-- 幂等去重索引
CREATE UNIQUE INDEX idx_ydsz_outbox_dedup
    ON ydsz_outbox (deduplication_id, status)
    WHERE deduplication_id IS NOT NULL AND deleted = FALSE;

-- 聚合根查询索引
CREATE INDEX idx_ydsz_outbox_aggregate
    ON ydsz_outbox (aggregate_type, aggregate_id, created_at DESC)
    WHERE deleted = FALSE;
