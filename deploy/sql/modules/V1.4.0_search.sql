-- ============================================================
-- YDSZ Common Search — PostgreSQL 索引表 DDL
-- ============================================================
-- 该脚本由 DBA 手动执行，搜索引擎构造器不再自动建表。
-- ============================================================

-- 1. 索引表
CREATE TABLE IF NOT EXISTS ydsz_search_index (
    id              VARCHAR(128)   NOT NULL,
    doc_type        VARCHAR(64)    NOT NULL,
    title           TEXT           NOT NULL DEFAULT '',
    subtitle        TEXT           NOT NULL DEFAULT '',
    content         TEXT           NOT NULL DEFAULT '',
    snippet         TEXT,
    tags            JSONB          NOT NULL DEFAULT '[]',
    status          VARCHAR(32)    NOT NULL DEFAULT '',
    path            TEXT,
    tenant_id       VARCHAR(64)    NOT NULL DEFAULT '',
    created_by      VARCHAR(64),
    created_at      TIMESTAMP,
    updated_by      VARCHAR(64),
    updated_at      TIMESTAMP,
    searchable_text TEXT           NOT NULL DEFAULT '',
    metadata        JSONB          NOT NULL DEFAULT '{}',
    created_at_ts   TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at_ts   TIMESTAMP      NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);

-- 2. GIN 全文索引
CREATE INDEX IF NOT EXISTS idx_search_index_tsvector
    ON ydsz_search_index
    USING gin (to_tsvector('search_zh', searchable_text));

-- 3. 类型索引（过滤）
CREATE INDEX IF NOT EXISTS idx_search_index_doc_type
    ON ydsz_search_index (doc_type);

-- 4. 租户索引
CREATE INDEX IF NOT EXISTS idx_search_index_tenant
    ON ydsz_search_index (tenant_id);

-- 5. 复合索引（类型+租户）
CREATE INDEX IF NOT EXISTS idx_search_index_type_tenant
    ON ydsz_search_index (doc_type, tenant_id);

-- 6. trigram 索引（模糊匹配 %）
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_search_index_text_trgm
    ON ydsz_search_index USING gin (searchable_text gin_trgm_ops);

-- 7. 标题索引（搜索建议）
CREATE INDEX IF NOT EXISTS idx_search_index_title
    ON ydsz_search_index (title);

-- 8. 更新时间索引（排序）
CREATE INDEX IF NOT EXISTS idx_search_index_updated
    ON ydsz_search_index (updated_at_ts);

-- ============================================================
-- 说明：
-- 1. 如果未安装 zhparser，将 search_zh 替换为 simple
-- 2. pg_trgm 扩展需要 superuser 权限
-- 3. GIN 索引在大量数据写入时可能较慢，建议在低峰期重建
-- ============================================================
