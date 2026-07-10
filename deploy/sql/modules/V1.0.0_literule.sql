-- ====================================================================
-- System.Collections.Hashtable[literule]
-- Module: literule
-- Version: V1.0.0
-- Target: PostgreSQL 18
-- Description: 鏈枃浠剁敱 deploy/sql/V1.0.0.sql 鎷嗗垎鐢熸垚
--   浠呬緵鍗曠嫭鍒濆鍖栧搴旀ā鍧楁椂浣跨敤; 瀹屾暣鍒濆鍖栬浣跨敤 V1.0.0.sql
-- ====================================================================

-- ============================ [041] init pmis literule schema ============================

-- ============================================================
-- LiteRule 轻量规则引擎：规则定义表 + 规则版本历史表
-- ============================================================

-- --------------------------------------------------------
-- 1. 规则定义主表
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_rule_def (
    id                    VARCHAR(20)       PRIMARY KEY,
    tenant_id             VARCHAR(20)          NOT NULL DEFAULT '1',
    rule_code             VARCHAR(128)    NOT NULL,
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
    mutex_group           VARCHAR(128)    DEFAULT NULL,
    drilldown_available   BOOLEAN         NOT NULL DEFAULT TRUE,
    version               INTEGER         NOT NULL DEFAULT 1,
    provider_trace_id     VARCHAR(64),
    created_by            VARCHAR(64)     NOT NULL DEFAULT 'SYSTEM',
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by            VARCHAR(64),
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted               SMALLINT        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT uk_prd_tenant_code       UNIQUE (tenant_id, rule_code, deleted),
    CONSTRAINT ck_prd_severity           CHECK (default_severity IN ('RED','YELLOW','BLUE','GREEN','GRAY')),
    CONSTRAINT ck_prd_priority           CHECK (priority > 0),
    CONSTRAINT ck_prd_version            CHECK (version > 0),
    CONSTRAINT ck_prd_deleted            CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_rule_def IS 'LiteRule 规则定义表';
COMMENT ON COLUMN pmis_rule_def.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_rule_def.rule_code IS '规则编码（租户内唯一）';
COMMENT ON COLUMN pmis_rule_def.condition_expression IS '条件表达式（Aviator 语法，返回 boolean）';
COMMENT ON COLUMN pmis_rule_def.severity_expression IS '严重度表达式（可选，动态决定严重度）';
COMMENT ON COLUMN pmis_rule_def.priority IS '优先级（数值越小越先执行，默认100）';
COMMENT ON COLUMN pmis_rule_def.mutex_group IS '互斥组名称（同组内首个命中后跳过其余规则；NULL 表示无互斥组）';

-- 复合/部分索引
CREATE INDEX IF NOT EXISTS idx_prd_tenant_category_enabled
    ON pmis_rule_def (tenant_id, category, enabled)
    WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_prd_tenant_priority
    ON pmis_rule_def (tenant_id, priority)
    WHERE deleted = 0 AND enabled = TRUE;
CREATE INDEX IF NOT EXISTS idx_prd_tenant_mutex_group
    ON pmis_rule_def (tenant_id, mutex_group)
    WHERE deleted = 0 AND mutex_group IS NOT NULL;

-- --------------------------------------------------------
-- 2. 规则版本历史表（审计追踪 + 回滚）
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_rule_version_history (
    id              VARCHAR(20)       PRIMARY KEY,
    tenant_id       VARCHAR(20)          NOT NULL DEFAULT '1',
    rule_code       VARCHAR(128)    NOT NULL,
    version         INTEGER         NOT NULL,
    definition_json TEXT            NOT NULL,
    change_desc     VARCHAR(512),
    operator        VARCHAR(64)     NOT NULL,
    provider_trace_id VARCHAR(64),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    -- 数据完整性约束
    CONSTRAINT uk_prvh_tenant_code_version UNIQUE (tenant_id, rule_code, version),
    CONSTRAINT ck_prvh_version             CHECK (version > 0)
);

COMMENT ON TABLE  pmis_rule_version_history IS 'LiteRule 规则版本历史表';
COMMENT ON COLUMN pmis_rule_version_history.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_rule_version_history.definition_json IS '规则定义 JSON 快照';

CREATE INDEX IF NOT EXISTS idx_prvh_tenant_code
    ON pmis_rule_version_history (tenant_id, rule_code, version DESC);

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
) ON CONFLICT (tenant_id, rule_code, deleted) WHERE deleted = 0 DO NOTHING;

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
) ON CONFLICT (tenant_id, rule_code, deleted) WHERE deleted = 0 DO NOTHING;

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
) ON CONFLICT (tenant_id, rule_code, deleted) WHERE deleted = 0 DO NOTHING;

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
) ON CONFLICT (tenant_id, rule_code, deleted) WHERE deleted = 0 DO NOTHING;

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
    id                    VARCHAR(20)       PRIMARY KEY,
    tenant_id             VARCHAR(20)          NOT NULL DEFAULT '1',
    template_code         VARCHAR(128)    NOT NULL,
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
    provider_trace_id     VARCHAR(64),
    created_by            VARCHAR(64)     NOT NULL DEFAULT 'SYSTEM',
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by            VARCHAR(64),
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted               SMALLINT        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT uk_prt_tenant_code           UNIQUE (tenant_id, template_code, deleted),
    CONSTRAINT ck_prt_severity              CHECK (default_severity IN ('RED','YELLOW','BLUE','GREEN','GRAY')),
    CONSTRAINT ck_prt_priority              CHECK (priority > 0),
    CONSTRAINT ck_prt_deleted               CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_rule_template IS 'LiteRule 规则模板表（模板市场）';
COMMENT ON COLUMN pmis_rule_template.tenant_id IS '租户 ID';

-- 复合/部分索引
CREATE INDEX IF NOT EXISTS idx_prt_tenant_category
    ON pmis_rule_template (tenant_id, category)
    WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_prt_tenant_industry
    ON pmis_rule_template (tenant_id, industry)
    WHERE deleted = 0;

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
),
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
),
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
),
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
),
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
),
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
)
-- 早期版本在每个 VALUES tuple 后面写 ON CONFLICT，PG 不支持该语法。
-- 多行 VALUES 必须在整个块之后接 ON CONFLICT 子句。
ON CONFLICT (tenant_id, template_code, deleted) WHERE deleted = 0 DO NOTHING;

-- --------------------------------------------------------------------

-- ============================ [042] init pmis rule test case ============================

-- ============================================
-- 规则测试用例管理表
-- ============================================

-- 测试用例主表
CREATE TABLE IF NOT EXISTS pmis_rule_test_case (
    id                 VARCHAR(20)       PRIMARY KEY,
    tenant_id          VARCHAR(20)          NOT NULL DEFAULT '1',
    name               VARCHAR(256)    NOT NULL,
    rule_code          VARCHAR(128),
    facts_data         JSONB           NOT NULL,
    expected_triggered JSONB,
    description        TEXT,
    provider_trace_id  VARCHAR(64),
    created_by         VARCHAR(64)     NOT NULL DEFAULT 'SYSTEM',
    created_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by         VARCHAR(64),
    updated_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted            SMALLINT        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_prtc_deleted           CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_rule_test_case IS 'P1-9: 规则测试用例表,用于规则评估的回归测试';
COMMENT ON COLUMN pmis_rule_test_case.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_rule_test_case.id IS '主键 ID';
COMMENT ON COLUMN pmis_rule_test_case.name IS '测试用例名称';
COMMENT ON COLUMN pmis_rule_test_case.rule_code IS '关联规则编码 (可选, null 表示通用测试用例)';
COMMENT ON COLUMN pmis_rule_test_case.facts_data IS '事实数据 JSON (输入参数)';
COMMENT ON COLUMN pmis_rule_test_case.expected_triggered IS '预期触发的规则编码列表 JSON';
COMMENT ON COLUMN pmis_rule_test_case.description IS '用例描述';
COMMENT ON COLUMN pmis_rule_test_case.created_at IS '创建时间';
COMMENT ON COLUMN pmis_rule_test_case.updated_at IS '更新时间';

-- 复合/部分索引
CREATE INDEX IF NOT EXISTS idx_prtc_tenant_code
    ON pmis_rule_test_case (tenant_id, rule_code)
    WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_prtc_tenant_name
    ON pmis_rule_test_case (tenant_id, name)
    WHERE deleted = 0;

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
  '利用率65%且有活跃项目，应触发') ON CONFLICT DO NOTHING;
-- --------------------------------------------------------------------

-- ============================ [043] add rule lifecycle and trace ============================

-- ============================================
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

-- 状态索引（按租户过滤）
CREATE INDEX IF NOT EXISTS idx_prd_tenant_status
    ON pmis_rule_def (tenant_id, status)
    WHERE deleted = 0;

-- 2. 执行链路追踪表
CREATE TABLE IF NOT EXISTS pmis_rule_execution_trace (
    id                VARCHAR(20)       PRIMARY KEY,
    tenant_id         VARCHAR(20)          NOT NULL DEFAULT '1',
    trace_id          VARCHAR(20)     NOT NULL,
    rule_code         VARCHAR(128)    NOT NULL,
    rule_name         VARCHAR(256),
    scenario          VARCHAR(128),
    triggered         BOOLEAN         NOT NULL DEFAULT FALSE,
    severity          VARCHAR(16),
    condition_result  VARCHAR(256),
    elapsed_ms        BIGINT          NOT NULL DEFAULT 0,
    facts_snapshot    JSONB,
    result_snapshot   JSONB,
    error_message     TEXT,
    provider_trace_id VARCHAR(64),
    created_by        VARCHAR(64)     NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE pmis_rule_execution_trace IS 'P1-11: 规则执行链路追踪表,一次评估一条记录';
COMMENT ON COLUMN pmis_rule_execution_trace.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_rule_execution_trace.id IS '主键 ID';
COMMENT ON COLUMN pmis_rule_execution_trace.trace_id IS '追踪 ID (同一批次评估共享)';
COMMENT ON COLUMN pmis_rule_execution_trace.rule_code IS '规则编码';
COMMENT ON COLUMN pmis_rule_execution_trace.rule_name IS '规则名称';
COMMENT ON COLUMN pmis_rule_execution_trace.scenario IS '业务场景';
COMMENT ON COLUMN pmis_rule_execution_trace.triggered IS '是否触发';
COMMENT ON COLUMN pmis_rule_execution_trace.severity IS '严重度 (RED/YELLOW/GREEN/INFO)';
COMMENT ON COLUMN pmis_rule_execution_trace.condition_result IS '条件表达式求值结果描述';
COMMENT ON COLUMN pmis_rule_execution_trace.elapsed_ms IS '执行耗时 (毫秒)';
COMMENT ON COLUMN pmis_rule_execution_trace.facts_snapshot IS '事实数据快照 JSON';
COMMENT ON COLUMN pmis_rule_execution_trace.result_snapshot IS '结果快照 JSON';
COMMENT ON COLUMN pmis_rule_execution_trace.error_message IS '错误信息';
COMMENT ON COLUMN pmis_rule_execution_trace.created_at IS '创建时间';

-- 数据完整性约束（表内）
ALTER TABLE pmis_rule_execution_trace
    ADD CONSTRAINT ck_prelapsed_nonneg  CHECK (elapsed_ms >= 0);

-- 复合/部分索引
CREATE INDEX IF NOT EXISTS idx_pret_tenant_trace
    ON pmis_rule_execution_trace (tenant_id, trace_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pret_tenant_rule_code
    ON pmis_rule_execution_trace (tenant_id, rule_code, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pret_tenant_scenario
    ON pmis_rule_execution_trace (tenant_id, scenario, created_at DESC);
-- --------------------------------------------------------------------

-- ============================ [044] add decision table ============================

-- ============================================
-- 决策表支持
-- ============================================

CREATE TABLE IF NOT EXISTS pmis_rule_decision_table (
    id                 VARCHAR(20)       PRIMARY KEY,
    tenant_id          VARCHAR(20)          NOT NULL DEFAULT '1',
    table_code         VARCHAR(128)    NOT NULL,
    table_name         VARCHAR(256)    NOT NULL,
    description        TEXT,
    category           VARCHAR(64),
    -- 条件列定义 JSON: [{"name":"字段名","label":"显示名","type":"number|string|boolean"}]
    condition_columns  JSONB           NOT NULL,
    -- 动作列定义 JSON: [{"name":"severity","label":"严重度","type":"string"}]
    action_columns     JSONB           NOT NULL,
    -- 决策行 JSON: [{"conditions":{"字段名":"值"},"actions":{"severity":"RED"}}]
    rows               JSONB           NOT NULL DEFAULT '[]',
    -- 默认动作（未匹配行时的动作）
    default_actions    JSONB,
    enabled            BOOLEAN         NOT NULL DEFAULT TRUE,
    priority           INTEGER         NOT NULL DEFAULT 100,
    version            INTEGER         NOT NULL DEFAULT 1,
    provider_trace_id  VARCHAR(64),
    created_by         VARCHAR(64),
    created_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by         VARCHAR(64),
    updated_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted            SMALLINT        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT uk_prdt_tenant_code        UNIQUE (tenant_id, table_code, deleted),
    CONSTRAINT ck_prdt_priority           CHECK (priority > 0),
    CONSTRAINT ck_prdt_version            CHECK (version > 0),
    CONSTRAINT ck_prdt_deleted            CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_rule_decision_table IS 'P1-12: 决策表 (DMN 简化版),条件/动作/行均以 JSON 存储';
COMMENT ON COLUMN pmis_rule_decision_table.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_rule_decision_table.id IS '主键 ID';
COMMENT ON COLUMN pmis_rule_decision_table.table_code IS '决策表编码 (租户内唯一)';
COMMENT ON COLUMN pmis_rule_decision_table.table_name IS '决策表名称';
COMMENT ON COLUMN pmis_rule_decision_table.description IS '描述';
COMMENT ON COLUMN pmis_rule_decision_table.category IS '类别';
COMMENT ON COLUMN pmis_rule_decision_table.condition_columns IS '条件列定义 JSON: [{name,label,type}]';
COMMENT ON COLUMN pmis_rule_decision_table.action_columns IS '动作列定义 JSON: [{name,label,type}]';
COMMENT ON COLUMN pmis_rule_decision_table.rows IS '决策行 JSON: [{conditions,actions}]';
COMMENT ON COLUMN pmis_rule_decision_table.default_actions IS '默认动作 (未匹配行时使用) JSON';
COMMENT ON COLUMN pmis_rule_decision_table.enabled IS '是否启用';
COMMENT ON COLUMN pmis_rule_decision_table.priority IS '优先级';
COMMENT ON COLUMN pmis_rule_decision_table.version IS '版本号';
COMMENT ON COLUMN pmis_rule_decision_table.created_by IS '创建人';
COMMENT ON COLUMN pmis_rule_decision_table.created_at IS '创建时间';
COMMENT ON COLUMN pmis_rule_decision_table.updated_by IS '更新人';
COMMENT ON COLUMN pmis_rule_decision_table.updated_at IS '更新时间';

-- 复合/部分索引
CREATE INDEX IF NOT EXISTS idx_prdt_tenant_category_enabled
    ON pmis_rule_decision_table (tenant_id, category, enabled)
    WHERE deleted = 0;
-- --------------------------------------------------------------------

-- ============================ [045] add decision table hit policy ============================

-- ============================================
-- 决策表命中策略字段
--
-- 为 pmis_rule_decision_table 增加 hit_policy 列，支持 DMN 标准命中策略：
--   UNIQUE  - 唯一命中，匹配多行时报错
--   FIRST   - 首次命中（默认）
--   PRIORITY- 优先级命中，返回优先级最高的匹配行
--   COLLECT - 收集命中，返回所有匹配行
--   ANY     - 任意命中，返回任意一条匹配行
-- ============================================

ALTER TABLE pmis_rule_decision_table
    ADD COLUMN IF NOT EXISTS hit_policy VARCHAR(32) NOT NULL DEFAULT 'FIRST';

COMMENT ON COLUMN pmis_rule_decision_table.hit_policy IS '命中策略：UNIQUE/FIRST/PRIORITY/COLLECT/ANY';

-- --------------------------------------------------------------------

-- ============================ [047] add rule canary ============================

-- ============================================================
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
COMMENT ON COLUMN pmis_rule_def.canary_conditions IS '灰度条件表达式列表（Aviator 语法，AND 关系；JSON 数组，示例：["tenantId == ''T001''"]）';
COMMENT ON COLUMN pmis_rule_def.canary_condition_expression IS '灰度候选版本条件表达式（覆盖主版本，进行 A/B 验证）';
COMMENT ON COLUMN pmis_rule_def.canary_severity_expression IS '灰度候选版本严重度表达式（覆盖主版本）';

-- 灰度规则索引（按租户过滤）
CREATE INDEX IF NOT EXISTS idx_prd_tenant_canary_ratio
    ON pmis_rule_def (tenant_id, canary_ratio)
    WHERE deleted = 0 AND canary_ratio > 0;

-- ------------------------------------------------------------
-- 灰度分桶统计表（运营监控：rule_code -> 主桶/灰桶计数）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_rule_canary_bucket (
    id              VARCHAR(20)       PRIMARY KEY,
    tenant_id       VARCHAR(20)          NOT NULL DEFAULT '1',
    rule_code       VARCHAR(128)    NOT NULL,
    bucket_type     VARCHAR(16)     NOT NULL,  -- PRIMARY / CANARY
    bucket_count    BIGINT          NOT NULL DEFAULT 0,
    stat_date       DATE            NOT NULL DEFAULT CURRENT_DATE,
    provider_trace_id VARCHAR(64),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    -- 数据完整性约束
    CONSTRAINT uk_prcb_tenant_code_date_type UNIQUE (tenant_id, rule_code, bucket_type, stat_date),
    CONSTRAINT ck_prcb_bucket_type            CHECK (bucket_type IN ('PRIMARY','CANARY')),
    CONSTRAINT ck_prcb_bucket_count           CHECK (bucket_count >= 0)
);

COMMENT ON TABLE  pmis_rule_canary_bucket IS '规则灰度分桶统计表（按日聚合，便于运营对比新旧版本流量）';
COMMENT ON COLUMN pmis_rule_canary_bucket.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_rule_canary_bucket.bucket_type IS '桶类型：PRIMARY=主版本，CANARY=候选版本';

-- 复合/部分索引
CREATE INDEX IF NOT EXISTS idx_prcb_tenant_rule_date
    ON pmis_rule_canary_bucket (tenant_id, rule_code, stat_date DESC);
CREATE INDEX IF NOT EXISTS idx_prcb_tenant_date
    ON pmis_rule_canary_bucket (tenant_id, stat_date DESC);

-- --------------------------------------------------------------------

-- ============================ [049] add rule status check ============================

-- ============================================================
-- 规则状态字段数据库层 CHECK 约束（纵深防御，配合应用层 RuleStatus 状态机校验）
-- ============================================================

-- pmis_rule_def.status 限定合法状态值
ALTER TABLE pmis_rule_def
    DROP CONSTRAINT IF EXISTS ck_rule_def_status_valid;
ALTER TABLE pmis_rule_def
    ADD CONSTRAINT ck_rule_def_status_valid
    CHECK (status IN ('DRAFT', 'REVIEW', 'PUBLISHED', 'DISABLED', 'ARCHIVED'));

COMMENT ON CONSTRAINT ck_rule_def_status_valid ON pmis_rule_def IS
    '规则状态合法性约束，配合应用层 RuleStatus.canTransitionTo 状态机校验';

-- --------------------------------------------------------------------

-- ============================ [051] init rule scorecard tree script ============================

-- ============================================================
-- 评分卡 / 决策树 / 脚本规则持久化
-- （原 V1.0.0_048 与 add_pmis_flow_run_task_priority 版本号冲突，迁移到 051）
-- ============================================================

-- --------------------------------------------------------
-- 1. 评分卡规则定义表
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_rule_scorecard (
    id               VARCHAR(20)       PRIMARY KEY,
    tenant_id        VARCHAR(20)          NOT NULL DEFAULT '1',
    rule_code        VARCHAR(128)    NOT NULL,
    rule_name        VARCHAR(256)    NOT NULL,
    category         VARCHAR(64)     NOT NULL DEFAULT 'RISK',
    description      TEXT,
    base_score       NUMERIC(10,2)   NOT NULL DEFAULT 100,
    red_threshold    NUMERIC(10,2)   NOT NULL,
    yellow_threshold NUMERIC(10,2)   NOT NULL,
    factors          JSONB           NOT NULL,   -- [{conditionExpression, score, description}]
    priority         INTEGER         NOT NULL DEFAULT 100,
    enabled          BOOLEAN         NOT NULL DEFAULT TRUE,
    scope            VARCHAR(128)    DEFAULT 'ALL',
    version          INTEGER         NOT NULL DEFAULT 1,
    provider_trace_id VARCHAR(64),
    created_by       VARCHAR(64)     NOT NULL DEFAULT 'SYSTEM',
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by       VARCHAR(64),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted          SMALLINT        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT uk_prs2_tenant_code        UNIQUE (tenant_id, rule_code, deleted),
    CONSTRAINT ck_prs2_base_score         CHECK (base_score >= 0),
    CONSTRAINT ck_prs2_threshold_order    CHECK (red_threshold <= yellow_threshold),
    CONSTRAINT ck_prs2_priority           CHECK (priority > 0),
    CONSTRAINT ck_prs2_version            CHECK (version > 0),
    CONSTRAINT ck_prs2_deleted            CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_rule_scorecard IS '评分卡规则定义表';
COMMENT ON COLUMN pmis_rule_scorecard.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_rule_scorecard.base_score IS '基础分（命中因子前的基础值，默认 100）';
COMMENT ON COLUMN pmis_rule_scorecard.red_threshold IS '红色阈值（总分低于此值为 RED）';
COMMENT ON COLUMN pmis_rule_scorecard.yellow_threshold IS '黄色阈值（总分低于此值为 YELLOW）';
COMMENT ON COLUMN pmis_rule_scorecard.factors IS '评分因子数组，JSON 格式：[{"conditionExpression":"...","score":-30,"description":"..."}]';

-- 复合/部分索引
CREATE INDEX IF NOT EXISTS idx_prs2_tenant_category_enabled
    ON pmis_rule_scorecard (tenant_id, category, enabled)
    WHERE deleted = 0;

-- --------------------------------------------------------
-- 2. 决策树规则定义表
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_rule_decision_tree (
    id              VARCHAR(20)       PRIMARY KEY,
    tenant_id       VARCHAR(20)          NOT NULL DEFAULT '1',
    rule_code       VARCHAR(128)    NOT NULL,
    rule_name       VARCHAR(256)    NOT NULL,
    category        VARCHAR(64)     NOT NULL DEFAULT 'GENERAL',
    description     TEXT,
    root_node       JSONB           NOT NULL,   -- 嵌套决策树节点
    priority        INTEGER         NOT NULL DEFAULT 100,
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    scope           VARCHAR(128)    DEFAULT 'ALL',
    version         INTEGER         NOT NULL DEFAULT 1,
    provider_trace_id VARCHAR(64),
    created_by      VARCHAR(64)     NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by      VARCHAR(64),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT uk_prdt2_tenant_code    UNIQUE (tenant_id, rule_code, deleted),
    CONSTRAINT ck_prdt2_priority       CHECK (priority > 0),
    CONSTRAINT ck_prdt2_version        CHECK (version > 0),
    CONSTRAINT ck_prdt2_deleted        CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_rule_decision_tree IS '决策树规则定义表';
COMMENT ON COLUMN pmis_rule_decision_tree.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_rule_decision_tree.root_node IS '决策树根节点 JSON：{conditionExpression, trueBranch, falseBranch, leaf, severity, title, description}';

-- 复合/部分索引
CREATE INDEX IF NOT EXISTS idx_prdt2_tenant_category_enabled
    ON pmis_rule_decision_tree (tenant_id, category, enabled)
    WHERE deleted = 0;

-- --------------------------------------------------------
-- 3. 脚本规则定义表
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_rule_script (
    id               VARCHAR(20)       PRIMARY KEY,
    tenant_id        VARCHAR(20)          NOT NULL DEFAULT '1',
    rule_code        VARCHAR(128)    NOT NULL,
    rule_name        VARCHAR(256)    NOT NULL,
    category         VARCHAR(64)     NOT NULL DEFAULT 'GENERAL',
    description      TEXT,
    script           TEXT            NOT NULL,   -- Groovy 脚本
    default_severity VARCHAR(16)     NOT NULL DEFAULT 'INFO',
    sandbox_enabled  BOOLEAN         NOT NULL DEFAULT TRUE,
    priority         INTEGER         NOT NULL DEFAULT 100,
    enabled          BOOLEAN         NOT NULL DEFAULT TRUE,
    scope            VARCHAR(128)    DEFAULT 'ALL',
    version          INTEGER         NOT NULL DEFAULT 1,
    provider_trace_id VARCHAR(64),
    created_by       VARCHAR(64)     NOT NULL DEFAULT 'SYSTEM',
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by       VARCHAR(64),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted          SMALLINT        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT uk_prsc_tenant_code     UNIQUE (tenant_id, rule_code, deleted),
    CONSTRAINT ck_prsc_severity         CHECK (default_severity IN ('RED','YELLOW','BLUE','GREEN','GRAY','INFO')),
    CONSTRAINT ck_prsc_priority         CHECK (priority > 0),
    CONSTRAINT ck_prsc_version          CHECK (version > 0),
    CONSTRAINT ck_prsc_deleted          CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_rule_script IS '脚本规则定义表（Groovy JSR-223）';
COMMENT ON COLUMN pmis_rule_script.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_rule_script.script IS 'Groovy 脚本内容（沙箱模式下禁止 System/反射/IO/网络访问）';
COMMENT ON COLUMN pmis_rule_script.sandbox_enabled IS '是否启用沙箱（默认 TRUE）';

-- 复合/部分索引
CREATE INDEX IF NOT EXISTS idx_prsc_tenant_category_enabled
    ON pmis_rule_script (tenant_id, category, enabled)
    WHERE deleted = 0;

-- --------------------------------------------------------------------

-- ============================ [053] add rule tenant id ============================

-- ============================================================
-- LiteRule 模块多租户字段预留（与项目其他业务表对齐）
--
-- 说明：
--   项目其他业务表（pmis_project_*、pmis_flow_* 等）已普遍预埋
--   tenant_id VARCHAR(20) NOT NULL DEFAULT '1' 字段。LiteRule 模块的表
--   此前完全缺失该字段，本次补齐以保持 schema 一致性。
--
--   本迁移仅添加字段与索引，不改变现有查询逻辑（单租户部署下
--   tenant_id 恒为 1）。运行时按租户过滤的能力待 v2.0 多租户化
--   阶段与 TenantContext/TenantLineInnerInterceptor 一并启用。
--   详见 docs/multi-tenant-evaluation.md。
-- ============================================================

-- 1. 规则定义表
ALTER TABLE pmis_rule_def
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_rule_def_tenant ON pmis_rule_def (tenant_id);
COMMENT ON COLUMN pmis_rule_def.tenant_id IS '租户 ID（单租户部署默认 1，多租户隔离待 v2.0 启用）';

-- 2. 规则版本历史表
ALTER TABLE pmis_rule_version_history
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_rule_version_tenant ON pmis_rule_version_history (tenant_id);

-- 3. 规则执行轨迹表
ALTER TABLE pmis_rule_execution_trace
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_rule_trace_tenant ON pmis_rule_execution_trace (tenant_id);

-- 4. 决策表定义表
ALTER TABLE pmis_rule_decision_table
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_rule_dt_tenant ON pmis_rule_decision_table (tenant_id);

-- 5. 评分卡定义表
ALTER TABLE pmis_rule_scorecard
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_rule_scorecard_tenant ON pmis_rule_scorecard (tenant_id);

-- 6. 决策树定义表
ALTER TABLE pmis_rule_decision_tree
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_rule_tree_tenant ON pmis_rule_decision_tree (tenant_id);

-- 7. 脚本规则定义表
ALTER TABLE pmis_rule_script
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_rule_script_tenant ON pmis_rule_script (tenant_id);

-- 8. 灰度分桶统计表
ALTER TABLE pmis_rule_canary_bucket
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';
CREATE INDEX IF NOT EXISTS idx_rule_canary_bucket_tenant ON pmis_rule_canary_bucket (tenant_id);

-- --------------------------------------------------------------------

-- ====================================================================
-- V1.0.0_054 已优化内联至 V1.0.0_001 的 pmis_user_account 定义中
-- (dept_id / leader_id / position_code 字段及对应 3 个索引均已内联)
-- ====================================================================

-- ============================ [055] init rule variable def ============================

-- ============================================================
-- P2-4 变量空间元数据：规则表达式中可引用的变量定义表
-- @author ydsz-pmis-team
-- @since 1.4.0
-- ============================================================

CREATE TABLE IF NOT EXISTS pmis_rule_variable_def (
    id                VARCHAR(20)       PRIMARY KEY,
    tenant_id         VARCHAR(20)          NOT NULL DEFAULT '1',
    var_name          VARCHAR(128)    NOT NULL,
    var_type          VARCHAR(128)    NOT NULL,
    description       VARCHAR(512),
    sample_value      TEXT,
    category          VARCHAR(64)     NOT NULL DEFAULT 'GENERAL',
    required          BOOLEAN         NOT NULL DEFAULT FALSE,
    enabled           BOOLEAN         NOT NULL DEFAULT TRUE,
    provider_trace_id VARCHAR(64),
    created_by        VARCHAR(64)     NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT uk_prvd_tenant_name       UNIQUE (tenant_id, var_name, deleted),
    CONSTRAINT ck_prvd_deleted           CHECK (deleted IN (0, 1))
);

-- 复合/部分索引
CREATE INDEX IF NOT EXISTS idx_prvd_tenant_category
    ON pmis_rule_variable_def (tenant_id, category, enabled)
    WHERE deleted = 0;

COMMENT ON TABLE  pmis_rule_variable_def IS '规则变量定义表：规则表达式中可引用的变量元数据';
COMMENT ON COLUMN pmis_rule_variable_def.var_name     IS '变量名（如 cpi / budgetAmount / evmRedCount）';
COMMENT ON COLUMN pmis_rule_variable_def.var_type     IS '变量类型（java.lang.Number / java.lang.String 等）';
COMMENT ON COLUMN pmis_rule_variable_def.description   IS '变量描述（中文，供前端编辑器提示）';
COMMENT ON COLUMN pmis_rule_variable_def.sample_value  IS '示例值（用于前端编辑器预览和 dryRun 默认 facts）';
COMMENT ON COLUMN pmis_rule_variable_def.category      IS '变量来源类别（EVM / PROJECT / FINANCE / BENCH 等）';
COMMENT ON COLUMN pmis_rule_variable_def.required       IS '是否必填（前端编辑器可标记必填变量）';
COMMENT ON COLUMN pmis_rule_variable_def.enabled        IS '是否启用';
COMMENT ON COLUMN pmis_rule_variable_def.tenant_id      IS '租户 ID（单租户部署默认 1）';

-- 内置变量种子数据（EVM 类）
INSERT INTO pmis_rule_variable_def (var_name, var_type, description, sample_value, category, required) VALUES
    ('cpi',                'java.lang.Number',  '成本绩效指数（CPI），>1 表示成本节约',     '0.85', 'EVM',     TRUE),
    ('spi',                'java.lang.Number',  '进度绩效指数（SPI），>1 表示进度超前',     '0.92', 'EVM',     TRUE),
    ('evmRedCount',        'java.lang.Integer', '红色 EVM 预警数量',                       '3',    'EVM',     FALSE),
    ('evmYellowCount',     'java.lang.Integer', '黄色 EVM 预警数量',                       '5',    'EVM',     FALSE),
    ('benchIdleCost',      'java.lang.Number',  '闲置设备成本',                            '1000', 'BENCH',   FALSE),
    ('benchIdleCount',     'java.lang.Integer', '闲置设备数量',                            '2',    'BENCH',   FALSE),
    ('grossMargin',        'java.lang.Number',  '毛利率（0~1）',                            '0.05', 'FINANCE', TRUE),
    ('confirmedRevenue',   'java.lang.Number',  '已确认收入',                               '5000', 'FINANCE', TRUE),
    ('budgetAmount',        'java.lang.Number',  '项目预算金额',                              '500000','BUDGET', TRUE),
    ('budgetUsageRatio',   'java.lang.Number',  '预算使用率（0~1）',                          '0.85', 'BUDGET', TRUE),
    ('projectName',        'java.lang.String',  '项目名称',                                  '示例项目','PROJECT',TRUE),
    ('projectStatus',      'java.lang.String',  '项目状态（IN_PROGRESS/Delayed/Completed）','IN_PROGRESS','PROJECT',TRUE),
    ('tenantId',           'java.lang.String',  '租户 ID',                                   'T001','PROJECT',FALSE)
ON CONFLICT (tenant_id, var_name, deleted) WHERE deleted = 0 DO NOTHING;

-- --------------------------------------------------------------------

