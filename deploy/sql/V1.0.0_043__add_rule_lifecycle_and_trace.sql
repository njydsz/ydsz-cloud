-- ============================================
-- V1.0.0_043__add_rule_lifecycle_and_trace.sql
-- 规则生命周期管理 & 执行链路追踪
-- ============================================

-- 1. 规则生命周期：添加状态字段
ALTER TABLE pmis_rule_def
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED';

ALTER TABLE pmis_rule_def
    ADD COLUMN IF NOT EXISTS effective_from TIMESTAMPTZ;

ALTER TABLE pmis_rule_def
    ADD COLUMN IF NOT EXISTS effective_to TIMESTAMPTZ;

ALTER TABLE pmis_rule_def
    ADD COLUMN IF NOT EXISTS reviewed_by VARCHAR(64);

ALTER TABLE pmis_rule_def
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ;

ALTER TABLE pmis_rule_def
    ADD COLUMN IF NOT EXISTS review_comment VARCHAR(512);

-- 状态索引
CREATE INDEX IF NOT EXISTS idx_rule_def_status ON pmis_rule_def(status);

-- 2. 执行链路追踪表
CREATE TABLE IF NOT EXISTS pmis_rule_execution_trace (
    id              BIGSERIAL       PRIMARY KEY,
    trace_id        VARCHAR(64)     NOT NULL,
    rule_code       VARCHAR(128)    NOT NULL,
    rule_name       VARCHAR(256),
    scenario        VARCHAR(128),
    triggered       BOOLEAN         NOT NULL DEFAULT FALSE,
    severity        VARCHAR(16),
    condition_result VARCHAR(256),
    elapsed_ms      BIGINT          NOT NULL DEFAULT 0,
    facts_snapshot  JSONB,
    result_snapshot JSONB,
    error_message   TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_rule_trace_trace_id ON pmis_rule_execution_trace(trace_id);
CREATE INDEX IF NOT EXISTS idx_rule_trace_rule_code ON pmis_rule_execution_trace(rule_code);
CREATE INDEX IF NOT EXISTS idx_rule_trace_created ON pmis_rule_execution_trace(created_at);
CREATE INDEX IF NOT EXISTS idx_rule_trace_scenario ON pmis_rule_execution_trace(scenario);