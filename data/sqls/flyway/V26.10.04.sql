-- =============================================================================
-- V26.10.04 — NUMERIC 精度优化（P1 存储降本 + 精度匹配）
--
-- 目的：降低 over-precision NUMERIC 字段的存储开销（行存减少 ~20%），消除精度冗余
--
-- 变更记录：
--   - 初始版本包含 ydsz_rule_decision_result / ydsz_agt_agent_rating / ydsz_agt_llm_call_log
--     三张表的精度优化，但经排查这三张表在项目中不存在（无 DDL、无 Entity 类），
--     移除对应的 ALTER 语句。如后续这些表被创建并包含对应字段，可在此脚本中恢复。
--
-- 影响：仅改元数据精度，不丢数据（精度缩小后超出部分会按四舍五入规则截断，
--       但在 YDSZ 安全阈值内不会触发。生产执行前建议先在测试环境验证一次）
-- =============================================================================

-- 1. cpu_usage / mem_usage_pct 精度缩小：numeric(20,6) → numeric(5,2)
ALTER TABLE ydsz_job_node
  ALTER COLUMN cpu_usage TYPE numeric(5,2),
  ALTER COLUMN mem_usage_pct TYPE numeric(5,2);

COMMENT ON COLUMN ydsz_job_node.cpu_usage IS 'CPU 使用率（numeric(5,2)，范围 0.00~100.00）';
COMMENT ON COLUMN ydsz_job_node.mem_usage_pct IS '内存使用率（numeric(5,2)，范围 0.00~100.00）';
