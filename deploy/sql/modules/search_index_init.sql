-- =====================================================
-- PMIS Search Index - PostgreSQL 全文检索基础设施
-- =====================================================
-- 前置条件：PostgreSQL 14+
-- 使用方式：在目标数据库执行此脚本
-- =====================================================

-- 1. 安装 zhparser 中文分词扩展（如 PG 已有 zhparser 则跳过）
-- 注意：zhparser 需要在 PG 服务器上预装共享库
-- CentOS/RHEL: yum install postgresql-zhparser
-- Ubuntu/Debian: apt install postgresql-zhparser
CREATE EXTENSION IF NOT EXISTS zhparser;

-- 2. 创建中文全文搜索配置
DO $$
BEGIN
    -- 检查 search_zh 配置是否已存在
    IF NOT EXISTS (SELECT 1 FROM pg_ts_config WHERE cfgname = 'search_zh') THEN
        -- 基于 simple 配置创建中文搜索配置
        CREATE TEXT SEARCH CONFIGURATION search_zh (parser = zhparser);

        -- 添加中文分词映射
        ALTER TEXT SEARCH CONFIGURATION search_zh
            ADD MAPPING FOR n,v,a,i,e,l WITH simple;

        -- 如果上述映射失败（zhparser token 类型不同），使用通配映射
        -- ALTER TEXT SEARCH CONFIGURATION search_zh ADD MAPPING FOR simple WITH simple;
    END IF;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'zhparser 不可用，将回退到 simple 配置: %', SQLERRM;
END $$;

-- 3. 安装 pg_trgm 扩展（模糊匹配/容错搜索）
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 4. 创建统一搜索索引表
CREATE TABLE IF NOT EXISTS pmis_search_index (
    id              VARCHAR(128) NOT NULL,
    doc_type        VARCHAR(64)  NOT NULL,
    title           TEXT,
    subtitle        TEXT,
    content         TEXT,
    snippet         TEXT,
    tags            JSONB        DEFAULT '[]'::jsonb,
    status          VARCHAR(32),
    path            TEXT,
    tenant_id       VARCHAR(64),
    created_by      VARCHAR(64),
    created_at      TIMESTAMPTZ,
    updated_by      VARCHAR(64),
    updated_at      TIMESTAMPTZ,
    searchable_text TEXT,
    metadata        JSONB        DEFAULT '{}'::jsonb,
    created_at_ts   TIMESTAMPTZ  DEFAULT NOW(),
    updated_at_ts   TIMESTAMPTZ  DEFAULT NOW(),
    PRIMARY KEY (id)
);

-- 5. 创建索引
-- GIN 全文检索索引（核心索引，加速 tsvector 匹配）
CREATE INDEX IF NOT EXISTS idx_pmis_search_index_fts
    ON pmis_search_index USING GIN (to_tsvector('search_zh', searchable_text));

-- 回退全文索引（当 search_zh 不可用时使用 simple）
CREATE INDEX IF NOT EXISTS idx_pmis_search_index_fts_simple
    ON pmis_search_index USING GIN (to_tsvector('simple', searchable_text));

-- 类型索引
CREATE INDEX IF NOT EXISTS idx_pmis_search_index_type
    ON pmis_search_index (doc_type);

-- 租户索引
CREATE INDEX IF NOT EXISTS idx_pmis_search_index_tenant
    ON pmis_search_index (tenant_id);

-- 更新时间索引（排序优化）
CREATE INDEX IF NOT EXISTS idx_pmis_search_index_updated
    ON pmis_search_index (updated_at_ts DESC);

-- pg_trgm 模糊匹配索引
CREATE INDEX IF NOT EXISTS idx_pmis_search_index_trgm
    ON pmis_search_index USING GIN (searchable_text gin_trgm_ops);

-- 标签 GIN 索引
CREATE INDEX IF NOT EXISTS idx_pmis_search_index_tags
    ON pmis_search_index USING GIN (tags jsonb_path_ops);

-- 6. 创建更新触发器（自动维护 updated_at_ts）
CREATE OR REPLACE FUNCTION update_pmis_search_index_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at_ts = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_pmis_search_index_update ON pmis_search_index;

CREATE TRIGGER trg_pmis_search_index_update
    BEFORE UPDATE ON pmis_search_index
    FOR EACH ROW
    EXECUTE FUNCTION update_pmis_search_index_timestamp();

-- 7. 验证
SELECT 'PMIS Search Index 初始化完成' AS message;
SELECT
    (SELECT COUNT(1) FROM pg_ts_config WHERE cfgname = 'search_zh') AS has_zhparser,
    (SELECT COUNT(1) FROM pg_extension WHERE extname = 'pg_trgm') AS has_pg_trgm,
    (SELECT COUNT(1) FROM pg_tables WHERE tablename = 'pmis_search_index') AS has_index_table;
