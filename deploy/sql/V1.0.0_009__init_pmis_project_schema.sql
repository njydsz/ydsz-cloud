-- =====================================================
-- PMIS 项目全生命周期模块 DDL
-- 版本: V1.0.0_009
-- 描述: 商机、立项、合同、补充协议、合同变更
-- =====================================================

-- =====================================================
-- 1. 商机主表 pmis_project_opportunity
-- =====================================================
DROP TABLE IF EXISTS pmis_project_opportunity;
CREATE TABLE pmis_project_opportunity (
    id                BIGSERIAL PRIMARY KEY,
    opportunity_code  VARCHAR(64)   NOT NULL,
    opportunity_name  VARCHAR(256)  NOT NULL,
    customer_id       BIGINT        NOT NULL,
    customer_name     VARCHAR(256),
    business_dept_id  BIGINT,
    owner_id          BIGINT        NOT NULL,
    owner_name        VARCHAR(64),
    level             VARCHAR(8)    NOT NULL DEFAULT 'C',  -- A/B/C
    source            VARCHAR(64),                        -- 商机来源
    industry          VARCHAR(64),
    estimated_amount  NUMERIC(18,2) NOT NULL DEFAULT 0,    -- 预计金额
    win_rate          NUMERIC(5,4)  NOT NULL DEFAULT 0,    -- 0~1
    expected_sign_date DATE,                               -- 预计签约日期
    expected_start_date DATE,
    expected_end_date   DATE,
    status            VARCHAR(32)   NOT NULL DEFAULT 'FOLLOWING',  -- FOLLOWING/QUOTED/NEGOTIATING/WON/LOST/INVALID
    lost_reason       VARCHAR(512),
    competitor        VARCHAR(256),
    remark            TEXT,
    tags              VARCHAR(512),                       -- 逗号分隔
    tenant_id         BIGINT        NOT NULL DEFAULT 1,
    created_by        BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        BIGINT        NOT NULL DEFAULT 0,
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_ppo_code UNIQUE (opportunity_code, deleted)
);
COMMENT ON TABLE pmis_project_opportunity IS '商机主表';
COMMENT ON COLUMN pmis_project_opportunity.level IS '商机分级 A/B/C';
COMMENT ON COLUMN pmis_project_opportunity.status IS 'FOLLOWING/QUOTED/NEGOTIATING/WON/LOST/INVALID';
COMMENT ON COLUMN pmis_project_opportunity.win_rate IS '赢率 0.0000-1.0000';

CREATE INDEX idx_ppo_customer   ON pmis_project_opportunity (customer_id);
CREATE INDEX idx_ppo_owner      ON pmis_project_opportunity (owner_id);
CREATE INDEX idx_ppo_status     ON pmis_project_opportunity (status);
CREATE INDEX idx_ppo_level      ON pmis_project_opportunity (level);
CREATE INDEX idx_ppo_created    ON pmis_project_opportunity (created_at DESC);
CREATE INDEX idx_ppo_tenant     ON pmis_project_opportunity (tenant_id);

-- =====================================================
-- 2. 商机跟进记录 pmis_project_opportunity_follow
-- =====================================================
DROP TABLE IF EXISTS pmis_project_opportunity_follow;
CREATE TABLE pmis_project_opportunity_follow (
    id                BIGSERIAL PRIMARY KEY,
    opportunity_id    BIGINT        NOT NULL,
    follow_type       VARCHAR(32)   NOT NULL,    -- VISIT/CALL/QUOTE/NEGOTIATE/OTHER
    follow_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    follower_id       BIGINT        NOT NULL,
    follower_name     VARCHAR(64),
    content           TEXT,
    next_step         TEXT,
    next_follow_date  DATE,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT      NOT NULL DEFAULT 0
);
COMMENT ON TABLE pmis_project_opportunity_follow IS '商机跟进记录';
CREATE INDEX idx_ppof_opp ON pmis_project_opportunity_follow (opportunity_id, follow_at DESC);

-- =====================================================
-- 3. 立项主表 pmis_project_initiation
-- =====================================================
DROP TABLE IF EXISTS pmis_project_initiation;
CREATE TABLE pmis_project_initiation (
    id                BIGSERIAL PRIMARY KEY,
    project_code      VARCHAR(64)   NOT NULL,
    project_name      VARCHAR(256)  NOT NULL,
    opportunity_id    BIGINT,                            -- 来源商机
    customer_id       BIGINT        NOT NULL,
    customer_name     VARCHAR(256),
    business_dept_id  BIGINT,
    project_type      VARCHAR(32)   NOT NULL,            -- FIXED_PRICE/T&M/OUTSOURCING/PRODUCT...
    project_level     VARCHAR(16)   NOT NULL DEFAULT 'C', -- A/B/C
    pm_id             BIGINT,                            -- 项目经理
    pm_name           VARCHAR(64),
    sponsor_id        BIGINT,                            -- 项目发起人
    sponsor_name      VARCHAR(64),
    estimated_amount  NUMERIC(18,2) NOT NULL DEFAULT 0,
    budget_amount     NUMERIC(18,2) NOT NULL DEFAULT 0,
    planned_start_date DATE,
    planned_end_date   DATE,
    duration_days     INTEGER       NOT NULL DEFAULT 0,
    stage             VARCHAR(32)   NOT NULL DEFAULT 'PRE_INITIATION', -- PRE_INITIATION/SUBMITTED/APPROVING/APPROVED/REJECTED/EXECUTING/CLOSED
    current_gate      VARCHAR(32),                       -- 当前门径 CD1/CD2/CD3/CD4/CD5
    description       TEXT,
    business_case     TEXT,                              -- 立项依据
    risk_assessment   TEXT,                              -- 风险评估
    workflow_id       VARCHAR(64),                       -- 关联 Flowable 流程实例
    tenant_id         BIGINT        NOT NULL DEFAULT 1,
    created_by        BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        BIGINT        NOT NULL DEFAULT 0,
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_ppi_code UNIQUE (project_code, deleted)
);
COMMENT ON TABLE pmis_project_initiation IS '项目立项主表';
COMMENT ON COLUMN pmis_project_initiation.stage IS '立项阶段 PRE_INITIATION/SUBMITTED/APPROVING/APPROVED/REJECTED/EXECUTING/CLOSED';
COMMENT ON COLUMN pmis_project_initiation.current_gate IS '门径评审 CD1/CD2/CD3/CD4/CD5';

CREATE INDEX idx_ppi_customer  ON pmis_project_initiation (customer_id);
CREATE INDEX idx_ppi_stage     ON pmis_project_initiation (stage);
CREATE INDEX idx_ppi_pm        ON pmis_project_initiation (pm_id);
CREATE INDEX idx_ppi_opp       ON pmis_project_initiation (opportunity_id);
CREATE INDEX idx_ppi_tenant    ON pmis_project_initiation (tenant_id);
CREATE INDEX idx_ppi_created   ON pmis_project_initiation (created_at DESC);

-- =====================================================
-- 4. 立项预算明细 pmis_project_budget_item
-- =====================================================
DROP TABLE IF EXISTS pmis_project_budget_item;
CREATE TABLE pmis_project_budget_item (
    id                BIGSERIAL PRIMARY KEY,
    initiation_id     BIGINT        NOT NULL,
    category          VARCHAR(32)   NOT NULL,    -- LABOR/PURCHASE/EXPENSE/OUTSOURCE/OTHER
    sub_category      VARCHAR(64),
    description       VARCHAR(256),
    quantity          NUMERIC(18,2) NOT NULL DEFAULT 0,
    unit              VARCHAR(16),
    unit_price        NUMERIC(18,2) NOT NULL DEFAULT 0,
    amount            NUMERIC(18,2) NOT NULL DEFAULT 0,
    remark            VARCHAR(512),
    sort_order        INTEGER       NOT NULL DEFAULT 0,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT      NOT NULL DEFAULT 0
);
COMMENT ON TABLE pmis_project_budget_item IS '立项预算明细';
CREATE INDEX idx_ppbi_init ON pmis_project_budget_item (initiation_id);

-- =====================================================
-- 5. 门径评审记录 pmis_project_gate_review
-- =====================================================
DROP TABLE IF EXISTS pmis_project_gate_review;
CREATE TABLE pmis_project_gate_review (
    id                BIGSERIAL PRIMARY KEY,
    initiation_id     BIGINT        NOT NULL,
    gate_code         VARCHAR(16)   NOT NULL,    -- CD1/CD2/CD3/CD4/CD5
    gate_name         VARCHAR(64),
    review_result     VARCHAR(16)   NOT NULL DEFAULT 'PENDING', -- PENDING/PASSED/REJECTED/CONDITIONAL
    reviewer_id       BIGINT,
    reviewer_name     VARCHAR(64),
    review_at         TIMESTAMP,
    decision_basis    TEXT,
    conditions        TEXT,
    next_gate         VARCHAR(16),
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT      NOT NULL DEFAULT 0
);
COMMENT ON TABLE pmis_project_gate_review IS '门径评审记录（CDCP 决策评审）';
CREATE INDEX idx_ppgr_init ON pmis_project_gate_review (initiation_id, gate_code);

-- =====================================================
-- 6. 合同主表 pmis_project_contract
-- =====================================================
DROP TABLE IF EXISTS pmis_project_contract;
CREATE TABLE pmis_project_contract (
    id                BIGSERIAL PRIMARY KEY,
    contract_code     VARCHAR(64)   NOT NULL,
    contract_name     VARCHAR(256)  NOT NULL,
    initiation_id     BIGINT,                            -- 关联立项
    customer_id       BIGINT        NOT NULL,
    customer_name     VARCHAR(256),
    contract_type     VARCHAR(32)   NOT NULL,            -- FIXED_PRICE/T&M/OUTSOURCING/PRODUCT/MAINTENANCE
    sign_date         DATE,
    effective_date    DATE,
    expire_date       DATE,
    total_amount      NUMERIC(18,2) NOT NULL DEFAULT 0,  -- 合同总额
    currency          VARCHAR(8)    NOT NULL DEFAULT 'CNY',
    payment_terms     TEXT,                              -- 付款条款
    billing_cycle     VARCHAR(32),                       -- 结算周期
    tax_rate          NUMERIC(5,4)  NOT NULL DEFAULT 0,   -- 税率 0.0000-1.0000
    status            VARCHAR(32)   NOT NULL DEFAULT 'DRAFT', -- DRAFT/SUBMITTED/APPROVING/ACTIVE/SUSPENDED/EXPIRED/TERMINATED
    risk_level        VARCHAR(8)    NOT NULL DEFAULT 'LOW',  -- LOW/MEDIUM/HIGH
    risk_notes        TEXT,
    owner_id          BIGINT        NOT NULL,
    owner_name        VARCHAR(64),
    contract_file_id  BIGINT,                            -- 合同文件 ID（关联 file 服务）
    workflow_id       VARCHAR(64),
    remark            TEXT,
    tenant_id         BIGINT        NOT NULL DEFAULT 1,
    created_by        BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        BIGINT        NOT NULL DEFAULT 0,
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_ppc_code UNIQUE (contract_code, deleted)
);
COMMENT ON TABLE pmis_project_contract IS '合同主表';
COMMENT ON COLUMN pmis_project_contract.status IS 'DRAFT/SUBMITTED/APPROVING/ACTIVE/SUSPENDED/EXPIRED/TERMINATED';
COMMENT ON COLUMN pmis_project_contract.risk_level IS '风险等级 LOW/MEDIUM/HIGH';

CREATE INDEX idx_ppc_customer  ON pmis_project_contract (customer_id);
CREATE INDEX idx_ppc_init      ON pmis_project_contract (initiation_id);
CREATE INDEX idx_ppc_status    ON pmis_project_contract (status);
CREATE INDEX idx_ppc_sign      ON pmis_project_contract (sign_date);
CREATE INDEX idx_ppc_risk      ON pmis_project_contract (risk_level);
CREATE INDEX idx_ppc_tenant    ON pmis_project_contract (tenant_id);

-- =====================================================
-- 7. 合同补充协议 pmis_project_contract_supplement
-- =====================================================
DROP TABLE IF EXISTS pmis_project_contract_supplement;
CREATE TABLE pmis_project_contract_supplement (
    id                BIGSERIAL PRIMARY KEY,
    contract_id       BIGINT        NOT NULL,
    supplement_code   VARCHAR(64)   NOT NULL,
    supplement_name   VARCHAR(256)  NOT NULL,
    supplement_type   VARCHAR(32)   NOT NULL,            -- AMOUNT/SCOPE/TERM/OTHER
    change_amount     NUMERIC(18,2) NOT NULL DEFAULT 0,
    new_total_amount  NUMERIC(18,2) NOT NULL DEFAULT 0,
    effective_date    DATE,
    expire_date       DATE,
    content           TEXT,
    file_id           BIGINT,
    status            VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    tenant_id         BIGINT        NOT NULL DEFAULT 1,
    created_by        BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        BIGINT        NOT NULL DEFAULT 0,
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_ppcs_code UNIQUE (supplement_code, deleted)
);
COMMENT ON TABLE pmis_project_contract_supplement IS '合同补充协议';
CREATE INDEX idx_ppcs_contract ON pmis_project_contract_supplement (contract_id);

-- =====================================================
-- 8. 合同变更记录 pmis_project_contract_change
-- =====================================================
DROP TABLE IF EXISTS pmis_project_contract_change;
CREATE TABLE pmis_project_contract_change (
    id                BIGSERIAL PRIMARY KEY,
    contract_id       BIGINT        NOT NULL,
    change_code       VARCHAR(64)   NOT NULL,
    change_type       VARCHAR(32)   NOT NULL,    -- SCOPE/AMOUNT/TERM/PERSONNEL/PROGRESS
    change_reason     TEXT,
    before_value      TEXT,
    after_value       TEXT,
    amount_delta      NUMERIC(18,2) NOT NULL DEFAULT 0,
    impact_analysis   TEXT,
    status            VARCHAR(32)   NOT NULL DEFAULT 'DRAFT', -- DRAFT/SUBMITTED/APPROVING/APPROVED/REJECTED
    applicant_id      BIGINT,
    applicant_name    VARCHAR(64),
    approver_id       BIGINT,
    approver_name     VARCHAR(64),
    approved_at       TIMESTAMP,
    workflow_id       VARCHAR(64),
    tenant_id         BIGINT        NOT NULL DEFAULT 1,
    created_by        BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        BIGINT        NOT NULL DEFAULT 0,
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_ppcc_code UNIQUE (change_code, deleted)
);
COMMENT ON TABLE pmis_project_contract_change IS '合同变更记录';
CREATE INDEX idx_ppcc_contract ON pmis_project_contract_change (contract_id);
CREATE INDEX idx_ppcc_status   ON pmis_project_contract_change (status);
