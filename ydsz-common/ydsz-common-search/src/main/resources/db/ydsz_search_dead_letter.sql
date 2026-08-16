-- =====================================================================
-- 搜索索引死信队列表 (ydsz_search_dead_letter)
-- =====================================================================
-- 用途：持久化存储索引写入失败的操作为 PostgreSQL 表，
--       支持定时重放补偿 + 告警监控 + 人工介入。
--
-- 表结构说明：
--   id            — 自增主键
--   operation     — 索引操作类型：UPSERT / DELETE / BULK
--   doc_type      — 实体类型（project/wiki/user 等）
--   document_id   — 文档主键（DELETE 操作时使用）
--   document_json — 文档 JSON（UPSERT/BULK 操作时使用）
--   error_msg     — 最后一次失败原因（截断 2000 字符）
--   retry_count   — 已重试次数（达到 5 次标记为 DISCARDED）
--   status        — 状态：PENDING / RETRYING / RESOLVED / DISCARDED
--   created_at    — 入队时间
--   resolved_at   — 解决时间
--
-- 索引：按状态 + 创建时间，支持高效扫描 PENDING 记录
-- =====================================================================

CREATE TABLE IF NOT EXISTS ydsz_search_dead_letter (
    id            BIGSERIAL   PRIMARY KEY,
    operation     VARCHAR(20) NOT NULL CHECK (operation IN ('UPSERT', 'DELETE', 'BULK')),
    doc_type      VARCHAR(64),
    document_id   VARCHAR(128),
    document_json TEXT,
    error_msg     TEXT,
    retry_count   INT         NOT NULL DEFAULT 0,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                              CHECK (status IN ('PENDING', 'RETRYING', 'RESOLVED', 'DISCARDED')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at   TIMESTAMPTZ
);

-- 按状态 + 创建时间索引，支持高效扫描待处理记录
CREATE INDEX IF NOT EXISTS idx_dlq_status_created
    ON ydsz_search_dead_letter (status, created_at);

-- 按实体类型索引，支持按类型查询失败记录
CREATE INDEX IF NOT EXISTS idx_dlq_doc_type
    ON ydsz_search_dead_letter (doc_type)
    WHERE status IN ('PENDING', 'RETRYING');

-- 表注释
COMMENT ON TABLE ydsz_search_dead_letter IS '搜索索引死信队列：存储索引写入失败的操作，支持定时重放补偿';
COMMENT ON COLUMN ydsz_search_dead_letter.status IS 'PENDING:待处理 RETRYING:处理中 RESOLVED:已解决 DISCARDED:已放弃(需人工介入)';
COMMENT ON COLUMN ydsz_search_dead_letter.retry_count IS '已重试次数，达到 5 次升级为 DISCARDED';
