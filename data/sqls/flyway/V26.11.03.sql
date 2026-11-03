-- =============================================================================
-- V26.11.03 — 待优化索引补齐（来自 data/sqls/flyway/INDEX_ANALYSIS.md）
-- =============================================================================
--
-- 背景：
--   INDEX_ANALYSIS.md 列出了 22 条未索引的外键/查询字段（P0 × 1、P1 × 12、P2 × 9），
--   但此前从未落地为实际 DDL。本脚本按清单补建 P0 + P1 共 13 条索引。
--
-- 与 INDEX_ANALYSIS.md 的差异（已逐条核对真实表结构后修正）：
--   1. ydsz_flow_instance.start_user_id —— 该列不存在，真实列为 initiator_id
--   2. ydsz_file_file_node.space_id —— 该列不存在；空间维度通过 path 前缀定位，
--      实际查询走 parent_id + tenant_id 与 path 前缀（见 FileNodeRepositoryImpl）
--   3. ydsz_file_file_node.parent_node_id —— 真实列为 parent_id
--   4. INDEX_ANALYSIS.md 建议的 idx_ydsi_* 前缀为笔误，本脚本统一用 idx_ydsz_*
--
-- 执行方式：
--   - 全部使用 CREATE INDEX CONCURRENTLY IF NOT EXISTS，可在业务不中断的情况下执行
--   - CONCURRENTLY 不能在事务块内运行，故本脚本不设 BEGIN/COMMIT
--   - 大表首次构建耗时较长（ydsz_msg_log / ydsz_comm_audit_log），建议在低峰期执行
--   - 每条索引执行前会检查同名索引是否已存在（IF NOT EXISTS）
--
-- 回滚：
--   DROP INDEX CONCURRENTLY IF EXISTS <index_name>;
--
-- =============================================================================

-- ---------- P0：待办任务按办理人查询 ----------
-- 场景：流程中心「我的待办」列表，WHERE assignee_id = ? AND tenant_id = ? AND is_deleted = 0
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsz_flow_task_assignee
    ON "public"."ydsz_flow_run_task" (assignee_id, tenant_id, is_deleted);

-- ---------- P1 ----------

-- 流程实例「我发起的」查询
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsz_flow_instance_starter
    ON "public"."ydsz_flow_instance" (initiator_id, tenant_id, is_deleted);

-- 流程任务节点维度查询
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsz_flow_task_node_code
    ON "public"."ydsz_flow_run_task" (node_code, tenant_id, is_deleted);

-- 文件目录树子节点查询（FileNodeRepositoryImpl.findChildren）
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsz_filenode_parent
    ON "public"."ydsz_file_file_node" (parent_id, tenant_id, is_deleted);

-- 文件路径前缀查询（FileNodeRepositoryImpl.findByPathPrefix / batchSoftDeleteByPathPrefix）
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsz_filenode_path
    ON "public"."ydsz_file_file_node" (path, tenant_id);

-- 审计日志按业务编号反查审计链
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsz_comm_audit_log_business_no
    ON "public"."ydsz_comm_audit_log" (business_no, tenant_id);

-- Outbox 链路追踪
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsz_comm_outbox_trace_id
    ON "public"."ydsz_comm_outbox" (trace_id, tenant_id);

-- Agent 异步任务用户维度查询
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsz_agt_async_task_user_id
    ON "public"."ydsz_agt_async_task" (user_id, tenant_id, is_deleted);

-- Agent 追踪步骤类型筛选（LLM_CALL / TOOL_CALL）
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsz_agt_trace_step_type
    ON "public"."ydsz_agt_trace_step" (step_type, tenant_id);

-- 消息按接收人 + 状态查询
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsz_msg_log_receiver_status
    ON "public"."ydsz_msg_log" (receiver, status, tenant_id);

-- 消息按业务实体反查
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsz_msg_log_biz
    ON "public"."ydsz_msg_log" (biz_type, biz_id, tenant_id);

-- 规则按分类查询
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsz_rule_def_category
    ON "public"."ydsz_rule_def" (category, tenant_id, is_deleted);

-- Outbox 事件类型筛选
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsz_comm_outbox_event_type
    ON "public"."ydsz_comm_outbox" (event_type, tenant_id);

-- 审计日志慢请求筛选
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsz_comm_audit_log_cost_time
    ON "public"."ydsz_comm_audit_log" (cost_time);

-- =============================================================================
-- 索引命名修正：idx_ydsi_* → idx_ydsz_*（V26.10.08.sql 笔误）
-- =============================================================================
--
-- V26.10.08.sql 中 11 条 trigram GIN 索引误用 idx_ydsi_ 前缀（全仓统一为 idx_ydsz_）。
-- 按项目规范「已执行脚本不再修改」，此处新建修正脚本：
--   1) 先 DROP 错误命名的索引（IF EXISTS，秒级）
--   2) 再以正确名称重建（CONCURRENTLY，不阻塞业务）
--
-- 说明：这些索引与上面 idx_ydsz_rule_def_category 用途不同 ——
--   BTree 索引服务精确/范围查询；trigram GIN 服务 ILIKE '%kw%' 模糊搜索，两者都需要。

-- ---------- ydsz_file_search_index（nextwiki 搜索索引表）----------
-- 先创建正确命名的新索引，确认成功后再删除错误命名的旧索引，避免出现查询空窗。
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsz_file_search_index_name_trgm
    ON ydsz_file_search_index USING gin (name gin_trgm_ops);
DROP INDEX CONCURRENTLY IF EXISTS idx_ydsi_file_search_index_name_trgm;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsz_file_search_index_path_trgm
    ON ydsz_file_search_index USING gin (path gin_trgm_ops);
DROP INDEX CONCURRENTLY IF EXISTS idx_ydsi_file_search_index_path_trgm;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsz_file_search_index_content_trgm
    ON ydsz_file_search_index USING gin (content gin_trgm_ops);
DROP INDEX CONCURRENTLY IF EXISTS idx_ydsi_file_search_index_content_trgm;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsz_file_search_index_tags_trgm
    ON ydsz_file_search_index USING gin (tags gin_trgm_ops);
DROP INDEX CONCURRENTLY IF EXISTS idx_ydsi_file_search_index_tags_trgm;

-- ---------- ydsz_rule_def（规则引擎规则定义表）----------
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsz_rule_def_rule_code_trgm
    ON ydsz_rule_def USING gin (rule_code gin_trgm_ops);
DROP INDEX CONCURRENTLY IF EXISTS idx_ydsi_rule_def_rule_code_trgm;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsz_rule_def_rule_name_trgm
    ON ydsz_rule_def USING gin (rule_name gin_trgm_ops);
DROP INDEX CONCURRENTLY IF EXISTS idx_ydsi_rule_def_rule_name_trgm;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsz_rule_def_description_trgm
    ON ydsz_rule_def USING gin (description gin_trgm_ops);
DROP INDEX CONCURRENTLY IF EXISTS idx_ydsi_rule_def_description_trgm;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsz_rule_def_condition_expr_trgm
    ON ydsz_rule_def USING gin (condition_expression gin_trgm_ops);
DROP INDEX CONCURRENTLY IF EXISTS idx_ydsi_rule_def_condition_expr_trgm;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsz_rule_def_category_trgm
    ON ydsz_rule_def USING gin (category gin_trgm_ops);
DROP INDEX CONCURRENTLY IF EXISTS idx_ydsi_rule_def_category_trgm;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsz_rule_def_category_path_trgm
    ON ydsz_rule_def USING gin (category_path gin_trgm_ops);
DROP INDEX CONCURRENTLY IF EXISTS idx_ydsi_rule_def_category_path_trgm;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ydsz_rule_def_owner_trgm
    ON ydsz_rule_def USING gin (owner gin_trgm_ops);
DROP INDEX CONCURRENTLY IF EXISTS idx_ydsi_rule_def_owner_trgm;

-- =============================================================================
-- 执行后验证（可选，DBA 人工确认）：
--
--   -- 确认索引已建立
--   SELECT indexname, indexdef FROM pg_indexes
--    WHERE schemaname = 'public'
--      AND indexname LIKE 'idx_ydsz_%'
--      AND indexname IN (
--        'idx_ydsz_flow_task_assignee','idx_ydsz_flow_instance_starter',
--        'idx_ydsz_flow_task_node_code','idx_ydsz_filenode_parent',
--        'idx_ydsz_filenode_path','idx_ydsz_comm_audit_log_business_no',
--        'idx_ydsz_comm_outbox_trace_id','idx_ydsz_agt_async_task_user_id',
--        'idx_ydsz_agt_trace_step_type','idx_ydsz_msg_log_receiver_status',
--        'idx_ydsz_msg_log_biz','idx_ydsz_rule_def_category',
--        'idx_ydsz_comm_outbox_event_type','idx_ydsz_comm_audit_log_cost_time'
--      ) ORDER BY indexname;
--
--   -- 确认查询命中索引（以「我的待办」为例）
--   EXPLAIN (ANALYZE, BUFFERS)
--   SELECT * FROM ydsz_flow_run_task
--    WHERE assignee_id = 'xxx' AND tenant_id = '0' AND is_deleted = 0;
--   -- 期望：Index Scan using idx_ydsz_flow_task_assignee
-- =============================================================================
