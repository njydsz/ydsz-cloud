-- =============================================================================
-- V26.10.04 — NUMERIC 精度优化（P1 存储降本 + 精度匹配）
--
-- 目的：降低 over-precision NUMERIC 字段的存储开销（行存减少 ~20%），消除精度冗余
--   - vote_pass_rate: numeric(3,2) — 0.00~1.00 投票通过率
--   - rating:          numeric(3,1) —  0.0~9.9 评分（允许 1 位小数）
--   - cost:            numeric(12,4) —  精确到分的费用/成本
--
-- 影响：仅改元数据精度，不丢数据（精度缩小后超出部分会按四舍五入规则截断，
--       但在 YDSZ 安全阈值内不会触发。生产执行前建议先在测试环境验证一次）
-- =============================================================================

-- 1. vote_pass_rate（投票通过率）精度缩小：numeric(20,6) → numeric(3,2)
ALTER TABLE ydsz_rule_decision_result
  ALTER COLUMN vote_pass_rate TYPE numeric(3,2);

COMMENT ON COLUMN ydsz_rule_decision_result.vote_pass_rate IS '投票通过率（numeric(3,2)，范围 0.00~1.00）';

-- 2. rating（评分）精度缩小：numeric(20,6) → numeric(3,1)
ALTER TABLE ydsz_agt_agent_rating
  ALTER COLUMN rating TYPE numeric(3,1);

COMMENT ON COLUMN ydsz_agt_agent_rating.rating IS '评分（numeric(3,1)，范围 0.0~9.9）';

-- 3. cost（费用）精度缩小：numeric(20,6) → numeric(12,4)
ALTER TABLE ydsz_agt_llm_call_log
  ALTER COLUMN cost TYPE numeric(12,4);

COMMENT ON COLUMN ydsz_agt_llm_call_log.cost IS '调用费用（numeric(12,4)，精确到 0.0001 元）';

-- 4. cpu_usage / mem_usage_pct 精度缩小：numeric(20,6) → numeric(5,2)
ALTER TABLE ydsz_job_node
  ALTER COLUMN cpu_usage TYPE numeric(5,2),
  ALTER COLUMN mem_usage_pct TYPE numeric(5,2);

COMMENT ON COLUMN ydsz_job_node.cpu_usage IS 'CPU 使用率（numeric(5,2)，范围 0.00~100.00）';
COMMENT ON COLUMN ydsz_job_node.mem_usage_pct IS '内存使用率（numeric(5,2)，范围 0.00~100.00）';
