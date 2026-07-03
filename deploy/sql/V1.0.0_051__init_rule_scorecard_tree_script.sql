-- ============================================================
-- V1.0.0_051__init_rule_scorecard_tree_script.sql
-- 评分卡 / 决策树 / 脚本规则持久化
-- （原 V1.0.0_048 与 add_pmis_flow_task_priority 版本号冲突，迁移到 051）
-- ============================================================

-- --------------------------------------------------------
-- 1. 评分卡规则定义表
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_rule_scorecard (
    id              BIGSERIAL       PRIMARY KEY,
    rule_code       VARCHAR(128)    NOT NULL UNIQUE,
    rule_name       VARCHAR(256)   NOT NULL,
    category        VARCHAR(64)     NOT NULL DEFAULT 'RISK',
    description     TEXT,
    base_score      NUMERIC(10,2)   NOT NULL DEFAULT 100,
    red_threshold   NUMERIC(10,2)   NOT NULL,
    yellow_threshold NUMERIC(10,2)  NOT NULL,
    factors         JSONB           NOT NULL,   -- [{conditionExpression, score, description}]
    priority        INTEGER         NOT NULL DEFAULT 100,
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    scope           VARCHAR(128)    DEFAULT 'ALL',
    version         INTEGER         NOT NULL DEFAULT 1,
    created_by      VARCHAR(64)     NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(64),
    updated_at      TIMESTAMPTZ
);

COMMENT ON TABLE  pmis_rule_scorecard IS '评分卡规则定义表';
COMMENT ON COLUMN pmis_rule_scorecard.base_score IS '基础分（命中因子前的基础值，默认 100）';
COMMENT ON COLUMN pmis_rule_scorecard.red_threshold IS '红色阈值（总分低于此值为 RED）';
COMMENT ON COLUMN pmis_rule_scorecard.yellow_threshold IS '黄色阈值（总分低于此值为 YELLOW）';
COMMENT ON COLUMN pmis_rule_scorecard.factors IS '评分因子数组，JSON 格式：[{"conditionExpression":"...","score":-30,"description":"..."}]';

CREATE INDEX IF NOT EXISTS idx_pmis_rule_scorecard_enabled ON pmis_rule_scorecard (enabled);
CREATE INDEX IF NOT EXISTS idx_pmis_rule_scorecard_category ON pmis_rule_scorecard (category);

-- --------------------------------------------------------
-- 2. 决策树规则定义表
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_rule_decision_tree (
    id              BIGSERIAL       PRIMARY KEY,
    rule_code       VARCHAR(128)    NOT NULL UNIQUE,
    rule_name       VARCHAR(256)    NOT NULL,
    category        VARCHAR(64)     NOT NULL DEFAULT 'GENERAL',
    description     TEXT,
    root_node       JSONB           NOT NULL,   -- 嵌套决策树节点
    priority        INTEGER         NOT NULL DEFAULT 100,
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    scope           VARCHAR(128)    DEFAULT 'ALL',
    version         INTEGER         NOT NULL DEFAULT 1,
    created_by      VARCHAR(64)     NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(64),
    updated_at      TIMESTAMPTZ
);

COMMENT ON TABLE  pmis_rule_decision_tree IS '决策树规则定义表';
COMMENT ON COLUMN pmis_rule_decision_tree.root_node IS '决策树根节点 JSON：{conditionExpression, trueBranch, falseBranch, leaf, severity, title, description}';

CREATE INDEX IF NOT EXISTS idx_pmis_rule_tree_enabled ON pmis_rule_decision_tree (enabled);
CREATE INDEX IF NOT EXISTS idx_pmis_rule_tree_category ON pmis_rule_decision_tree (category);

-- --------------------------------------------------------
-- 3. 脚本规则定义表
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_rule_script (
    id              BIGSERIAL       PRIMARY KEY,
    rule_code       VARCHAR(128)    NOT NULL UNIQUE,
    rule_name       VARCHAR(256)    NOT NULL,
    category        VARCHAR(64)     NOT NULL DEFAULT 'GENERAL',
    description     TEXT,
    script          TEXT            NOT NULL,   -- Groovy 脚本
    default_severity VARCHAR(16)    NOT NULL DEFAULT 'INFO',
    sandbox_enabled BOOLEAN         NOT NULL DEFAULT TRUE,
    priority        INTEGER         NOT NULL DEFAULT 100,
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    scope           VARCHAR(128)    DEFAULT 'ALL',
    version         INTEGER         NOT NULL DEFAULT 1,
    created_by      VARCHAR(64)     NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(64),
    updated_at      TIMESTAMPTZ
);

COMMENT ON TABLE  pmis_rule_script IS '脚本规则定义表（Groovy JSR-223）';
COMMENT ON COLUMN pmis_rule_script.script IS 'Groovy 脚本内容（沙箱模式下禁止 System/反射/IO/网络访问）';
COMMENT ON COLUMN pmis_rule_script.sandbox_enabled IS '是否启用沙箱（默认 TRUE）';

CREATE INDEX IF NOT EXISTS idx_pmis_rule_script_enabled ON pmis_rule_script (enabled);
CREATE INDEX IF NOT EXISTS idx_pmis_rule_script_category ON pmis_rule_script (category);
