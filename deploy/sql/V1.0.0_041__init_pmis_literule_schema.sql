-- ============================================================
-- V1.0.0_041__init_pmis_literule_schema.sql
-- LiteRule 轻量规则引擎：规则定义表 + 规则版本历史表
-- ============================================================

-- --------------------------------------------------------
-- 1. 规则定义主表
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_rule_def (
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

COMMENT ON TABLE  pmis_rule_def IS 'LiteRule 规则定义表';
COMMENT ON COLUMN pmis_rule_def.rule_code IS '规则编码（全局唯一）';
COMMENT ON COLUMN pmis_rule_def.condition_expression IS '条件表达式（Aviator 语法，返回 boolean）';
COMMENT ON COLUMN pmis_rule_def.severity_expression IS '严重度表达式（可选，动态决定严重度）';
COMMENT ON COLUMN pmis_rule_def.priority IS '优先级（数值越小越先执行，默认100）';

-- 索引
CREATE INDEX IF NOT EXISTS idx_pmis_rule_def_category ON pmis_rule_def (category);
CREATE INDEX IF NOT EXISTS idx_pmis_rule_def_enabled  ON pmis_rule_def (enabled);

-- --------------------------------------------------------
-- 2. 规则版本历史表（审计追踪 + 回滚）
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_rule_version_history (
    id              BIGSERIAL       PRIMARY KEY,
    rule_code       VARCHAR(128)    NOT NULL,
    version         INTEGER         NOT NULL,
    definition_json TEXT            NOT NULL,
    change_desc     VARCHAR(512),
    operator        VARCHAR(64)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (rule_code, version)
);

COMMENT ON TABLE  pmis_rule_version_history IS 'LiteRule 规则版本历史表';
COMMENT ON COLUMN pmis_rule_version_history.definition_json IS '规则定义 JSON 快照';

CREATE INDEX IF NOT EXISTS idx_pmis_rule_ver_code ON pmis_rule_version_history (rule_code);

-- --------------------------------------------------------
-- 3. 预置规则（从硬编码迁移为表达式配置）
--    对标原 execution 模块 AlertRuleEngine 4 条内置规则
-- --------------------------------------------------------
INSERT INTO pmis_rule_def (rule_code, rule_name, category, description, condition_expression, severity_expression, default_severity, title_template, description_template, priority, scope, created_by)
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

INSERT INTO pmis_rule_def (rule_code, rule_name, category, description, condition_expression, severity_expression, default_severity, title_template, description_template, priority, scope, created_by)
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

INSERT INTO pmis_rule_def (rule_code, rule_name, category, description, condition_expression, severity_expression, default_severity, title_template, description_template, priority, scope, created_by)
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

INSERT INTO pmis_rule_def (rule_code, rule_name, category, description, condition_expression, severity_expression, default_severity, title_template, description_template, priority, scope, created_by)
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
INSERT INTO pmis_rule_version_history (rule_code, version, definition_json, change_desc, operator)
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
FROM pmis_rule_def
WHERE NOT EXISTS (
    SELECT 1 FROM pmis_rule_version_history h WHERE h.rule_code = pmis_rule_def.rule_code
);

-- --------------------------------------------------------
-- 5. 规则模板表（P2: 规则模板市场）
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_rule_template (
    id                    BIGSERIAL       PRIMARY KEY,
    template_code         VARCHAR(128)    NOT NULL UNIQUE,
    template_name         VARCHAR(256)    NOT NULL,
    category              VARCHAR(64)     NOT NULL DEFAULT 'GENERAL',
    description           TEXT,
    condition_expression  TEXT            NOT NULL,
    severity_expression   TEXT,
    default_severity      VARCHAR(16)     NOT NULL DEFAULT 'YELLOW',
    title_template        VARCHAR(512),
    description_template  TEXT,
    priority              INTEGER         NOT NULL DEFAULT 100,
    scope                 VARCHAR(128)    DEFAULT 'ALL',
    industry              VARCHAR(64)     DEFAULT 'GENERAL',
    tags                  VARCHAR(256),
    created_by            VARCHAR(64)     NOT NULL DEFAULT 'SYSTEM',
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE pmis_rule_template IS 'LiteRule 规则模板表（模板市场）';

CREATE INDEX IF NOT EXISTS idx_pmis_rule_tpl_category ON pmis_rule_template (category);
CREATE INDEX IF NOT EXISTS idx_pmis_rule_tpl_industry ON pmis_rule_template (industry);

-- 预置行业模板
INSERT INTO pmis_rule_template (template_code, template_name, category, description, condition_expression, severity_expression, default_severity, title_template, description_template, priority, scope, industry, tags)
VALUES
(
    'TPL_EVM_RED',
    'EVM 红色预警模板',
    'EVM',
    'EVM 红色项目数超标预警，适用于 IT 服务交付类项目',
    'evmRedCount >= 3',
    NULL,
    'RED',
    'EVM 红色项目 ${evmRedCount} 个',
    '红色告警项目数 ${evmRedCount}，超过阈值 3',
    100, 'ALL', 'IT_SERVICE', 'EVM,预警,成本'
) ON CONFLICT (template_code) DO NOTHING,
(
    'TPL_MARGIN_LOW',
    '毛利率过低模板',
    'COST',
    '毛利率低于行业基准预警，适用于所有项目类型',
    'grossMargin < 0.10 && confirmedRevenue > 0',
    'grossMargin < 0.05 ? "RED" : "YELLOW"',
    'YELLOW',
    '毛利率 ${grossMargin} 偏低',
    '毛利率 ${grossMargin} 低于阈值',
    110, 'ALL', 'GENERAL', '毛利,成本,预警'
) ON CONFLICT (template_code) DO NOTHING,
(
    'TPL_BENCH_IDLE',
    'Bench 闲置成本模板',
    'BENCH',
    'Bench 人员闲置成本超标预警，适用于人员外派型项目',
    'benchIdleCost >= 500000',
    'benchIdleCost >= 1000000 ? "RED" : "YELLOW"',
    'YELLOW',
    'Bench 闲置成本 ${benchIdleCost} 元',
    'Bench 闲置成本 ${benchIdleCost} 元，资源利用率不足',
    120, 'RESOURCE_POOL', 'STAFFING', 'Bench,闲置,资源'
) ON CONFLICT (template_code) DO NOTHING,
(
    'TPL_UTILIZATION_LOW',
    '利用率偏低模板',
    'UTILIZATION',
    '可计费利用率偏低预警，适用于咨询/外包服务类项目',
    'avgBillableUtilization < 0.70 && activeProjects > 0',
    'avgBillableUtilization < 0.50 ? "RED" : "YELLOW"',
    'YELLOW',
    '利用率 ${avgBillableUtilization} 偏低',
    '可计费利用率 ${avgBillableUtilization} 低于阈值',
    130, 'ALL', 'CONSULTING', '利用率,资源,预警'
) ON CONFLICT (template_code) DO NOTHING,
(
    'TPL_BUDGET_OVERRUN',
    '预算超支模板',
    'BUDGET',
    '项目预算使用率超标预警，适用于所有项目类型',
    'budgetUsageRatio >= 0.80',
    'budgetUsageRatio >= 0.95 ? "RED" : "YELLOW"',
    'YELLOW',
    '预算使用率 ${budgetUsageRatio}',
    '预算使用率 ${budgetUsageRatio}，已接近或超出预算',
    105, 'ALL', 'GENERAL', '预算,成本,预警'
) ON CONFLICT (template_code) DO NOTHING,
(
    'TPL_SLA_BREACH',
    'SLA 超时模板',
    'SLA',
    '工单 SLA 超时预警，适用于运维服务类项目',
    'slaBreachedCount > 0',
    'slaBreachedCount >= 3 ? "RED" : "YELLOW"',
    'YELLOW',
    'SLA 超时 ${slaBreachedCount} 单',
    'SLA 超时工单 ${slaBreachedCount} 个，需关注服务响应时效',
    115, 'ALL', 'IT_SERVICE', 'SLA,超时,运维'
) ON CONFLICT (template_code) DO NOTHING;
