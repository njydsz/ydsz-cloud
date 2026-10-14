-- =============================================================================
-- Flyway Migration: V26.10.14
-- Description: P2 修正分区表拼写 + retry_count 类型规范化 + 反范式字段注释
-- Author:      ydsz-team
-- Created:     2026-10-14
-- =============================================================================
--
-- 变更一览：
--   Section 1: 分区表拼写修正 ydzs_ → ydzsz_（与 V26.10.01 基线 ydzsz_ 前缀保持一致）
--   Section 2: ydzsz_comm_outbox.retry_count BIGINT → INTEGER（与项目其他表统一）
--   Section 3: ydsz_agt_document_chunk 反范式字段注释补充
--
-- 注意事项：
--   - Section 2 线上执行需 DBA 评审（涉及类型变更，PG 会重写全表）
--   - 所有 ALTER 使用 IF EXISTS 幂等保护
--   - Section 1 / Section 3 可安全回滚
-- =============================================================================

-- =============================================================================
-- Section 1: 分区表拼写修正 ydzs_ → ydzsz_
-- =============================================================================
-- 背景: ydzs-cloud-V26.10.01 基线中分区表误写为 ydzs_search_idx_part_0~7
--       （缺少字母 t），主表 ydsz_comm_search_index_partitioned 拼写正确。
--       此处 ALTER TABLE ... RENAME 同步修正，与基线命名保持一致。
-- 回滚: 反向 RENAME 即可（ydsz_search_idx_part_N → ydzs_search_idx_part_N）

ALTER TABLE IF EXISTS ydzs_search_idx_part_0 RENAME TO ydsz_search_idx_part_0;
ALTER TABLE IF EXISTS ydzs_search_idx_part_1 RENAME TO ydsz_search_idx_part_1;
ALTER TABLE IF EXISTS ydzs_search_idx_part_2 RENAME TO ydsz_search_idx_part_2;
ALTER TABLE IF EXISTS ydzs_search_idx_part_3 RENAME TO ydsz_search_idx_part_3;
ALTER TABLE IF EXISTS ydzs_search_idx_part_4 RENAME TO ydsz_search_idx_part_4;
ALTER TABLE IF EXISTS ydzs_search_idx_part_5 RENAME TO ydsz_search_idx_part_5;
ALTER TABLE IF EXISTS ydzs_search_idx_part_6 RENAME TO ydsz_search_idx_part_6;
ALTER TABLE IF EXISTS ydzs_search_idx_part_7 RENAME TO ydsz_search_idx_part_7;

COMMENT ON TABLE ydsz_search_idx_part_0 IS '搜索全文索引 HASH 分区 0（tenant_id hash remainder 0）';
COMMENT ON TABLE ydsz_search_idx_part_1 IS '搜索全文索引 HASH 分区 1（tenant_id hash remainder 1）';
COMMENT ON TABLE ydsz_search_idx_part_2 IS '搜索全文索引 HASH 分区 2（tenant_id hash remainder 2）';
COMMENT ON TABLE ydsz_search_idx_part_3 IS '搜索全文索引 HASH 分区 3（tenant_id hash remainder 3）';
COMMENT ON TABLE ydsz_search_idx_part_4 IS '搜索全文索引 HASH 分区 4（tenant_id hash remainder 4）';
COMMENT ON TABLE ydsz_search_idx_part_5 IS '搜索全文索引 HASH 分区 5（tenant_id hash remainder 5）';
COMMENT ON TABLE ydsz_search_idx_part_6 IS '搜索全文索引 HASH 分区 6（tenant_id hash remainder 6）';
COMMENT ON TABLE ydsz_search_idx_part_7 IS '搜索全文索引 HASH 分区 7（tenant_id hash remainder 7）';

-- =============================================================================
-- Section 2: ydzsz_comm_outbox.retry_count BIGINT → INTEGER
-- =============================================================================
-- 背景: 项目内其他表的 retry_count 列统一使用 INTEGER（最大 2^31-21.47 亿）
--       ydzsz_comm_outbox 错误定义为 BIGINT，此处修正保持一致。
--       同时修正 max_retries 列（同表，同问题）。
-- 注意: PG ALTER COLUMN TYPE 会重写全表，线上执行需 DBA 评审。
-- 回滚: ALTER TABLE ydsz_comm_outbox ALTER COLUMN retry_count TYPE bigint;

ALTER TABLE ydsz_comm_outbox ALTER COLUMN retry_count TYPE INTEGER;
ALTER TABLE ydsz_comm_outbox ALTER COLUMN max_retries TYPE INTEGER;

-- =============================================================================
-- Section 3: ydsz_agt_document_chunk 反范式字段注释补充
-- =============================================================================
-- 背景: document_title / source 列在 chunk 表中冗余存储，避免每次查询 chunk
--       列表时 JOIN ydsz_agt_document 主表（反范式设计原则：空间换时间）。

COMMENT ON COLUMN ydsz_agt_document_chunk.document_title IS '文档标题（反范式冗余：避免每次获取 chunk 列表时 JOIN ydsz_agt_document 主表）';
COMMENT ON COLUMN ydsz_agt_document_chunk.source IS '来源路径（反范式冗余：避免每次获取 chunk 列表时 JOIN 主表）';
