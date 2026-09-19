-- ============================================================================
-- YDSZ Cloud 26.09.19 - Outbox 事件归档表 DDL（F-4）
--
-- 功能：存储已投递完成或已丢弃的消息，降低主 Outbox 表压力，支持事件回溯。
-- 启用条件：ydsz.event.outbox.archive.enabled=true
-- 默认不启用，业务需要时开启并执行本脚本。
-- ============================================================================

CREATE TABLE IF NOT EXISTS ydsz_com_outbox_archive (
    id              VARCHAR(64)     PRIMARY KEY,
    aggregate_id    VARCHAR(128)    NOT NULL,
    aggregate_type  VARCHAR(128)    DEFAULT NULL,
    event_type      VARCHAR(256)    NOT NULL,
    payload         TEXT            NOT NULL,
    status          VARCHAR(16)     NOT NULL,
    retry_count     INT             NOT NULL DEFAULT 0,
    max_retries     INT             NOT NULL DEFAULT 5,
    tenant_id       VARCHAR(64)     DEFAULT NULL,
    idempotency_key VARCHAR(128)    DEFAULT NULL,
    trace_id        VARCHAR(64)     DEFAULT NULL,
    schema_version  INT             NOT NULL DEFAULT 1,
    compressed      TINYINT(1)      NOT NULL DEFAULT 0,
    created_at      TIMESTAMP       NOT NULL,
    updated_at      TIMESTAMP       NOT NULL,
    sent_at         TIMESTAMP       DEFAULT NULL,
    archived_at     TIMESTAMP       NOT NULL,
    error_message   VARCHAR(4000)   DEFAULT NULL,
    INDEX idx_archive_aggregate (aggregate_id, created_at),
    INDEX idx_archive_event_type (event_type, created_at),
    INDEX idx_archive_created_at (created_at),
    INDEX idx_archive_archived_at (archived_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Outbox 事件归档表（已投递或已丢弃的消息）';

-- ============================================================================
-- 回滚脚本
-- ============================================================================
/*
-- DROP TABLE IF EXISTS ydsz_com_outbox_archive;
*/
