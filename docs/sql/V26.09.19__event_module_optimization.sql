-- ============================================================================
-- YDSZ Cloud 26.09.19 - 事件模块优化 DDL
--
-- 变更清单：
--   1. 重命名 deduplication_id → idempotency_key（E-2 字段对齐）
--   2. 新增 schema_version 列（O-4 事件 schema 版本）
--   3. 新增 compressed 列（P3 payload GZIP 压缩标记）
--   4. 调整 error_message 长度至 VARCHAR(4000)（E-1 智能截断缓冲）
--   5. 新增索引：idempotency_key 复合索引优化幂等查询
--
-- 注意：建议在业务低峰期执行，大表执行前请在测试环境验证。
-- ============================================================================

-- 1. 重命名幂等键列（MySQL 8.0+ 使用 RENAME COLUMN，无需重建表）
--    如使用 MySQL 5.7，改为：ALTER TABLE ydsz_com_outbox CHANGE COLUMN deduplication_id idempotency_key VARCHAR(128) DEFAULT NULL;
ALTER TABLE ydsz_com_outbox
  RENAME COLUMN deduplication_id TO idempotency_key;

-- 2. 添加 schema 版本列（默认 1，NOT NULL 含默认值避免全表更新）
ALTER TABLE ydsz_com_outbox
  ADD COLUMN schema_version INT NOT NULL DEFAULT 1 COMMENT '事件 schema 版本（向前兼容）';

-- 3. 添加压缩标记列（默认 false）
ALTER TABLE ydsz_com_outbox
  ADD COLUMN compressed TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'payload 是否 GZIP 压缩存储';

-- 4. error_message 扩展长度（从 VARCHAR(2000) 扩展至 VARCHAR(4000)，提供更宽裕的错误诊断缓冲）
ALTER TABLE ydsz_com_outbox
  MODIFY COLUMN error_message VARCHAR(4000) DEFAULT NULL COMMENT '错误信息（智能截断，保留首尾各 800/1200 字符）';

-- 5. 幂等键 + 状态的复合索引（覆盖 existsByIdempotencyKey 方法的查询场景）
CREATE INDEX IF NOT EXISTS idx_idempotency_status ON ydsz_com_outbox (idempotency_key, status);

-- ============================================================================
-- 回滚脚本（谨慎使用）
-- ============================================================================
/*
-- 回滚步骤：按逆序执行以下语句
-- ALTER TABLE ydsz_com_outbox DROP INDEX idx_idempotency_status;
-- ALTER TABLE ydsz_com_outbox MODIFY COLUMN error_message VARCHAR(2000) DEFAULT NULL;
-- ALTER TABLE ydsz_com_outbox DROP COLUMN compressed;
-- ALTER TABLE ydsz_com_outbox DROP COLUMN schema_version;
-- ALTER TABLE ydsz_com_outbox RENAME COLUMN idempotency_key TO deduplication_id;
*/
