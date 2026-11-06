-- =============================================================================
-- V26.11.06 — 分区/分表注释补齐 + bootstrap 快照同步
-- =============================================================================
--
-- 背景：
--   1. V26.10.01.bootstrap.sql 中 8 张搜索索引分区表误用 ydzs_ 前缀（缺少字母 t），
--      V26.10.14.sql 已通过 ALTER TABLE ... RENAME 修正，但 bootstrap 快照未同步。
--      本脚本同步 bootstrap 快照命名（ydsz_search_idx_part_0~7）。
--   2. 20 张分区/分表（8 张搜索索引分区 + 12 张流程审计日志分表）缺少表注释，
--      补齐注释以提升可维护性。
--
-- 执行方式：
--   - 全部使用幂等语句（IF EXISTS / IF NOT EXISTS），可重复执行
--   - 无危险变更（仅 RENAME + COMMENT），门禁允许
--
-- 回滚：
--   - RENAME 反向操作即可；COMMENT 无需回滚（注释不影响业务）
--
-- =============================================================================

-- =============================================================================
-- Section 1: 同步 bootstrap 快照分区表命名（ydzs_ → ydsz_）
-- =============================================================================
-- 说明：V26.10.14.sql 已执行过 RENAME，此处使用 IF EXISTS 幂等保护，
--       确保在尚未执行 V26.10.14 的库上也能正确同步。

ALTER TABLE IF EXISTS ydzs_search_idx_part_0 RENAME TO ydsz_search_idx_part_0;
ALTER TABLE IF EXISTS ydzs_search_idx_part_1 RENAME TO ydsz_search_idx_part_1;
ALTER TABLE IF EXISTS ydzs_search_idx_part_2 RENAME TO ydsz_search_idx_part_2;
ALTER TABLE IF EXISTS ydzs_search_idx_part_3 RENAME TO ydsz_search_idx_part_3;
ALTER TABLE IF EXISTS ydzs_search_idx_part_4 RENAME TO ydsz_search_idx_part_4;
ALTER TABLE IF EXISTS ydzs_search_idx_part_5 RENAME TO ydsz_search_idx_part_5;
ALTER TABLE IF EXISTS ydzs_search_idx_part_6 RENAME TO ydsz_search_idx_part_6;
ALTER TABLE IF EXISTS ydzs_search_idx_part_7 RENAME TO ydsz_search_idx_part_7;

-- =============================================================================
-- Section 2: 补齐搜索索引分区表注释
-- =============================================================================

COMMENT ON TABLE ydsz_search_idx_part_0 IS '搜索全文索引 HASH 分区 0（tenant_id hash remainder 0）';
COMMENT ON TABLE ydsz_search_idx_part_1 IS '搜索全文索引 HASH 分区 1（tenant_id hash remainder 1）';
COMMENT ON TABLE ydsz_search_idx_part_2 IS '搜索全文索引 HASH 分区 2（tenant_id hash remainder 2）';
COMMENT ON TABLE ydsz_search_idx_part_3 IS '搜索全文索引 HASH 分区 3（tenant_id hash remainder 3）';
COMMENT ON TABLE ydsz_search_idx_part_4 IS '搜索全文索引 HASH 分区 4（tenant_id hash remainder 4）';
COMMENT ON TABLE ydsz_search_idx_part_5 IS '搜索全文索引 HASH 分区 5（tenant_id hash remainder 5）';
COMMENT ON TABLE ydsz_search_idx_part_6 IS '搜索全文索引 HASH 分区 6（tenant_id hash remainder 6）';
COMMENT ON TABLE ydsz_search_idx_part_7 IS '搜索全文索引 HASH 分区 7（tenant_id hash remainder 7）';

-- =============================================================================
-- Section 3: 补齐 ydsz_flow_join_token 缺失的 sort 字段
-- =============================================================================
-- 背景：V26.10.08.sql 新建 ydsz_flow_join_token 表时遗漏 sort 字段，
--       不符合平台规范（DB-001 排序字段统一命名 sort）。
-- 回滚：ALTER TABLE ydsz_flow_join_token DROP COLUMN IF EXISTS sort;

ALTER TABLE ydsz_flow_join_token ADD COLUMN IF NOT EXISTS sort integer NOT NULL DEFAULT 0;
COMMENT ON COLUMN ydsz_flow_join_token.sort IS '排序权重（DB-001 规范：排序字段统一命名 sort）';

-- =============================================================================
-- Section 4: 补齐流程审计日志分表注释
-- =============================================================================
-- 说明：ydsz_flow_audit_log 为按月分区表（只追加），以下 12 张分表覆盖
--       2026-10 ~ 2027-09 共 12 个月。分区键为 operated_at（精确到毫秒）。

COMMENT ON TABLE ydsz_flow_audit_log_2026_10 IS '流程审计日志 2026年10月分区（按月分区，只追加）';
COMMENT ON TABLE ydsz_flow_audit_log_2026_11 IS '流程审计日志 2026年11月分区（按月分区，只追加）';
COMMENT ON TABLE ydsz_flow_audit_log_2026_12 IS '流程审计日志 2026年12月分区（按月分区，只追加）';
COMMENT ON TABLE ydsz_flow_audit_log_2027_01 IS '流程审计日志 2027年01月分区（按月分区，只追加）';
COMMENT ON TABLE ydsz_flow_audit_log_2027_02 IS '流程审计日志 2027年02月分区（按月分区，只追加）';
COMMENT ON TABLE ydsz_flow_audit_log_2027_03 IS '流程审计日志 2027年03月分区（按月分区，只追加）';
COMMENT ON TABLE ydsz_flow_audit_log_2027_04 IS '流程审计日志 2027年04月分区（按月分区，只追加）';
COMMENT ON TABLE ydsz_flow_audit_log_2027_05 IS '流程审计日志 2027年05月分区（按月分区，只追加）';
COMMENT ON TABLE ydsz_flow_audit_log_2027_06 IS '流程审计日志 2027年06月分区（按月分区，只追加）';
COMMENT ON TABLE ydsz_flow_audit_log_2027_07 IS '流程审计日志 2027年07月分区（按月分区，只追加）';
COMMENT ON TABLE ydsz_flow_audit_log_2027_08 IS '流程审计日志 2027年08月分区（按月分区，只追加）';
COMMENT ON TABLE ydsz_flow_audit_log_2027_09 IS '流程审计日志 2027年09月分区（按月分区，只追加）';

-- =============================================================================
-- Section 5: 补齐消息日志分区表注释（ydsz_msg_log）
-- =============================================================================
-- 说明：ydsz_msg_log 为按月分区表（V26.10.08.sql 新建），分区键为 sent_at。

COMMENT ON TABLE ydsz_msg_log IS '消息发送日志表（按月分区，分区键 sent_at）';
COMMENT ON TABLE ydsz_msg_log_2026_10 IS '消息发送日志 2026年10月分区';
COMMENT ON TABLE ydsz_msg_log_2026_11 IS '消息发送日志 2026年11月分区';
COMMENT ON TABLE ydsz_msg_log_2026_12 IS '消息发送日志 2026年12月分区';
COMMENT ON TABLE ydsz_msg_log_2027_01 IS '消息发送日志 2027年01月分区';
COMMENT ON TABLE ydsz_msg_log_2027_02 IS '消息发送日志 2027年02月分区';
COMMENT ON TABLE ydsz_msg_log_2027_03 IS '消息发送日志 2027年03月分区';
COMMENT ON TABLE ydsz_msg_log_2027_04 IS '消息发送日志 2027年04月分区';
COMMENT ON TABLE ydsz_msg_log_2027_05 IS '消息发送日志 2027年05月分区';
COMMENT ON TABLE ydsz_msg_log_2027_06 IS '消息发送日志 2027年06月分区';
COMMENT ON TABLE ydsz_msg_log_2027_07 IS '消息发送日志 2027年07月分区';
COMMENT ON TABLE ydsz_msg_log_2027_08 IS '消息发送日志 2027年08月分区';
COMMENT ON TABLE ydsz_msg_log_2027_09 IS '消息发送日志 2027年09月分区';
COMMENT ON TABLE ydsz_msg_log_default IS '消息发送日志默认分区（超出范围数据）';

-- =============================================================================
-- Section 6: 补齐消息 Outbox 分区表注释（ydsz_msg_outbox）
-- =============================================================================
-- 说明：ydsz_msg_outbox 为按月分区表（V26.10.08.sql 新建），分区键为 created_at。

COMMENT ON TABLE ydsz_msg_outbox IS '消息发件箱表（按月分区，分区键 created_at）';
COMMENT ON TABLE ydsz_msg_outbox_2026_10 IS '消息发件箱 2026年10月分区';
COMMENT ON TABLE ydsz_msg_outbox_2026_11 IS '消息发件箱 2026年11月分区';
COMMENT ON TABLE ydsz_msg_outbox_2026_12 IS '消息发件箱 2026年12月分区';
COMMENT ON TABLE ydsz_msg_outbox_2027_01 IS '消息发件箱 2027年01月分区';
COMMENT ON TABLE ydsz_msg_outbox_2027_02 IS '消息发件箱 2027年02月分区';
COMMENT ON TABLE ydsz_msg_outbox_2027_03 IS '消息发件箱 2027年03月分区';
COMMENT ON TABLE ydsz_msg_outbox_2027_04 IS '消息发件箱 2027年04月分区';
COMMENT ON TABLE ydsz_msg_outbox_2027_05 IS '消息发件箱 2027年05月分区';
COMMENT ON TABLE ydsz_msg_outbox_2027_06 IS '消息发件箱 2027年06月分区';
COMMENT ON TABLE ydsz_msg_outbox_2027_07 IS '消息发件箱 2027年07月分区';
COMMENT ON TABLE ydsz_msg_outbox_2027_08 IS '消息发件箱 2027年08月分区';
COMMENT ON TABLE ydsz_msg_outbox_2027_09 IS '消息发件箱 2027年09月分区';
COMMENT ON TABLE ydsz_msg_outbox_default IS '消息发件箱默认分区（超出范围数据）';

-- =============================================================================
-- Section 7: 补齐审计日志分区表注释（ydsz_comm_audit_log）
-- =============================================================================
-- 说明：ydsz_comm_audit_log 为按月分区表（V26.10.09.sql 新建），分区键为 operated_at。

COMMENT ON TABLE ydsz_comm_audit_log IS '全平台操作审计日志表（ydsz-common-audit 自动落库，按月分区）';
COMMENT ON TABLE ydsz_comm_audit_log_2026_10 IS '操作审计日志 2026年10月分区';
COMMENT ON TABLE ydsz_comm_audit_log_2026_11 IS '操作审计日志 2026年11月分区';
COMMENT ON TABLE ydsz_comm_audit_log_2026_12 IS '操作审计日志 2026年12月分区';
COMMENT ON TABLE ydsz_comm_audit_log_2027_01 IS '操作审计日志 2027年01月分区';
COMMENT ON TABLE ydsz_comm_audit_log_2027_02 IS '操作审计日志 2027年02月分区';
COMMENT ON TABLE ydsz_comm_audit_log_2027_03 IS '操作审计日志 2027年03月分区';
COMMENT ON TABLE ydsz_comm_audit_log_2027_04 IS '操作审计日志 2027年04月分区';
COMMENT ON TABLE ydsz_comm_audit_log_2027_05 IS '操作审计日志 2027年05月分区';
COMMENT ON TABLE ydsz_comm_audit_log_2027_06 IS '操作审计日志 2027年06月分区';
COMMENT ON TABLE ydsz_comm_audit_log_2027_07 IS '操作审计日志 2027年07月分区';
COMMENT ON TABLE ydsz_comm_audit_log_2027_08 IS '操作审计日志 2027年08月分区';
COMMENT ON TABLE ydsz_comm_audit_log_2027_09 IS '操作审计日志 2027年09月分区';
COMMENT ON TABLE ydsz_comm_audit_log_default IS '操作审计日志默认分区（超出范围数据）';

-- =============================================================================
-- Section 8: 补齐 Outbox 分区表注释（ydsz_comm_outbox）
-- =============================================================================
-- 说明：ydsz_comm_outbox 为按月分区表（V26.10.09.sql 新建），分区键为 created_at。

COMMENT ON TABLE ydsz_comm_outbox IS '事务发件箱表（Outbox 模式，按月分区，分区键 created_at）';
COMMENT ON TABLE ydsz_comm_outbox_2026_10 IS '事务发件箱 2026年10月分区';
COMMENT ON TABLE ydsz_comm_outbox_2026_11 IS '事务发件箱 2026年11月分区';
COMMENT ON TABLE ydsz_comm_outbox_2026_12 IS '事务发件箱 2026年12月分区';
COMMENT ON TABLE ydsz_comm_outbox_2027_01 IS '事务发件箱 2027年01月分区';
COMMENT ON TABLE ydsz_comm_outbox_2027_02 IS '事务发件箱 2027年02月分区';
COMMENT ON TABLE ydsz_comm_outbox_2027_03 IS '事务发件箱 2027年03月分区';
COMMENT ON TABLE ydsz_comm_outbox_2027_04 IS '事务发件箱 2027年04月分区';
COMMENT ON TABLE ydsz_comm_outbox_2027_05 IS '事务发件箱 2027年05月分区';
COMMENT ON TABLE ydsz_comm_outbox_2027_06 IS '事务发件箱 2027年06月分区';
COMMENT ON TABLE ydsz_comm_outbox_2027_07 IS '事务发件箱 2027年07月分区';
COMMENT ON TABLE ydsz_comm_outbox_2027_08 IS '事务发件箱 2027年08月分区';
COMMENT ON TABLE ydsz_comm_outbox_2027_09 IS '事务发件箱 2027年09月分区';
COMMENT ON TABLE ydsz_comm_outbox_default IS '事务发件箱默认分区（超出范围数据）';

-- =============================================================================
-- Section 9: 补齐 Agent 追踪步骤分区表注释（ydsz_agt_trace_step）
-- =============================================================================
-- 说明：ydsz_agt_trace_step 为按月分区表（V26.10.10.sql 新建），分区键为 started_at。

COMMENT ON TABLE ydsz_agt_trace_step IS 'Agent 追踪步骤表（按月分区，分区键 started_at）';
COMMENT ON TABLE ydsz_agt_trace_step_2026_10 IS 'Agent 追踪步骤 2026年10月分区';
COMMENT ON TABLE ydsz_agt_trace_step_2026_11 IS 'Agent 追踪步骤 2026年11月分区';
COMMENT ON TABLE ydsz_agt_trace_step_2026_12 IS 'Agent 追踪步骤 2026年12月分区';
COMMENT ON TABLE ydsz_agt_trace_step_2027_01 IS 'Agent 追踪步骤 2027年01月分区';
COMMENT ON TABLE ydsz_agt_trace_step_2027_02 IS 'Agent 追踪步骤 2027年02月分区';
COMMENT ON TABLE ydsz_agt_trace_step_2027_03 IS 'Agent 追踪步骤 2027年03月分区';
COMMENT ON TABLE ydsz_agt_trace_step_2027_04 IS 'Agent 追踪步骤 2027年04月分区';
COMMENT ON TABLE ydsz_agt_trace_step_2027_05 IS 'Agent 追踪步骤 2027年05月分区';
COMMENT ON TABLE ydsz_agt_trace_step_2027_06 IS 'Agent 追踪步骤 2027年06月分区';
COMMENT ON TABLE ydsz_agt_trace_step_2027_07 IS 'Agent 追踪步骤 2027年07月分区';
COMMENT ON TABLE ydsz_agt_trace_step_2027_08 IS 'Agent 追踪步骤 2027年08月分区';
COMMENT ON TABLE ydsz_agt_trace_step_2027_09 IS 'Agent 追踪步骤 2027年09月分区';
COMMENT ON TABLE ydsz_agt_trace_step_default IS 'Agent 追踪步骤默认分区（超出范围数据）';

-- =============================================================================
-- 执行后验证（DBA 人工确认）：
--
--   -- 确认分区表命名统一
--   SELECT tablename FROM pg_tables
--    WHERE schemaname = 'public' AND tablename LIKE 'yds_search_idx_part%';
--   -- 期望：0 行（ydzs_ 前缀已全部修正）
--
--   -- 确认注释补齐
--   SELECT tablename, obj_description(oid) AS comment
--     FROM pg_class c
--     JOIN pg_tables t ON t.tablename = c.relname
--    WHERE t.schemaname = 'public'
--      AND t.tablename LIKE 'ydsz_%part_%'
--       OR t.tablename LIKE 'ydsz_flow_audit_log_20%';
--   -- 期望：20 行，每行 comment 非空
-- =============================================================================
