-- =====================================================
-- PMIS 批次10 DDL：EVM 挣值 / 对外报价费率 / 对内成本费率 / 利润测算
-- 版本: V1.0.0_013
-- 描述: 挣值测量(pmis_evm_measure)、对外报价费率(pmis_rate_card)、
--       对内成本费率(pmis_rate_internal)、利润测算版本(pmis_profit_simulation)
-- Schema: pmis
-- =====================================================

-- =====================================================
-- 1. EVM 挣值测量表 pmis_evm_measure
-- =====================================================
DROP TABLE IF EXISTS pmis.pmis_evm_measure;
CREATE TABLE pmis.pmis_evm_measure (
    id                  BIGSERIAL PRIMARY KEY,
    initiation_id       BIGINT       NOT NULL,
    wbs_task_id         BIGINT,                                -- 可空：项目级度量
    period              VARCHAR(16)  NOT NULL,                 -- YYYY-MM
    pv                  NUMERIC(15,2) NOT NULL DEFAULT 0,      -- 计划值
    ev                  NUMERIC(15,2) NOT NULL DEFAULT 0,      -- 挣值
    ac                  NUMERIC(15,2) NOT NULL DEFAULT 0,      -- 实际成本
    bac                 NUMERIC(15,2) NOT NULL DEFAULT 0,      -- 完工预算
    cpi                 NUMERIC(7,4)  NOT NULL DEFAULT 1.0,
    spi                 NUMERIC(7,4)  NOT NULL DEFAULT 1.0,
    cv                  NUMERIC(15,2) NOT NULL DEFAULT 0,      -- 成本偏差 EV-AC
    sv                  NUMERIC(15,2) NOT NULL DEFAULT 0,      -- 进度偏差 EV-PV
    eac                 NUMERIC(15,2) NOT NULL DEFAULT 0,      -- 完工估算 BAC/CPI
    vac                 NUMERIC(15,2) NOT NULL DEFAULT 0,      -- 完工偏差 BAC-EAC
    etc                 NUMERIC(15,2) NOT NULL DEFAULT 0,      -- 完工尚需 EAC-AC
    tcpi                NUMERIC(7,4)  NOT NULL DEFAULT 1.0,    -- 完工绩效指数
    alert_level         VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',
    alert_reason        TEXT,
    measure_date        DATE         NOT NULL,
    remark              TEXT,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0
);
COMMENT ON TABLE pmis.pmis_evm_measure IS 'EVM 挣值测量记录（PV/EV/AC/CPI/SPI/EAC/VAC）';
CREATE INDEX idx_pem_initiation ON pmis.pmis_evm_measure(initiation_id);
CREATE INDEX idx_pem_wbs ON pmis.pmis_evm_measure(wbs_task_id);
CREATE INDEX idx_pem_period ON pmis.pmis_evm_measure(initiation_id, period);
CREATE INDEX idx_pem_alert ON pmis.pmis_evm_measure(alert_level);

-- =====================================================
-- 2. 对外报价费率表 pmis_rate_card
-- =====================================================
DROP TABLE IF EXISTS pmis.pmis_rate_card;
CREATE TABLE pmis.pmis_rate_card (
    id                  BIGSERIAL PRIMARY KEY,
    rate_code           VARCHAR(64)  NOT NULL,
    level_code          VARCHAR(16)  NOT NULL,                 -- L1-L18
    project_type        VARCHAR(32),                           -- ProjectType
    customer_level      VARCHAR(8),                            -- A/B/C/D
    billing_unit        VARCHAR(16)  NOT NULL DEFAULT 'DAY',   -- DAY/HOUR
    rate_amount         NUMERIC(15,2) NOT NULL DEFAULT 0,
    currency            VARCHAR(16)  NOT NULL DEFAULT 'CNY',
    effective_date      DATE         NOT NULL,
    expiry_date         DATE,
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    remark              TEXT,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_prc_code UNIQUE (rate_code, deleted)
);
COMMENT ON TABLE pmis.pmis_rate_card IS '对外报价费率 Rate Card';
CREATE INDEX idx_prc_level ON pmis.pmis_rate_card(level_code, project_type, customer_level);
CREATE INDEX idx_prc_status ON pmis.pmis_rate_card(status, effective_date);

-- =====================================================
-- 3. 对内成本费率表 pmis_rate_internal
-- =====================================================
DROP TABLE IF EXISTS pmis.pmis_rate_internal;
CREATE TABLE pmis.pmis_rate_internal (
    id                  BIGSERIAL PRIMARY KEY,
    rate_code           VARCHAR(64)  NOT NULL,
    level_code          VARCHAR(16)  NOT NULL,
    department_id       BIGINT,                                -- 事业部/部门
    department_name     VARCHAR(256),
    billing_unit        VARCHAR(16)  NOT NULL DEFAULT 'DAY',
    cost_amount         NUMERIC(15,2) NOT NULL DEFAULT 0,
    currency            VARCHAR(16)  NOT NULL DEFAULT 'CNY',
    effective_date      DATE         NOT NULL,
    expiry_date         DATE,
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    remark              TEXT,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pri_code UNIQUE (rate_code, deleted)
);
COMMENT ON TABLE pmis.pmis_rate_internal IS '对内成本费率';
CREATE INDEX idx_pri_level_dept ON pmis.pmis_rate_internal(level_code, department_id);
CREATE INDEX idx_pri_status ON pmis.pmis_rate_internal(status, effective_date);

-- =====================================================
-- 4. 利润测算版本表 pmis_profit_simulation
-- =====================================================
DROP TABLE IF EXISTS pmis.pmis_profit_simulation;
CREATE TABLE pmis.pmis_profit_simulation (
    id                  BIGSERIAL PRIMARY KEY,
    simulation_code     VARCHAR(64)  NOT NULL,
    simulation_name     VARCHAR(256) NOT NULL,
    initiation_id       BIGINT       NOT NULL,
    version             INTEGER      NOT NULL DEFAULT 1,
    scenario_type       VARCHAR(32)  NOT NULL DEFAULT 'BASE',   -- BASE/OPTIMISTIC/PESSIMISTIC/CUSTOM
    contract_amount     NUMERIC(15,2) NOT NULL DEFAULT 0,
    external_revenue    NUMERIC(15,2) NOT NULL DEFAULT 0,
    internal_cost       NUMERIC(15,2) NOT NULL DEFAULT 0,
    expected_hours      NUMERIC(15,2) NOT NULL DEFAULT 0,
    blended_rate        NUMERIC(15,2) NOT NULL DEFAULT 0,
    gross_profit        NUMERIC(15,2) NOT NULL DEFAULT 0,
    gross_margin        NUMERIC(7,4)  NOT NULL DEFAULT 0,
    target_margin       NUMERIC(7,4)  NOT NULL DEFAULT 0,
    labor_cost          NUMERIC(15,2) NOT NULL DEFAULT 0,
    purchase_cost       NUMERIC(15,2) NOT NULL DEFAULT 0,
    expense_cost        NUMERIC(15,2) NOT NULL DEFAULT 0,
    outsource_cost      NUMERIC(15,2) NOT NULL DEFAULT 0,
    assumptions         TEXT,                                  -- JSON
    status              VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    approver_name       VARCHAR(64),
    approved_at         TIMESTAMP,
    remark              TEXT,
    applicant_id        BIGINT,
    applicant_name      VARCHAR(64),
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pps_code UNIQUE (simulation_code, deleted)
);
COMMENT ON TABLE pmis.pmis_profit_simulation IS '利润测算版本（What-if 多版本对比）';
CREATE INDEX idx_pps_initiation ON pmis.pmis_profit_simulation(initiation_id);
CREATE INDEX idx_pps_version ON pmis.pmis_profit_simulation(initiation_id, version);
CREATE INDEX idx_pps_status ON pmis.pmis_profit_simulation(status, scenario_type);

-- =====================================================
-- 5. 初始化 L1-L18 职级默认对外报价费率（基线参考）
-- =====================================================
INSERT INTO pmis.pmis_rate_card
    (rate_code, level_code, project_type, customer_level, billing_unit, rate_amount, currency, effective_date, status, tenant_id, provider_trace_id)
VALUES
    ('RC-L1-DEFAULT',  'L1',  NULL, NULL, 'DAY',  1200.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L2-DEFAULT',  'L2',  NULL, NULL, 'DAY',  1500.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L3-DEFAULT',  'L3',  NULL, NULL, 'DAY',  1800.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L4-DEFAULT',  'L4',  NULL, NULL, 'DAY',  2200.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L5-DEFAULT',  'L5',  NULL, NULL, 'DAY',  2600.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L6-DEFAULT',  'L6',  NULL, NULL, 'DAY',  3000.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L7-DEFAULT',  'L7',  NULL, NULL, 'DAY',  3500.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L8-DEFAULT',  'L8',  NULL, NULL, 'DAY',  4000.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L9-DEFAULT',  'L9',  NULL, NULL, 'DAY',  4500.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L10-DEFAULT', 'L10', NULL, NULL, 'DAY',  5200.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L11-DEFAULT', 'L11', NULL, NULL, 'DAY',  6000.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L12-DEFAULT', 'L12', NULL, NULL, 'DAY',  7000.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L13-DEFAULT', 'L13', NULL, NULL, 'DAY',  8200.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L14-DEFAULT', 'L14', NULL, NULL, 'DAY',  9500.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L15-DEFAULT', 'L15', NULL, NULL, 'DAY', 11000.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L16-DEFAULT', 'L16', NULL, NULL, 'DAY', 13000.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L17-DEFAULT', 'L17', NULL, NULL, 'DAY', 15000.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RC-L18-DEFAULT', 'L18', NULL, NULL, 'DAY', 18000.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init');

-- =====================================================
-- 6. 初始化 L1-L18 职级默认对内成本费率（基线参考）
-- =====================================================
INSERT INTO pmis.pmis_rate_internal
    (rate_code, level_code, billing_unit, cost_amount, currency, effective_date, status, tenant_id, provider_trace_id)
VALUES
    ('RI-L1-DEFAULT',  'L1',  'DAY',  800.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L2-DEFAULT',  'L2',  'DAY',  1000.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L3-DEFAULT',  'L3',  'DAY',  1200.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L4-DEFAULT',  'L4',  'DAY',  1500.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L5-DEFAULT',  'L5',  'DAY',  1800.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L6-DEFAULT',  'L6',  'DAY',  2100.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L7-DEFAULT',  'L7',  'DAY',  2400.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L8-DEFAULT',  'L8',  'DAY',  2800.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L9-DEFAULT',  'L9',  'DAY',  3200.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L10-DEFAULT', 'L10', 'DAY',  3700.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L11-DEFAULT', 'L11', 'DAY',  4200.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L12-DEFAULT', 'L12', 'DAY',  4800.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L13-DEFAULT', 'L13', 'DAY',  5500.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L14-DEFAULT', 'L14', 'DAY',  6300.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L15-DEFAULT', 'L15', 'DAY',  7300.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L16-DEFAULT', 'L16', 'DAY',  8500.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L17-DEFAULT', 'L17', 'DAY', 10000.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init'),
    ('RI-L18-DEFAULT', 'L18', 'DAY', 12000.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init');
