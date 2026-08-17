-- P2-3: Agent 执行链路步骤增加 cost 字段（每次 LLM 调用的 Token 成本）
-- 用于在链路中直接体现成本分布，便于按步骤分析 Token 消耗

ALTER TABLE ydsz_agent_trace_step
    ADD COLUMN cost DECIMAL(12, 6) DEFAULT 0.0 COMMENT 'Token 成本（USD，精确到 6 位小数；非 LLM 调用步骤为 0）';

-- 为 cost 字段创建索引，支持按成本范围查询
CREATE INDEX idx_trace_step_cost ON ydsz_agent_trace_step (cost);
