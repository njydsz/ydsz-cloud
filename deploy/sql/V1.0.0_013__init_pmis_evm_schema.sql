-- =====================================================
-- PMIS 批次10 DDL：EVM 挣值 / 对外报价费率 / 对内成本费率 / 利润测算
-- 版本: V1.0.0_013
-- 描述: 挣值测量(pmis_evm_measure)、对外报价费率(pmis_rate_card)、
--       对内成本费率(pmis_rate_internal)、利润测算版本(pmis_profit_simulation)
-- =====================================================

-- =====================================================
-- 1. EVM 挣值测量表 pmis_evm_measure
-- =====================================================
DROP TABLE IF EXISTS pmis_evm_measure;
CREATE TABLE pmis_evm_measure (
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
COMMENT ON TABLE  pmis_evm_measure IS 'EVM 挣值测量记录: 周期性（按 period 月度）记录 PV/EV/AC/BAC 等挣值指标,EvmCalculator 用 nz() 防空 NPE,EvmAlertLevel 评估预警';
COMMENT ON COLUMN pmis_evm_measure.initiation_id IS '所属立项 ID';
COMMENT ON COLUMN pmis_evm_measure.wbs_task_id IS '关联 WBS 任务 ID: 可空,NULL 表示项目级度量';
COMMENT ON COLUMN pmis_evm_measure.period IS '测量周期: 格式 YYYY-MM,例如 2026-06';
COMMENT ON COLUMN pmis_evm_measure.pv IS '计划值 PV(元): 截至当前周期计划完成工作的预算成本';
COMMENT ON COLUMN pmis_evm_measure.ev IS '挣值 EV(元): 截至当前周期实际完成工作的预算成本';
COMMENT ON COLUMN pmis_evm_measure.ac IS '实际成本 AC(元): 截至当前周期实际花费';
COMMENT ON COLUMN pmis_evm_measure.bac IS '完工预算 BAC(元): 项目总预算';
COMMENT ON COLUMN pmis_evm_measure.cpi IS '成本绩效指数 CPI = EV/AC: >1 节约,<1 超支';
COMMENT ON COLUMN pmis_evm_measure.spi IS '进度绩效指数 SPI = EV/PV: >1 提前,<1 滞后';
COMMENT ON COLUMN pmis_evm_measure.cv IS '成本偏差 CV = EV-AC(元)';
COMMENT ON COLUMN pmis_evm_measure.sv IS '进度偏差 SV = EV-PV(元)';
COMMENT ON COLUMN pmis_evm_measure.eac IS '完工估算 EAC = BAC/CPI(元)';
COMMENT ON COLUMN pmis_evm_measure.vac IS '完工偏差 VAC = BAC-EAC(元)';
COMMENT ON COLUMN pmis_evm_measure.etc IS '完工尚需 ETC = EAC-AC(元)';
COMMENT ON COLUMN pmis_evm_measure.tcpi IS '完工绩效指数 TCPI = (BAC-EV)/(BAC-AC)';
COMMENT ON COLUMN pmis_evm_measure.alert_level IS '预警等级: NORMAL 正常 / YELLOW 黄色 / RED 红色,基于 CPI/SPI 阈值';
COMMENT ON COLUMN pmis_evm_measure.alert_reason IS '预警原因: 描述触发预警的具体指标';
COMMENT ON COLUMN pmis_evm_measure.measure_date IS '测量日期: 数据采集时点';
COMMENT ON COLUMN pmis_evm_measure.remark IS '备注';
COMMENT ON COLUMN pmis_evm_measure.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_evm_measure.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_evm_measure.deleted IS '逻辑删除: 0=未删除,1=已删除';
CREATE INDEX idx_pem_initiation ON pmis_evm_measure(initiation_id);
CREATE INDEX idx_pem_wbs ON pmis_evm_measure(wbs_task_id);
CREATE INDEX idx_pem_period ON pmis_evm_measure(initiation_id, period);
CREATE INDEX idx_pem_alert ON pmis_evm_measure(alert_level);

-- =====================================================
-- 2. 对外报价费率表 pmis_rate_card
-- =====================================================
DROP TABLE IF EXISTS pmis_rate_card;
CREATE TABLE pmis_rate_card (
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
COMMENT ON TABLE  pmis_rate_card IS '对外报价费率表 Rate Card: 客户报价用,支持 3 级匹配（level+project+customer > level+project > level）,matchEffective 自动选最优';
COMMENT ON COLUMN pmis_rate_card.rate_code IS '费率编码: 业务唯一,如 RC-L5-FIX-A';
COMMENT ON COLUMN pmis_rate_card.level_code IS '职级: L1-L18';
COMMENT ON COLUMN pmis_rate_card.project_type IS '项目类型: FIXED_PRICE/T_M/OUTSOURCING 等,NULL=全类型';
COMMENT ON COLUMN pmis_rate_card.customer_level IS '客户级别: A/B/C/D,NULL=全级别';
COMMENT ON COLUMN pmis_rate_card.billing_unit IS '计费单位: DAY 日 / HOUR 小时';
COMMENT ON COLUMN pmis_rate_card.rate_amount IS '费率(元): 单价';
COMMENT ON COLUMN pmis_rate_card.currency IS '币种: 默认 CNY';
COMMENT ON COLUMN pmis_rate_card.effective_date IS '生效日期';
COMMENT ON COLUMN pmis_rate_card.expiry_date IS '失效日期: NULL=长期有效';
COMMENT ON COLUMN pmis_rate_card.status IS '状态: ACTIVE 启用 / INACTIVE 停用';
COMMENT ON COLUMN pmis_rate_card.remark IS '备注';
COMMENT ON COLUMN pmis_rate_card.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_rate_card.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_rate_card.deleted IS '逻辑删除: 0=未删除,1=已删除';
CREATE INDEX idx_prc_level ON pmis_rate_card(level_code, project_type, customer_level);
CREATE INDEX idx_prc_status ON pmis_rate_card(status, effective_date);

-- =====================================================
-- 3. 对内成本费率表 pmis_rate_internal
-- =====================================================
DROP TABLE IF EXISTS pmis_rate_internal;
CREATE TABLE pmis_rate_internal (
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
COMMENT ON TABLE  pmis_rate_internal IS '对内成本费率表: 内部人力成本计算用,matchEffective 优先 (level+department) 而非 (level only)';
COMMENT ON COLUMN pmis_rate_internal.rate_code IS '费率编码: 业务唯一,如 RI-L5-DEV-001';
COMMENT ON COLUMN pmis_rate_internal.level_code IS '职级: L1-L18';
COMMENT ON COLUMN pmis_rate_internal.department_id IS '部门 ID: 事业部/部门,NULL=全部门';
COMMENT ON COLUMN pmis_rate_internal.department_name IS '部门名称（冗余）';
COMMENT ON COLUMN pmis_rate_internal.billing_unit IS '计费单位: DAY 日 / HOUR 小时';
COMMENT ON COLUMN pmis_rate_internal.cost_amount IS '成本金额(元): 人天/人时成本';
COMMENT ON COLUMN pmis_rate_internal.currency IS '币种: 默认 CNY';
COMMENT ON COLUMN pmis_rate_internal.effective_date IS '生效日期';
COMMENT ON COLUMN pmis_rate_internal.expiry_date IS '失效日期';
COMMENT ON COLUMN pmis_rate_internal.status IS '状态: ACTIVE 启用 / INACTIVE 停用';
COMMENT ON COLUMN pmis_rate_internal.remark IS '备注';
COMMENT ON COLUMN pmis_rate_internal.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_rate_internal.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_rate_internal.deleted IS '逻辑删除: 0=未删除,1=已删除';
CREATE INDEX idx_pri_level_dept ON pmis_rate_internal(level_code, department_id);
CREATE INDEX idx_pri_status ON pmis_rate_internal(status, effective_date);

-- =====================================================
-- 4. 利润测算版本表 pmis_profit_simulation
-- =====================================================
DROP TABLE IF EXISTS pmis_profit_simulation;
CREATE TABLE pmis_profit_simulation (
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
COMMENT ON TABLE  pmis_profit_simulation IS '利润测算版本表 What-if: 同一立项支持多个测算版本,create() 自动 version=max+1,APPROVED/ARCHIVED 状态禁止删除';
COMMENT ON COLUMN pmis_profit_simulation.simulation_code IS '测算单号: 业务唯一,如 SIM-2026-001';
COMMENT ON COLUMN pmis_profit_simulation.simulation_name IS '测算名称';
COMMENT ON COLUMN pmis_profit_simulation.initiation_id IS '所属立项 ID';
COMMENT ON COLUMN pmis_profit_simulation.version IS '版本号: 同立项内递增,create() 时自动 max+1';
COMMENT ON COLUMN pmis_profit_simulation.scenario_type IS '场景类型: BASE 基准 / OPTIMISTIC 乐观 / PESSIMISTIC 悲观 / CUSTOM 自定义';
COMMENT ON COLUMN pmis_profit_simulation.contract_amount IS '合同金额(元)';
COMMENT ON COLUMN pmis_profit_simulation.external_revenue IS '外部收入(元): 对外报价合计';
COMMENT ON COLUMN pmis_profit_simulation.internal_cost IS '内部成本(元): 人力 + 采购 + 费用 + 外包';
COMMENT ON COLUMN pmis_profit_simulation.expected_hours IS '预计工时(小时)';
COMMENT ON COLUMN pmis_profit_simulation.blended_rate IS '综合人天费率(元)';
COMMENT ON COLUMN pmis_profit_simulation.gross_profit IS '毛利润(元) = external_revenue - internal_cost';
COMMENT ON COLUMN pmis_profit_simulation.gross_margin IS '毛利率: 0.25=25%';
COMMENT ON COLUMN pmis_profit_simulation.target_margin IS '目标毛利率: 业务方预设的达标线';
COMMENT ON COLUMN pmis_profit_simulation.labor_cost IS '人工成本(元)';
COMMENT ON COLUMN pmis_profit_simulation.purchase_cost IS '采购成本(元)';
COMMENT ON COLUMN pmis_profit_simulation.expense_cost IS '费用(元)';
COMMENT ON COLUMN pmis_profit_simulation.outsource_cost IS '外包成本(元)';
COMMENT ON COLUMN pmis_profit_simulation.assumptions IS '假设条件 JSON: 输入参数快照';
COMMENT ON COLUMN pmis_profit_simulation.status IS '测算状态: DRAFT 草稿 / SUBMITTED 已提交 / APPROVED 已批准 / REJECTED 已驳回 / ARCHIVED 已归档,REJECTED 可回退到 DRAFT';
COMMENT ON COLUMN pmis_profit_simulation.approver_name IS '审批人姓名（冗余）';
COMMENT ON COLUMN pmis_profit_simulation.approved_at IS '审批时间';
COMMENT ON COLUMN pmis_profit_simulation.remark IS '备注';
COMMENT ON COLUMN pmis_profit_simulation.applicant_id IS '申请人 ID';
COMMENT ON COLUMN pmis_profit_simulation.applicant_name IS '申请人姓名（冗余）';
COMMENT ON COLUMN pmis_profit_simulation.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_profit_simulation.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_profit_simulation.deleted IS '逻辑删除: 0=未删除,1=已删除';
CREATE INDEX idx_psm_initiation ON pmis_profit_simulation(initiation_id);
CREATE INDEX idx_psm_version ON pmis_profit_simulation(initiation_id, version);
CREATE INDEX idx_psm_status ON pmis_profit_simulation(status, scenario_type);

-- =====================================================
-- 5. 初始化 L1-L18 职级默认对外报价费率（基线参考）
-- =====================================================
INSERT INTO pmis_rate_card
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
INSERT INTO pmis_rate_internal
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
