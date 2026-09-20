-- =============================================================================
-- 搜索模块 — 索引表按租户 HASH 分区
-- -----------------------------------------------------------------------------
-- 目的：将全文索引表 ydsz_wiki_search_index 按 tenant_id 做 HASH 分区，实现：
--   1. 大租户的数据物理隔离，避免热租户拖垮全局查询性能
--   2. 索引清理（DROP PARTITION）比 DELETE 快数个数量级，便于租户级数据清理
--   3. GIN 索引按分区独立构建，提升单分区索引选择性
--
-- 注意：本脚本创建新结构（分区表 ydsz_com_search_index_partitioned），并提供迁移步骤。
-- 线上切换请采用"双写 → 历史迁移 → 切读 → 下线旧表"灰度策略。
--
-- 依赖：V26.09.20__search_dead_letter.sql
-- 作者：ydsz-team
-- 日期：26.09.21
-- =============================================================================

-- ==================== 1. 创建分区表结构 ====================
-- 与原表字段一致，新增 tenant_id 为 HASH 分区键（8 个分区，按租户 ID 取模分散）

CREATE TABLE IF NOT EXISTS ydsz_com_search_index_partitioned
(
    id              VARCHAR(128) NOT NULL,                                                  -- 文档全局 ID
    doc_type        VARCHAR(64)  NOT NULL,                                                  -- 实体类型（wiki、project 等）
    tenant_id       VARCHAR(64)  NOT NULL,                                                  -- 租户 ID（HASH 分区键）
    title           VARCHAR(512),                                                           -- 标题（tsvector 源字段 A 权重）
    subtitle        VARCHAR(512),                                                           -- 副标题（B 权重）
    content         TEXT,                                                                   -- 正文（C 权重）
    tags            VARCHAR(255)[],                                                         -- 标签数组（D 权重）
    locale          VARCHAR(16)  DEFAULT 'zh-CN',                                            -- 语言标识
    metadata        JSONB,                                                                  -- 扩展元数据
    search_vector   TSVECTOR GENERATED ALWAYS AS (                                           -- 全文检索向量（自动生成）
        setweight(to_tsvector('search_zh', COALESCE(title, '')), 'A') ||
        setweight(to_tsvector('search_zh', COALESCE(subtitle, '')), 'B') ||
        setweight(to_tsvector('search_zh', COALESCE(content, '')), 'C') ||
        setweight(to_tsvector('search_zh', COALESCE(array_to_string(tags, ' '), '')), 'D')
    ) STORED,
    is_active       BOOLEAN     DEFAULT TRUE,                                                -- 是否启用（软删除标记）
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT pk_search_partitioned PRIMARY KEY (tenant_id, id, doc_type)
) PARTITION BY HASH (tenant_id);

-- ==================== 2. 创建 8 个 HASH 分区 ====================
-- 分区数选择 8（2 的幂，PostgreSQL 原生支持）；如需更大规模可扩展到 16/32

CREATE TABLE IF NOT EXISTS ydsz_search_idx_part_0 PARTITION OF ydsz_com_search_index_partitioned
    FOR VALUES WITH (MODULUS 8, REMAINDER 0);
CREATE TABLE IF NOT EXISTS ydsz_search_idx_part_1 PARTITION OF ydsz_com_search_index_partitioned
    FOR VALUES WITH (MODULUS 8, REMAINDER 1);
CREATE TABLE IF NOT EXISTS ydsz_search_idx_part_2 PARTITION OF ydsz_com_search_index_partitioned
    FOR VALUES WITH (MODULUS 8, REMAINDER 2);
CREATE TABLE IF NOT EXISTS ydsz_search_idx_part_3 PARTITION OF ydsz_com_search_index_partitioned
    FOR VALUES WITH (MODULUS 8, REMAINDER 3);
CREATE TABLE IF NOT EXISTS ydsz_search_idx_part_4 PARTITION OF ydsz_com_search_index_partitioned
    FOR VALUES WITH (MODULUS 8, REMAINDER 4);
CREATE TABLE IF NOT EXISTS ydsz_search_idx_part_5 PARTITION OF ydsz_com_search_index_partitioned
    FOR VALUES WITH (MODULUS 8, REMAINDER 5);
CREATE TABLE IF NOT EXISTS ydsz_search_idx_part_6 PARTITION OF ydsz_com_search_index_partitioned
    FOR VALUES WITH (MODULUS 8, REMAINDER 6);
CREATE TABLE IF NOT EXISTS ydsz_search_idx_part_7 PARTITION OF ydsz_com_search_index_partitioned
    FOR VALUES WITH (MODULUS 8, REMAINDER 7);

-- ==================== 3. 分区本地 GIN 索引 ====================
-- 每个分区独立创建 GIN 索引，查询时 PG 自动剪枝无关分区

CREATE INDEX IF NOT EXISTS idx_search_part_0_gist ON ydsz_search_idx_part_0 USING GIN (search_vector);
CREATE INDEX IF NOT EXISTS idx_search_part_1_gist ON ydsz_search_idx_part_1 USING GIN (search_vector);
CREATE INDEX IF NOT EXISTS idx_search_part_2_gist ON ydsz_search_idx_part_2 USING GIN (search_vector);
CREATE INDEX IF NOT EXISTS idx_search_part_3_gist ON ydsz_search_idx_part_3 USING GIN (search_vector);
CREATE INDEX IF NOT EXISTS idx_search_part_4_gist ON ydsz_search_idx_part_4 USING GIN (search_vector);
CREATE INDEX IF NOT EXISTS idx_search_part_5_gist ON ydsz_search_idx_part_5 USING GIN (search_vector);
CREATE INDEX IF NOT EXISTS idx_search_part_6_gist ON ydzsz_search_idx_part_6 USING GIN (search_vector);
CREATE INDEX IF NOT EXISTS idx_search_part_7_gist ON ydzsz_search_idx_part_7 USING GIN (search_vector);

-- 加速 doc_type + is_active 筛选
CREATE INDEX IF NOT EXISTS idx_search_part_0_type ON ydz_search_idx_part_0 (doc_type, is_active);
CREATE INDEX IF NOT EXISTS idx_search_part_1_type ON ydz_search_idx_part_1 (doc_type, is_active);
CREATE INDEX IF NOT EXISTS idx_search_part_2_type ON ydz_search_idx_part_2 (doc_type, is_active);
CREATE INDEX IF NOT EXISTS idx_search_part_3_type ON ydz_search_idx_part_3 (doc_type, is_active);
CREATE INDEX IF NOT EXISTS idx_search_part_4_type ON ydz_search_idx_part_4 (doc_type, is_active);
CREATE INDEX IF NOT EXISTS idx_search_part_5_type ON ydz_search_idx_part_5 (doc_type, is_active);
CREATE INDEX IF NOT EXISTS idx_search_part_6_type ON ydzsz_search_idx_part_6 (doc_type, is_active);
CREATE INDEX IF NOT EXISTS idx_search_part_7_type ON ydzsz_search_idx_part_7 (doc_type, is_active);

-- ==================== 4. 历史数据迁移（灰度执行参考） ====================
-- 迁移步骤（线上变更窗口，评估数据量后分批执行）：
--
--   步骤 1：双写 — 业务代码同时写入新旧表
--   步骤 2：分批迁移历史数据（每批 10000 条，控制 WAL 压力）：
--
--     INSERT INTO ydz_com_search_index_partitioned
--     SELECT *, gen_random_uuid()::varchar FROM ydz_wiki_search_index
--     WHERE id > :last_migrated_id
--     ORDER BY id
--     LIMIT 10000;
--
--   步骤 3：校验行数一致性（新旧表 COUNT 对比）
--   步骤 4：修改 ydsz.search.pg.index-table=ydz_com_search_index_partitioned 切读
--   步骤 5：下线双写，保留旧表 7 天以便回滚

-- ==================== 5. 预期收益 ====================
--   • 查询：tenant_id 等值过滤 + 全文检索 → PG 剪枝 7/8 分区，仅扫描 1 个分区的 GIN 索引
--   • 删除： DROP PARTITION 秒级完成，远优于 DELETE ... WHERE tenant_id = ?
--   • 备份： pg_dump 可单租户分区导出，恢复粒度更细
