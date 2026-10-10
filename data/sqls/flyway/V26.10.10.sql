-- =============================================================================
-- Flyway Migration: V26.10.10
-- Description: BOOLEAN → SMALLINT 迁移 + active_flag 重命名为 is_active
-- Author:      ydsz-team
-- Created:     2026-10-10
-- =============================================================================
--
-- 合规：YDIZ-DB-007（P0 布尔类型统一 SMALLINT）；YDIZ-OOP-006（数据库列必须 is_xxx 前缀）
--
-- 各 Section 变更一览：
--   Section 1: ydzs_comm_outbox / ydzs_comm_outbox_archive — compressed → is_compressed (SMALLINT)
--   Section 2: ydzs_comm_search_index_partitioned — active_flag → is_active (SMALLINT)
--   Section 3: ydzs_gen_template_group / ydzs_gen_template / ydzs_file_file_version — active_flag → is_active (SMALLINT)
--
-- 执行建议:
--   - 建议在低峰期执行，ALTER TABLE ADD/DROP 涉及表级锁
--   - PostgreSQL 17+ 支持 IF NOT EXISTS / IF EXISTS 语法
-- =============================================================================

-- =============================================================================
-- Section 1: ydzs_comm_outbox / ydzs_comm_outbox_archive — compressed → is_compressed
-- =============================================================================

-- 1.1 ydsz_comm_outbox: 如果存在旧列 compressed (BOOLEAN)，迁移为 is_compressed (SMALLINT)
ALTER TABLE ydsz_comm_outbox ADD COLUMN IF NOT EXISTS is_compressed SMALLINT NOT NULL DEFAULT 0;
UPDATE ydsz_comm_outbox SET is_compressed = CASE WHEN compressed THEN 1 ELSE 0 END WHERE compressed IS NOT NULL;
ALTER TABLE ydsz_comm_outbox DROP COLUMN IF EXISTS compressed;
COMMENT ON COLUMN ydsz_comm_outbox.is_compressed IS 'payload 是否 GZIP 压缩存储';

-- 1.2 ydsz_comm_outbox_archive: 同上
ALTER TABLE ydsz_comm_outbox_archive ADD COLUMN IF NOT EXISTS is_compressed SMALLINT NOT NULL DEFAULT 0;
UPDATE ydsz_comm_outbox_archive SET is_compressed = CASE WHEN compressed THEN 1 ELSE 0 END WHERE compressed IS NOT NULL;
ALTER TABLE ydsz_comm_outbox_archive DROP COLUMN IF EXISTS compressed;
COMMENT ON COLUMN ydsz_comm_outbox_archive.is_compressed IS 'payload 是否 GZIP 压缩存储';

-- =============================================================================
-- Section 2: ydsz_comm_search_index_partitioned — active_flag → is_active
-- =============================================================================

ALTER TABLE ydsz_comm_search_index_partitioned ADD COLUMN IF NOT EXISTS is_active SMALLINT NOT NULL DEFAULT 1;
UPDATE ydsz_comm_search_index_partitioned SET is_active = CASE WHEN active_flag THEN 1 ELSE 0 END WHERE active_flag IS NOT NULL;
ALTER TABLE ydsz_comm_search_index_partitioned DROP COLUMN IF EXISTS active_flag;

-- =============================================================================
-- Section 3: ydzs_gen_template_group / ydzs_gen_template / ydsz_file_file_version — active_flag → is_active
-- =============================================================================

ALTER TABLE ydsz_gen_template_group ADD COLUMN IF NOT EXISTS is_active SMALLINT NOT NULL DEFAULT 1;
UPDATE ydsz_gen_template_group SET is_active = CASE WHEN active_flag THEN 1 ELSE 0 END WHERE active_flag IS NOT NULL;
ALTER TABLE ydsz_gen_template_group DROP COLUMN IF EXISTS active_flag;

ALTER TABLE ydzs_gen_template ADD COLUMN IF NOT EXISTS is_active SMALLINT NOT NULL DEFAULT 1;
UPDATE ydzs_gen_template SET is_active = CASE WHEN active_flag THEN 1 ELSE 0 END WHERE active_flag IS NOT NULL;
ALTER TABLE ydzs_gen_template DROP COLUMN IF EXISTS active_flag;

ALTER TABLE ydzs_file_file_version ADD COLUMN IF NOT EXISTS is_active SMALLINT NOT NULL DEFAULT 1;
UPDATE ydsz_file_file_version SET is_active = CASE WHEN active_flag THEN 1 ELSE 0 END WHERE active_flag IS NOT NULL;
ALTER TABLE ydzs_file_file_version DROP COLUMN IF EXISTS active_flag;
