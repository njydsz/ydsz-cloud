-- ============================================================================
-- ydsz-cloud 公共组件模块数据库脚本 (ydsz-common)
-- ============================================================================
-- 模块：ydsz-common（公共组件，含 ydsz-common-event、ydsz-common-search）
-- 说明：基于 ydsz-common-event 与 ydsz-common-search 既有 SQL 整理的完整建表脚本。
--       outbox 表沿用 ydsz-common-event/src/main/resources/db/outbox_mysql.sql 原定义；
--       搜索死信队列表由 PostgreSQL 版本（ydsz_search_dead_letter.sql）转译为 MySQL。
-- 数据库：MySQL 8.0+，InnoDB / utf8mb4
-- 日期：2026-08-25
-- @author ydsz-team
-- ============================================================================

-- ============================================================================
-- 1. 事务性 Outbox 表（ydsz-common-event）
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

    -- ========== 投递控制 ==========
    status              ENUM('PENDING','PROCESSING','SENT','DEAD_LETTER')
                                        NOT NULL DEFAULT 'PENDING' COMMENT '投递状态',
    retry_count         INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '当前重试次数',
    max_retries         INT UNSIGNED    NOT NULL DEFAULT 5 COMMENT '最大重试次数',
    next_retry_at       DATETIME(3)             COMMENT '下次重试时间（指数退避）',
    error_message       TEXT                     COMMENT '最后一次失败的错误信息',

    -- ========== 上下文 ==========
    tenant_id           VARCHAR(64)              COMMENT '租户 ID（多租户隔离）',
    trace_id            VARCHAR(64)              COMMENT '链路追踪 ID',
    deduplication_id    VARCHAR(64)              COMMENT '幂等去重 ID',

    -- ========== 时间戳 ==========
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    sent_at             DATETIME(3)              COMMENT '投递成功时间',

    -- ========== 约束与主键 ==========
    PRIMARY KEY (id),
    -- 轮询待投递消息索引
    INDEX idx_ydsz_outbox_pending (status, created_at ASC),
    -- 下次重试时间索引
    INDEX idx_ydsz_outbox_retry (status, next_retry_at),
    -- PROCESSING 超时回收索引
    INDEX idx_ydsz_outbox_processing (status, updated_at),
    -- 已投递清理索引
    INDEX idx_ydsz_outbox_sent_at (status, sent_at),
    -- 租户隔离索引
    INDEX idx_ydsz_outbox_tenant (tenant_id, status),
    -- 幂等去重索引
    INDEX idx_ydsz_outbox_dedup (deduplication_id, status),
    -- 聚合根查询索引
    INDEX idx_ydsz_outbox_aggregate (aggregate_type, aggregate_id, created_at DESC)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='事务性 Outbox 表：存储领域事件，保障业务写操作与事件投递的事务一致性';

-- ============================================================================
-- 2. 搜索索引死信队列表（ydsz-common-search）
-- ============================================================================
-- 用途：持久化存储索引写入失败的操作，
--       支持定时重放补偿 + 告警监控 + 人工介入。
-- 由 PostgreSQL 版转译：BIGSERIAL→BIGINT AUTO_INCREMENT、TIMESTAMPTZ→DATETIME、
-- CHECK 约束并入注释、部分索引转译为普通复合索引。

CREATE TABLE IF NOT EXISTS ydsz_search_dead_letter (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    operation     VARCHAR(20)  NOT NULL COMMENT '索引操作类型：UPSERT / DELETE / BULK',
    doc_type      VARCHAR(64)  DEFAULT NULL COMMENT '实体类型（project/wiki/user 等）',
    document_id   VARCHAR(128) DEFAULT NULL COMMENT '文档主键（DELETE 操作时使用）',
    document_json TEXT         DEFAULT NULL COMMENT '文档 JSON（UPSERT/BULK 操作时使用）',
    error_msg     TEXT         DEFAULT NULL COMMENT '最后一次失败原因（截断 2000 字符）',
    retry_count   INT          NOT NULL DEFAULT 0 COMMENT '已重试次数，达到 5 次升级为 DISCARDED',
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待处理 / RETRYING-处理中 / RESOLVED-已解决 / DISCARDED-已放弃(需人工介入)',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入队时间',
    resolved_at   DATETIME     DEFAULT NULL COMMENT '解决时间',
    PRIMARY KEY (id),
    -- 按状态 + 创建时间索引，支持高效扫描待处理记录
    INDEX idx_dlq_status_created (status, created_at),
    -- 按实体类型索引，支持按类型查询失败记录（原 PG 部分索引转译）
    INDEX idx_dlq_doc_type (doc_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='搜索索引死信队列：存储索引写入失败的操作，支持定时重放补偿';
