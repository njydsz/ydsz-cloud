--
-- V26.10.30 — NextWiki 搜索 + 仪表盘聚合优化
--
-- 1. 内容搜索 trigram GIN 索引（pg_trgm 已在服务器级启用 extension）
--    ILIKE '%keyword%' 可直接利用 @> 操作符走 GIN，无需全表扫描
-- 2. 文件搜索标题/路径的 trigram 索引（辅助 filename 模糊搜索）
-- 3. 仪表盘概览聚合辅助索引（user + is_deleted + node_type + size）
--

-- ============================================================
-- 表: ydsz_file_search_index
-- ============================================================
-- 内容 trigram 索引：加速 content ILIKE '%xxx%' / similarity() 查询
CREATE INDEX IF NOT EXISTS idx_search_content_trgm
    ON ydsz_file_search_index USING gin (content gin_trgm_ops);

-- 文件名字段 trigram 索引：加速 name ILIKE '%xxx%' 查询
CREATE INDEX IF NOT EXISTS idx_search_name_trgm
    ON ydsz_file_search_index USING gin (name gin_trgm_ops);

-- ============================================================
-- 表: ydsz_file_file_node
-- ============================================================
-- 仪表盘聚合复合索引：同表多维度聚合无需回表
CREATE INDEX IF NOT EXISTS idx_filenode_overview_agg
    ON ydsz_file_file_node (created_by, is_deleted, node_type);
