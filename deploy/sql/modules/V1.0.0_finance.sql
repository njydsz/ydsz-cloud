-- ============================================================
-- PMIS finance module SQL
-- 财务会计服务 (ydsz-pmis-finance, port 9011)
-- ============================================================
-- 本脚本 DDL 对应后端 finance 服务的 Mapper / DO,
--   物理 Mapper 实际所在模块即表归属。跨服务引用禁止直连,统一走
--   Feign Client (FinanceDataClient / SalesDataClient)。
--
-- 表归属依据: ydsz-pmis-finance/src/main/java/.../infra/mapper/
-- 表数量: 8 张
-- --------------------------------------------------------------------

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_cost_expense(
    id                  VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    expense_code        VARCHAR(64)    NOT NULL,
    initiation_id       VARCHAR(20),
    employee_id         VARCHAR(20)         NOT NULL,
    employee_name       VARCHAR(64),
    expense_type        VARCHAR(32)    NOT NULL,
    amount              NUMERIC(18,2)  NOT NULL DEFAULT 0,
    expense_date        DATE           NOT NULL,
    description         TEXT,
    receipt_url         VARCHAR(512),
    status              VARCHAR(32)    NOT NULL DEFAULT 'DRAFT',
    approver_id         VARCHAR(20),
    approver_name       VARCHAR(64),
    approved_at         TIMESTAMPTZ,
    provider_trace_id   VARCHAR(64)    NOT NULL DEFAULT '',
    created_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT       NOT NULL DEFAULT 0,
    tenant_id           VARCHAR(20)         NOT NULL DEFAULT '1',
    version             INTEGER        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT uk_pce_code           UNIQUE (expense_code, deleted),
    CONSTRAINT ck_pce_type_enum      CHECK (expense_type IN ('TRAVEL', 'CATERING', 'MEETING', 'SUPPLIES', 'COMMUNICATION', 'OTHER')),
    CONSTRAINT ck_pce_status_enum    CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'PAID')),
    CONSTRAINT ck_pce_amount_nonneg  CHECK (amount >= 0),
    CONSTRAINT ck_pce_version_nonneg CHECK (version >= 0),
    CONSTRAINT ck_pce_deleted_enum   CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_cost_expense IS '费用报销表: 差旅/团建/会议/办公等费用报销,可关联项目(影响项目预算)';

COMMENT ON COLUMN pmis_cost_expense.id IS '主键 ID';

COMMENT ON COLUMN pmis_cost_expense.expense_code IS '报销单编码(全局唯一)';

COMMENT ON COLUMN pmis_cost_expense.initiation_id IS '关联立项 ID(项目级费用必填,公司公共费用可空)';

COMMENT ON COLUMN pmis_cost_expense.employee_id IS '报销人 ID';

COMMENT ON COLUMN pmis_cost_expense.employee_name IS '报销人姓名';

COMMENT ON COLUMN pmis_cost_expense.expense_type IS '费用类型: TRAVEL 差旅 / CATERING 餐饮 / MEETING 会议 / SUPPLIES 办公 / COMMUNICATION 通讯 / OTHER 其他';

COMMENT ON COLUMN pmis_cost_expense.amount IS '报销金额(元)';

COMMENT ON COLUMN pmis_cost_expense.expense_date IS '费用发生日期';

COMMENT ON COLUMN pmis_cost_expense.description IS '费用说明';

COMMENT ON COLUMN pmis_cost_expense.receipt_url IS '发票/凭证 URL';

COMMENT ON COLUMN pmis_cost_expense.status IS '审批状态: DRAFT 草稿 / SUBMITTED 已提交 / APPROVED 已批准 / REJECTED 已驳回 / PAID 已打款';

COMMENT ON COLUMN pmis_cost_expense.approver_id IS '审批人 ID';

COMMENT ON COLUMN pmis_cost_expense.approver_name IS '审批人姓名';

COMMENT ON COLUMN pmis_cost_expense.approved_at IS '审批时间';

COMMENT ON COLUMN pmis_cost_expense.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_cost_expense.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_cost_expense.created_at IS '创建时间';

COMMENT ON COLUMN pmis_cost_expense.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_cost_expense.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_cost_expense.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_cost_expense.tenant_id IS '租户 ID(单租户部署默认 1)';

COMMENT ON COLUMN pmis_cost_expense.version IS '乐观锁版本号';

CREATE INDEX IF NOT EXISTS idx_pce_initiation
    ON pmis_cost_expense (initiation_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pce_employee
    ON pmis_cost_expense (employee_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pce_status
    ON pmis_cost_expense (status) WHERE deleted = 0;

-- [INLINE-OPT] 员工 + 状态(员工报销台账)
CREATE INDEX IF NOT EXISTS idx_pce_employee_status
    ON pmis_cost_expense (employee_id, status) WHERE deleted = 0;

-- [INLINE-OPT] 复合索引:租户 + 费用日期(报销中心时间筛选)
CREATE INDEX IF NOT EXISTS idx_pce_tenant_date
    ON pmis_cost_expense (tenant_id, expense_date DESC) WHERE deleted = 0;

-- [INLINE-OPT] 链路追踪 ID 索引
CREATE INDEX IF NOT EXISTS idx_pce_trace
    ON pmis_cost_expense (provider_trace_id) WHERE provider_trace_id <> '';

-- =====================================================
-- 6. 收入确认表 pmis_profit_revenue

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_profit_revenue(
    id                  VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    contract_id         VARCHAR(20)         NOT NULL,
    initiation_id       VARCHAR(20)         NOT NULL,
    revenue_code        VARCHAR(64)    NOT NULL,
    recognition_method  VARCHAR(32)    NOT NULL,
    period              VARCHAR(7)     NOT NULL,
    amount              NUMERIC(18,2)  NOT NULL DEFAULT 0,
    recognition_date    DATE           NOT NULL,
    milestone           VARCHAR(128),
    percent_complete    NUMERIC(5,2),
    invoice_id          VARCHAR(20),
    status              VARCHAR(32)    NOT NULL DEFAULT 'DRAFT',
    confirmed_by        VARCHAR(20),
    confirmed_at        TIMESTAMPTZ,
    description         TEXT,
    provider_trace_id   VARCHAR(64)    NOT NULL DEFAULT '',
    created_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT       NOT NULL DEFAULT 0,
    tenant_id           VARCHAR(20)         NOT NULL DEFAULT '1',
    version             INTEGER        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT uk_ppr_code              UNIQUE (revenue_code, deleted),
    CONSTRAINT ck_ppr_method_enum       CHECK (recognition_method IN ('MILESTONE', 'PERCENTAGE', 'PERCENT_COMPLETE', 'POINTS', 'MANUAL')),
    CONSTRAINT ck_ppr_status_enum       CHECK (status IN ('DRAFT', 'CONFIRMED', 'REVERSED')),
    CONSTRAINT ck_ppr_amount_nonneg     CHECK (amount >= 0),
    CONSTRAINT ck_ppr_pct_range         CHECK (percent_complete IS NULL OR (percent_complete >= 0 AND percent_complete <= 100)),
    CONSTRAINT ck_ppr_version_nonneg    CHECK (version >= 0),
    CONSTRAINT ck_ppr_deleted_enum      CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_profit_revenue IS '收入确认表: 按里程碑/百分比/完工法/手动法等多维度确认项目收入';

COMMENT ON COLUMN pmis_profit_revenue.id IS '主键 ID';

COMMENT ON COLUMN pmis_profit_revenue.contract_id IS '合同 ID(关联 pmis_project_contract.id)';

COMMENT ON COLUMN pmis_profit_revenue.initiation_id IS '立项 ID(关联 pmis_project_initiation.id)';

COMMENT ON COLUMN pmis_profit_revenue.revenue_code IS '收入确认单编码(全局唯一)';

COMMENT ON COLUMN pmis_profit_revenue.recognition_method IS '确认方法: MILESTONE 里程碑法 / PERCENTAGE 比例法 / PERCENT_COMPLETE 完工法 / POINTS 工分法 / MANUAL 手动';

COMMENT ON COLUMN pmis_profit_revenue.period IS '所属期间(YYYY-MM)';

COMMENT ON COLUMN pmis_profit_revenue.amount IS '确认金额(元)';

COMMENT ON COLUMN pmis_profit_revenue.recognition_date IS '确认日期';

COMMENT ON COLUMN pmis_profit_revenue.milestone IS '里程碑描述';

COMMENT ON COLUMN pmis_profit_revenue.percent_complete IS '完工百分比(0-100,完工法)';

COMMENT ON COLUMN pmis_profit_revenue.invoice_id IS '关联开票申请 ID';

COMMENT ON COLUMN pmis_profit_revenue.status IS '状态: DRAFT 草稿 / CONFIRMED 已确认 / REVERSED 已冲销';

COMMENT ON COLUMN pmis_profit_revenue.confirmed_by IS '确认人 ID';

COMMENT ON COLUMN pmis_profit_revenue.confirmed_at IS '确认时间';

COMMENT ON COLUMN pmis_profit_revenue.description IS '收入确认说明';

COMMENT ON COLUMN pmis_profit_revenue.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_profit_revenue.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_profit_revenue.created_at IS '创建时间';

COMMENT ON COLUMN pmis_profit_revenue.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_profit_revenue.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_profit_revenue.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_profit_revenue.tenant_id IS '租户 ID(单租户部署默认 1)';

COMMENT ON COLUMN pmis_profit_revenue.version IS '乐观锁版本号';

CREATE INDEX IF NOT EXISTS idx_ppr_contract
    ON pmis_profit_revenue (contract_id) WHERE deleted = 0;

-- [INLINE-OPT] 复合索引:立项 + 期间(项目月度收入走势)
CREATE INDEX IF NOT EXISTS idx_ppr_initiation
    ON pmis_profit_revenue (initiation_id, period) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ppr_status
    ON pmis_profit_revenue (status) WHERE deleted = 0;

-- [INLINE-OPT] 复合索引:租户 + 期间(全公司收入月报)
CREATE INDEX IF NOT EXISTS idx_ppr_tenant_period
    ON pmis_profit_revenue (tenant_id, period) WHERE deleted = 0;

-- [INLINE-OPT] 关联开票申请 ID
CREATE INDEX IF NOT EXISTS idx_ppr_invoice
    ON pmis_profit_revenue (invoice_id) WHERE deleted = 0 AND invoice_id IS NOT NULL;

-- [INLINE-OPT] 链路追踪 ID 索引
CREATE INDEX IF NOT EXISTS idx_ppr_trace
    ON pmis_profit_revenue (provider_trace_id) WHERE provider_trace_id <> '';

-- =====================================================
-- 7. 项目利润快照表 pmis_profit_snapshot

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_profit_snapshot(
    id                  VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    initiation_id       VARCHAR(20)         NOT NULL,
    period              VARCHAR(7)     NOT NULL,
    contract_amount     NUMERIC(18,2)  NOT NULL DEFAULT 0,
    recognized_revenue  NUMERIC(18,2)  NOT NULL DEFAULT 0,
    billed_amount       NUMERIC(18,2)  NOT NULL DEFAULT 0,
    received_amount     NUMERIC(18,2)  NOT NULL DEFAULT 0,
    labor_cost          NUMERIC(18,2)  NOT NULL DEFAULT 0,
    purchase_cost       NUMERIC(18,2)  NOT NULL DEFAULT 0,
    expense_cost        NUMERIC(18,2)  NOT NULL DEFAULT 0,
    outsource_cost      NUMERIC(18,2)  NOT NULL DEFAULT 0,
    allocation_cost     NUMERIC(18,2)  NOT NULL DEFAULT 0,
    total_cost          NUMERIC(18,2)  NOT NULL DEFAULT 0,
    gross_profit        NUMERIC(18,2)  NOT NULL DEFAULT 0,
    gross_margin        NUMERIC(5,4)   NOT NULL DEFAULT 0,
    progress_pct        NUMERIC(5,2)   NOT NULL DEFAULT 0,
    billable_hours      NUMERIC(10,2)  NOT NULL DEFAULT 0,
    non_billable_hours  NUMERIC(10,2)  NOT NULL DEFAULT 0,
    snapshot_at         TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    provider_trace_id   VARCHAR(64)    NOT NULL DEFAULT '',
    deleted             SMALLINT       NOT NULL DEFAULT 0,
    tenant_id           VARCHAR(20)         NOT NULL DEFAULT '1',
    version             INTEGER        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT uk_pps_init_period     UNIQUE (initiation_id, period, deleted),
    CONSTRAINT ck_pps_amount_nonneg   CHECK (contract_amount >= 0 AND recognized_revenue >= 0 AND billed_amount >= 0
                                              AND received_amount >= 0 AND labor_cost >= 0 AND purchase_cost >= 0
                                              AND expense_cost >= 0 AND outsource_cost >= 0 AND allocation_cost >= 0
                                              AND total_cost >= 0 AND gross_profit >= 0),
    CONSTRAINT ck_pps_margin_range    CHECK (gross_margin >= 0 AND gross_margin <= 1),
    CONSTRAINT ck_pps_progress_range  CHECK (progress_pct >= 0 AND progress_pct <= 100),
    CONSTRAINT ck_pps_hours_nonneg    CHECK (billable_hours >= 0 AND non_billable_hours >= 0),
    CONSTRAINT ck_pps_version_nonneg  CHECK (version >= 0),
    CONSTRAINT ck_pps_deleted_enum    CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_profit_snapshot IS '项目利润快照(按月): 立项 × 期间 唯一约束,周期性滚动生成';

COMMENT ON COLUMN pmis_profit_snapshot.id IS '主键 ID';

COMMENT ON COLUMN pmis_profit_snapshot.initiation_id IS '立项 ID(关联 pmis_project_initiation.id)';

COMMENT ON COLUMN pmis_profit_snapshot.period IS '快照周期(YYYY-MM)';

COMMENT ON COLUMN pmis_profit_snapshot.contract_amount IS '合同总额(元)';

COMMENT ON COLUMN pmis_profit_snapshot.recognized_revenue IS '已确认收入(元)';

COMMENT ON COLUMN pmis_profit_snapshot.billed_amount IS '已开票金额(元)';

COMMENT ON COLUMN pmis_profit_snapshot.received_amount IS '已回款金额(元)';

COMMENT ON COLUMN pmis_profit_snapshot.labor_cost IS '人力成本(元)';

COMMENT ON COLUMN pmis_profit_snapshot.purchase_cost IS '采购成本(元)';

COMMENT ON COLUMN pmis_profit_snapshot.expense_cost IS '费用(元)';

COMMENT ON COLUMN pmis_profit_snapshot.outsource_cost IS '外包(元)';

COMMENT ON COLUMN pmis_profit_snapshot.allocation_cost IS '分摊费用(元)';

COMMENT ON COLUMN pmis_profit_snapshot.total_cost IS '总成本(元)';

COMMENT ON COLUMN pmis_profit_snapshot.gross_profit IS '毛利(元)';

COMMENT ON COLUMN pmis_profit_snapshot.gross_margin IS '毛利率 0.0000-1.0000';

COMMENT ON COLUMN pmis_profit_snapshot.progress_pct IS '完工进度(0-100)';

COMMENT ON COLUMN pmis_profit_snapshot.billable_hours IS '可计费工时(小时)';

COMMENT ON COLUMN pmis_profit_snapshot.non_billable_hours IS '不可计费工时(小时)';

COMMENT ON COLUMN pmis_profit_snapshot.snapshot_at IS '快照生成时间';

COMMENT ON COLUMN pmis_profit_snapshot.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_profit_snapshot.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_profit_snapshot.tenant_id IS '租户 ID(单租户部署默认 1)';

COMMENT ON COLUMN pmis_profit_snapshot.version IS '乐观锁版本号';

-- [INLINE-OPT] 复合索引:立项 + 期间(项目利润走势)
CREATE INDEX IF NOT EXISTS idx_pps_initiation
    ON pmis_profit_snapshot (initiation_id, period) WHERE deleted = 0;

-- [INLINE-OPT] 复合索引:租户 + 期间(全公司月度利润驾驶舱)
CREATE INDEX IF NOT EXISTS idx_pps_tenant_period
    ON pmis_profit_snapshot (tenant_id, period) WHERE deleted = 0;

-- [INLINE-OPT] 链路追踪 ID 索引
CREATE INDEX IF NOT EXISTS idx_pps_trace
    ON pmis_profit_snapshot (provider_trace_id) WHERE provider_trace_id <> '';

-- =====================================================
-- 8. 项目风险登记表 pmis_execution_risk

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_finance_invoice(
    id                  VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    invoice_no          VARCHAR(64),                              -- 财务发票号
    invoice_code        VARCHAR(64)  NOT NULL,                    -- 业务编号（系统生成）
    invoice_type        VARCHAR(32)  NOT NULL DEFAULT 'NORMAL',   -- NORMAL/RED_REVERSE
    contract_id         VARCHAR(20)       NOT NULL,
    initiation_id       VARCHAR(20)       NOT NULL,
    customer_id         VARCHAR(20)       NOT NULL,
    customer_name       VARCHAR(256),
    invoice_basis       VARCHAR(32)  NOT NULL,                    -- MILESTONE/OUTSOURCING/MONTHLY/FINAL/OTHER
    amount              NUMERIC(15,2) NOT NULL DEFAULT 0,         -- 含税金额
    tax_amount          NUMERIC(15,2) NOT NULL DEFAULT 0,
    net_amount          NUMERIC(15,2) NOT NULL DEFAULT 0,         -- 不含税金额
    tax_rate            NUMERIC(5,4) NOT NULL DEFAULT 0.06,
    currency            VARCHAR(16)  NOT NULL DEFAULT 'CNY',
    invoice_date        DATE,
    tax_period          VARCHAR(16),                              -- YYYY-MM
    title               VARCHAR(256),                             -- 发票抬头
    tax_no              VARCHAR(64),                              -- 纳税人识别号
    bank_info           VARCHAR(256),                             -- 开户行+账号
    address             VARCHAR(256),
    phone               VARCHAR(64),
    remark              TEXT,
    status              VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',     -- InvoiceStatus
    reversed_by_id      VARCHAR(20),                                   -- 被红冲的发票ID
    attachment_id       VARCHAR(64),                              -- 发票扫描件
    approval_comment    TEXT,
    applied_by          VARCHAR(20),
    approved_at         TIMESTAMPTZ,
    approved_by         VARCHAR(20),
    issued_at           TIMESTAMPTZ,
    issued_by           VARCHAR(20),
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pfi_code              UNIQUE (invoice_code, deleted),
    CONSTRAINT ck_pfi_invoice_type      CHECK (invoice_type IN ('NORMAL','RED_REVERSE')),
    CONSTRAINT ck_pfi_invoice_basis     CHECK (invoice_basis IN ('MILESTONE','OUTSOURCING','MONTHLY','FINAL','OTHER')),
    CONSTRAINT ck_pfi_status_enum       CHECK (status IN ('DRAFT','SUBMITTED','ISSUED','RED_REVERSED','CANCELLED')),
    CONSTRAINT ck_pfi_tax_period_fmt    CHECK (tax_period IS NULL OR tax_period ~ '^\d{4}-(0[1-9]|1[0-2])$'),
    CONSTRAINT ck_pfi_tax_rate_range    CHECK (tax_rate >= 0 AND tax_rate <= 1),
    CONSTRAINT ck_pfi_amount_nonneg     CHECK (amount >= 0 AND tax_amount >= 0 AND net_amount >= 0),
    CONSTRAINT ck_pfi_deleted_enum      CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_finance_invoice IS '发票主表: 支持正常开票与红冲（RED_REVERSED）,执行 InvoiceStatus 状态机校验,invoice_code 唯一,invoice_no 在 ISSUED 时分配';

COMMENT ON COLUMN pmis_finance_invoice.invoice_no IS '财务发票号: 税务局分配的纸质/电子发票号,ISSUED 状态时分配';

COMMENT ON COLUMN pmis_finance_invoice.invoice_code IS '业务编号: 系统生成的唯一编码,如 INV-2026-001';

COMMENT ON COLUMN pmis_finance_invoice.invoice_type IS '发票类型: NORMAL 正常开票 / RED_REVERSE 红冲发票';

COMMENT ON COLUMN pmis_finance_invoice.contract_id IS '所属合同 ID';

COMMENT ON COLUMN pmis_finance_invoice.initiation_id IS '所属立项 ID';

COMMENT ON COLUMN pmis_finance_invoice.customer_id IS '客户 ID';

COMMENT ON COLUMN pmis_finance_invoice.customer_name IS '客户名称（冗余）';

COMMENT ON COLUMN pmis_finance_invoice.invoice_basis IS '开票依据: MILESTONE 里程碑 / OUTSOURCING 外包人天 / MONTHLY 月度结算 / FINAL 终验 / OTHER 其他';

COMMENT ON COLUMN pmis_finance_invoice.amount IS '含税金额(元)';

COMMENT ON COLUMN pmis_finance_invoice.tax_amount IS '税额(元)';

COMMENT ON COLUMN pmis_finance_invoice.net_amount IS '不含税金额(元)';

COMMENT ON COLUMN pmis_finance_invoice.tax_rate IS '税率: 0.06=6%,0.13=13%';

COMMENT ON COLUMN pmis_finance_invoice.currency IS '币种: CNY/USD/EUR,默认 CNY';

COMMENT ON COLUMN pmis_finance_invoice.invoice_date IS '开票日期';

COMMENT ON COLUMN pmis_finance_invoice.tax_period IS '税务所属期: 格式 YYYY-MM,用于税务申报';

COMMENT ON COLUMN pmis_finance_invoice.title IS '发票抬头';

COMMENT ON COLUMN pmis_finance_invoice.tax_no IS '纳税人识别号: 客户税号';

COMMENT ON COLUMN pmis_finance_invoice.bank_info IS '开户行+账号: 客户收票方银行信息';

COMMENT ON COLUMN pmis_finance_invoice.address IS '客户地址';

COMMENT ON COLUMN pmis_finance_invoice.phone IS '客户电话';

COMMENT ON COLUMN pmis_finance_invoice.remark IS '备注';

COMMENT ON COLUMN pmis_finance_invoice.status IS '发票状态: DRAFT 草稿 / SUBMITTED 已提交 / ISSUED 已开票 / RED_REVERSED 已红冲 / CANCELLED 已取消,严格状态机';

COMMENT ON COLUMN pmis_finance_invoice.reversed_by_id IS '红冲来源发票 ID: 红冲发票指向被红冲的原始发票';

COMMENT ON COLUMN pmis_finance_invoice.attachment_id IS '发票扫描件: 引用 pmis_file_metadata.id';

COMMENT ON COLUMN pmis_finance_invoice.approval_comment IS '审批意见';

COMMENT ON COLUMN pmis_finance_invoice.applied_by IS '申请人 ID';

COMMENT ON COLUMN pmis_finance_invoice.approved_by IS '审批人 ID';

COMMENT ON COLUMN pmis_finance_invoice.approved_at IS '审批时间';

COMMENT ON COLUMN pmis_finance_invoice.issued_by IS '开票人 ID';

COMMENT ON COLUMN pmis_finance_invoice.issued_at IS '开票时间';

COMMENT ON COLUMN pmis_finance_invoice.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_finance_invoice.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_finance_invoice.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_pfi_*)
CREATE INDEX IF NOT EXISTS idx_pfi_tenant_contract
    ON pmis_finance_invoice(tenant_id, contract_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfi_tenant_initiation
    ON pmis_finance_invoice(tenant_id, initiation_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfi_tenant_customer
    ON pmis_finance_invoice(tenant_id, customer_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfi_tenant_status_type
    ON pmis_finance_invoice(tenant_id, status, invoice_type)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfi_tenant_invoice_date
    ON pmis_finance_invoice(tenant_id, invoice_date)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfi_tenant_tax_period
    ON pmis_finance_invoice(tenant_id, tax_period)
    WHERE deleted = 0;

-- =====================================================
-- 2. 回款主表 pmis_finance_payment

-- =====================================================
-- P1-6: 已废弃,无需 DROP
CREATE TABLE IF NOT EXISTS pmis_finance_payment(
    id                  VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    payment_no          VARCHAR(64),                              -- 银行流水号/系统流水
    payment_code        VARCHAR(64)  NOT NULL,                    -- 业务编号
    contract_id         VARCHAR(20)       NOT NULL,
    initiation_id       VARCHAR(20)       NOT NULL,
    customer_id         VARCHAR(20)       NOT NULL,
    customer_name       VARCHAR(256),
    amount              NUMERIC(15,2) NOT NULL DEFAULT 0,         -- 回款总金额
    currency            VARCHAR(16)  NOT NULL DEFAULT 'CNY',
    payment_method      VARCHAR(32)  NOT NULL DEFAULT 'BANK_TRANSFER', -- BANK_TRANSFER/CHECK/CASH/OTHER
    payment_date        DATE         NOT NULL,                    -- 到账日期
    bank_account        VARCHAR(64),                              -- 客户付款账号
    our_bank_account    VARCHAR(64),                              -- 我方收款账号
    bank_reference      VARCHAR(128),                             -- 银行流水号
    invoice_allocation  TEXT,                                     -- 已分配发票ID（逗号分隔）
    allocated_amount    NUMERIC(15,2) NOT NULL DEFAULT 0,         -- 已核销金额
    unallocated_amount  NUMERIC(15,2) NOT NULL DEFAULT 0,         -- 未核销金额
    status              VARCHAR(32)  NOT NULL DEFAULT 'PENDING',  -- PaymentStatus
    remark              TEXT,
    confirmed_by        VARCHAR(20),
    confirmed_at        TIMESTAMPTZ,
    recorded_by         VARCHAR(20),
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pfp_code              UNIQUE (payment_code, deleted),
    CONSTRAINT ck_pfp_payment_method    CHECK (payment_method IN ('BANK_TRANSFER','CHECK','CASH','OTHER')),
    CONSTRAINT ck_pfp_status_enum       CHECK (status IN ('PENDING','RECEIVED','PARTIAL','ALLOCATED','CANCELLED')),
    CONSTRAINT ck_pfp_amount_nonneg     CHECK (amount >= 0 AND allocated_amount >= 0 AND unallocated_amount >= 0),
    CONSTRAINT ck_pfp_alloc_le_amount   CHECK (allocated_amount <= amount),
    CONSTRAINT ck_pfp_deleted_enum      CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_finance_payment IS '回款主表: 客户回款记录,支持核销发票（allocated_amount/unallocated_amount）,unallocatedAmount=0 时自动转 ALLOCATED';

COMMENT ON COLUMN pmis_finance_payment.payment_no IS '回款流水号: 银行流水号或系统生成';

COMMENT ON COLUMN pmis_finance_payment.payment_code IS '业务编号: 系统生成的唯一编码,如 PAY-2026-001';

COMMENT ON COLUMN pmis_finance_payment.contract_id IS '所属合同 ID';

COMMENT ON COLUMN pmis_finance_payment.initiation_id IS '所属立项 ID';

COMMENT ON COLUMN pmis_finance_payment.customer_id IS '客户 ID';

COMMENT ON COLUMN pmis_finance_payment.customer_name IS '客户名称（冗余）';

COMMENT ON COLUMN pmis_finance_payment.amount IS '回款总金额(元)';

COMMENT ON COLUMN pmis_finance_payment.currency IS '币种: 默认 CNY';

COMMENT ON COLUMN pmis_finance_payment.payment_method IS '支付方式: BANK_TRANSFER 银行转账 / CHECK 支票 / CASH 现金 / OTHER 其他';

COMMENT ON COLUMN pmis_finance_payment.payment_date IS '到账日期';

COMMENT ON COLUMN pmis_finance_payment.bank_account IS '客户付款账号';

COMMENT ON COLUMN pmis_finance_payment.our_bank_account IS '我方收款账号';

COMMENT ON COLUMN pmis_finance_payment.bank_reference IS '银行流水号: 银行端的流水标识';

COMMENT ON COLUMN pmis_finance_payment.invoice_allocation IS '已分配发票 ID 列表: 逗号分隔';

COMMENT ON COLUMN pmis_finance_payment.allocated_amount IS '已核销金额(元): 关联到发票';

COMMENT ON COLUMN pmis_finance_payment.unallocated_amount IS '未核销金额(元): amount - allocatedAmount';

COMMENT ON COLUMN pmis_finance_payment.status IS '回款状态: PENDING 待确认 / RECEIVED 已到账 / PARTIAL 部分核销 / ALLOCATED 已核销完 / CANCELLED 已取消';

COMMENT ON COLUMN pmis_finance_payment.remark IS '备注';

COMMENT ON COLUMN pmis_finance_payment.confirmed_by IS '确认人 ID: 财务确认到账';

COMMENT ON COLUMN pmis_finance_payment.confirmed_at IS '确认时间';

COMMENT ON COLUMN pmis_finance_payment.recorded_by IS '录入人 ID';

COMMENT ON COLUMN pmis_finance_payment.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_finance_payment.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_finance_payment.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_pfp_*)
CREATE INDEX IF NOT EXISTS idx_pfp_tenant_contract
    ON pmis_finance_payment(tenant_id, contract_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfp_tenant_initiation
    ON pmis_finance_payment(tenant_id, initiation_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfp_tenant_customer_status
    ON pmis_finance_payment(tenant_id, customer_id, status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfp_tenant_payment_date
    ON pmis_finance_payment(tenant_id, payment_date)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfp_tenant_customer_unalloc
    ON pmis_finance_payment(tenant_id, customer_id, unallocated_amount)
    WHERE deleted = 0 AND status IN ('PENDING','RECEIVED','PARTIAL');

-- =====================================================
-- 3. 客户信用表 pmis_finance_customer_credit

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_finance_customer_credit(
    id                    VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    customer_id           VARCHAR(20)       NOT NULL,
    customer_name         VARCHAR(256),
    credit_level          VARCHAR(8)   NOT NULL DEFAULT 'D',       -- A/B/C/D
    credit_score          INTEGER      NOT NULL DEFAULT 0,        -- 0-100
    total_contract_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_invoiced_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_received_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    on_time_rate          NUMERIC(5,4) NOT NULL DEFAULT 0,        -- 及时回款率
    contract_count        INTEGER      NOT NULL DEFAULT 0,
    overdue_count         INTEGER      NOT NULL DEFAULT 0,
    last_evaluation_at    TIMESTAMPTZ,
    evaluator             VARCHAR(64),
    remark                TEXT,
    tenant_id             VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id     VARCHAR(64)  NOT NULL DEFAULT '',
    created_by            VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pfcc_customer         UNIQUE (customer_id, deleted),
    CONSTRAINT ck_pfcc_credit_level     CHECK (credit_level IN ('A','B','C','D')),
    CONSTRAINT ck_pfcc_credit_score      CHECK (credit_score >= 0 AND credit_score <= 100),
    CONSTRAINT ck_pfcc_on_time_rate      CHECK (on_time_rate >= 0 AND on_time_rate <= 1),
    CONSTRAINT ck_pfcc_amount_nonneg     CHECK (total_contract_amount >= 0 AND total_invoiced_amount >= 0 AND total_received_amount >= 0),
    CONSTRAINT ck_pfcc_count_nonneg      CHECK (contract_count >= 0 AND overdue_count >= 0),
    CONSTRAINT ck_pfcc_deleted_enum      CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_finance_customer_credit IS '客户信用表: 客户信用评分与等级（A/B/C/D）,CustomerCreditScoreEvaluator 评分（0-100）';

COMMENT ON COLUMN pmis_finance_customer_credit.customer_id IS '客户 ID: 全局唯一';

COMMENT ON COLUMN pmis_finance_customer_credit.customer_name IS '客户名称（冗余）';

COMMENT ON COLUMN pmis_finance_customer_credit.credit_level IS '信用等级: A=优质(90-100) B=良好(75-89) C=一般(60-74) D=风险(0-59),fromScore() 使用 >= 比较';

COMMENT ON COLUMN pmis_finance_customer_credit.credit_score IS '信用分: 0-100,新客户默认 30 分（A 级基线）';

COMMENT ON COLUMN pmis_finance_customer_credit.total_contract_amount IS '累计合同金额(元)';

COMMENT ON COLUMN pmis_finance_customer_credit.total_invoiced_amount IS '累计开票金额(元)';

COMMENT ON COLUMN pmis_finance_customer_credit.total_received_amount IS '累计回款金额(元)';

COMMENT ON COLUMN pmis_finance_customer_credit.on_time_rate IS '及时回款率: 0.85=85%';

COMMENT ON COLUMN pmis_finance_customer_credit.contract_count IS '合同总数';

COMMENT ON COLUMN pmis_finance_customer_credit.overdue_count IS '逾期次数';

COMMENT ON COLUMN pmis_finance_customer_credit.last_evaluation_at IS '最近一次评估时间';

COMMENT ON COLUMN pmis_finance_customer_credit.evaluator IS '评估人/评估器名称';

COMMENT ON COLUMN pmis_finance_customer_credit.remark IS '备注';

COMMENT ON COLUMN pmis_finance_customer_credit.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_finance_customer_credit.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_finance_customer_credit.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_pfcc_level / idx_pfcc_tenant)
CREATE INDEX IF NOT EXISTS idx_pfcc_tenant_level_score
    ON pmis_finance_customer_credit(tenant_id, credit_level, credit_score DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfcc_tenant_updated
    ON pmis_finance_customer_credit(tenant_id, updated_at DESC)
    WHERE deleted = 0;

-- =====================================================
-- 4. 初始数据：信用等级字典（用于前端展示）
-- =====================================================
-- credit_level 字段含义（见上方 COLUMN COMMENT）: A=优质(90-100) B=良好(75-89) C=一般(60-74) D=风险(0-59)

-- --------------------------------------------------------------------

-- ============================ [013] init pmis evm schema ============================

-- =====================================================
-- PMIS 批次10 DDL：EVM 挣值 / 对外报价费率 / 对内成本费率 / 利润测算
-- 版本: V1.0.0_013
-- 描述: 挣值测量(pmis_evm_measure)、对外报价费率(pmis_rate_card)、
--       对内成本费率(pmis_rate_internal)、利润测算版本(pmis_profit_simulation)
-- =====================================================

-- =====================================================
-- 1. EVM 挣值测量表 pmis_evm_measure

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_profit_simulation(
    id                  VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    simulation_code     VARCHAR(64)  NOT NULL,
    simulation_name     VARCHAR(256) NOT NULL,
    initiation_id       VARCHAR(20)       NOT NULL,
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
    approved_at         TIMESTAMPTZ,
    remark              TEXT,
    applicant_id        VARCHAR(20),
    applicant_name      VARCHAR(64),
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pps_code              UNIQUE (simulation_code, deleted),
    CONSTRAINT ck_pps_scenario_type     CHECK (scenario_type IN ('BASE','OPTIMISTIC','PESSIMISTIC','CUSTOM')),
    CONSTRAINT ck_pps_status_enum       CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','ARCHIVED')),
    CONSTRAINT ck_pps_version_pos       CHECK (version > 0),
    CONSTRAINT ck_pps_amounts_nonneg    CHECK (contract_amount >= 0 AND external_revenue >= 0 AND internal_cost >= 0
                                              AND expected_hours >= 0 AND blended_rate >= 0
                                              AND labor_cost >= 0 AND purchase_cost >= 0
                                              AND expense_cost >= 0 AND outsource_cost >= 0),
    CONSTRAINT ck_pps_margin_range      CHECK (gross_margin >= -1 AND gross_margin <= 1 AND target_margin >= -1 AND target_margin <= 1),
    CONSTRAINT ck_pps_deleted_enum      CHECK (deleted IN (0, 1))
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

-- 复合/部分索引(替代零散的 idx_psm_initiation / idx_psm_version / idx_psm_status)
CREATE INDEX IF NOT EXISTS idx_psm_tenant_initiation_version
    ON pmis_profit_simulation(tenant_id, initiation_id, version DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_psm_tenant_status_scenario
    ON pmis_profit_simulation(tenant_id, status, scenario_type)
    WHERE deleted = 0;

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
        ('RC-L18-DEFAULT', 'L18', NULL, NULL, 'DAY', 18000.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init') ON CONFLICT DO NOTHING;

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
        ('RI-L18-DEFAULT', 'L18', 'DAY', 12000.00, 'CNY', CURRENT_DATE, 'ACTIVE', 1, 'init') ON CONFLICT DO NOTHING;

-- --------------------------------------------------------------------

-- ============================ [015] init pmis cockpit views ============================

-- ============================================================
-- V1.0.0_015  经营驾驶舱 + 高级报表  视图脚本
-- ============================================================
-- 说明：为驾驶舱与高级报表提供跨模块聚合视图，避免在 Java 层做
--      多次单表查询。所有视图 LEFT JOIN + COALESCE 确保 0 收入/0 成本
--      的项目也能出现在下钻结果中。
-- ============================================================

-- ----------------------------
-- 1. 项目收入 + 成本视图（按 initiation × period）
-- ----------------------------
-- 优化: 显式带 tenant_id,避免 JOIN 放大导致跨租户数据泄露
CREATE OR REPLACE VIEW pmis_view_initiation_revenue_cost
    WITH (security_invoker = true) AS
SELECT i.tenant_id,
       i.id              AS initiation_id,
       COALESCE((SELECT SUM(amount) FROM pmis_profit_revenue r
                  WHERE r.initiation_id = i.id AND r.deleted = 0
                    AND r.tenant_id = i.tenant_id), 0)         AS total_revenue,
       COALESCE((SELECT SUM(amount) FROM pmis_finance_invoice p
                  WHERE p.initiation_id = i.id AND p.deleted = 0
                    AND p.tenant_id = i.tenant_id), 0)         AS invoiced_amount,
       COALESCE((SELECT SUM(amount) FROM pmis_profit_revenue r2
                  WHERE r2.initiation_id = i.id AND r2.deleted = 0
                    AND r2.status = 'CONFIRMED'
                    AND r2.tenant_id = i.tenant_id), 0)        AS confirmed_revenue,
       COALESCE((SELECT SUM(amount) FROM pmis_cost_allocation
                  WHERE initiation_id = i.id AND deleted = 0 AND cost_type = 'LABOR'
                    AND tenant_id = i.tenant_id), 0)           AS labor_cost,
       COALESCE((SELECT SUM(amount) FROM pmis_cost_purchase
                  WHERE initiation_id = i.id AND deleted = 0
                    AND tenant_id = i.tenant_id), 0)          AS purchase_cost,
       COALESCE((SELECT SUM(amount) FROM pmis_cost_expense
                  WHERE initiation_id = i.id AND deleted = 0
                    AND tenant_id = i.tenant_id), 0)          AS expense_cost
FROM pmis_project_initiation i
WHERE i.deleted = 0;

COMMENT ON VIEW pmis_view_initiation_revenue_cost IS '项目收入 + 成本聚合视图: CockpitReportServiceImpl 读取,total_revenue 包含所有收入记录,confirmed_revenue 仅 CONFIRMED 状态;labor/purchase/expense 三类成本分别聚合;LEFT JOIN + COALESCE 保证 0 收入/0 成本项目也出现;每条子查询强制带 tenant_id = i.tenant_id 防止跨租户数据污染';

-- ----------------------------
-- 2. 项目 EVM 预警分布
-- ----------------------------
CREATE OR REPLACE VIEW pmis_view_initiation_evm
    WITH (security_invoker = true) AS
SELECT tenant_id,
       initiation_id,
       CASE
           WHEN COUNT(*) FILTER (WHERE alert_level = 'RED') > 0 THEN 'RED'::VARCHAR
           WHEN COUNT(*) FILTER (WHERE alert_level = 'YELLOW') > 0 THEN 'YELLOW'::VARCHAR
           ELSE 'NORMAL'::VARCHAR
       END                                           AS top_alert,
       COUNT(*) FILTER (WHERE alert_level = 'RED')    AS red_count,
       COUNT(*) FILTER (WHERE alert_level = 'YELLOW') AS yellow_count,
       COUNT(*) FILTER (WHERE alert_level = 'NORMAL') AS green_count
FROM pmis_evm_measure
WHERE deleted = 0
GROUP BY tenant_id, initiation_id;

COMMENT ON VIEW pmis_view_initiation_evm IS '项目 EVM 预警分布视图: 按 tenant_id + 立项聚合 RED/YELLOW/NORMAL 计数,AdvancedReportService#evmReport 读取,top_alert 取最高等级';

-- ----------------------------
-- 3. 经营驾驶舱 KPI 总览视图
-- ----------------------------
-- 注意: 多租户场景下,此视图按 tenant_id 分组聚合,确保租户间数据隔离
CREATE OR REPLACE VIEW pmis_view_cockpit_overview
    WITH (security_invoker = true) AS
SELECT
    tenant_id,
    (SELECT COUNT(*) FROM pmis_project_initiation
        WHERE deleted = 0 AND stage IN ('APPROVED','IN_PROGRESS')
          AND tenant_id = t.tenant_id)                                            AS active_projects,
    (SELECT COALESCE(SUM(amount), 0) FROM pmis_finance_invoice
        WHERE deleted = 0 AND status IN ('ISSUED','RED_REVERSED')
          AND tenant_id = t.tenant_id)                                            AS total_invoiced,
    (SELECT COALESCE(SUM(allocated_amount), 0) FROM pmis_finance_payment
        WHERE deleted = 0 AND status = 'ALLOCATED'
          AND tenant_id = t.tenant_id)                                           AS confirmed_revenue
FROM (SELECT DISTINCT tenant_id FROM pmis_project_initiation WHERE deleted = 0) t;

COMMENT ON VIEW pmis_view_cockpit_overview IS '经营驾驶舱 KPI 总览视图: 按 tenant_id 分组汇总 active_projects/total_invoiced/confirmed_revenue,单租户场景返回单行;多租户需前端按租户过滤;底层子查询都强制带 tenant_id 关联,杜绝跨租户数据污染;CockpitReportController#overview 直接读取';

-- --------------------------------------------------------------------

-- ============================ [017] init pmis after sales schema ============================

-- ============================================================
-- V1.0.0_017  项目售后管理  脚本
-- ============================================================
-- 说明：批次 14 项目售后管理（PRD 3.8）
-- 1) 质保期：pmis_warranty
-- 2) 运维工单：pmis_ops_ticket
-- 3) 满意度评价：pmis_satisfaction

-- ============================================================

-- ----------------------------
-- 1) 工时表新增 billable 字段
-- ----------------------------
ALTER TABLE pmis_execution_time_entry
    ADD COLUMN IF NOT EXISTS billable SMALLINT NOT NULL DEFAULT 1;

COMMENT ON COLUMN pmis_execution_time_entry.billable IS '可计费标识: 1=可计费（计入 BillableUtilization）,0=非计费';

-- ----------------------------
-- 3) 每日对账表
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_reconcile_daily (
    id                  VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    reconcile_date      DATE         NOT NULL,
    reconcile_type      VARCHAR(32)  NOT NULL,
    initiation_id       VARCHAR(20),
    expected_amount     NUMERIC(18,2) NOT NULL DEFAULT 0,
    actual_amount       NUMERIC(18,2) NOT NULL DEFAULT 0,
    diff_amount         NUMERIC(18,2) NOT NULL DEFAULT 0,
    diff_pct            NUMERIC(8,4) NOT NULL DEFAULT 0,
    status              VARCHAR(16)  NOT NULL DEFAULT 'OK',
    detail              TEXT,
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id   VARCHAR(64),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    -- 枚举约束
    CONSTRAINT ck_prd_reconcile_type    CHECK (reconcile_type IN ('COST','REVENUE','PAYMENT','INVOICE','TIMESHEET','PROFIT','BENCH','BUDGET')),
    CONSTRAINT ck_prd_status_enum       CHECK (status IN ('OK','WARN','FAIL')),
    -- 数值与比例范围
    CONSTRAINT ck_prd_diff_amount_eq    CHECK (diff_amount = actual_amount - expected_amount),
    CONSTRAINT ck_prd_diff_pct_range    CHECK (diff_pct >= -1 AND diff_pct <= 1),
    CONSTRAINT ck_prd_deleted           CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_reconcile_daily IS '每日自动对账表: 成本/收入/回款/开票 跨模块校验,ReconcileServiceImpl 执行';

COMMENT ON COLUMN pmis_reconcile_daily.reconcile_date IS '对账日期: 每日 02:00 触发';

COMMENT ON COLUMN pmis_reconcile_daily.reconcile_type IS '对账类型: COST 成本 / REVENUE 收入 / PAYMENT 回款 / INVOICE 开票 / TIMESHEET 工时 / PROFIT 利润 / BENCH 闲置 / BUDGET 预算';

COMMENT ON COLUMN pmis_reconcile_daily.initiation_id IS '所属立项 ID: 可空,NULL 表示全局维度';

COMMENT ON COLUMN pmis_reconcile_daily.expected_amount IS '应计金额(元)';

COMMENT ON COLUMN pmis_reconcile_daily.actual_amount IS '实计金额(元)';

COMMENT ON COLUMN pmis_reconcile_daily.diff_amount IS '差异金额(元) = actual - expected';

COMMENT ON COLUMN pmis_reconcile_daily.diff_pct IS '差异比例: -1 ~ 1,例如 0.05=5%';

COMMENT ON COLUMN pmis_reconcile_daily.status IS '对账状态: OK 一致 / WARN 警告（|diff_pct| < 5%）/ FAIL 失败（|diff_pct| >= 5%）';

COMMENT ON COLUMN pmis_reconcile_daily.detail IS '对账明细 JSON: 列出差异项';

COMMENT ON COLUMN pmis_reconcile_daily.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_reconcile_daily.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_reconcile_daily.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的单列索引)
CREATE INDEX IF NOT EXISTS idx_prd_tenant_date_type
    ON pmis_reconcile_daily(tenant_id, reconcile_date DESC, reconcile_type)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_prd_tenant_init_date
    ON pmis_reconcile_daily(tenant_id, initiation_id, reconcile_date DESC)
    WHERE deleted = 0 AND initiation_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_prd_tenant_status_date
    ON pmis_reconcile_daily(tenant_id, status, reconcile_date DESC)
    WHERE deleted = 0 AND status IN ('WARN','FAIL');

-- 唯一约束：每天每个维度只能有一条
CREATE UNIQUE INDEX IF NOT EXISTS uk_prd_tenant_date_type_init
    ON pmis_reconcile_daily(tenant_id, reconcile_date, reconcile_type, COALESCE(initiation_id, '0'), deleted);

-- --------------------------------------------------------------------

-- ============================ [020] init pmis billable utilization snapshot ============================

-- ====================================================================
-- V1.0.0_020  可计费利用率快照表
--
--  说明：可计费利用率（BillableUtilization）由 cronjob 每日计算后
--        持久化到本表，驾驶舱 / 排行榜 / 趋势分析均直接读快照，
--        避免每次实时聚合 pmis_execution_time_entry 大表。
--
--  写入路径：ydsz-pmis-cronjob 模块的
--           BillableUtilizationJobHandler#execute
--  读取路径：CockpitReportService / AdvancedReportService /
--           BillableUtilizationController
--
--  键设计：(period, employee_id) 唯一，
--         PostgreSQL UPSERT ON CONFLICT 保证幂等。

