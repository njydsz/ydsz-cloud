-- ============================================================
-- V1.0.0_035  P2-6 注册数据一致性校验定时任务
-- ============================================================
-- 说明：每日 02:30 执行数据一致性校验
--   1. 发票总额 vs 回款总额
--   2. 预算 vs 实际成本（超支检测）
--   3. WBS 进度 vs 工时完成率
--   差异超阈值自动记录日志并触发告警。
-- 表 pmis_job 已在 V1.0.0_006 创建。
-- 注意：版本号 033/034 已被流程引擎占用，本任务使用 035。
-- ============================================================

-- 清理旧记录（保证可重跑，按 job_key 唯一键清理）
DELETE FROM pmis_job WHERE job_key = 'data-consistency-check';

-- ---------- 数据一致性校验任务 ----------
INSERT INTO pmis_job (job_name, job_group, job_key, handler, cron_expression, params_json, status, remark, tenant_id)
VALUES (
    'data-consistency-check',
    'PMIS_SCHEDULER',
    'data-consistency-check',
    'dataConsistencyJobHandler',
    '0 30 2 * * ?',
    '{}',
    'NORMAL',
    '数据一致性校验（发票vs回款、预算vs成本、WBSvs工时）',
    1
);
