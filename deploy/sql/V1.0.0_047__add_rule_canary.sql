-- ============================================================
-- V1.0.0_047__add_rule_canary.sql
-- 规则灰度发布：在 pmis_rule_def 表新增灰度路由字段
-- ============================================================

-- 灰度比例（0.0~1.0，0 表示不启用灰度）
ALTER TABLE pmis_rule_def
    ADD COLUMN IF NOT EXISTS canary_ratio NUMERIC(5,4) NOT NULL DEFAULT 0.0;

-- 灰度条件表达式列表（JSON 数组，AND 关系；为空时仅按比例分桶）
ALTER TABLE pmis_rule_def
    ADD COLUMN IF NOT EXISTS canary_conditions JSONB;

-- 灰度候选版本的条件表达式（覆盖主版本 condition_expression）
ALTER TABLE pmis_rule_def
    ADD COLUMN IF NOT EXISTS canary_condition_expression TEXT;

-- 灰度候选版本的严重度表达式（覆盖主版本 severity_expression）
ALTER TABLE pmis_rule_def
    ADD COLUMN IF NOT EXISTS canary_severity_expression TEXT;

COMMENT ON COLUMN pmis_rule_def.canary_ratio IS '灰度比例（0~1.0，0 不启用灰度；启用后按比例将流量路由到候选版本）';
COMMENT ON COLUMN pmis_rule_def.canary_conditions IS '灰度条件表达式列表（Aviator 语法，AND 关系；JSON 数组，示例：["tenantId == \'T001\'"]）';
COMMENT ON COLUMN pmis_rule_def.canary_condition_expression IS '灰度候选版本条件表达式（覆盖主版本，进行 A/B 验证）';
COMMENT ON COLUMN pmis_rule_def.canary_severity_expression IS '灰度候选版本严重度表达式（覆盖主版本）';

-- 灰度规则索引（便于快速查询启用了灰度的规则集）
CREATE INDEX IF NOT EXISTS idx_pmis_rule_def_canary ON pmis_rule_def (canary_ratio) WHERE canary_ratio > 0;

-- ------------------------------------------------------------
-- 灰度分桶统计表（运营监控：rule_code -> 主桶/灰桶计数）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_rule_canary_bucket (
    id              BIGSERIAL       PRIMARY KEY,
    rule_code       VARCHAR(128)    NOT NULL,
    bucket_type     VARCHAR(16)     NOT NULL,  -- PRIMARY / CANARY
    bucket_count    BIGINT          NOT NULL DEFAULT 0,
    stat_date       DATE            NOT NULL DEFAULT CURRENT_DATE,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (rule_code, bucket_type, stat_date)
);

COMMENT ON TABLE  pmis_rule_canary_bucket IS '规则灰度分桶统计表（按日聚合，便于运营对比新旧版本流量）';
COMMENT ON COLUMN pmis_rule_canary_bucket.bucket_type IS '桶类型：PRIMARY=主版本，CANARY=候选版本';

CREATE INDEX IF NOT EXISTS idx_pmis_canary_bucket_rule   ON pmis_rule_canary_bucket (rule_code);
CREATE INDEX IF NOT EXISTS idx_pmis_canary_bucket_date   ON pmis_rule_canary_bucket (stat_date);
