-- =============================================================================
-- 性能优化索引增补（2026-11-06）
--
-- 来源：docs/reports 全面分析报告「性能维度」+ data/sqls/flyway/INDEX_ANALYSIS.md 待排期清单
-- 说明：本文件与既有 V26.11.06.sql 同名不同描述，迁移工具以「文件名（去 .sql）」为去重键，
--       故使用 V26.11.06__index-optimization.sql 避免与 V26.11.06.sql 冲突。
-- 执行方式：人工按版本号升序执行；CREATE INDEX CONCURRENTLY 需在低峰期执行（不阻塞写）。
-- 注意：PostgreSQL 的 CONCURRENTLY 不支持在事务块内执行，若迁移工具开启事务需去掉该关键字。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 工作流「我的待办」覆盖索引（P1，收益最高）
--
-- 对应查询：FlowRunTaskMapper.selectTodoByAssignee
--   WHERE assignee_id = ?
--     AND task_status IN ('PENDING','CLAIMED')
--     AND status = 'ENABLED'
--     AND is_deleted = 0
--   ORDER BY priority DESC, created_at ASC
--
-- 现有索引 idx_ydsz_flow_run_task_assignee_id(assignee_id) 与
-- idx_ydsz_flow_run_task_assignee_status_is_deleted(assignee_id, status, is_deleted)
-- 均不含 task_status 与排序字段，10 万级数据下会退化为「索引扫描 + 回表 + 排序」。
-- 新索引把筛选列与排序列全部纳入，实现纯 Index Scan。
-- -----------------------------------------------------------------------------
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_flow_task_todo_cover
    ON ydsz_flow_run_task (assignee_id, task_status, is_deleted, priority DESC, created_at ASC);

-- 批量待办查询（selectTodoByAssignees，assignee_id IN (...)）同样受益
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_flow_task_todo_cover_batch
    ON ydsz_flow_run_task (task_status, is_deleted, priority DESC, created_at ASC);

-- -----------------------------------------------------------------------------
-- 2. INDEX_ANALYSIS.md 待排期条目（P2）
-- -----------------------------------------------------------------------------

-- 2.1 Agent 异步任务 Worker 节点维度监控
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_agt_async_task_worker_id
    ON ydsz_agt_async_task (worker_id, created_at DESC);

-- 2.2 文档分块按文档 + 分块序号排序（RAG 检索组装上下文）
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_chunk_doc_chunk_idx
    ON ydsz_agt_document_chunk (document_id, chunk_index);

-- 2.3 文件引擎「最近修改」排序
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_filenode_updated_at
    ON ydsz_file_file_node (updated_at DESC);

-- 2.4 任务引擎任务类型筛选
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_job_task_type
    ON ydsz_job_task (task_type, created_at DESC);

-- 2.5 字典项租户级查询（数据权限拦截器注入 tenant_id 条件）
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_dict_item_tenant
    ON ydsz_sys_dict_item (tenant_id, is_deleted);

-- 2.6 流程 join 令牌过期清理
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_join_token_created_at
    ON ydsz_flow_join_token (created_at);

-- -----------------------------------------------------------------------------
-- 3. 未落地条目的澄清（2026-11-06 核对）
--
-- INDEX_ANALYSIS.md 曾建议为以下两表建索引，核对 bootstrap DDL 后确认列不存在：
--   - ydsz_job_main.dag_id        → 该表无 dag_id 列（DAG 关联在 ydsz_job_dag_instance）
--   - ydsz_job_task.dag_id       → 该表同样无 dag_id 列
-- 故本次不创建；如需 DAG 关联查询优化，应建在 ydsz_job_dag_instance.dag_id
-- （已有 idx_ydsz_job_dag_instance_di_dag_id，无需重复创建）。
-- -----------------------------------------------------------------------------
