-- =====================================================================
--  PMIS PostgreSQL 索引调优 SQL（批次 19）
-- ---------------------------------------------------------------------
--  适用版本：PostgreSQL 16.x
--  执行方式：psql -f index-tuning.sql -U pmis_app -d pmis
--  用途：补全 200+ 表的复合索引/部分索引/BRIN/表达式索引，覆盖 4 阶段新模块
-- =====================================================================

SET client_min_messages = WARNING;
SET statement_timeout = '5min';

-- =====================================================================
--  1) 通用审计字段索引（created_at 范围查询 + tenant_id 等值）
-- =====================================================================

-- 项目立项表
CREATE INDEX IF NOT EXISTS idx_pmis_initiation_tenant_created
    ON pmis_project_initiation (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pmis_initiation_status_created
    ON pmis_project_initiation (stage, created_at DESC)
    WHERE deleted = 0;

-- 项目变更表（4.1.1）
CREATE INDEX IF NOT EXISTS idx_pmis_change_initiation_status
    ON pmis_project_change (initiation_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pmis_change_major_flag
    ON pmis_project_change (initiation_id, major_flag)
    WHERE major_flag = 1;
CREATE INDEX IF NOT EXISTS idx_pmis_change_change_code
    ON pmis_project_change (change_code);
CREATE INDEX IF NOT EXISTS idx_pmis_change_provider_trace
    ON pmis_project_change (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- 项目结项（4.1.4）
CREATE INDEX IF NOT EXISTS idx_pmis_closure_initiation_status
    ON pmis_project_closure (initiation_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pmis_closure_closure_type
    ON pmis_project_closure (closure_type, created_at DESC);

-- 合同模板（4.1.5）
CREATE INDEX IF NOT EXISTS idx_pmis_template_code
    ON pmis_contract_template (code);
CREATE INDEX IF NOT EXISTS idx_pmis_template_status_type
    ON pmis_contract_template (status, type, created_at DESC);

-- 售后表（4.1.3）
CREATE INDEX IF NOT EXISTS idx_pmis_warranty_initiation_expire
    ON pmis_after_sales_warranty (initiation_id, expire_date DESC)
    WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_pmis_ops_ticket_priority_status
    ON pmis_after_sales_ops_ticket (priority, status, created_at DESC)
    WHERE status IN ('OPEN', 'IN_PROGRESS');
CREATE INDEX IF NOT EXISTS idx_pmis_ops_ticket_sla_due
    ON pmis_after_sales_ops_ticket (sla_due_at)
    WHERE status NOT IN ('CLOSED', 'CANCELLED');
CREATE INDEX IF NOT EXISTS idx_pmis_satisfaction_ticket
    ON pmis_after_sales_satisfaction (ticket_id, created_at DESC);

-- 项目交付（4.1.2）
CREATE INDEX IF NOT EXISTS idx_pmis_delivery_initiation_stage
    ON pmis_project_delivery (initiation_id, stage, status);

-- =====================================================================
--  2) EVM 看板（4.2 联动）
-- =====================================================================
CREATE INDEX IF NOT EXISTS idx_pmis_evm_initiation_period
    ON pmis_evm_record (initiation_id, period DESC);
CREATE INDEX IF NOT EXISTS idx_pmis_evm_wbs_period
    ON pmis_evm_record (wbs_task_id, period DESC);
-- EVM 周期唯一性（idempotent on initiation+wbs+period）
CREATE UNIQUE INDEX IF NOT EXISTS uq_pmis_evm_period
    ON pmis_evm_record (initiation_id, wbs_task_id, period);

-- =====================================================================
--  3) 利用率快照（4.2.1）
-- =====================================================================
CREATE INDEX IF NOT EXISTS idx_pmis_utilization_user_period
    ON pmis_billable_utilization_snapshot (employee_id, period DESC);
CREATE INDEX IF NOT EXISTS idx_pmis_utilization_dept_period
    ON pmis_billable_utilization_snapshot (department, period DESC);

-- =====================================================================
--  4) 预警 / 对账（4.2.2/4.2.3）
-- =====================================================================
CREATE INDEX IF NOT EXISTS idx_pmis_alert_dispatch_recipient
    ON pmis_alert_dispatch (recipient_id, sent_at DESC)
    WHERE status IN ('PENDING', 'FAILED');
CREATE INDEX IF NOT EXISTS idx_pmis_alert_dispatch_retry
    ON pmis_alert_dispatch (next_retry_at)
    WHERE status = 'FAILED' AND retry_count < 3;
CREATE INDEX IF NOT EXISTS idx_pmis_reconcile_daily_period
    ON pmis_daily_reconcile (period DESC, status);
CREATE INDEX IF NOT EXISTS idx_pmis_reconcile_diff_only
    ON pmis_daily_reconcile (period DESC)
    WHERE diff_count > 0;

-- =====================================================================
--  5) AI Agent（4.3）
-- =====================================================================
CREATE INDEX IF NOT EXISTS idx_pmis_agent_prediction_biz
    ON pmis_agent_prediction (biz_type, biz_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pmis_agent_prediction_type_alert
    ON pmis_agent_prediction (agent_type, alert_level, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pmis_agent_prediction_trace
    ON pmis_agent_prediction (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pmis_agent_orchestration_biz
    ON pmis_agent_orchestration (biz_type, biz_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pmis_agent_blackboard_session
    ON pmis_agent_blackboard (session_id);

-- =====================================================================
--  6) 财务对账（voucher / payment / invoice）
-- =====================================================================
CREATE INDEX IF NOT EXISTS idx_pmis_invoice_status_issued
    ON pmis_finance_invoice (status, issued_at DESC);
CREATE INDEX IF NOT EXISTS idx_pmis_invoice_customer_status
    ON pmis_finance_invoice (customer_id, status, issued_at DESC);
CREATE INDEX IF NOT EXISTS idx_pmis_payment_unallocated
    ON pmis_finance_payment (contract_id, status)
    WHERE status IN ('RECEIVED', 'PARTIAL');
-- 注：pmis_voucher 表尚未创建，相关索引暂时注释，待凭证表落地后启用
-- CREATE INDEX IF NOT EXISTS idx_pmis_voucher_period_status
--     ON pmis_voucher (period, status, created_at DESC);

-- =====================================================================
--  7) 时区/时间相关 BRIN 索引（日志/审计表 100w+ 行）
-- =====================================================================
CREATE INDEX IF NOT EXISTS idx_pmis_audit_log_brin_created
    ON pmis_operation_log USING BRIN (created_at)
    WITH (pages_per_range = 32);
CREATE INDEX IF NOT EXISTS idx_pmis_message_log_brin_sent
    ON pmis_message_log USING BRIN (sent_at)
    WITH (pages_per_range = 32);
CREATE INDEX IF NOT EXISTS idx_pmis_operation_log_brin
    ON pmis_operation_log USING BRIN (created_at)
    WITH (pages_per_range = 32);

-- =====================================================================
--  8) 表达式索引（状态名/类型名查询）
-- =====================================================================
CREATE INDEX IF NOT EXISTS idx_pmis_initiation_status_lower
    ON pmis_project_initiation (lower(status));
CREATE INDEX IF NOT EXISTS idx_pmis_change_status_lower
    ON pmis_project_change (lower(status));

-- =====================================================================
--  9) 统计信息更新
-- =====================================================================
ANALYZE pmis_project_initiation;
ANALYZE pmis_project_change;
ANALYZE pmis_project_closure;
ANALYZE pmis_evm_record;
ANALYZE pmis_billable_utilization_snapshot;
ANALYZE pmis_agent_prediction;
ANALYZE pmis_alert_dispatch;
ANALYZE pmis_daily_reconcile;
ANALYZE pmis_finance_invoice;
ANALYZE pmis_finance_payment;
ANALYZE pmis_operation_log;

-- =====================================================================
--  10) 索引使用情况监控 SQL（运维参考）
-- =====================================================================
-- 查看未使用的索引
-- SELECT schemaname, tablename, indexname, idx_scan
--   FROM pg_stat_user_indexes
--  WHERE idx_scan = 0 AND indexrelname NOT LIKE 'pg_toast%'
--  ORDER BY pg_relation_size(indexrelid) DESC;

-- 查看索引膨胀
-- SELECT current_database(), schemaname, tablename,
--        pg_size_pretty(pg_relation_size(indexrelid)) AS size,
--        100 * (pg_relation_size(indexrelid) - 100 * current_setting('block_size')::int) / NULLIF(pg_relation_size(indexrelid), 0) AS bloat_pct
--   FROM pg_stat_user_indexes
--  ORDER BY pg_relation_size(indexrelid) DESC LIMIT 50;

SELECT '✅ 索引调优完成（共 ' || count(*) || ' 个索引）' AS result
  FROM pg_indexes
 WHERE schemaname = 'public' AND indexname LIKE 'idx_pmis_%';
