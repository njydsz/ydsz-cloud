-- =====================================================
-- PMIS 项目执行/成本/利润模块 DDL
-- 版本: V1.0.0_010
-- 描述: WBS 任务、工时、成本归集、利润核算
-- =====================================================

-- =====================================================
-- 1. WBS 任务表 pmis_execution_wbs_task
-- =====================================================
DROP TABLE IF EXISTS pmis_execution_wbs_task;
CREATE TABLE pmis_execution_wbs_task (
    id                  BIGSERIAL PRIMARY KEY,
    task_code           VARCHAR(64)   NOT NULL,
    task_name           VARCHAR(256)  NOT NULL,
    initiation_id       BIGINT        NOT NULL,         -- 关联立项
    parent_id           BIGINT        NOT NULL DEFAULT 0,
    task_level          INTEGER       NOT NULL DEFAULT 1, -- WBS 层级
    wbs_path            VARCHAR(512),                    -- 形如 /1/3/5
    sort_order          INTEGER       NOT NULL DEFAULT 0,
    task_type           VARCHAR(32)   NOT NULL DEFAULT 'TASK', -- TASK/MILESTONE/SUMMARY
    planned_start_date  DATE,
    planned_end_date    DATE,
    actual_start_date   DATE,
    actual_end_date     DATE,
    duration_days       INTEGER,
    planned_effort      NUMERIC(10,2) NOT NULL DEFAULT 0,    -- 计划人天
    actual_effort       NUMERIC(10,2) NOT NULL DEFAULT 0,    -- 实际人天
    progress_pct        NUMERIC(5,2)  NOT NULL DEFAULT 0,    -- 0-100
    owner_id            BIGINT        NOT NULL,               -- 责任人
    owner_name          VARCHAR(64),
    assignee_ids        VARCHAR(512),                        -- 逗号分隔执行人
    priority            VARCHAR(16)   NOT NULL DEFAULT 'NORMAL', -- LOW/NORMAL/HIGH/URGENT
    status              VARCHAR(32)   NOT NULL DEFAULT 'PLANNED',
    -- PLANNED/IN_PROGRESS/BLOCKED/IN_REVIEW/COMPLETED/CANCELLED
    depends_on          VARCHAR(512),                        -- 依赖任务ID列表
    milestone           SMALLINT      NOT NULL DEFAULT 0,
    description         TEXT,
    deliverable         TEXT,
    risk_level          VARCHAR(16)   NOT NULL DEFAULT 'LOW', -- LOW/MEDIUM/HIGH
    tenant_id           BIGINT        NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64)   NOT NULL DEFAULT '',
    created_by          BIGINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT        NOT NULL DEFAULT 0,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_pewt_code UNIQUE (task_code, deleted)
);
COMMENT ON TABLE pmis_execution_wbs_task IS 'WBS 任务表';
COMMENT ON COLUMN pmis_execution_wbs_task.task_type IS 'TASK/MILESTONE/SUMMARY';
COMMENT ON COLUMN pmis_execution_wbs_task.status IS 'PLANNED/IN_PROGRESS/BLOCKED/IN_REVIEW/COMPLETED/CANCELLED';
COMMENT ON COLUMN pmis_execution_wbs_task.priority IS 'LOW/NORMAL/HIGH/URGENT';

CREATE INDEX idx_pewt_initiation ON pmis_execution_wbs_task (initiation_id, deleted);
CREATE INDEX idx_pewt_parent     ON pmis_execution_wbs_task (parent_id);
CREATE INDEX idx_pewt_owner      ON pmis_execution_wbs_task (owner_id) WHERE deleted = 0;
CREATE INDEX idx_pewt_status     ON pmis_execution_wbs_task (status) WHERE deleted = 0;
CREATE INDEX idx_pewt_milestone  ON pmis_execution_wbs_task (initiation_id, milestone) WHERE deleted = 0;
CREATE INDEX idx_pewt_trace      ON pmis_execution_wbs_task (provider_trace_id);

-- =====================================================
-- 2. 工时录入表 pmis_execution_time_entry
-- =====================================================
DROP TABLE IF EXISTS pmis_execution_time_entry;
CREATE TABLE pmis_execution_time_entry (
    id                  BIGSERIAL PRIMARY KEY,
    entry_date          DATE          NOT NULL,
    employee_id         BIGINT        NOT NULL,        -- 填报人
    employee_name       VARCHAR(64),
    level_code          VARCHAR(8)    NOT NULL,        -- 职级
    initiation_id       BIGINT        NOT NULL,
    initiation_name     VARCHAR(256),
    task_id             BIGINT,                          -- 关联 WBS 任务（可空：项目级工时）
    task_name           VARCHAR(256),
    hours               NUMERIC(5,2)  NOT NULL,        -- 工时（小时）
    days                NUMERIC(5,2)  NOT NULL DEFAULT 0, -- 人天（按 8h 折算）
    overtime            NUMERIC(5,2)  NOT NULL DEFAULT 0, -- 加班工时
    work_type           VARCHAR(32)   NOT NULL DEFAULT 'REGULAR', -- REGULAR/OVERTIME/TRAINING/LEAVE
    description         TEXT,
    status              VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
    -- DRAFT/SUBMITTED/APPROVED/REJECTED
    approver_id         BIGINT,
    approver_name       VARCHAR(64),
    approved_at         TIMESTAMP,
    reject_reason       VARCHAR(512),
    tenant_id           BIGINT        NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64)   NOT NULL DEFAULT '',
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT      NOT NULL DEFAULT 0
);
COMMENT ON TABLE pmis_execution_time_entry IS '工时录入（日清日结）';
COMMENT ON COLUMN pmis_execution_time_entry.hours IS '工时（小时）';
COMMENT ON COLUMN pmis_execution_time_entry.days IS '人天（按 8h 折算）';
COMMENT ON COLUMN pmis_execution_time_entry.status IS 'DRAFT/SUBMITTED/APPROVED/REJECTED';

CREATE INDEX idx_pete_employee  ON pmis_execution_time_entry (employee_id, entry_date DESC);
CREATE INDEX idx_pete_initiation ON pmis_execution_time_entry (initiation_id, entry_date DESC);
CREATE INDEX idx_pete_task      ON pmis_execution_time_entry (task_id) WHERE deleted = 0;
CREATE INDEX idx_pete_status    ON pmis_execution_time_entry (status) WHERE deleted = 0;
CREATE INDEX idx_pete_level     ON pmis_execution_time_entry (level_code) WHERE deleted = 0;
CREATE INDEX idx_pete_trace     ON pmis_execution_time_entry (provider_trace_id);

-- =====================================================
-- 3. 成本归集表 pmis_cost_allocation
-- =====================================================
DROP TABLE IF EXISTS pmis_cost_allocation;
CREATE TABLE pmis_cost_allocation (
    id                  BIGSERIAL PRIMARY KEY,
    initiation_id       BIGINT        NOT NULL,
    period              VARCHAR(7)    NOT NULL,        -- 形如 2026-06
    cost_type           VARCHAR(32)   NOT NULL,        -- LABOR/PURCHASE/EXPENSE/OUTSOURCE/ALLOCATION/OTHER
    source_id           BIGINT,                          -- 源单据ID（time_entry/purchase/expense）
    source_type         VARCHAR(32),                    -- 源单据类型
    description         VARCHAR(512),
    amount              NUMERIC(18,2) NOT NULL DEFAULT 0,
    billable            SMALLINT      NOT NULL DEFAULT 1, -- 是否可计费
    allocated           SMALLINT      NOT NULL DEFAULT 0, -- 是否已分摊
    employee_id         BIGINT,
    employee_name       VARCHAR(64),
    level_code          VARCHAR(8),
    tenant_id           BIGINT        NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64)   NOT NULL DEFAULT '',
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT      NOT NULL DEFAULT 0
);
COMMENT ON TABLE pmis_cost_allocation IS '项目成本归集';
COMMENT ON COLUMN pmis_cost_allocation.cost_type IS 'LABOR/PURCHASE/EXPENSE/OUTSOURCE/ALLOCATION/OTHER';
COMMENT ON COLUMN pmis_cost_allocation.billable IS '1=可计费，0=不可计费';
COMMENT ON COLUMN pmis_cost_allocation.allocated IS '1=已分摊到 WBS 节点';

CREATE INDEX idx_pca_initiation ON pmis_cost_allocation (initiation_id, period);
CREATE INDEX idx_pca_type       ON pmis_cost_allocation (cost_type) WHERE deleted = 0;
CREATE INDEX idx_pca_source     ON pmis_cost_allocation (source_type, source_id);
CREATE INDEX idx_pca_employee   ON pmis_cost_allocation (employee_id) WHERE deleted = 0;
CREATE INDEX idx_pca_trace      ON pmis_cost_allocation (provider_trace_id);

-- =====================================================
-- 4. 采购成本表 pmis_cost_purchase
-- =====================================================
DROP TABLE IF EXISTS pmis_cost_purchase;
CREATE TABLE pmis_cost_purchase (
    id                  BIGSERIAL PRIMARY KEY,
    purchase_code       VARCHAR(64)   NOT NULL,
    initiation_id       BIGINT        NOT NULL,
    vendor              VARCHAR(256),
    item_name           VARCHAR(256)  NOT NULL,
    quantity            NUMERIC(10,2) NOT NULL DEFAULT 1,
    unit_price          NUMERIC(18,2) NOT NULL DEFAULT 0,
    amount              NUMERIC(18,2) NOT NULL DEFAULT 0,
    purchase_date       DATE,
    status              VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    -- DRAFT/SUBMITTED/APPROVED/REJECTED/PAID
    applicant_id        BIGINT        NOT NULL,
    applicant_name      VARCHAR(64),
    approver_id         BIGINT,
    approver_name       VARCHAR(64),
    approved_at         TIMESTAMP,
    description         TEXT,
    tenant_id           BIGINT        NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64)   NOT NULL DEFAULT '',
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_pcp_code UNIQUE (purchase_code, deleted)
);
COMMENT ON TABLE pmis_cost_purchase IS '采购成本申请';
COMMENT ON COLUMN pmis_cost_purchase.status IS 'DRAFT/SUBMITTED/APPROVED/REJECTED/PAID';

CREATE INDEX idx_pcp_initiation ON pmis_cost_purchase (initiation_id) WHERE deleted = 0;
CREATE INDEX idx_pcp_status     ON pmis_cost_purchase (status) WHERE deleted = 0;
CREATE INDEX idx_pcp_applicant  ON pmis_cost_purchase (applicant_id) WHERE deleted = 0;
CREATE INDEX idx_pcp_trace      ON pmis_cost_purchase (provider_trace_id);

-- =====================================================
-- 5. 费用报销表 pmis_cost_expense
-- =====================================================
DROP TABLE IF EXISTS pmis_cost_expense;
CREATE TABLE pmis_cost_expense (
    id                  BIGSERIAL PRIMARY KEY,
    expense_code        VARCHAR(64)   NOT NULL,
    initiation_id       BIGINT,                          -- 项目级费用可空（公司公共费用）
    employee_id         BIGINT        NOT NULL,
    employee_name       VARCHAR(64),
    expense_type        VARCHAR(32)   NOT NULL,        -- TRAVEL/CATERING/MEETING/SUPPLIES/COMMUNICATION/OTHER
    amount              NUMERIC(18,2) NOT NULL DEFAULT 0,
    expense_date        DATE          NOT NULL,
    description         TEXT,
    receipt_url         VARCHAR(512),
    status              VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    -- DRAFT/SUBMITTED/APPROVED/REJECTED/PAID
    approver_id         BIGINT,
    approver_name       VARCHAR(64),
    approved_at         TIMESTAMP,
    tenant_id           BIGINT        NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64)   NOT NULL DEFAULT '',
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_pce_code UNIQUE (expense_code, deleted)
);
COMMENT ON TABLE pmis_cost_expense IS '费用报销';
COMMENT ON COLUMN pmis_cost_expense.expense_type IS 'TRAVEL/CATERING/MEETING/SUPPLIES/COMMUNICATION/OTHER';
COMMENT ON COLUMN pmis_cost_expense.status IS 'DRAFT/SUBMITTED/APPROVED/REJECTED/PAID';

CREATE INDEX idx_pce_initiation ON pmis_cost_expense (initiation_id) WHERE deleted = 0;
CREATE INDEX idx_pce_employee   ON pmis_cost_expense (employee_id) WHERE deleted = 0;
CREATE INDEX idx_pce_status     ON pmis_cost_expense (status) WHERE deleted = 0;
CREATE INDEX idx_pce_trace      ON pmis_cost_expense (provider_trace_id);

-- =====================================================
-- 6. 收入确认表 pmis_profit_revenue
-- =====================================================
DROP TABLE IF EXISTS pmis_profit_revenue;
CREATE TABLE pmis_profit_revenue (
    id                  BIGSERIAL PRIMARY KEY,
    contract_id         BIGINT        NOT NULL,
    initiation_id       BIGINT        NOT NULL,
    revenue_code        VARCHAR(64)   NOT NULL,
    recognition_method  VARCHAR(32)   NOT NULL,        -- MILESTONE/PERCENTAGE/PERCENT_COMPLETE/POINTS/MANUAL
    period              VARCHAR(7)    NOT NULL,        -- 2026-06
    amount              NUMERIC(18,2) NOT NULL DEFAULT 0,
    recognition_date    DATE          NOT NULL,
    milestone           VARCHAR(128),                    -- 里程碑描述
    percent_complete    NUMERIC(5,2),                    -- 完工百分比（完工法）
    invoice_id          BIGINT,                          -- 关联开票申请（批次8）
    status              VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    -- DRAFT/CONFIRMED/REVERSED
    confirmed_by        BIGINT,
    confirmed_at        TIMESTAMP,
    description         TEXT,
    tenant_id           BIGINT        NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64)   NOT NULL DEFAULT '',
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_ppr_code UNIQUE (revenue_code, deleted)
);
COMMENT ON TABLE pmis_profit_revenue IS '收入确认';
COMMENT ON COLUMN pmis_profit_revenue.recognition_method IS 'MILESTONE/PERCENTAGE/PERCENT_COMPLETE/POINTS/MANUAL';

CREATE INDEX idx_ppr_contract    ON pmis_profit_revenue (contract_id) WHERE deleted = 0;
CREATE INDEX idx_ppr_initiation  ON pmis_profit_revenue (initiation_id, period) WHERE deleted = 0;
CREATE INDEX idx_ppr_status      ON pmis_profit_revenue (status) WHERE deleted = 0;
CREATE INDEX idx_ppr_trace       ON pmis_profit_revenue (provider_trace_id);

-- =====================================================
-- 7. 项目利润快照表 pmis_profit_snapshot
-- =====================================================
DROP TABLE IF EXISTS pmis_profit_snapshot;
CREATE TABLE pmis_profit_snapshot (
    id                  BIGSERIAL PRIMARY KEY,
    initiation_id       BIGINT        NOT NULL,
    period              VARCHAR(7)    NOT NULL,
    contract_amount     NUMERIC(18,2) NOT NULL DEFAULT 0, -- 合同总额
    recognized_revenue  NUMERIC(18,2) NOT NULL DEFAULT 0, -- 已确认收入
    billed_amount       NUMERIC(18,2) NOT NULL DEFAULT 0, -- 已开票
    received_amount     NUMERIC(18,2) NOT NULL DEFAULT 0, -- 已回款
    labor_cost          NUMERIC(18,2) NOT NULL DEFAULT 0, -- 人力成本
    purchase_cost       NUMERIC(18,2) NOT NULL DEFAULT 0, -- 采购成本
    expense_cost        NUMERIC(18,2) NOT NULL DEFAULT 0, -- 费用
    outsource_cost      NUMERIC(18,2) NOT NULL DEFAULT 0, -- 外包
    allocation_cost     NUMERIC(18,2) NOT NULL DEFAULT 0, -- 分摊费用
    total_cost          NUMERIC(18,2) NOT NULL DEFAULT 0, -- 总成本
    gross_profit        NUMERIC(18,2) NOT NULL DEFAULT 0, -- 毛利
    gross_margin        NUMERIC(5,4)  NOT NULL DEFAULT 0, -- 毛利率
    progress_pct        NUMERIC(5,2)  NOT NULL DEFAULT 0, -- 完工进度
    billable_hours      NUMERIC(10,2) NOT NULL DEFAULT 0, -- 可计费工时
    non_billable_hours  NUMERIC(10,2) NOT NULL DEFAULT 0, -- 不可计费工时
    snapshot_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tenant_id           BIGINT        NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64)   NOT NULL DEFAULT '',
    deleted             SMALLINT      NOT NULL DEFAULT 0
);
COMMENT ON TABLE pmis_profit_snapshot IS '项目利润快照（按月）';
COMMENT ON COLUMN pmis_profit_snapshot.gross_margin IS '毛利率 0.0000-1.0000';

CREATE INDEX idx_pps_initiation ON pmis_profit_snapshot (initiation_id, period) WHERE deleted = 0;
CREATE INDEX idx_pps_period     ON pmis_profit_snapshot (period) WHERE deleted = 0;
CREATE INDEX idx_pps_trace      ON pmis_profit_snapshot (provider_trace_id);

-- =====================================================
-- 8. 项目风险登记表 pmis_execution_risk
-- =====================================================
DROP TABLE IF EXISTS pmis_execution_risk;
CREATE TABLE pmis_execution_risk (
    id                  BIGSERIAL PRIMARY KEY,
    risk_code           VARCHAR(64)   NOT NULL,
    initiation_id       BIGINT        NOT NULL,
    risk_title          VARCHAR(256)  NOT NULL,
    risk_type           VARCHAR(32)   NOT NULL DEFAULT 'OTHER', -- SCOPE/SCHEDULE/COST/QUALITY/RESOURCE/EXTERNAL/OTHER
    description         TEXT,
    probability         VARCHAR(16)   NOT NULL DEFAULT 'MEDIUM', -- LOW/MEDIUM/HIGH
    impact              VARCHAR(16)   NOT NULL DEFAULT 'MEDIUM',
    risk_level          VARCHAR(16)   NOT NULL DEFAULT 'MEDIUM', -- 计算后的等级
    mitigation          TEXT,
    contingency         TEXT,
    owner_id            BIGINT        NOT NULL,
    owner_name          VARCHAR(64),
    status              VARCHAR(32)   NOT NULL DEFAULT 'OPEN', -- OPEN/MITIGATING/CLOSED/OCCURRED
    occurred_at         TIMESTAMP,
    closed_at           TIMESTAMP,
    tenant_id           BIGINT        NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64)   NOT NULL DEFAULT '',
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_per_code UNIQUE (risk_code, deleted)
);
COMMENT ON TABLE pmis_execution_risk IS '项目风险登记';
COMMENT ON COLUMN pmis_execution_risk.risk_type IS 'SCOPE/SCHEDULE/COST/QUALITY/RESOURCE/EXTERNAL/OTHER';
COMMENT ON COLUMN pmis_execution_risk.status IS 'OPEN/MITIGATING/CLOSED/OCCURRED';

CREATE INDEX idx_per_initiation ON pmis_execution_risk (initiation_id) WHERE deleted = 0;
CREATE INDEX idx_per_status     ON pmis_execution_risk (status) WHERE deleted = 0;
CREATE INDEX idx_per_level      ON pmis_execution_risk (risk_level) WHERE deleted = 0;
CREATE INDEX idx_per_trace      ON pmis_execution_risk (provider_trace_id);
