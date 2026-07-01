-- =====================================================
-- PMIS 批次9 DDL：开票/回款/客户信用
-- 版本: V1.0.0_012
-- 描述: 发票主表(pmis_finance_invoice)、回款主表(pmis_finance_payment)、
--       客户信用表(pmis_finance_customer_credit)
-- =====================================================

-- =====================================================
-- 1. 发票主表 pmis_finance_invoice
-- =====================================================
DROP TABLE IF EXISTS pmis_finance_invoice;
CREATE TABLE pmis_finance_invoice (
    id                  BIGSERIAL PRIMARY KEY,
    invoice_no          VARCHAR(64),                              -- 财务发票号
    invoice_code        VARCHAR(64)  NOT NULL,                    -- 业务编号（系统生成）
    invoice_type        VARCHAR(32)  NOT NULL DEFAULT 'NORMAL',   -- NORMAL/RED_REVERSE
    contract_id         BIGINT       NOT NULL,
    initiation_id       BIGINT       NOT NULL,
    customer_id         BIGINT       NOT NULL,
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
    reversed_by_id      BIGINT,                                   -- 被红冲的发票ID
    attachment_id       VARCHAR(64),                              -- 发票扫描件
    approval_comment    TEXT,
    applied_by          BIGINT,
    approved_by         BIGINT,
    approved_at         TIMESTAMP,
    issued_by           BIGINT,
    issued_at           TIMESTAMP,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pfi_code UNIQUE (invoice_code, deleted)
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
CREATE INDEX idx_pfi_contract ON pmis_finance_invoice(contract_id);
CREATE INDEX idx_pfi_initiation ON pmis_finance_invoice(initiation_id);
CREATE INDEX idx_pfi_customer ON pmis_finance_invoice(customer_id);
CREATE INDEX idx_pfi_status ON pmis_finance_invoice(status, invoice_type);
CREATE INDEX idx_pfi_invoice_date ON pmis_finance_invoice(invoice_date);
CREATE INDEX idx_pfi_tax_period ON pmis_finance_invoice(tax_period);

-- =====================================================
-- 2. 回款主表 pmis_finance_payment
-- =====================================================
DROP TABLE IF EXISTS pmis_finance_payment;
CREATE TABLE pmis_finance_payment (
    id                  BIGSERIAL PRIMARY KEY,
    payment_no          VARCHAR(64),                              -- 银行流水号/系统流水
    payment_code        VARCHAR(64)  NOT NULL,                    -- 业务编号
    contract_id         BIGINT       NOT NULL,
    initiation_id       BIGINT       NOT NULL,
    customer_id         BIGINT       NOT NULL,
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
    confirmed_by        BIGINT,
    confirmed_at        TIMESTAMP,
    recorded_by         BIGINT,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pfp_code UNIQUE (payment_code, deleted)
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
CREATE INDEX idx_pfp_contract ON pmis_finance_payment(contract_id);
CREATE INDEX idx_pfp_initiation ON pmis_finance_payment(initiation_id);
CREATE INDEX idx_pfp_customer ON pmis_finance_payment(customer_id);
CREATE INDEX idx_pfp_status ON pmis_finance_payment(status);
CREATE INDEX idx_pfp_payment_date ON pmis_finance_payment(payment_date);
CREATE INDEX idx_pfp_unalloc ON pmis_finance_payment(customer_id, status, unallocated_amount);

-- =====================================================
-- 3. 客户信用表 pmis_finance_customer_credit
-- =====================================================
DROP TABLE IF EXISTS pmis_finance_customer_credit;
CREATE TABLE pmis_finance_customer_credit (
    id                    BIGSERIAL PRIMARY KEY,
    customer_id           BIGINT       NOT NULL,
    customer_name         VARCHAR(256),
    credit_level          VARCHAR(8)   NOT NULL DEFAULT 'D',       -- A/B/C/D
    credit_score          INTEGER      NOT NULL DEFAULT 0,        -- 0-100
    total_contract_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_invoiced_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_received_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    on_time_rate          NUMERIC(5,4) NOT NULL DEFAULT 0,        -- 及时回款率
    contract_count        INTEGER      NOT NULL DEFAULT 0,
    overdue_count         INTEGER      NOT NULL DEFAULT 0,
    last_evaluation_at    TIMESTAMP,
    evaluator             VARCHAR(64),
    remark                TEXT,
    tenant_id             BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id     VARCHAR(64)  NOT NULL DEFAULT '',
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pfcc_customer UNIQUE (customer_id, deleted)
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
CREATE INDEX idx_pfcc_level ON pmis_finance_customer_credit(credit_level, credit_score);
CREATE INDEX idx_pfcc_tenant ON pmis_finance_customer_credit(tenant_id);

-- =====================================================
-- 4. 初始数据：信用等级字典（用于前端展示）
-- =====================================================
-- credit_level 字段含义（见上方 COLUMN COMMENT）: A=优质(90-100) B=良好(75-89) C=一般(60-74) D=风险(0-59)
