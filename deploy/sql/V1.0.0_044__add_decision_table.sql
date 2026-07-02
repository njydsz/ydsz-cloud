-- ============================================
-- V1.0.0_044__add_decision_table.sql
-- 决策表支持
-- ============================================

CREATE TABLE IF NOT EXISTS pmis_rule_decision_table (
    id              BIGSERIAL       PRIMARY KEY,
    table_code      VARCHAR(128)    NOT NULL UNIQUE,
    table_name      VARCHAR(256)    NOT NULL,
    description     TEXT,
    category        VARCHAR(64),
    -- 条件列定义 JSON: [{"name":"字段名","label":"显示名","type":"number|string|boolean"}]
    condition_columns JSONB        NOT NULL,
    -- 动作列定义 JSON: [{"name":"severity","label":"严重度","type":"string"}]
    action_columns   JSONB        NOT NULL,
    -- 决策行 JSON: [{"conditions":{"字段名":"值"},"actions":{"severity":"RED"}}]
    rows            JSONB        NOT NULL DEFAULT '[]',
    -- 默认动作（未匹配行时的动作）
    default_actions JSONB,
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    priority        INTEGER       NOT NULL DEFAULT 100,
    version         INTEGER       NOT NULL DEFAULT 1,
    created_by      VARCHAR(64),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(64),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dt_code ON pmis_rule_decision_table(table_code);
CREATE INDEX IF NOT EXISTS idx_dt_category ON pmis_rule_decision_table(category);
CREATE INDEX IF NOT EXISTS idx_dt_enabled ON pmis_rule_decision_table(enabled);