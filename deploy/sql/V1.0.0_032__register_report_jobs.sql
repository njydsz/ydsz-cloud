-- ============================================================
-- V1.0.0_032  P1-5 注册报表定时任务
-- ============================================================
-- 说明：定时报表生成与分发（P1-5）
--   report-daily    日报：每天 08:00
--   report-weekly   周报：每周一 08:00
--   report-monthly  月报：每月 1 日 08:00
-- 表 pmis_job 已在 V1.0.0_006 创建。
-- ============================================================

-- 清理旧记录（保证可重跑）
DELETE FROM pmis_job WHERE job_key IN (
    'reportDailyJob',
    'reportWeeklyJob',
    'reportMonthlyJob'
);

-- ---------- 日报表生成与分发 ----------
INSERT INTO pmis_job (job_name, job_group, job_key, handler, cron_expression, params_json, status, remark, tenant_id)
VALUES (
    '日报表生成与分发任务',
    'PMIS_REPORT',
    'reportDailyJob',
    'reportScheduleJobHandler',
    '0 0 8 * * ?',
    'DAILY',
    'NORMAL',
    '每日 08:00 生成驾驶舱/EVM/利润/利用率/Bench/风险日报并分发到订阅人',
    1
);

-- ---------- 周报表生成与分发 ----------
INSERT INTO pmis_job (job_name, job_group, job_key, handler, cron_expression, params_json, status, remark, tenant_id)
VALUES (
    '周报表生成与分发任务',
    'PMIS_REPORT',
    'reportWeeklyJob',
    'reportScheduleJobHandler',
    '0 0 8 ? * MON',
    'WEEKLY',
    'NORMAL',
    '每周一 08:00 生成周报表并分发到订阅人',
    1
);

-- ---------- 月报表生成与分发 ----------
INSERT INTO pmis_job (job_name, job_group, job_key, handler, cron_expression, params_json, status, remark, tenant_id)
VALUES (
    '月报表生成与分发任务',
    'PMIS_REPORT',
    'reportMonthlyJob',
    'reportScheduleJobHandler',
    '0 0 8 1 * ?',
    'MONTHLY',
    'NORMAL',
    '每月 1 日 08:00 生成月报表并分发到订阅人',
    1
);
