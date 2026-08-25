-- ============================================================
-- V4: 冷数据归档支持（文件节点新增 storage_class 字段）
-- ============================================================

-- 文件节点表新增存储类型字段
ALTER TABLE ydsz_nw_file_node ADD COLUMN IF NOT EXISTS storage_class VARCHAR(20) DEFAULT 'STANDARD';

COMMENT ON COLUMN ydsz_nw_file_node.storage_class IS '存储类型：STANDARD(标准) / GLACIER(归档) / DEEP_ARCHIVE(深度归档)';

-- 冷数据查询索引
CREATE INDEX IF NOT EXISTS idx_ydsz_nw_file_node_storage_class
    ON ydsz_nw_file_node (node_type, deleted, storage_class, updated_at)
    WHERE node_type = 'file' AND deleted = 0 AND (storage_class IS NULL OR storage_class = 'STANDARD');
