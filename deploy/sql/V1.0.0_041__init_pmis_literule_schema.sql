-- ============================================================
-- V1.0.0_041__init_pmis_literule_schema.sql
-- LiteRule 轻量规则引擎：规则定义表 + 规则版本历史表
-- ============================================================

-- --------------------------------------------------------
-- 1. 规则定义主表
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS pm_rule_def (
    id                    BIGSERIAL       PRIMARY KEY,
    rule_code             VARCHAR(128)    NOT NULL UNIQUE,
    rule_name             VARCHAR(256)    NOT NULL,
    category              VARCHAR(64)     NOT NULL DEFAULT 'GENERAL',
    description           TEXT,
    condition_expression  TEXT            NOT NULL,
    severity_expression   TEXT,
    default_severity      VARCHAR(16)     NOT NULL DEFAULT 'YELLOW',
    title_template        VARCHAR(512),
    description_template  TEXT,
    priority              INTEGER         NOT NULL DEFAULT 100,
    enabled               BOOLEAN         NOT NULL DEFAULT TRUE,
    scope                 VARCHAR(128)    DEFAULT 'ALL',
    drilldown_available   BOOLEAN         NOT NULL DEFAULT TRUE,
    version               INTEGER         NOT NULL DEFAULT 1,
    created_by            VARCHAR(64)     NOT NULL DEFAULT 'SYSTEM',
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by            VARCHAR(64),
    updated_at            TIMESTAMPTZ
);

COMMENT ON TABLE  pm_rule_def IS 'LiteRule 规则定义表';
COMMENT ON COLUMN pm_rule_def.rule_code IS '规则编码（全局唯一）';
COMMENT ON COLUMN pm_rule_def.condition_expression IS '条件表达式（Aviator 语法，返回 boolean）';
COMMENT ON COLUMN pm_rule_def.severity_expression IS '严重度表达式（可选，动态决定严重度）';
COMMENT ON COLUMN pm_rule_def.priority IS '优先级（数值越小越先执行，默认100）';

-- 索引
CREATE INDEX IF NOT EXISTS idx_pm_rule_def_category ON pm_rule_def (category);
CREATE INDEX IF NOT EXISTS idx_pm_rule_def_enabled  ON pm_rule_def (enabled);

-- --------------------------------------------------------
-- 2. 规则版本历史表（审计追踪 + 回滚）
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS pm_rule_version_history (
    id              BIGSERIAL       PRIMARY KEY,
    rule_code       VARCHAR(128)    NOT NULL,
    version         INTEGER         NOT NULL,
    definition_json TEXT            NOT NULL,
    change_desc     VARCHAR(512),
    operator        VARCHAR(64)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (rule_code, version)
);

COMMENT ON TABLE  pm_rule_version_history IS 'LiteRule 规则版本历史表';
COMMENT ON COLUMN pm_rule_version_history.definition_json IS '规则定义 JSON 快照';

CREATE INDEX IF NOT EXISTS idx_pm_rule_ver_code ON pm_rule_version_history (rule_code);

-- --------------------------------------------------------
-- 3. 预置规则（从硬编码迁移为表达式配置）
--    对标原 execution 模块 AlertRuleEngine 4 条内置规则
-- --------------------------------------------------------
INSERT INTO pm_rule_def (rule_code, rule_name, category, description, condition_expression, severity_expression, default_severity, title_template, description_template, priority, scope, created_by)
VALUES
(
    'EVM_RED_EXCESS',
    'EVM 红色告警项目过多',
    'EVM',
    '当 EVM 红色项目数超过阈值(3)时触发',
    'evmRedCount >= 3',
    NULL,
    'RED',
    'EVM 红色告警项目 ${evmRedCount} 个',
    '当前周期红色告警项目数已达到 ${evmRedCount} 个，超过阈值 3。请关注挣值偏差并复盘。',
    100,
    'ALL',
    'SYSTEM'
) ON CONFLICT (rule_code) DO NOTHING;

INSERT INTO pm_rule_def (rule_code, rule_name, category, description, condition_expression, severity_expression, default_severity, title_template, description_template, priority, scope, created_by)
VALUES
(
    'MARGIN_LOW',
    '毛利率过低',
    'COST',
    '当毛利率低于阈值时触发（YELLOW<10%, RED<5%）',
    'grossMargin < 0.10 && confirmedRevenue > 0',
    'grossMargin < 0.05 ? "RED" : "YELLOW"',
    'YELLOW',
    '毛利率仅 ${grossMargin}（低于阈值）',
    '当前累计毛利率为 ${grossMargin}，低于阈值。需关注毛利结构与项目组合。',
    110,
    'ALL',
    'SYSTEM'
) ON CONFLICT (rule_code) DO NOTHING;

INSERT INTO pm_rule_def (rule_code, rule_name, category, description, condition_expression, severity_expression, default_severity, title_template, description_template, priority, scope, created_by)
VALUES
(
    'BENCH_IDLE_COST_HIGH',
    'Bench 闲置成本过高',
    'BENCH',
    '当累计 Bench 闲置成本超过阈值时触发（YELLOW>=50万, RED>=100万）',
    'benchIdleCost >= 500000',
    'benchIdleCost >= 1000000 ? "RED" : "YELLOW"',
    'YELLOW',
    'Bench 闲置成本 ${benchIdleCost} 元',
    '累计 Bench 闲置成本已达到 ${benchIdleCost} 元，资源池利用率不足。建议加速调度。',
    120,
    'RESOURCE_POOL',
    'SYSTEM'
) ON CONFLICT (rule_code) DO NOTHING;

INSERT INTO pm_rule_def (rule_code, rule_name, category, description, condition_expression, severity_expression, default_severity, title_template, description_template, priority, scope, created_by)
VALUES
(
    'UTILIZATION_LOW',
    '可计费利用率偏低',
    'UTILIZATION',
    '当平均可计费利用率低于阈值时触发（YELLOW<70%, RED<50%）',
    'avgBillableUtilization < 0.70 && activeProjects > 0',
    'avgBillableUtilization < 0.50 ? "RED" : "YELLOW"',
    'YELLOW',
    '可计费利用率仅 ${avgBillableUtilization}',
    '团队平均可计费利用率为 ${avgBillableUtilization}，低于阈值。请关注资源调度。',
    130,
    'ALL',
    'SYSTEM'
) ON CONFLICT (rule_code) DO NOTHING;

-- --------------------------------------------------------
-- 4. 初始版本快照
-- --------------------------------------------------------
INSERT INTO pm_rule_version_history (rule_code, version, definition_json, change_desc, operator)
SELECT rule_code, 1,
       json_build_object(
           'code', rule_code,
           'name', rule_name,
           'category', category,
           'conditionExpression', condition_expression,
           'severityExpression', severity_expression,
           'defaultSeverity', default_severity,
           'priority', priority,
           'enabled', enabled
       )::text,
       '初始版本（从硬编码迁移为表达式配置）',
       'SYSTEM'
FROM pm_rule_def
WHERE NOT EXISTS (
    SELECT 1 FROM pm_rule_version_history h WHERE h.rule_code = pm_rule_def.rule_code
);
