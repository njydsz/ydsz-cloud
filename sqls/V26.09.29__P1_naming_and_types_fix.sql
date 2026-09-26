-- ============================================================================
-- V26.09.29: P1/P2 命名规范与数据类型统一迁移
--
-- 修复项：
--   1. ydsz_job_history.history_deleted → is_deleted（YDIZ-OOP-006 布尔命名统一）
--   2. ydsz_flow_category.sort_num → sort（YDIZ-DB-001 排序字段统一）
--   3. ydsz_agt_dag_workflow.is_deleted BOOLEAN → SMALLINT
--   4. ydsz_agt_async_task.is_deleted BOOLEAN → SMALLINT
--   5. ydsz_agt_document_chunk.is_deleted BOOLEAN → SMALLINT
--   6. ydsz_agt_definition.temperature DOUBLE PRECISION → NUMERIC(5,2)
--   7. 高频复合索引补充（flow_run_task / job_main / acct_user.email）
--   8. ydsz_agt_async_task.tenant_code → tenant_id（租户字段命名统一）
--
-- 执行方式: psql -U <user> -d <db> -f V26.09.29__P1_naming_and_types_fix.sql
-- 回滚: 见文件末尾 ROLLBACK 段落（如需）
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. ydsz_job_history.history_deleted → is_deleted（YDIZ-OOP-006）
--    Java 实体已使用 @TableLogic isDeleted → is_deleted，DDL 需同步
-- ----------------------------------------------------------------------------
ALTER TABLE ydsz_job_history
    RENAME COLUMN history_deleted TO is_deleted;

COMMENT ON COLUMN ydsz_job_history.is_deleted IS '逻辑删除标识（0=未删除，1=已删除）';

-- ----------------------------------------------------------------------------
-- 2. ydsz_flow_category.sort_num → sort（YDIZ-DB-001，排序字段统一）
--    Java 实体使用 @TableField("sort_num")，DDL 统一为 sort
-- ----------------------------------------------------------------------------
ALTER TABLE ydsz_flow_category
    RENAME COLUMN sort_num TO sort;

-- 同步更新索引名
ALTER INDEX IF EXISTS idx_ydsz_flow_category_sort_num RENAME TO idx_ydsz_flow_category_sort;

COMMENT ON COLUMN ydsz_flow_category.sort IS '排序号（越小越靠前）';

-- ----------------------------------------------------------------------------
-- 3-5. Agent 模块 BOOLEAN → SMALLINT（统一布尔类型，消除存储和 JOIN 歧义）
-- ----------------------------------------------------------------------------
ALTER TABLE ydsz_agt_dag_workflow
    ALTER COLUMN is_deleted TYPE SMALLINT USING CASE WHEN is_deleted = TRUE THEN 1 ELSE 0 END;

ALTER TABLE ydsz_agt_async_task
    ALTER COLUMN is_deleted TYPE SMALLINT USING CASE WHEN is_deleted = TRUE THEN 1 ELSE 0 END;

ALTER TABLE ydsz_agt_document_chunk
    ALTER COLUMN is_deleted TYPE SMALLINT USING CASE WHEN is_deleted = TRUE THEN 1 ELSE 0 END;

-- ----------------------------------------------------------------------------
-- 6. ydsz_agt_definition.temperature 精度修复（DOUBLE PRECISION → NUMERIC(5,2)）
-- ----------------------------------------------------------------------------
ALTER TABLE ydsz_agt_definition
    ALTER COLUMN temperature TYPE NUMERIC(5,2);

COMMENT ON COLUMN ydsz_agt_definition.temperature IS '温度参数（NUMERIC(5,2)，范围 0.00-2.00）';

-- ----------------------------------------------------------------------------
-- 7. 高频复合索引补充
-- ----------------------------------------------------------------------------

-- ydsz_flow_run_task: 增加 (assignee_id, status, is_deleted) 复合索引（任务列表过滤高频场景）
CREATE INDEX IF NOT EXISTS idx_ydsz_flow_run_task_assignee_status_is_deleted
    ON ydsz_flow_run_task (assignee_id, status, is_deleted);

-- ydsz_job_main: 增加 (status, is_deleted, next_fire_time) 复合索引（调度扫描高频场景）
CREATE INDEX IF NOT EXISTS idx_ydsz_job_main_status_is_deleted_next_fire
    ON ydsz_job_main (status, is_deleted, next_fire_time);

-- ydsz_acct_user: 增加 email 索引（登录/查找高频场景）
CREATE INDEX IF NOT EXISTS idx_ydsz_acct_user_email ON ydsz_acct_user (email);

-- ydsz_acct_user: 增加 (tenant_id, is_deleted) 复合索引（用户列表高频过滤）
CREATE INDEX IF NOT EXISTS idx_ydsz_acct_user_tenant_is_deleted ON ydsz_acct_user (tenant_id, is_deleted);

-- ----------------------------------------------------------------------------
-- 8. ydsz_agt_async_task.tenant_code → tenant_id（租户字段命名统一）
-- ----------------------------------------------------------------------------
ALTER TABLE ydsz_agt_async_task
    RENAME COLUMN tenant_code TO tenant_id;

-- 同步更新索引
ALTER INDEX IF EXISTS idx_ydsz_agt_async_task_tenant_status RENAME TO idx_ydsz_agt_async_task_tenant_id_status;

COMMENT ON COLUMN ydsz_agt_async_task.tenant_id IS '租户 ID（多租户隔离）';
