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
COMMENT ON TABLE pmis_execution_wbs_task IS 'WBS 任务表: 项目工作分解结构,层级化任务编排,支撑进度/工时/责任追踪';
COMMENT ON COLUMN pmis_execution_wbs_task.id IS '主键 ID';
COMMENT ON COLUMN pmis_execution_wbs_task.task_code IS '任务编码(全局唯一,如 TASK20260001001)';
COMMENT ON COLUMN pmis_execution_wbs_task.task_name IS '任务名称';
COMMENT ON COLUMN pmis_execution_wbs_task.initiation_id IS '立项 ID(关联 pmis_project_initiation.id)';
COMMENT ON COLUMN pmis_execution_wbs_task.parent_id IS '父任务 ID(0=根,支持多级 WBS)';
COMMENT ON COLUMN pmis_execution_wbs_task.task_level IS 'WBS 层级(1=顶层)';
COMMENT ON COLUMN pmis_execution_wbs_task.wbs_path IS 'WBS 路径(以斜杠分隔的祖先链路,如 /1/3/5)';
COMMENT ON COLUMN pmis_execution_wbs_task.sort_order IS '同级排序号';
COMMENT ON COLUMN pmis_execution_wbs_task.task_type IS '任务类型: TASK 普通任务 / MILESTONE 里程碑 / SUMMARY 汇总节点';
COMMENT ON COLUMN pmis_execution_wbs_task.planned_start_date IS '计划开始日期';
COMMENT ON COLUMN pmis_execution_wbs_task.planned_end_date IS '计划结束日期';
COMMENT ON COLUMN pmis_execution_wbs_task.actual_start_date IS '实际开始日期';
COMMENT ON COLUMN pmis_execution_wbs_task.actual_end_date IS '实际结束日期';
COMMENT ON COLUMN pmis_execution_wbs_task.duration_days IS '工期(天)';
COMMENT ON COLUMN pmis_execution_wbs_task.planned_effort IS '计划人天';
COMMENT ON COLUMN pmis_execution_wbs_task.actual_effort IS '实际人天(从工时聚合)';
COMMENT ON COLUMN pmis_execution_wbs_task.progress_pct IS '完成进度(0-100)';
COMMENT ON COLUMN pmis_execution_wbs_task.owner_id IS '责任人 ID(关联 pmis_employee.id)';
COMMENT ON COLUMN pmis_execution_wbs_task.owner_name IS '责任人姓名';
COMMENT ON COLUMN pmis_execution_wbs_task.assignee_ids IS '执行人 ID 列表(逗号分隔)';
COMMENT ON COLUMN pmis_execution_wbs_task.priority IS '优先级: LOW / NORMAL / HIGH / URGENT';
COMMENT ON COLUMN pmis_execution_wbs_task.status IS '任务状态: PLANNED 计划中 / IN_PROGRESS 进行中 / BLOCKED 阻塞 / IN_REVIEW 评审中 / COMPLETED 已完成 / CANCELLED 已取消';
COMMENT ON COLUMN pmis_execution_wbs_task.depends_on IS '依赖任务 ID 列表(逗号分隔,用于甘特图依赖连线)';
COMMENT ON COLUMN pmis_execution_wbs_task.milestone IS '是否里程碑: 1 是 / 0 否';
COMMENT ON COLUMN pmis_execution_wbs_task.description IS '任务描述';
COMMENT ON COLUMN pmis_execution_wbs_task.deliverable IS '交付物说明';
COMMENT ON COLUMN pmis_execution_wbs_task.risk_level IS '风险等级: LOW / MEDIUM / HIGH';
COMMENT ON COLUMN pmis_execution_wbs_task.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_execution_wbs_task.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_execution_wbs_task.created_by IS '创建人 ID';
COMMENT ON COLUMN pmis_execution_wbs_task.created_at IS '创建时间';
COMMENT ON COLUMN pmis_execution_wbs_task.updated_by IS '最后修改人 ID';
COMMENT ON COLUMN pmis_execution_wbs_task.updated_at IS '最后修改时间';
COMMENT ON COLUMN pmis_execution_wbs_task.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

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
    billable            SMALLINT      NOT NULL DEFAULT 1,           -- 是否可计费: 1 可计费 / 0 不可计费
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
COMMENT ON TABLE pmis_execution_time_entry IS '工时录入表: 日清日结,员工每日填报工时,自动计算人天/成本';
COMMENT ON COLUMN pmis_execution_time_entry.id IS '主键 ID';
COMMENT ON COLUMN pmis_execution_time_entry.entry_date IS '工时日期';
COMMENT ON COLUMN pmis_execution_time_entry.employee_id IS '填报人 ID(关联 pmis_employee.id)';
COMMENT ON COLUMN pmis_execution_time_entry.employee_name IS '填报人姓名';
COMMENT ON COLUMN pmis_execution_time_entry.level_code IS '填报人职级(冗余,锁定当时费率)';
COMMENT ON COLUMN pmis_execution_time_entry.initiation_id IS '立项 ID(关联 pmis_project_initiation.id)';
COMMENT ON COLUMN pmis_execution_time_entry.initiation_name IS '立项名称(冗余)';
COMMENT ON COLUMN pmis_execution_time_entry.task_id IS 'WBS 任务 ID(关联 pmis_execution_wbs_task.id,可空:项目级工时)';
COMMENT ON COLUMN pmis_execution_time_entry.task_name IS 'WBS 任务名称(冗余)';
COMMENT ON COLUMN pmis_execution_time_entry.hours IS '工时(小时)';
COMMENT ON COLUMN pmis_execution_time_entry.days IS '人天(按 8h 折算)';
COMMENT ON COLUMN pmis_execution_time_entry.overtime IS '加班工时(小时)';
COMMENT ON COLUMN pmis_execution_time_entry.work_type IS '工时类型: REGULAR 正常 / OVERTIME 加班 / TRAINING 培训 / LEAVE 请假';
COMMENT ON COLUMN pmis_execution_time_entry.billable IS '是否可计费: 1 可计费(向客户收费) / 0 不可计费(培训/管理工时)';
COMMENT ON COLUMN pmis_execution_time_entry.description IS '工时说明';
COMMENT ON COLUMN pmis_execution_time_entry.status IS '审批状态: DRAFT 草稿 / SUBMITTED 已提交 / APPROVED 已批准 / REJECTED 已驳回';
COMMENT ON COLUMN pmis_execution_time_entry.approver_id IS '审批人 ID';
COMMENT ON COLUMN pmis_execution_time_entry.approver_name IS '审批人姓名';
COMMENT ON COLUMN pmis_execution_time_entry.approved_at IS '审批时间';
COMMENT ON COLUMN pmis_execution_time_entry.reject_reason IS '驳回原因';
COMMENT ON COLUMN pmis_execution_time_entry.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_execution_time_entry.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_execution_time_entry.created_at IS '创建时间';
COMMENT ON COLUMN pmis_execution_time_entry.updated_at IS '最后修改时间';
COMMENT ON COLUMN pmis_execution_time_entry.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

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
COMMENT ON TABLE pmis_cost_allocation IS '项目成本归集表: 按月 × 类别归集项目发生的所有成本,支撑利润核算与驾驶舱';
COMMENT ON COLUMN pmis_cost_allocation.id IS '主键 ID';
COMMENT ON COLUMN pmis_cost_allocation.initiation_id IS '立项 ID(关联 pmis_project_initiation.id)';
COMMENT ON COLUMN pmis_cost_allocation.period IS '归集周期(YYYY-MM,如 2026-06)';
COMMENT ON COLUMN pmis_cost_allocation.cost_type IS '成本类型: LABOR 人力 / PURCHASE 采购 / EXPENSE 费用 / OUTSOURCE 外包 / ALLOCATION 分摊 / OTHER 其他';
COMMENT ON COLUMN pmis_cost_allocation.source_id IS '源单据 ID(关联 time_entry/purchase/expense)';
COMMENT ON COLUMN pmis_cost_allocation.source_type IS '源单据类型(TIME_ENTRY/PURCHASE/EXPENSE/MANUAL)';
COMMENT ON COLUMN pmis_cost_allocation.description IS '成本说明';
COMMENT ON COLUMN pmis_cost_allocation.amount IS '金额(元)';
COMMENT ON COLUMN pmis_cost_allocation.billable IS '是否可计费: 1 可计费 / 0 不可计费';
COMMENT ON COLUMN pmis_cost_allocation.allocated IS '是否已分摊到 WBS 节点: 1 已分摊 / 0 待分摊';
COMMENT ON COLUMN pmis_cost_allocation.employee_id IS '员工 ID(人力成本时关联)';
COMMENT ON COLUMN pmis_cost_allocation.employee_name IS '员工姓名';
COMMENT ON COLUMN pmis_cost_allocation.level_code IS '职级(冗余,锁定费率)';
COMMENT ON COLUMN pmis_cost_allocation.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_cost_allocation.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_cost_allocation.created_at IS '创建时间';
COMMENT ON COLUMN pmis_cost_allocation.updated_at IS '最后修改时间';
COMMENT ON COLUMN pmis_cost_allocation.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

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
COMMENT ON TABLE pmis_cost_purchase IS '采购成本申请表: 项目硬件/软件/服务采购,触发预算占用校验(80% 黄/95% 红)';
COMMENT ON COLUMN pmis_cost_purchase.id IS '主键 ID';
COMMENT ON COLUMN pmis_cost_purchase.purchase_code IS '采购单编码(全局唯一)';
COMMENT ON COLUMN pmis_cost_purchase.initiation_id IS '立项 ID(关联 pmis_project_initiation.id)';
COMMENT ON COLUMN pmis_cost_purchase.vendor IS '供应商';
COMMENT ON COLUMN pmis_cost_purchase.item_name IS '采购品名';
COMMENT ON COLUMN pmis_cost_purchase.quantity IS '采购数量';
COMMENT ON COLUMN pmis_cost_purchase.unit_price IS '单价(元)';
COMMENT ON COLUMN pmis_cost_purchase.amount IS '总金额(元,=quantity*unit_price)';
COMMENT ON COLUMN pmis_cost_purchase.purchase_date IS '采购日期';
COMMENT ON COLUMN pmis_cost_purchase.status IS '审批状态: DRAFT 草稿 / SUBMITTED 已提交 / APPROVED 已批准 / REJECTED 已驳回 / PAID 已付款';
COMMENT ON COLUMN pmis_cost_purchase.applicant_id IS '申请人 ID';
COMMENT ON COLUMN pmis_cost_purchase.applicant_name IS '申请人姓名';
COMMENT ON COLUMN pmis_cost_purchase.approver_id IS '审批人 ID';
COMMENT ON COLUMN pmis_cost_purchase.approver_name IS '审批人姓名';
COMMENT ON COLUMN pmis_cost_purchase.approved_at IS '审批时间';
COMMENT ON COLUMN pmis_cost_purchase.description IS '采购说明';
COMMENT ON COLUMN pmis_cost_purchase.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_cost_purchase.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_cost_purchase.created_at IS '创建时间';
COMMENT ON COLUMN pmis_cost_purchase.updated_at IS '最后修改时间';
COMMENT ON COLUMN pmis_cost_purchase.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

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
COMMENT ON COLUMN pmis_cost_expense.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_cost_expense.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_cost_expense.created_at IS '创建时间';
COMMENT ON COLUMN pmis_cost_expense.updated_at IS '最后修改时间';
COMMENT ON COLUMN pmis_cost_expense.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

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
COMMENT ON COLUMN pmis_profit_revenue.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_profit_revenue.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_profit_revenue.created_at IS '创建时间';
COMMENT ON COLUMN pmis_profit_revenue.updated_at IS '最后修改时间';
COMMENT ON COLUMN pmis_profit_revenue.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

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
COMMENT ON TABLE pmis_execution_risk IS '项目风险登记表: 项目执行过程中的风险识别、跟踪与闭环管理';
COMMENT ON COLUMN pmis_execution_risk.risk_type IS '风险类型: SCOPE 范围 / SCHEDULE 进度 / COST 成本 / QUALITY 质量 / RESOURCE 资源 / EXTERNAL 外部 / OTHER 其他';
COMMENT ON COLUMN pmis_execution_risk.status IS '风险状态: OPEN 待处理 / MITIGATING 处理中 / CLOSED 已关闭 / OCCURRED 已发生';

CREATE INDEX idx_per_initiation ON pmis_execution_risk (initiation_id) WHERE deleted = 0;
CREATE INDEX idx_per_status     ON pmis_execution_risk (status) WHERE deleted = 0;
CREATE INDEX idx_per_level      ON pmis_execution_risk (risk_level) WHERE deleted = 0;
CREATE INDEX idx_per_trace      ON pmis_execution_risk (provider_trace_id);
