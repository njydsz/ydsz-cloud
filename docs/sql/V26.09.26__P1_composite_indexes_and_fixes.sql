-- ============================================================================
-- V26.09.26 — P1 数据库复合索引与表结构修复
-- ============================================================================
-- 本次变更：
--   1. workflow: ydsz_flow_run_task 复合索引 (assignee_id, task_status, status, is_deleted)
--   2. cronjob:  ydz_job_main 复合索引 (status, is_deleted, next_fire_time)
--   3. userinfo: ydz_acct_user email 单列索引
--   4. agent:    删除 ydsz_agt_approval 冗余索引 (tenant_id 左前缀)
--   5. ANALYZE 收集统计信息
--
-- 执行环境：PostgreSQL 16+
-- 执行时间：约 1-5 分钟（取决于数据量）
-- 回滚策略：每个索引独立，可单独 DROP
-- ============================================================================

-- ============================================================================
-- 1. workflow: ydz_flow_run_task 复合索引
-- ============================================================================
-- 优化目标：待办查询 selectTodoByAssignee / selectDoneByAssignee / countTodoByAssignee
-- 原索引：仅 idx_ydsz_flow_run_task_assignee_id (单列)
-- 新索引：同时覆盖 assignee_id + task_status + status + is_deleted

CREATE INDEX IF NOT EXISTS idx_flow_run_task_assignee_status
    ON ydsz_flow_run_task (assignee_id, task_status, status, is_deleted);

-- ============================================================================
-- 2. cronjob: ydz_job_main 复合索引
-- ============================================================================
-- 优化目标：调度扫描 selectDueJobs (status='NORMAL' AND is_deleted=0 AND next_fire_time<=now)
-- 原索引：仅 idx_ydsz_job_job_next_fire (单列 next_fire_time)
-- 新索引：覆盖调度扫描完整过滤条件

CREATE INDEX IF NOT EXISTS idx_job_main_due_scan
    ON ydz_job_main (status, is_deleted, next_fire_time);

-- ============================================================================
-- 3. userinfo: ydz_acct_user email 索引
-- ============================================================================
-- 优化目标：邮箱登录查询（高频场景）
-- 现代系统邮箱登录已是必备功能，缺失索引导致全表扫描

CREATE INDEX IF NOT EXISTS idx_acct_user_email
    ON ydz_acct_user (email);

-- ============================================================================
-- 4. agent: 删除 ydsz_agt_approval 冗余索引
-- ============================================================================
-- 原索引：
--   idx_ydsz_agt_approval_approval_tenant (tenant_id) — 冗余
--   idx_ydsz_agt_approval_tenant_created (tenant_id, created_at) — 保留
-- 前者是后者的左前缀，完全冗余

DROP INDEX IF EXISTS idx_ydsz_agt_approval_approval_tenant;

-- ============================================================================
-- 5. 收集统计信息（加速查询计划器适应新索引）
-- ============================================================================

ANALYZE ydsz_flow_run_task;
ANALYZE ydz_job_main;
ANALYZE ydz_acct_user;
ANALYZE ydsz_agt_approval;

-- ============================================================================
-- 回滚 SQL（需要时逐条执行）
-- ============================================================================
-- DROP INDEX IF EXISTS idx_flow_run_task_assignee_status;
-- DROP INDEX IF EXISTS idx_job_main_due_scan;
-- DROP INDEX IF EXISTS idx_acct_user_email;
-- CREATE INDEX IF NOT EXISTS idx_ydsz_agt_approval_approval_tenant ON ydsz_agt_approval (tenant_id);
-- ============================================================================
