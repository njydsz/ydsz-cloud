-- =============================================================================
-- Flyway Migration: V26.10.09
-- Description:  pg_trgm GIN 索引优化左模糊查询性能
-- Author:      ydsz-team
-- Created:     2026-10-09
-- =============================================================================
--
-- 背景: 以下 Mapper 存在大量 ILIKE CONCAT('%', #{kw}, '%') 左模糊查询，
--       普通 B-Tree 索引无法生效，导致全表扫描。
--       PostgreSQL pg_trgm 扩展提供 GIN trigram 索引，可加速任意子串匹配。
--
-- 受影响 Mapper:
--   1. SearchIndexMapper (ydsz_file_search_index 表)
--      - searchPage / countSearchResults / searchAdvanced
--      - 字段: name, path, content, tags
--      - 使用 ILIKE（大小写不敏感），trigram 索引同样适用
--   2. RuleDefinitionMapper (ydsz_rule_def 表)
--      - searchRules / searchRulesCount
--      - 字段: rule_code, rule_name, description, condition_expression, category, category_path, owner
--      - 使用 LIKE（大小写敏感），trigram 索引同样适用
--
-- 执行建议:
--   - 启用 pg_trgm 扩展需要超级用户权限（RDS/CloudPG 通常已预装）
--   - GIN 索引 CONCURRENTLY 方式创建，避免锁表（低峰期执行）
--   - 索引大小约为表数据的 2-3 倍，请确保磁盘空间充足
--                            ydsz_file_search_index 预估 < 500MB
--                            ydsz_rule_def 预估 < 50MB
--   - 写入性能影响: DML 性能下降约 10-20%（trigram 索引维护开销）
-- =============================================================================

-- =============================================================================
-- 1. 启用 pg_trgm 扩展
-- =============================================================================
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- =============================================================================
-- 2. ydsz_file_search_index (nextwiki 搜索索引表)
--    受影响查询: SearchIndexMapper.searchPage / countSearchResults / searchAdvanced
-- =============================================================================

-- name 字段（文件名称搜索，高频）
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsi_file_search_index_name_trgm
    ON ydsz_file_search_index USING gin (name gin_trgm_ops);

-- path 字段（文件路径搜索）
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsi_file_search_index_path_trgm
    ON ydsz_file_search_index USING gin (path gin_trgm_ops);

-- content 字段（文件内容全文搜索）
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsi_file_search_index_content_trgm
    ON ydsz_file_search_index USING gin (content gin_trgm_ops);

-- tags 字段（标签搜索）
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsi_file_search_index_tags_trgm
    ON ydsz_file_search_index USING gin (tags gin_trgm_ops);

-- =============================================================================
-- 3. ydsz_rule_def (规则引擎规则定义表)
--    受影响查询: RuleDefinitionMapper.searchRules / searchRulesCount
-- =============================================================================

-- rule_code 字段（规则编码搜索）
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsi_rule_def_rule_code_trgm
    ON ydsz_rule_def USING gin (rule_code gin_trgm_ops);

-- rule_name 字段（规则名称搜索）
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsi_rule_def_rule_name_trgm
    ON ydsz_rule_def USING gin (rule_name gin_trgm_ops);

-- description 字段（描述搜索）
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsi_rule_def_description_trgm
    ON ydsz_rule_def USING gin (description gin_trgm_ops);

-- condition_expression 字段（条件表达式搜索，该字段较长，索引体积较大）
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsi_rule_def_condition_expr_trgm
    ON ydsz_rule_def USING gin (condition_expression gin_trgm_ops);

-- category 字段（分类精确过滤 + 模糊搜索复合场景，trigram 同时服务 = 和 LIKE）
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsi_rule_def_category_trgm
    ON ydsz_rule_def USING gin (category gin_trgm_ops);

-- category_path 字段（分类路径搜索）
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsi_rule_def_category_path_trgm
    ON ydsz_rule_def USING gin (category_path gin_trgm_ops);

-- owner 字段（负责人搜索）
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsi_rule_def_owner_trgm
    ON ydsz_rule_def USING gin (owner gin_trgm_ops);

-- =============================================================================
-- 4. 验证索引创建成功（需手动取消注释后执行）
-- =============================================================================
-- DO $$
-- DECLARE
--     v_count INTEGER;
-- BEGIN
--     SELECT COUNT(*) INTO v_count
--     FROM pg_indexes
--     WHERE schemaname = 'public'
--       AND indexname LIKE 'idx_ydsi_%_trgm';
--
--     RAISE NOTICE '已创建 trigram 索引数量: %', v_count;
--
--     IF v_count < 11 THEN
--         RAISE WARNING '预期 11 个 trigram 索引，实际仅 % 个，请检查', v_count;
--     END IF;
-- END $$;
-- =============================================================================

-- =============================================================================
-- 5. Mapper XML 优化建议（仅供开发参考，无需在 Flyway 中执行）
-- =============================================================================
--
-- (1) SearchIndexMapper.xml
--     当前 ILIKE 模式可继续使用，创建 trigram 索引后 PostgreSQL 会自动选择
--     GIN 索引扫描代替顺序扫描，无需修改 SQL 语句。
--     注意: ILIKE 与 trigram 索引兼容性已验证（PostgreSQL 9.1+ 支持）。
--     可选优化: 若 keyword 为单字符，trigram 索引可能不生效（三元组长度不足），
--     可在 Java 层限制最小搜索长度为 2-3 字符。
--
-- (2) RuleDefinitionMapper.xml
--     与 SearchIndexMapper 同理，LIKE 左模糊自动命中 trigram 索引。
--     注意: searchRules 使用 query.split('\\s+') 多关键词 AND 组合，
--     每个关键词独立走索引后做 Bitmap AND，性能可接受。
--     若未来搜索量大，建议迁移至 PostgreSQL 全文检索（tsvector/tsquery）+ GIN 索引。
--
-- (3) 通用建议
--     - ANALYZE 表以更新统计信息，帮助 CBO 正确选择索引:
--       ANALYZE ydsz_file_search_index;
--       ANALYZE ydsz_rule_def;
--     - 设置 pg_trgm.similarity_threshold 控制相似度阈值（默认 0.3）
--     - 监控索引使用频率: SELECT * FROM pg_stat_user_indexes WHERE indexrelname LIKE '%trgm%';
-- =============================================================================
