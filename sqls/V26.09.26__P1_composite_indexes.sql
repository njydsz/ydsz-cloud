-- ============================================================================
-- V26.09.26: P1 核心表复合索引补齐（PostgreSQL 方言）
-- 解决慢查询：高频 WHERE 条件缺少复合索引导致全表扫描
--
-- ⚠️  原 MySQL 脚本中的表名已按 PostgreSQL 实际 DDL 对齐：
--   - ydsz_cronjob_log  → ydsz_job_log（无 tenant_id，使用 job_key 替代）
--   - ydsz_nextwiki_file_node → ydsz_wiki_file_node
--   - ydsz_user_session  → 该表在 PostgreSQL 模块中不存在，已移除
--   - ydsz_workflow_instance → ydsz_flow_instance（def_code → flow_code）
--   - ydsz_literule_version  → ydsz_rule_version_history（无 tenant_id，
--     且 base DDL 已有 idx_ydsz_rule_version_history_rule_version，跳过）
--
-- 执行方式: psql -U <user> -d <db> -f V26.09.26__P1_composite_indexes.sql
-- ============================================================================

-- 【message】渠道消息历史查询：按租户+渠道+时间范围
-- 高频场景：消息列表按渠道过滤 + 时间倒序分页
CREATE INDEX IF NOT EXISTS idx_ydsz_msg_log_tenant_channel_time
    ON ydsz_msg_log (tenant_id, channel, created_at);

-- 【cronjob】任务执行历史检索：按任务编码+状态+创建时间
-- 高频场景：调度日志查询（注：ydsz_job_log 无 tenant_id 列，按 job_key 替代）
CREATE INDEX IF NOT EXISTS idx_ydsz_job_log_job_key_status_created
    ON ydsz_job_log (job_key, status, created_at);

-- 【nextwiki】目录树懒加载：按租户+父节点+删除标记
-- 高频场景：file_node 目录树展开时过滤已删除子节点
CREATE INDEX IF NOT EXISTS idx_ydsz_wiki_file_node_tenant_parent_is_deleted
    ON ydsz_wiki_file_node (tenant_id, parent_id, is_deleted);

-- 【workflow】流程实例查询优化：按租户+流程编码+创建时间
-- 高频场景：流程实例列表按 flow_code 过滤 + 时间排序
CREATE INDEX IF NOT EXISTS idx_ydsz_flow_instance_tenant_flow_code_time
    ON ydsz_flow_instance (tenant_id, flow_code, created_at);
