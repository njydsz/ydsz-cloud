-- ============================================================================
-- ydsz-workflow 索引补丁（P1-1: 补充组合索引）
-- ============================================================================
-- 适用数据库：PostgreSQL 16+
-- 创建时间：2026-08-16
-- 说明：基于高频查询模式分析，补充组合索引以优化查询性能
--       请在低峰期执行，建议使用 CONCURRENTLY 选项（不锁表）
--
-- 执行方式：
--   psql -U username -d database -f index_patch.sql
--   或在 PGAdmin 中逐条执行
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. ydsz_flow_run_task 表索引
-- ----------------------------------------------------------------------------

-- 1.1 待办查询组合索引（assignee_id + task_status + priority + created_at）
-- 覆盖场景：selectTodoByAssignee / selectTodoByAssigneePage / selectTodoByAssigneeCursor
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_run_task_assignee_status_priority
    ON ydsz_flow_run_task (assignee_id, task_status, priority DESC, created_at ASC, id ASC);

-- 1.2 实例+任务状态组合索引
-- 覆盖场景：selectPendingByInstance / countPendingByNode
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_run_task_instance_status
    ON ydsz_flow_run_task (instance_id, task_status)
    WHERE status = 'ENABLED' AND deleted = 0;

-- 1.3 实例+节点+任务状态组合索引
-- 覆盖场景：selectPendingByNode / countPendingByNode
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_run_task_instance_node_status
    ON ydsz_flow_run_task (instance_id, node_code, task_status)
    WHERE status = 'ENABLED' AND deleted = 0;

-- 1.4 SLA 扫描索引（due_at + task_status）
-- 覆盖场景：selectOverdue / countOverdue / selectSlaCandidates
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_run_task_due_status
    ON ydsz_flow_run_task (due_at ASC)
    WHERE task_status IN ('PENDING', 'CLAIMED')
      AND status = 'ENABLED'
      AND deleted = 0
      AND due_at IS NOT NULL;

-- ----------------------------------------------------------------------------
-- 2. ydsz_flow_instance 表索引
-- ----------------------------------------------------------------------------

-- 2.1 业务类型+状态组合索引（管理员视图筛选）
-- 覆盖场景：listAllInstances 按 businessType + flowStatus 筛选
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_instance_business_status
    ON ydsz_flow_instance (business_type, flow_status, created_at DESC)
    WHERE status = 'ENABLED' AND deleted = 0;

-- 2.2 发起人+状态组合索引（"我发起的"查询）
-- 覆盖场景：按发起人查询流程实例
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_instance_initiator_status
    ON ydsz_flow_instance (initiator_id, flow_status, created_at DESC)
    WHERE status = 'ENABLED' AND deleted = 0;

-- 2.3 时间范围查询索引（用于趋势统计）
-- 覆盖场景：selectTodayCount / selectTrendByDate
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_instance_created_at
    ON ydsz_flow_instance (tenant_id, created_at DESC)
    WHERE status = 'ENABLED' AND deleted = 0;

-- ----------------------------------------------------------------------------
-- 3. ydsz_flow_his_task 表索引
-- ----------------------------------------------------------------------------

-- 3.1 已办查询组合索引（assignee_id + task_status + finish_at）
-- 覆盖场景：selectDoneByAssignee / selectDoneByAssigneePage
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_his_task_assignee_status_finish
    ON ydsz_flow_his_task (assignee_id, task_status, finish_at DESC)
    WHERE status = 'ENABLED' AND deleted = 0;

-- 3.2 实例维度历史任务索引
-- 覆盖场景：getTimeline / getReplaySteps
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_his_task_instance
    ON ydsz_flow_his_task (instance_id, finish_at ASC)
    WHERE status = 'ENABLED' AND deleted = 0;

-- ----------------------------------------------------------------------------
-- 4. ydsz_flow_audit_log 表索引
-- ----------------------------------------------------------------------------

-- 4.1 实例维度审计日志索引
-- 覆盖场景：listAuditTrail / getTimeline
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_log_instance
    ON ydsz_flow_audit_log (instance_id, operated_at ASC)
    WHERE deleted = 0;

-- ----------------------------------------------------------------------------
-- 5. ydsz_flow_definition 表索引
-- ----------------------------------------------------------------------------

-- 5.1 流程编码+发布状态索引
-- 覆盖场景：selectPublished / selectLatestByCode
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_definition_code_publish
    ON ydsz_flow_definition (flow_code, is_publish, version DESC)
    WHERE status = 'ENABLED' AND deleted = 0;

-- ----------------------------------------------------------------------------
-- 6. ydsz_flow_timer 表索引
-- ----------------------------------------------------------------------------

-- 6.1 定时器扫描索引（fire_at + status）
-- 覆盖场景：selectDueTimers
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_timer_fire_status
    ON ydsz_flow_timer (fire_at ASC, status)
    WHERE deleted = 0 AND status = 'PENDING';

-- ----------------------------------------------------------------------------
-- 7. ydsz_flow_attachment 表索引
-- ----------------------------------------------------------------------------

-- 7.1 MD5 去重索引（秒传功能）
-- 覆盖场景：selectByMd5 / 文件秒传查询
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_attachment_md5
    ON ydsz_flow_attachment (md5)
    WHERE deleted = 0 AND md5 IS NOT NULL;

-- ----------------------------------------------------------------------------
-- 8. ydsz_flow_comment 表索引
-- ----------------------------------------------------------------------------

-- 8.1 租户+实例组合索引（评论列表查询）
-- 覆盖场景：listByInstance / listRootComments
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_comment_tenant_instance
    ON ydsz_flow_comment (tenant_id, instance_id, created_at ASC)
    WHERE deleted = 0;

-- ----------------------------------------------------------------------------
-- 9. ydsz_flow_user 表索引
-- ----------------------------------------------------------------------------

-- 9.1 用户任务查询组合索引
-- 覆盖场景：selectTaskIdsByUser
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_task_query
    ON ydsz_flow_user (user_id, processed, task_status, tenant_id)
    WHERE deleted = 0;

-- ----------------------------------------------------------------------------
-- 10. ydsz_flow_cc 表索引
-- ----------------------------------------------------------------------------

-- 10.1 抄送人+租户组合索引
-- 覆盖场景：selectCcByUser / countUnread
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cc_user_tenant
    ON ydsz_flow_cc (cc_user_id, tenant_id, is_read, created_at DESC)
    WHERE deleted = 0;

-- ============================================================================
-- 执行完成后，建议运行 ANALYZE 更新统计信息
-- ============================================================================
-- ANALYZE ydsz_flow_run_task;
-- ANALYZE ydsz_flow_instance;
-- ANALYZE ydsz_flow_his_task;
-- ANALYZE ydsz_flow_audit_log;
-- ANALYZE ydsz_flow_definition;
-- ANALYZE ydsz_flow_timer;
-- ANALYZE ydsz_flow_attachment;
-- ANALYZE ydsz_flow_comment;
-- ANALYZE ydsz_flow_user;
-- ANALYZE ydsz_flow_cc;
-- ============================================================================
