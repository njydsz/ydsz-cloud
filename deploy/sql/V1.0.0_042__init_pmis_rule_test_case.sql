-- ============================================
-- V1.0.0_042__init_pmis_rule_test_case.sql
-- 规则测试用例管理表
-- ============================================

-- 测试用例主表
CREATE TABLE IF NOT EXISTS pmis_rule_test_case (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(256)    NOT NULL,
    rule_code       VARCHAR(128),
    facts_data      JSONB           NOT NULL,
    expected_triggered JSONB,
    description     TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_rule_test_case_code ON pmis_rule_test_case(rule_code);
CREATE INDEX IF NOT EXISTS idx_rule_test_case_name ON pmis_rule_test_case(name);

-- 更新触发器
CREATE OR REPLACE FUNCTION update_rule_test_case_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_rule_test_case_updated_at ON pmis_rule_test_case;
CREATE TRIGGER trigger_rule_test_case_updated_at
    BEFORE UPDATE ON pmis_rule_test_case
    FOR EACH ROW EXECUTE FUNCTION update_rule_test_case_updated_at();

-- 预置测试用例
INSERT INTO pmis_rule_test_case (name, rule_code, facts_data, expected_triggered, description) VALUES
('EVM 3个红色告警项目', 'EVM_RED_EXCESS',
 '{"evmRedCount": 3, "projectCount": 10}',
 '["EVM_RED_EXCESS"]',
 'EVM红色告警≥3个，应触发规则'),
('EVM 2个红色告警项目', 'EVM_RED_EXCESS',
 '{"evmRedCount": 2, "projectCount": 10}',
 '[]',
 'EVM红色告警2个，不应触发规则'),
('毛利率低于10%', 'MARGIN_LOW',
 '{"grossMargin": 0.08, "confirmedRevenue": 1000000}',
 '["MARGIN_LOW"]',
 '毛利率8%且有确认收入，应触发'),
('毛利率严重低于5%', 'MARGIN_LOW',
 '{"grossMargin": 0.03, "confirmedRevenue": 500000}',
 '["MARGIN_LOW"]',
 '毛利率3%应触发RED严重度'),
('闲置成本超过50万', 'BENCH_IDLE_COST_HIGH',
 '{"benchIdleCost": 500000}',
 '["BENCH_IDLE_COST_HIGH"]',
 '闲置成本=50万，应触发'),
('利用率低于70%', 'UTILIZATION_LOW',
 '{"avgBillableUtilization": 0.65, "activeProjects": 5}',
 '["UTILIZATION_LOW"]',
 '利用率65%且有活跃项目，应触发');