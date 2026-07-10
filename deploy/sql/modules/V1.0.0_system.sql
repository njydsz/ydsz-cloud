-- ============================================================
-- PMIS system module SQL
-- Auto-generated from V1.0.0.sql
-- ============================================================

-- ====================================================================
-- 6. 系统配置
-- ====================================================================

CREATE TABLE IF NOT EXISTS pmis_config(
    id              VARCHAR(20)      PRIMARY KEY DEFAULT replace(gen_random_uuid()::text,'-',''),
    config_group    VARCHAR(64)    NOT NULL,
    config_key      VARCHAR(128)   NOT NULL,
    config_value    TEXT,
    value_type      VARCHAR(16)    NOT NULL DEFAULT 'STRING',
    default_value   TEXT,
    description     TEXT,
    is_public       SMALLINT       NOT NULL DEFAULT 0,
    sort_order      INTEGER        NOT NULL DEFAULT 0,
    status          VARCHAR(16)    NOT NULL DEFAULT 'ENABLED',
    created_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT       NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(20)         NOT NULL DEFAULT '1',
    CONSTRAINT uk_pmis_config_key UNIQUE (config_group, config_key, deleted),
    CONSTRAINT ck_pc_value_type    CHECK (value_type IN ('STRING', 'NUMBER', 'BOOLEAN', 'JSON')),
    CONSTRAINT ck_pc_status_enum   CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_pc_public_enum   CHECK (is_public IN (0, 1)),
    CONSTRAINT ck_pc_deleted_enum  CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_config IS '系统配置表: 业务可热更新的参数(预警阈值/费率/工作流引擎等),按 group 分组';

COMMENT ON COLUMN pmis_config.id IS '主键 ID';

COMMENT ON COLUMN pmis_config.config_group IS '配置分组(如 alert/rate/workflow/system)';

COMMENT ON COLUMN pmis_config.config_key IS '配置键(同组下唯一,如 alert.cpi.yellow)';

COMMENT ON COLUMN pmis_config.config_value IS '配置值';

COMMENT ON COLUMN pmis_config.value_type IS '值类型: STRING 字符串 / NUMBER 数值 / BOOLEAN 布尔 / JSON JSON 对象';

COMMENT ON COLUMN pmis_config.default_value IS '默认值(配置缺失时回退使用)';

COMMENT ON COLUMN pmis_config.description IS '配置项说明';

COMMENT ON COLUMN pmis_config.is_public IS '是否对前端公开: 1 公开 / 0 仅后端(避免敏感配置泄漏)';

COMMENT ON COLUMN pmis_config.sort_order IS '排序号';

COMMENT ON COLUMN pmis_config.status IS '启用状态: ENABLED 启用 / DISABLED 停用';

COMMENT ON COLUMN pmis_config.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_config.created_at IS '创建时间';

COMMENT ON COLUMN pmis_config.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_config.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_config.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_config.tenant_id IS '租户 ID(单租户部署默认 1)';

CREATE INDEX IF NOT EXISTS idx_pmis_config_group ON pmis_config (config_group) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_config_tenant ON pmis_config(tenant_id);

CREATE INDEX IF NOT EXISTS idx_config_tenant_created
    ON pmis_config(tenant_id, created_at DESC) WHERE deleted = 0;

-- 初始化系统配置
INSERT INTO pmis_config (config_group, config_key, config_value, value_type, description, created_by) VALUES
    ('system', 'system.name', 'PMIS 项目运营管理系统', 'STRING', '系统名称', 0),
    ('system', 'system.version', '1.0.0', 'STRING', '系统版本', 0),
    ('rate', 'rate.social.company.rate', '0.245', 'NUMBER', '公司社保比例', 0),
    ('rate', 'rate.fund.company.rate', '0.05', 'NUMBER', '公司公积金比例', 0),
    ('rate', 'rate.workdays.per.month', '21.75', 'NUMBER', '月计薪天数', 0),
    ('rate', 'rate.hours.per.day', '8', 'NUMBER', '日标准工时', 0),
    ('workflow', 'workflow.engine', 'pmis', 'STRING', '工作流引擎（自研 pmis_flow_*）', 0),
    ('alert', 'alert.cpi.yellow', '0.95', 'NUMBER', 'CPI 黄色预警阈值', 0),
    ('alert', 'alert.cpi.red', '0.85', 'NUMBER', 'CPI 红色预警阈值', 0),
    ('alert', 'alert.spi.yellow', '0.90', 'NUMBER', 'SPI 黄色预警阈值', 0),
    ('alert', 'alert.spi.red', '0.80', 'NUMBER', 'SPI 红色预警阈值', 0),
    ('alert', 'alert.bench.days.yellow', '7', 'NUMBER', 'Bench 黄色预警天数', 0),
    ('alert', 'alert.bench.days.red', '15', 'NUMBER', 'Bench 红色预警天数', 0)
ON CONFLICT DO NOTHING;

-- ============================ [006e] P7-2 租户级配额 ============================

-- [P7-2] 租户级配额表：控制单个租户可创建任务数、并发执行数、日执行总量
-- 未配置记录的租户视为 unlimited（由应用层 CronjobProperties.Quota.defaultMax* 兜底）
CREATE TABLE IF NOT EXISTS pmis_tenant_quota(
    id                    VARCHAR(20)      PRIMARY KEY DEFAULT replace(gen_random_uuid()::text,'-',''),
    tenant_id             VARCHAR(20)      NOT NULL UNIQUE,
    -- 任务数上限（NULL=unlimited；超过此值拒绝创建新任务）
    max_jobs              INTEGER,
    -- 并发执行上限（NULL=unlimited；超过此值拒绝派发，P7-3 实现）
    max_concurrent        INTEGER,
    -- 日执行量上限（NULL=unlimited；超过此值拒绝派发，P7-3 实现）
    max_daily_executions  INTEGER,
    -- 是否启用配额检查（false=该租户不受配额限制，即使配置了上限）
    enabled               SMALLINT       NOT NULL DEFAULT 1,
    -- 审计字段
    created_by            VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               SMALLINT       NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_ptq_max_jobs_pos        CHECK (max_jobs IS NULL OR max_jobs > 0),
    CONSTRAINT ck_ptq_max_concurrent_pos CHECK (max_concurrent IS NULL OR max_concurrent > 0),
    CONSTRAINT ck_ptq_max_daily_pos      CHECK (max_daily_executions IS NULL OR max_daily_executions > 0),
    CONSTRAINT ck_ptq_enabled_enum       CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_ptq_deleted_enum      CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_tenant_quota IS '租户级配额表（P7-2）：控制单个租户的任务数/并发数/日执行量上限';

COMMENT ON COLUMN pmis_tenant_quota.id IS '主键 ID';

COMMENT ON COLUMN pmis_tenant_quota.tenant_id IS '租户 ID（唯一，一个租户一条配额记录）';

COMMENT ON COLUMN pmis_tenant_quota.max_jobs IS '任务数上限（NULL=unlimited）';

COMMENT ON COLUMN pmis_tenant_quota.max_concurrent IS '并发执行上限（NULL=unlimited，P7-3 实现）';

COMMENT ON COLUMN pmis_tenant_quota.max_daily_executions IS '日执行量上限（NULL=unlimited，P7-3 实现）';

COMMENT ON COLUMN pmis_tenant_quota.enabled IS '是否启用配额检查: 0 禁用 / 1 启用';

COMMENT ON COLUMN pmis_tenant_quota.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_tenant_quota.created_at IS '创建时间';

COMMENT ON COLUMN pmis_tenant_quota.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_tenant_quota.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_tenant_quota.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

-- 默认租户（tenant_id='1'）的初始配额记录（unlimited，便于单租户部署直接使用）
INSERT INTO pmis_tenant_quota (id, tenant_id, max_jobs, max_concurrent, max_daily_executions, enabled)
VALUES ('1', '1', NULL, NULL, NULL, 1)
ON CONFLICT (tenant_id) DO NOTHING;

-- --------------------------------------------------------------------

-- ============================ [019] init pmis alert thresholds ============================

-- ====================================================================
-- 预警阈值配置（pmis_config，group=alert）
--
--  说明：EVM / Bench / 预算 / 毛利率 / 利用率 等模块的告警阈值从此处读取，
--       业务模块通过 ConfigClient Feign 调用 ydsz-pmis-system 读取。
-- ====================================================================

INSERT INTO pmis_config (config_group, config_key, config_value, value_type, description, is_public, created_by)
VALUES
    -- EVM 阈值
    ('alert', 'alert.cpi.yellow', '0.95', 'NUMBER', 'CPI 黄色预警阈值（低于即黄灯）', 0, 0),
    ('alert', 'alert.cpi.red',    '0.85', 'NUMBER', 'CPI 红色预警阈值（低于即红灯）', 0, 0),
    ('alert', 'alert.spi.yellow', '0.90', 'NUMBER', 'SPI 黄色预警阈值', 0, 0),
    ('alert', 'alert.spi.red',    '0.80', 'NUMBER', 'SPI 红色预警阈值', 0, 0),
    -- Bench 阈值
    ('alert', 'alert.bench.days.yellow', '7',  'NUMBER', 'Bench 黄色预警天数', 0, 0),
    ('alert', 'alert.bench.days.red',    '15', 'NUMBER', 'Bench 红色预警天数', 0, 0),
    ('alert', 'alert.bench.cost.ratio',  '0.08', 'NUMBER', 'Bench 成本占比预警阈值（占总人力成本）', 0, 0),
    -- EVM 红色项目数
    ('alert', 'alert.evm.red.count',     '3',        'NUMBER', 'EVM 红色项目数预警阈值', 0, 0),
    -- 毛利率
    ('alert', 'alert.margin.yellow',     '0.10',     'NUMBER', '毛利率黄色预警阈值', 0, 0),
    ('alert', 'alert.margin.red',        '0.05',     'NUMBER', '毛利率红色预警阈值', 0, 0),
    -- Bench 闲置成本
    ('alert', 'alert.bench.yellow.cost', '500000',   'NUMBER', 'Bench 闲置成本黄色预警阈值（元）', 0, 0),
    ('alert', 'alert.bench.red.cost',    '1000000',  'NUMBER', 'Bench 闲置成本红色预警阈值（元）', 0, 0),
    -- 可计费利用率
    ('alert', 'alert.utilization.yellow', '0.70',    'NUMBER', '可计费利用率黄色预警阈值', 0, 0),
    ('alert', 'alert.utilization.red',    '0.50',    'NUMBER', '可计费利用率红色预警阈值', 0, 0),
    -- 预算使用率
    ('alert', 'alert.budget.yellow',     '0.80',     'NUMBER', '预算使用率黄色预警阈值', 0, 0),
    ('alert', 'alert.budget.red',        '0.95',     'NUMBER', '预算使用率红色预警阈值', 0, 0)
ON CONFLICT (config_group, config_key, deleted) DO UPDATE
    SET config_value = EXCLUDED.config_value,
        description   = EXCLUDED.description,
        updated_at    = CURRENT_TIMESTAMP;

-- 16. 配置
ALTER TABLE pmis_config ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_config_tenant ON pmis_config(tenant_id);

CREATE INDEX IF NOT EXISTS idx_config_tenant_created
    ON pmis_config(tenant_id, created_at DESC) WHERE deleted = 0;

ANALYZE pmis_config;

