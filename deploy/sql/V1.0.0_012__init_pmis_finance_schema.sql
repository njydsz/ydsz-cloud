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
COMMENT ON TABLE pmis_finance_invoice IS '发票主表（支持正常开票与红冲）';
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
COMMENT ON TABLE pmis_finance_payment IS '回款主表';
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
COMMENT ON TABLE pmis_finance_customer_credit IS '客户信用记录';
CREATE INDEX idx_pfcc_level ON pmis_finance_customer_credit(credit_level, credit_score);
CREATE INDEX idx_pfcc_tenant ON pmis_finance_customer_credit(tenant_id);

-- =====================================================
-- 4. 初始数据：信用等级字典（用于前端展示）
-- =====================================================
COMMENT ON COLUMN pmis_finance_customer_credit.credit_level IS 'A=优质(90-100) B=良好(75-89) C=一般(60-74) D=风险(0-59)';
