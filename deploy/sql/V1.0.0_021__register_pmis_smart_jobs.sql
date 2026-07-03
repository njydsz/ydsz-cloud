-- ============================================================
-- V1.0.0_021  智能化升级 P5/P6/P7  定时任务注册
-- ============================================================
-- 说明：批次 16 智能化升级-系统内部数据管理（PRD 4.2）
--   P5-2 预警重试补偿：每 5 分钟扫描 PENDING/FAILED 预警重发
--   P6-1 每日自动对账：每日 02:00 跑成本/收入/回款/开票/工时/利润 对账
--   P7-3 售后巡检    ：每日 03:00 扫质保期 + 运维工单 SLA
-- 表 pmis_job 已在 V1.0.0_006 创建。
-- ============================================================

-- 清理旧记录（保证可重跑）
DELETE FROM pmis_job WHERE job_key IN (
    'alertDispatchRetryJob',
    'dailyReconcileJob',
    'afterSalesScanJob'
);

-- ---------- P5-2 预警重试补偿 ----------
INSERT INTO pmis_job (job_name, job_group, job_key, handler, cron_expression, status, remark, tenant_id)
VALUES (
    '预警重试补偿任务',
    'ALERT',
    'alertDispatchRetryJob',
    'alertDispatchRetryJobHandler',
    '0 0/5 * * * ?',
    'NORMAL',
    '每 5 分钟扫描 PENDING/FAILED 预警并重发，超过 maxRetry 后保持 FAILED',
    1
);

-- ---------- P6-1 每日自动对账 ----------
INSERT INTO pmis_job (job_name, job_group, job_key, handler, cron_expression, status, remark, tenant_id)
VALUES (
    '每日对账任务',
    'RECONCILE',
    'dailyReconcileJob',
    'dailyReconcileJobHandler',
    '0 0 2 * * ?',
    'NORMAL',
    '每日 02:00 校验成本/收入/开票/回款/工时/利润 6 维度双向一致性，落库 pmis_reconcile_daily',
    1
);

-- ---------- P7-3 售后巡检 ----------
INSERT INTO pmis_job (job_name, job_group, job_key, handler, cron_expression, status, remark, tenant_id)
VALUES (
    '售后巡检任务',
    'AFTERSALES',
    'afterSalesScanJob',
    'afterSalesScanJobHandler',
    '0 0 3 * * ?',
    'NORMAL',
    '每日 03:00 扫描即将到期/已过期质保期 + 运维工单 SLA 违约',
    1
);
