-- ===========================================================================
-- ydsz-nextwiki 复合索引优化（P0-2）
-- 版本：V2
-- 说明：为 ydsz_nw_file_node 表添加复合索引，覆盖高频查询场景
-- 数据库：PostgreSQL 16+
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- 1. 目录分页查询复合索引
--    高频场景：listFiles 按 parent_id + deleted + updated_at 排序分页
--    覆盖查询：WHERE parent_id = ? AND deleted = 0 ORDER BY updated_at DESC
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_ydsz_nw_file_node_parent_deleted_updated
    ON ydsz_nw_file_node (parent_id, deleted, updated_at DESC);

-- ---------------------------------------------------------------------------
-- 2. 目录分页查询 + 类型过滤复合索引
--    高频场景：listFiles 按类型筛选（file/folder/all）+ 排序分页
--    覆盖查询：WHERE parent_id = ? AND deleted = 0 AND node_type = ? ORDER BY ...
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_ydsz_nw_file_node_parent_deleted_type_updated
    ON ydsz_nw_file_node (parent_id, deleted, node_type, updated_at DESC);

-- ---------------------------------------------------------------------------
-- 3. 路径前缀查询索引（LIKE 'prefix%'）
--    高频场景：findAllDescendantsByPath、batchUpdatePathPrefix、batchSoftDeleteByPathPrefix
--    利用 PostgreSQL B-tree 对 LIKE 'prefix%' 的前缀匹配支持
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_ydsz_nw_file_node_path
    ON ydsz_nw_file_node (path);

-- ---------------------------------------------------------------------------
-- 4. 用户配额统计复合索引
--    高频场景：countByUser、sumSizeByUser、countFoldersByUser、statsBySuffixAndUser
--    覆盖查询：WHERE created_by = ? AND deleted = 0 AND node_type = ?
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_ydsz_nw_file_node_created_deleted_type
    ON ydsz_nw_file_node (created_by, deleted, node_type);

-- ---------------------------------------------------------------------------
-- 5. 文件哈希快速查找索引（秒传去重）
--    高频场景：findByFileHash 秒传去重检查
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_ydsz_nw_file_node_file_hash
    ON ydsz_nw_file_node (file_hash)
    WHERE file_hash IS NOT NULL AND deleted = 0;

-- ---------------------------------------------------------------------------
-- 6. 活跃文件节点部分索引（减小索引体积，加速 active 查询）
--    场景：加速所有带 deleted = 0 的查询
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_ydsz_nw_file_node_not_deleted
    ON ydsz_nw_file_node (id, parent_id, tenant_id)
    WHERE deleted = 0;

-- ---------------------------------------------------------------------------
-- 验证索引创建成功
-- ---------------------------------------------------------------------------
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'ydsz_nw_file_node'
  AND schemaname = 'public'
ORDER BY indexname;
