-- =============================================================================
-- 搜索模块 — 持久化死信队列表
-- -----------------------------------------------------------------------------
-- 目的：将索引写入失败的操作落库到 PostgreSQL，支持定时重放与人工介入。
-- 依赖：无（独立表，使用后创建的 SERIAL 主键）
-- 作者：ydsz-team
-- 日期：26.09.20
-- =============================================================================

CREATE TABLE IF NOT EXISTS ydsz_com_search_dead_letter
(
    id            BIGSERIAL PRIMARY KEY,
    operation     VARCHAR(20)  NOT NULL,                                               -- UPSERT / DELETE / BULK
    doc_type      VARCHAR(64),                                                         -- 实体类型（如 wiki、project）
    document_id   VARCHAR(128),                                                        -- 文档主键（DELETE 时使用）
    document_json TEXT,                                                                -- 文档 JSON（UPSERT 时使用）
    error_msg     TEXT,                                                                -- 最后一次失败原因
    retry_count   INT         DEFAULT 0,
    status        VARCHAR(20) DEFAULT 'PENDING',                                       -- PENDING / RETRYING / RESOLVED / DISCARDED
    created_at    TIMESTAMPTZ DEFAULT NOW(),
    resolved_at   TIMESTAMPTZ                                                          -- 解决时间
);

-- 加速「待处理 + 最早失败」的扫描（replayPending 核心查询路径）
CREATE INDEX IF NOT EXISTS idx_dlq_status_created
    ON ydsz_com_search_dead_letter (status, created_at);

-- 加速按文档类型 + ID 查询特定死信记录（人工介入场景）
CREATE INDEX IF NOT EXISTS idx_dlq_type_docid
    ON ydsz_com_search_dead_letter (doc_type, document_id);
