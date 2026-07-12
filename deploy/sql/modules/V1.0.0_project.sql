-- ============================================================
-- PMIS project module SQL
-- 项目执行服务 (ydsz-pmis-project, port 9003)
-- ============================================================
-- 本脚本 DDL 对应后端 project 服务的 Mapper / DO,
--   物理 Mapper 实际所在模块即表归属。跨服务引用禁止直连,统一走
--   Feign Client (FinanceDataClient / SalesDataClient)。
--
-- 表归属依据: ydsz-pmis-project/src/main/java/.../infra/mapper/
-- 表数量: 20 张 (原 42 张表拆分后剩余)
-- --------------------------------------------------------------------
-- [P4 架构优化提示] 跨模块冗余字段：pmis_cost_allocation.employee_name、
--   pmis_cost_purchase.applicant_name / approver_name 等 *_name 字段为历史
--   冗余存储，原则上应通过 NameAssembler 实时解析，禁止在写入时同步冗余。
--   现有数据保留（兼容历史查询），新写入由 Java 端 NameAssembler 自动注入。
-- --------------------------------------------------------------------

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_project_initiation(
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    project_code      VARCHAR(64)    NOT NULL,
    project_name      VARCHAR(256)   NOT NULL,
    opportunity_id    VARCHAR(20),
    customer_id       VARCHAR(20)         NOT NULL,
    customer_name     VARCHAR(256),
    business_dept_id  VARCHAR(20),
    project_type      VARCHAR(32)    NOT NULL,
    project_level     VARCHAR(16)    NOT NULL DEFAULT 'C',
    pm_id             VARCHAR(20),
    pm_name           VARCHAR(64),
    sponsor_id        VARCHAR(20),
    sponsor_name      VARCHAR(64),
    estimated_amount  NUMERIC(18,2)  NOT NULL DEFAULT 0,
    budget_amount     NUMERIC(18,2)  NOT NULL DEFAULT 0,
    planned_start_date DATE,
    planned_end_date   DATE,
    duration_days     INTEGER        NOT NULL DEFAULT 0,
    stage             VARCHAR(32)    NOT NULL DEFAULT 'PRE_INITIATION',
    current_gate      VARCHAR(32),
    description       TEXT,
    business_case     TEXT,
    risk_assessment   TEXT,
    workflow_id       VARCHAR(20),
    created_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT       NOT NULL DEFAULT 0,
    tenant_id         VARCHAR(20)         NOT NULL DEFAULT '1',
    version           INTEGER        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT uk_ppi_code           UNIQUE (project_code, deleted),
    CONSTRAINT ck_ppi_type_enum      CHECK (project_type IN ('FIXED_PRICE', 'T_M', 'OUTSOURCING', 'PRODUCT', 'MAINTENANCE', 'CONSULTING', 'TRAINING', 'OTHER')),
    CONSTRAINT ck_ppi_level_enum     CHECK (project_level IN ('A', 'B', 'C')),
    CONSTRAINT ck_ppi_stage_enum     CHECK (stage IN ('PRE_INITIATION', 'SUBMITTED', 'APPROVING', 'APPROVED', 'REJECTED', 'EXECUTING', 'CLOSED')),
    CONSTRAINT ck_ppi_gate_enum      CHECK (current_gate IS NULL OR current_gate IN ('CD1', 'CD2', 'CD3', 'CD4', 'CD5')),
    CONSTRAINT ck_ppi_amount_nonneg  CHECK (estimated_amount >= 0 AND budget_amount >= 0),
    CONSTRAINT ck_ppi_duration_nonneg CHECK (duration_days >= 0),
    CONSTRAINT ck_ppi_version_nonneg CHECK (version >= 0),
    CONSTRAINT ck_ppi_deleted_enum   CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_project_initiation IS '项目立项主表: 商机到合同之间的立项流程载体,关联预算/PM/CDCP 门径评审';

COMMENT ON COLUMN pmis_project_initiation.id IS '主键 ID';

COMMENT ON COLUMN pmis_project_initiation.project_code IS '项目编码(全局唯一,如 PRJ20260001)';

COMMENT ON COLUMN pmis_project_initiation.project_name IS '项目名称';

COMMENT ON COLUMN pmis_project_initiation.opportunity_id IS '来源商机 ID(关联 pmis_project_opportunity.id)';

COMMENT ON COLUMN pmis_project_initiation.customer_id IS '客户 ID';

COMMENT ON COLUMN pmis_project_initiation.customer_name IS '客户名称(冗余)';

COMMENT ON COLUMN pmis_project_initiation.business_dept_id IS '负责事业部 ID';

COMMENT ON COLUMN pmis_project_initiation.project_type IS '项目类型: FIXED_PRICE 固定总价 / T_M 人月计费 / OUTSOURCING 人力外包 / PRODUCT 产品销售 / MAINTENANCE 运维 / CONSULTING 咨询 / TRAINING 培训 / OTHER';

COMMENT ON COLUMN pmis_project_initiation.project_level IS '项目级别: A 重大 / B 重要 / C 一般(影响审批流)';

COMMENT ON COLUMN pmis_project_initiation.pm_id IS '项目经理 ID(关联 pmis_employee.id)';

COMMENT ON COLUMN pmis_project_initiation.pm_name IS '项目经理姓名';

COMMENT ON COLUMN pmis_project_initiation.sponsor_id IS '项目发起人/赞助人 ID(业务方)';

COMMENT ON COLUMN pmis_project_initiation.sponsor_name IS '项目发起人姓名';

COMMENT ON COLUMN pmis_project_initiation.estimated_amount IS '预计合同金额(元)';

COMMENT ON COLUMN pmis_project_initiation.budget_amount IS '项目预算总额(元,人力+采购+费用+外包)';

COMMENT ON COLUMN pmis_project_initiation.planned_start_date IS '计划开始日期';

COMMENT ON COLUMN pmis_project_initiation.planned_end_date IS '计划结束日期';

COMMENT ON COLUMN pmis_project_initiation.duration_days IS '计划工期(天)';

COMMENT ON COLUMN pmis_project_initiation.stage IS '立项阶段: PRE_INITIATION 预立项 / SUBMITTED 已提交 / APPROVING 审批中 / APPROVED 已批准 / REJECTED 已驳回 / EXECUTING 执行中 / CLOSED 已结项';

COMMENT ON COLUMN pmis_project_initiation.current_gate IS '当前 CDCP 门径: CD1 启动 / CD2 设计 / CD3 建设 / CD4 UAT / CD5 上线';

COMMENT ON COLUMN pmis_project_initiation.description IS '项目描述';

COMMENT ON COLUMN pmis_project_initiation.business_case IS '立项依据(业务价值/ROI 分析)';

COMMENT ON COLUMN pmis_project_initiation.risk_assessment IS '风险评估';

COMMENT ON COLUMN pmis_project_initiation.workflow_id IS '关联自研工作流流程实例 ID';

COMMENT ON COLUMN pmis_project_initiation.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_project_initiation.created_at IS '创建时间';

COMMENT ON COLUMN pmis_project_initiation.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_project_initiation.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_project_initiation.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_project_initiation.tenant_id IS '租户 ID(单租户部署默认 1)';

COMMENT ON COLUMN pmis_project_initiation.version IS '乐观锁版本号';

CREATE INDEX IF NOT EXISTS idx_ppi_customer
    ON pmis_project_initiation (customer_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ppi_stage
    ON pmis_project_initiation (stage) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ppi_pm
    ON pmis_project_initiation (pm_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ppi_opp
    ON pmis_project_initiation (opportunity_id) WHERE deleted = 0;

-- [INLINE-OPT] PM 工作台:PM + 阶段(项目经理视图)
CREATE INDEX IF NOT EXISTS idx_ppi_pm_stage
    ON pmis_project_initiation (pm_id, stage) WHERE deleted = 0;

-- [INLINE-OPT] 复合索引:租户 + 阶段 + 创建时间倒序(立项中心列表)
CREATE INDEX IF NOT EXISTS idx_ppi_tenant_stage_created
    ON pmis_project_initiation (tenant_id, stage, created_at DESC) WHERE deleted = 0;

-- [INLINE-OPT] 当前门径索引(CDCP 流程编排)
CREATE INDEX IF NOT EXISTS idx_ppi_current_gate
    ON pmis_project_initiation (current_gate) WHERE deleted = 0 AND current_gate IS NOT NULL;

-- =====================================================
-- 4. 立项预算明细 pmis_project_budget_item

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_project_budget_item(
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    initiation_id     VARCHAR(20)         NOT NULL,
    category          VARCHAR(32)    NOT NULL,
    sub_category      VARCHAR(64),
    description       VARCHAR(256),
    quantity          NUMERIC(18,2)  NOT NULL DEFAULT 0,
    unit              VARCHAR(16),
    unit_price        NUMERIC(18,2)  NOT NULL DEFAULT 0,
    amount            NUMERIC(18,2)  NOT NULL DEFAULT 0,
    remark            VARCHAR(512),
    sort_order        INTEGER        NOT NULL DEFAULT 0,
    created_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT       NOT NULL DEFAULT 0,
    tenant_id         VARCHAR(20)         NOT NULL DEFAULT '1',
    version           INTEGER        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_ppbi_category_enum CHECK (category IN ('LABOR', 'PURCHASE', 'EXPENSE', 'OUTSOURCE', 'OTHER')),
    CONSTRAINT ck_ppbi_amount_nonneg CHECK (quantity >= 0 AND unit_price >= 0 AND amount >= 0),
    CONSTRAINT ck_ppbi_sort_nonneg   CHECK (sort_order >= 0),
    CONSTRAINT ck_ppbi_version_nonneg CHECK (version >= 0),
    CONSTRAINT ck_ppbi_deleted_enum  CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_project_budget_item IS '立项预算明细: 按类别拆解预算,支撑执行期预算占用控制(80% 黄/95% 红)';

COMMENT ON COLUMN pmis_project_budget_item.id IS '主键 ID';

COMMENT ON COLUMN pmis_project_budget_item.initiation_id IS '立项 ID(关联 pmis_project_initiation.id)';

COMMENT ON COLUMN pmis_project_budget_item.category IS '预算类别: LABOR 人力 / PURCHASE 采购 / EXPENSE 费用 / OUTSOURCE 外包 / OTHER 其他';

COMMENT ON COLUMN pmis_project_budget_item.sub_category IS '子类别(可关联字典,如差旅/团建/硬件/软件)';

COMMENT ON COLUMN pmis_project_budget_item.description IS '预算项说明';

COMMENT ON COLUMN pmis_project_budget_item.quantity IS '数量';

COMMENT ON COLUMN pmis_project_budget_item.unit IS '计量单位(人天/件/次/月)';

COMMENT ON COLUMN pmis_project_budget_item.unit_price IS '单价(元)';

COMMENT ON COLUMN pmis_project_budget_item.amount IS '金额(元,=quantity*unit_price)';

COMMENT ON COLUMN pmis_project_budget_item.remark IS '备注';

COMMENT ON COLUMN pmis_project_budget_item.sort_order IS '排序号';

COMMENT ON COLUMN pmis_project_budget_item.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_project_budget_item.created_at IS '创建时间';

COMMENT ON COLUMN pmis_project_budget_item.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_project_budget_item.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_project_budget_item.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_project_budget_item.tenant_id IS '租户 ID(单租户部署默认 1)';

COMMENT ON COLUMN pmis_project_budget_item.version IS '乐观锁版本号(P1-2)';

-- [INLINE-OPT] 复合索引:立项 + 类别 + 排序(预算分类展示)
CREATE INDEX IF NOT EXISTS idx_ppbi_init_cat_sort
    ON pmis_project_budget_item (initiation_id, category, sort_order) WHERE deleted = 0;

-- [INLINE-OPT] 立项 + 租户(预算中心筛选)
CREATE INDEX IF NOT EXISTS idx_ppbi_tenant_init
    ON pmis_project_budget_item (tenant_id, initiation_id) WHERE deleted = 0;

-- =====================================================
-- 5. 门径评审记录 pmis_project_gate_review

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_project_gate_review(
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    initiation_id     VARCHAR(20)         NOT NULL,
    gate_code         VARCHAR(16)    NOT NULL,
    gate_name         VARCHAR(64),
    review_result     VARCHAR(16)    NOT NULL DEFAULT 'PENDING',
    reviewer_id       VARCHAR(20),
    reviewer_name     VARCHAR(64),
    review_at         TIMESTAMPTZ,
    decision_basis    TEXT,
    conditions        TEXT,
    next_gate         VARCHAR(16),
    created_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT       NOT NULL DEFAULT 0,
    tenant_id         VARCHAR(20)         NOT NULL DEFAULT '1',
    version           INTEGER        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_ppgr_gate_enum      CHECK (gate_code IN ('CD1', 'CD2', 'CD3', 'CD4', 'CD5')),
    CONSTRAINT ck_ppgr_result_enum    CHECK (review_result IN ('PENDING', 'PASSED', 'REJECTED', 'CONDITIONAL')),
    CONSTRAINT ck_ppgr_next_gate_enum CHECK (next_gate IS NULL OR next_gate IN ('CD1', 'CD2', 'CD3', 'CD4', 'CD5')),
    CONSTRAINT ck_ppgr_version_nonneg CHECK (version >= 0),
    CONSTRAINT ck_ppgr_deleted_enum   CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_project_gate_review IS '门径评审记录: CDCP 决策评审(CD1 启动/CD2 设计/CD3 建设/CD4 UAT/CD5 上线)';

COMMENT ON COLUMN pmis_project_gate_review.id IS '主键 ID';

COMMENT ON COLUMN pmis_project_gate_review.initiation_id IS '立项 ID(关联 pmis_project_initiation.id)';

COMMENT ON COLUMN pmis_project_gate_review.gate_code IS '门径编码: CD1 / CD2 / CD3 / CD4 / CD5';

COMMENT ON COLUMN pmis_project_gate_review.gate_name IS '门径名称';

COMMENT ON COLUMN pmis_project_gate_review.review_result IS '评审结果: PENDING 待评审 / PASSED 通过 / REJECTED 不通过 / CONDITIONAL 有条件通过';

COMMENT ON COLUMN pmis_project_gate_review.reviewer_id IS '评审人 ID';

COMMENT ON COLUMN pmis_project_gate_review.reviewer_name IS '评审人姓名';

COMMENT ON COLUMN pmis_project_gate_review.review_at IS '评审时间';

COMMENT ON COLUMN pmis_project_gate_review.decision_basis IS '决策依据';

COMMENT ON COLUMN pmis_project_gate_review.conditions IS '有条件通过的条件清单';

COMMENT ON COLUMN pmis_project_gate_review.next_gate IS '下一门径';

COMMENT ON COLUMN pmis_project_gate_review.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_project_gate_review.created_at IS '创建时间';

COMMENT ON COLUMN pmis_project_gate_review.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_project_gate_review.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_project_gate_review.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_project_gate_review.tenant_id IS '租户 ID(单租户部署默认 1)';

COMMENT ON COLUMN pmis_project_gate_review.version IS '乐观锁版本号';

-- [INLINE-OPT] 复合索引:立项 + 门径(门径状态查询)
CREATE INDEX IF NOT EXISTS idx_ppgr_init_gate
    ON pmis_project_gate_review (initiation_id, gate_code) WHERE deleted = 0;

-- [INLINE-OPT] 评审结果索引(待评审 / 已驳回 工作台)
CREATE INDEX IF NOT EXISTS idx_ppgr_result
    ON pmis_project_gate_review (review_result) WHERE deleted = 0;

-- [INLINE-OPT] 评审人 + 评审时间(评审人工作台)
CREATE INDEX IF NOT EXISTS idx_ppgr_reviewer_at
    ON pmis_project_gate_review (reviewer_id, review_at DESC) WHERE deleted = 0;

-- =====================================================
-- 6. 合同主表 pmis_project_contract

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_execution_wbs_task(
    id                  VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    task_code           VARCHAR(64)    NOT NULL,
    task_name           VARCHAR(256)   NOT NULL,
    initiation_id       VARCHAR(20)         NOT NULL,
    parent_id           VARCHAR(20)         NOT NULL DEFAULT 0,
    task_level          INTEGER        NOT NULL DEFAULT 1,
    wbs_path            VARCHAR(512),
    sort_order          INTEGER        NOT NULL DEFAULT 0,
    task_type           VARCHAR(32)    NOT NULL DEFAULT 'TASK',
    planned_start_date  DATE,
    planned_end_date    DATE,
    actual_start_date   DATE,
    actual_end_date     DATE,
    duration_days       INTEGER,
    planned_effort      NUMERIC(10,2)  NOT NULL DEFAULT 0,
    actual_effort       NUMERIC(10,2)  NOT NULL DEFAULT 0,
    progress_pct        NUMERIC(5,2)   NOT NULL DEFAULT 0,
    owner_id            VARCHAR(20)         NOT NULL,
    owner_name          VARCHAR(64),
    assignee_ids        VARCHAR(512),
    priority            VARCHAR(16)    NOT NULL DEFAULT 'NORMAL',
    status              VARCHAR(32)    NOT NULL DEFAULT 'PLANNED',
    depends_on          VARCHAR(512),
    milestone           SMALLINT       NOT NULL DEFAULT 0,
    description         TEXT,
    deliverable         TEXT,
    risk_level          VARCHAR(16)    NOT NULL DEFAULT 'LOW',
    provider_trace_id   VARCHAR(64)    NOT NULL DEFAULT '',
    created_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT       NOT NULL DEFAULT 0,
    tenant_id           VARCHAR(20)         NOT NULL DEFAULT '1',
    version             INTEGER        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT uk_pewt_code           UNIQUE (task_code, deleted),
    CONSTRAINT ck_pewt_type_enum      CHECK (task_type IN ('TASK', 'MILESTONE', 'SUMMARY')),
    CONSTRAINT ck_pewt_priority_enum  CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT ck_pewt_status_enum    CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'BLOCKED', 'IN_REVIEW', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_pewt_risk_enum      CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_pewt_milestone_enum CHECK (milestone IN (0, 1)),
    CONSTRAINT ck_pewt_progress_range CHECK (progress_pct >= 0 AND progress_pct <= 100),
    CONSTRAINT ck_pewt_effort_nonneg  CHECK (planned_effort >= 0 AND actual_effort >= 0),
    CONSTRAINT ck_pewt_duration_nonneg CHECK (duration_days IS NULL OR duration_days >= 0),
    CONSTRAINT ck_pewt_dates_valid    CHECK (planned_end_date IS NULL OR planned_start_date IS NULL OR planned_end_date >= planned_start_date),
    CONSTRAINT ck_pewt_version_nonneg CHECK (version >= 0),
    CONSTRAINT ck_pewt_deleted_enum   CHECK (deleted IN (0, 1))
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

COMMENT ON COLUMN pmis_execution_wbs_task.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_execution_wbs_task.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_execution_wbs_task.created_at IS '创建时间';

COMMENT ON COLUMN pmis_execution_wbs_task.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_execution_wbs_task.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_execution_wbs_task.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_execution_wbs_task.tenant_id IS '租户 ID(单租户部署默认 1)';

COMMENT ON COLUMN pmis_execution_wbs_task.version IS '乐观锁版本号';

-- [INLINE-OPT] 复合索引:立项 + 删除标记(WBS 树形加载)
CREATE INDEX IF NOT EXISTS idx_pewt_initiation
    ON pmis_execution_wbs_task (initiation_id, deleted);

CREATE INDEX IF NOT EXISTS idx_pewt_parent
    ON pmis_execution_wbs_task (parent_id);

CREATE INDEX IF NOT EXISTS idx_pewt_owner
    ON pmis_execution_wbs_task (owner_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pewt_status
    ON pmis_execution_wbs_task (status) WHERE deleted = 0;

-- [INLINE-OPT] 立项 + 里程碑(WBS 里程碑视图)
CREATE INDEX IF NOT EXISTS idx_pewt_milestone
    ON pmis_execution_wbs_task (initiation_id, milestone) WHERE deleted = 0;

-- [INLINE-OPT] 责任人 + 状态(个人任务工作台)
CREATE INDEX IF NOT EXISTS idx_pewt_owner_status
    ON pmis_execution_wbs_task (owner_id, status) WHERE deleted = 0;

-- [INLINE-OPT] 链路追踪 ID 索引(分布式排障)
CREATE INDEX IF NOT EXISTS idx_pewt_trace
    ON pmis_execution_wbs_task (provider_trace_id) WHERE provider_trace_id <> '';

-- =====================================================
-- 2. 工时录入表 pmis_execution_time_entry

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_execution_time_entry(
    id                  VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    entry_date          DATE           NOT NULL,
    employee_id         VARCHAR(20)         NOT NULL,
    employee_name       VARCHAR(64),
    level_code          VARCHAR(8)     NOT NULL,
    initiation_id       VARCHAR(20)         NOT NULL,
    initiation_name     VARCHAR(256),
    task_id             VARCHAR(20),
    task_name           VARCHAR(256),
    hours               NUMERIC(5,2)   NOT NULL,
    days                NUMERIC(5,2)   NOT NULL DEFAULT 0,
    overtime            NUMERIC(5,2)   NOT NULL DEFAULT 0,
    work_type           VARCHAR(32)    NOT NULL DEFAULT 'REGULAR',
    billable            SMALLINT       NOT NULL DEFAULT 1,
    description         TEXT,
    rate_id             VARCHAR(20),
    rate                NUMERIC(10,2),
    status              VARCHAR(16)    NOT NULL DEFAULT 'DRAFT',
    approver_id         VARCHAR(20),
    approver_name       VARCHAR(64),
    approved_at         TIMESTAMPTZ,
    reject_reason       VARCHAR(512),
    provider_trace_id   VARCHAR(64)    NOT NULL DEFAULT '',
    created_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT       NOT NULL DEFAULT 0,
    tenant_id           VARCHAR(20)         NOT NULL DEFAULT '1',
    version             INTEGER        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_pete_hours_nonneg    CHECK (hours    >= 0),
    CONSTRAINT ck_pete_days_nonneg     CHECK (days     >= 0),
    CONSTRAINT ck_pete_overtime_nonneg CHECK (overtime >= 0),
    CONSTRAINT ck_pete_rate_nonneg     CHECK (rate IS NULL OR rate >= 0),
    CONSTRAINT ck_pete_work_type_enum  CHECK (work_type IN ('REGULAR', 'OVERTIME', 'TRAINING', 'LEAVE')),
    CONSTRAINT ck_pete_status_enum     CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_pete_billable_enum   CHECK (billable IN (0, 1)),
    CONSTRAINT ck_pete_version_nonneg  CHECK (version >= 0),
    CONSTRAINT ck_pete_deleted_enum    CHECK (deleted IN (0, 1))
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

COMMENT ON COLUMN pmis_execution_time_entry.rate_id IS '命中的费率卡 ID(关联 pmis_rate_card.id,可空:未匹配到费率卡)';

COMMENT ON COLUMN pmis_execution_time_entry.rate IS '人天费率(冗余,锁定当时报价,用于成本归集)';

COMMENT ON COLUMN pmis_execution_time_entry.status IS '审批状态: DRAFT 草稿 / SUBMITTED 已提交 / APPROVED 已批准 / REJECTED 已驳回';

COMMENT ON COLUMN pmis_execution_time_entry.approver_id IS '审批人 ID';

COMMENT ON COLUMN pmis_execution_time_entry.approver_name IS '审批人姓名';

COMMENT ON COLUMN pmis_execution_time_entry.approved_at IS '审批时间';

COMMENT ON COLUMN pmis_execution_time_entry.reject_reason IS '驳回原因';

COMMENT ON COLUMN pmis_execution_time_entry.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_execution_time_entry.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_execution_time_entry.created_at IS '创建时间';

COMMENT ON COLUMN pmis_execution_time_entry.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_execution_time_entry.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_execution_time_entry.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_execution_time_entry.tenant_id IS '租户 ID(单租户部署默认 1)';

COMMENT ON COLUMN pmis_execution_time_entry.version IS '乐观锁版本号';

-- [INLINE-OPT] 复合索引:员工 + 日期(个人工时历史)
CREATE INDEX IF NOT EXISTS idx_pete_employee
    ON pmis_execution_time_entry (employee_id, entry_date DESC) WHERE deleted = 0;

-- [INLINE-OPT] 复合索引:立项 + 日期(项目工时聚合)
CREATE INDEX IF NOT EXISTS idx_pete_initiation
    ON pmis_execution_time_entry (initiation_id, entry_date DESC) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pete_task
    ON pmis_execution_time_entry (task_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pete_status
    ON pmis_execution_time_entry (status) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pete_level
    ON pmis_execution_time_entry (level_code) WHERE deleted = 0;

-- [INLINE-OPT] 费率卡 ID 索引(按费率卡聚合成本)
CREATE INDEX IF NOT EXISTS idx_pete_rate_id
    ON pmis_execution_time_entry (rate_id) WHERE deleted = 0 AND rate_id IS NOT NULL;

-- [INLINE-OPT] 审批人 + 状态(审批人工作台)
CREATE INDEX IF NOT EXISTS idx_pete_approver_status
    ON pmis_execution_time_entry (approver_id, status) WHERE deleted = 0;

-- [INLINE-OPT] 复合索引:租户 + 创建时间倒序(工时中心列表)
CREATE INDEX IF NOT EXISTS idx_pete_tenant_created
    ON pmis_execution_time_entry (tenant_id, created_at DESC) WHERE deleted = 0;

-- [INLINE-OPT] 链路追踪 ID 索引
CREATE INDEX IF NOT EXISTS idx_pete_trace
    ON pmis_execution_time_entry (provider_trace_id) WHERE provider_trace_id <> '';

-- =====================================================
-- 3. 成本归集表 pmis_cost_allocation

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_cost_allocation(
    id                  VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    initiation_id       VARCHAR(20)         NOT NULL,
    period              VARCHAR(7)     NOT NULL,
    cost_type           VARCHAR(32)    NOT NULL,
    source_id           VARCHAR(20),
    source_type         VARCHAR(32),
    description         VARCHAR(512),
    amount              NUMERIC(18,2)  NOT NULL DEFAULT 0,
    billable            SMALLINT       NOT NULL DEFAULT 1,
    allocated           SMALLINT       NOT NULL DEFAULT 0,
    employee_id         VARCHAR(20),
    employee_name       VARCHAR(64),
    level_code          VARCHAR(8),
    provider_trace_id   VARCHAR(64)    NOT NULL DEFAULT '',
    created_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT       NOT NULL DEFAULT 0,
    tenant_id           VARCHAR(20)         NOT NULL DEFAULT '1',
    version             INTEGER        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_pca_type_enum         CHECK (cost_type IN ('LABOR', 'PURCHASE', 'EXPENSE', 'OUTSOURCE', 'ALLOCATION', 'OTHER')),
    CONSTRAINT ck_pca_source_type_enum  CHECK (source_type IS NULL OR source_type IN ('TIME_ENTRY', 'PURCHASE', 'EXPENSE', 'MANUAL')),
    CONSTRAINT ck_pca_amount_nonneg     CHECK (amount >= 0),
    CONSTRAINT ck_pca_billable_enum     CHECK (billable IN (0, 1)),
    CONSTRAINT ck_pca_allocated_enum    CHECK (allocated IN (0, 1)),
    CONSTRAINT ck_pca_version_nonneg    CHECK (version >= 0),
    CONSTRAINT ck_pca_deleted_enum      CHECK (deleted IN (0, 1))
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

COMMENT ON COLUMN pmis_cost_allocation.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_cost_allocation.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_cost_allocation.created_at IS '创建时间';

COMMENT ON COLUMN pmis_cost_allocation.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_cost_allocation.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_cost_allocation.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_cost_allocation.tenant_id IS '租户 ID(单租户部署默认 1)';

COMMENT ON COLUMN pmis_cost_allocation.version IS '乐观锁版本号';

-- [INLINE-OPT] 复合索引:立项 + 周期(月度成本透视)
CREATE INDEX IF NOT EXISTS idx_pca_initiation
    ON pmis_cost_allocation (initiation_id, period) WHERE deleted = 0;

-- [INLINE-OPT] 复合索引:周期 + 类型(全公司月度成本分析)
CREATE INDEX IF NOT EXISTS idx_pca_period_type
    ON pmis_cost_allocation (period, cost_type) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pca_type
    ON pmis_cost_allocation (cost_type) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pca_source
    ON pmis_cost_allocation (source_type, source_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pca_employee
    ON pmis_cost_allocation (employee_id) WHERE deleted = 0;

-- [INLINE-OPT] 复合索引:租户 + 周期(全租户成本驾驶舱)
CREATE INDEX IF NOT EXISTS idx_pca_tenant_period
    ON pmis_cost_allocation (tenant_id, period) WHERE deleted = 0;

-- [INLINE-OPT] 链路追踪 ID 索引
CREATE INDEX IF NOT EXISTS idx_pca_trace
    ON pmis_cost_allocation (provider_trace_id) WHERE provider_trace_id <> '';

-- =====================================================
-- 4. 采购成本表 pmis_cost_purchase

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_cost_purchase(
    id                  VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    purchase_code       VARCHAR(64)    NOT NULL,
    initiation_id       VARCHAR(20)         NOT NULL,
    vendor              VARCHAR(256),
    item_name           VARCHAR(256)   NOT NULL,
    quantity            NUMERIC(10,2)  NOT NULL DEFAULT 1,
    unit_price          NUMERIC(18,2)  NOT NULL DEFAULT 0,
    amount              NUMERIC(18,2)  NOT NULL DEFAULT 0,
    purchase_date       DATE,
    status              VARCHAR(32)    NOT NULL DEFAULT 'DRAFT',
    applicant_id        VARCHAR(20)         NOT NULL,
    applicant_name      VARCHAR(64),
    approver_id         VARCHAR(20),
    approver_name       VARCHAR(64),
    approved_at         TIMESTAMPTZ,
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
    CONSTRAINT uk_pcp_code           UNIQUE (purchase_code, deleted),
    CONSTRAINT ck_pcp_status_enum    CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'PAID')),
    CONSTRAINT ck_pcp_amount_nonneg  CHECK (quantity > 0 AND unit_price >= 0 AND amount >= 0),
    CONSTRAINT ck_pcp_version_nonneg CHECK (version >= 0),
    CONSTRAINT ck_pcp_deleted_enum   CHECK (deleted IN (0, 1))
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

COMMENT ON COLUMN pmis_cost_purchase.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_cost_purchase.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_cost_purchase.created_at IS '创建时间';

COMMENT ON COLUMN pmis_cost_purchase.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_cost_purchase.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_cost_purchase.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_cost_purchase.tenant_id IS '租户 ID(单租户部署默认 1)';

COMMENT ON COLUMN pmis_cost_purchase.version IS '乐观锁版本号';

CREATE INDEX IF NOT EXISTS idx_pcp_initiation
    ON pmis_cost_purchase (initiation_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pcp_status
    ON pmis_cost_purchase (status) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pcp_applicant
    ON pmis_cost_purchase (applicant_id) WHERE deleted = 0;

-- [INLINE-OPT] 申请人 + 状态(申请人台账)
CREATE INDEX IF NOT EXISTS idx_pcp_applicant_status
    ON pmis_cost_purchase (applicant_id, status) WHERE deleted = 0;

-- [INLINE-OPT] 复合索引:租户 + 采购日期(采购中心时间筛选)
CREATE INDEX IF NOT EXISTS idx_pcp_tenant_date
    ON pmis_cost_purchase (tenant_id, purchase_date DESC) WHERE deleted = 0;

-- [INLINE-OPT] 链路追踪 ID 索引
CREATE INDEX IF NOT EXISTS idx_pcp_trace
    ON pmis_cost_purchase (provider_trace_id) WHERE provider_trace_id <> '';

-- =====================================================
-- 5. 费用报销表 pmis_cost_expense

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_execution_risk(
    id                  VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    risk_code           VARCHAR(64)    NOT NULL,
    initiation_id       VARCHAR(20)         NOT NULL,
    risk_title          VARCHAR(256)   NOT NULL,
    risk_type           VARCHAR(32)    NOT NULL DEFAULT 'OTHER',
    description         TEXT,
    probability         VARCHAR(16)    NOT NULL DEFAULT 'MEDIUM',
    impact              VARCHAR(16)    NOT NULL DEFAULT 'MEDIUM',
    risk_level          VARCHAR(16)    NOT NULL DEFAULT 'MEDIUM',
    mitigation          TEXT,
    contingency         TEXT,
    owner_id            VARCHAR(20)         NOT NULL,
    owner_name          VARCHAR(64),
    status              VARCHAR(32)    NOT NULL DEFAULT 'OPEN',
    occurred_at         TIMESTAMPTZ,
    closed_at           TIMESTAMPTZ,
    provider_trace_id   VARCHAR(64)    NOT NULL DEFAULT '',
    created_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT       NOT NULL DEFAULT 0,
    tenant_id           VARCHAR(20)         NOT NULL DEFAULT '1',
    version             INTEGER        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT uk_per_code           UNIQUE (risk_code, deleted),
    CONSTRAINT ck_per_type_enum      CHECK (risk_type IN ('SCOPE', 'SCHEDULE', 'COST', 'QUALITY', 'RESOURCE', 'EXTERNAL', 'OTHER')),
    CONSTRAINT ck_per_level_enum     CHECK (probability IN ('LOW', 'MEDIUM', 'HIGH') AND impact IN ('LOW', 'MEDIUM', 'HIGH') AND risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_per_status_enum    CHECK (status IN ('OPEN', 'MITIGATING', 'CLOSED', 'OCCURRED')),
    CONSTRAINT ck_per_dates_valid    CHECK (closed_at IS NULL OR occurred_at IS NULL OR closed_at >= occurred_at),
    CONSTRAINT ck_per_version_nonneg CHECK (version >= 0),
    CONSTRAINT ck_per_deleted_enum   CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_execution_risk IS '项目风险登记表: 项目执行过程中的风险识别、跟踪与闭环管理';

COMMENT ON COLUMN pmis_execution_risk.id IS '主键 ID';

COMMENT ON COLUMN pmis_execution_risk.risk_code IS '风险编号(全局唯一)';

COMMENT ON COLUMN pmis_execution_risk.initiation_id IS '立项 ID(关联 pmis_project_initiation.id)';

COMMENT ON COLUMN pmis_execution_risk.risk_title IS '风险标题';

COMMENT ON COLUMN pmis_execution_risk.risk_type IS '风险类型: SCOPE 范围 / SCHEDULE 进度 / COST 成本 / QUALITY 质量 / RESOURCE 资源 / EXTERNAL 外部 / OTHER 其他';

COMMENT ON COLUMN pmis_execution_risk.description IS '风险描述';

COMMENT ON COLUMN pmis_execution_risk.probability IS '发生概率: LOW / MEDIUM / HIGH';

COMMENT ON COLUMN pmis_execution_risk.impact IS '影响程度: LOW / MEDIUM / HIGH';

COMMENT ON COLUMN pmis_execution_risk.risk_level IS '风险等级(probability × impact 派生): LOW / MEDIUM / HIGH';

COMMENT ON COLUMN pmis_execution_risk.mitigation IS '缓解措施';

COMMENT ON COLUMN pmis_execution_risk.contingency IS '应急方案';

COMMENT ON COLUMN pmis_execution_risk.owner_id IS '风险责任人 ID';

COMMENT ON COLUMN pmis_execution_risk.owner_name IS '风险责任人姓名';

COMMENT ON COLUMN pmis_execution_risk.status IS '风险状态: OPEN 待处理 / MITIGATING 处理中 / CLOSED 已关闭 / OCCURRED 已发生';

COMMENT ON COLUMN pmis_execution_risk.occurred_at IS '风险发生时间';

COMMENT ON COLUMN pmis_execution_risk.closed_at IS '风险关闭时间';

COMMENT ON COLUMN pmis_execution_risk.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_execution_risk.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_execution_risk.created_at IS '创建时间';

COMMENT ON COLUMN pmis_execution_risk.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_execution_risk.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_execution_risk.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_execution_risk.tenant_id IS '租户 ID(单租户部署默认 1)';

COMMENT ON COLUMN pmis_execution_risk.version IS '乐观锁版本号';

CREATE INDEX IF NOT EXISTS idx_per_initiation
    ON pmis_execution_risk (initiation_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_per_status
    ON pmis_execution_risk (status) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_per_level
    ON pmis_execution_risk (risk_level) WHERE deleted = 0;

-- [INLINE-OPT] 复合索引:立项 + 状态(项目风险面板)
CREATE INDEX IF NOT EXISTS idx_per_init_status
    ON pmis_execution_risk (initiation_id, status) WHERE deleted = 0;

-- [INLINE-OPT] 复合索引:租户 + 状态(全公司高风险扫描)
CREATE INDEX IF NOT EXISTS idx_per_tenant_status
    ON pmis_execution_risk (tenant_id, status) WHERE deleted = 0;

-- [INLINE-OPT] 链路追踪 ID 索引
CREATE INDEX IF NOT EXISTS idx_per_trace
    ON pmis_execution_risk (provider_trace_id) WHERE provider_trace_id <> '';

-- --------------------------------------------------------------------

-- ============================ [011] init pmis batch8 schema ============================

-- =====================================================
-- PMIS 批次8 DDL：合同模板/项目变更/项目交付/项目结项/AI智能体
-- 版本: V1.0.0_011
-- 描述: 合同模板(Project)、项目变更(Project)、交付物标准(Execution)、
--       交付物实例(Execution)、项目结项(Execution)、AI预测(Agent)
-- =====================================================

-- =====================================================
-- 1. 合同模板表 pmis_project_contract_template

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_project_change(
    id                       VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    change_code              VARCHAR(64)  NOT NULL,
    initiation_id            VARCHAR(20)       NOT NULL,
    change_type              VARCHAR(32)  NOT NULL,            -- SCOPE/COST/CONTRACT/STAFF/SCHEDULE
    change_title             VARCHAR(256) NOT NULL,
    change_reason            TEXT,
    change_desc              TEXT,
    budget_impact            NUMERIC(15,2) NOT NULL DEFAULT 0,
    contract_impact          NUMERIC(15,2) NOT NULL DEFAULT 0,
    schedule_impact_days     INTEGER      NOT NULL DEFAULT 0,
    profit_impact            NUMERIC(15,2) NOT NULL DEFAULT 0,
    profit_impact_pct        NUMERIC(5,4) NOT NULL DEFAULT 0,
    risk_level_after         VARCHAR(16)  NOT NULL DEFAULT 'LOW',  -- LOW/MEDIUM/HIGH
    affected_wbs_count       INTEGER      NOT NULL DEFAULT 0,
    affected_staff_count     INTEGER      NOT NULL DEFAULT 0,
    major_flag               SMALLINT     NOT NULL DEFAULT 0,        -- 0/1 重大变更
    approver_roles           VARCHAR(256) NOT NULL DEFAULT '[]',     -- JSON
    applicant_id             VARCHAR(20)       NOT NULL,
    applicant_name           VARCHAR(64),
    contract_id              VARCHAR(20),
    workflow_id              VARCHAR(20),
    status                   VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',  -- ChangeStatus
    submitted_at             TIMESTAMPTZ,
    approved_at              TIMESTAMPTZ,
    executed_at              TIMESTAMPTZ,
    remark                   TEXT,
    tenant_id                VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id        VARCHAR(64)  NOT NULL DEFAULT '',
    created_by               VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by               VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                  SMALLINT     NOT NULL DEFAULT 0,
    -- 业务唯一性
    CONSTRAINT uk_pch_code              UNIQUE (change_code, deleted),
    -- 枚举约束
    CONSTRAINT ck_pch_change_type       CHECK (change_type IN ('SCOPE','COST','CONTRACT','STAFF','SCHEDULE')),
    CONSTRAINT ck_pch_risk_level        CHECK (risk_level_after IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT ck_pch_status_enum       CHECK (status IN ('DRAFT','SUBMITTED','UNDER_REVIEW','APPROVED','REJECTED','EXECUTING')),
    CONSTRAINT ck_pch_major_flag        CHECK (major_flag IN (0, 1)),
    CONSTRAINT ck_pch_deleted_enum      CHECK (deleted IN (0, 1)),
    -- 数值约束
    CONSTRAINT ck_pch_wbs_count         CHECK (affected_wbs_count >= 0),
    CONSTRAINT ck_pch_staff_count       CHECK (affected_staff_count >= 0)
);

COMMENT ON TABLE  pmis_project_change IS '项目变更主表: 5 类变更（SCOPE/COST/CONTRACT/STAFF/SCHEDULE）全过程管理,严格执行 DRAFT→SUBMITTED→UNDER_REVIEW→APPROVED/REJECTED→EXECUTING 状态机';

COMMENT ON COLUMN pmis_project_change.change_code IS '变更单号: 业务唯一,如 CHG-2026-001';

COMMENT ON COLUMN pmis_project_change.initiation_id IS '所属立项 ID';

COMMENT ON COLUMN pmis_project_change.change_type IS '变更类型: SCOPE 范围 / COST 成本 / CONTRACT 合同 / STAFF 人员 / SCHEDULE 进度';

COMMENT ON COLUMN pmis_project_change.change_title IS '变更标题';

COMMENT ON COLUMN pmis_project_change.change_reason IS '变更原因';

COMMENT ON COLUMN pmis_project_change.change_desc IS '变更详细描述';

COMMENT ON COLUMN pmis_project_change.budget_impact IS '预算影响金额(元): 正数=增加,负数=减少';

COMMENT ON COLUMN pmis_project_change.contract_impact IS '合同金额影响(元): 正数=增加,负数=减少';

COMMENT ON COLUMN pmis_project_change.schedule_impact_days IS '进度影响天数: 正数=延期,负数=提前';

COMMENT ON COLUMN pmis_project_change.profit_impact IS '利润影响(元)';

COMMENT ON COLUMN pmis_project_change.profit_impact_pct IS '利润影响比例: 0.05=5%';

COMMENT ON COLUMN pmis_project_change.risk_level_after IS '变更后风险等级: LOW 低 / MEDIUM 中 / HIGH 高';

COMMENT ON COLUMN pmis_project_change.affected_wbs_count IS '受影响 WBS 任务数';

COMMENT ON COLUMN pmis_project_change.affected_staff_count IS '受影响人员数';

COMMENT ON COLUMN pmis_project_change.major_flag IS '是否重大变更: 0=否,1=是（重大变更需走完整审批流）';

COMMENT ON COLUMN pmis_project_change.approver_roles IS '审批人角色 JSON 数组: 指定审批节点的角色列表';

COMMENT ON COLUMN pmis_project_change.applicant_id IS '申请人 ID';

COMMENT ON COLUMN pmis_project_change.applicant_name IS '申请人姓名（冗余）';

COMMENT ON COLUMN pmis_project_change.contract_id IS '关联合同 ID（合同变更时必填）';

COMMENT ON COLUMN pmis_project_change.workflow_id IS '流程实例 ID: 关联工作流引擎的实例';

COMMENT ON COLUMN pmis_project_change.status IS '变更状态: DRAFT 草稿 / SUBMITTED 已提交 / UNDER_REVIEW 审批中 / APPROVED 已批准 / REJECTED 已驳回 / EXECUTING 执行中';

COMMENT ON COLUMN pmis_project_change.submitted_at IS '提交时间';

COMMENT ON COLUMN pmis_project_change.approved_at IS '审批通过时间';

COMMENT ON COLUMN pmis_project_change.executed_at IS '执行完成时间';

COMMENT ON COLUMN pmis_project_change.remark IS '备注';

COMMENT ON COLUMN pmis_project_change.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_project_change.provider_trace_id IS '链路追踪 ID: AI 智能体调用时的 trace 标识';

COMMENT ON COLUMN pmis_project_change.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_pch_initiation / idx_pch_type_status / idx_pch_major)
CREATE INDEX IF NOT EXISTS idx_pch_tenant_initiation
    ON pmis_project_change(tenant_id, initiation_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pch_tenant_type_status
    ON pmis_project_change(tenant_id, change_type, status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pch_initiation_major
    ON pmis_project_change(initiation_id, major_flag);

-- =====================================================
-- 3. 交付物标准表 pmis_execution_delivery_standard

-- =====================================================
-- P1-6: 已废弃,无需 DROP
CREATE TABLE IF NOT EXISTS pmis_execution_delivery_standard(
    id                    VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    project_type          VARCHAR(32)  NOT NULL,                 -- ProjectType
    project_level         VARCHAR(16),                            -- L1-L18, NULL=全部
    delivery_name         VARCHAR(256) NOT NULL,
    delivery_category     VARCHAR(32)  NOT NULL DEFAULT 'DOC',   -- DOC/CODE/MODEL/RUNBOOK/REPORT/OTHER
    stage                 VARCHAR(32)  NOT NULL,                 -- DeliveryStage
    required              SMALLINT     NOT NULL DEFAULT 1,        -- 1=必交付
    trigger_tr            SMALLINT     NOT NULL DEFAULT 0,        -- 是否触发 TR
    acceptance_criteria   TEXT,
    template_ref          VARCHAR(256),
    remark                TEXT,
    tenant_id             VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id     VARCHAR(64)  NOT NULL DEFAULT '',
    created_by            VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               SMALLINT     NOT NULL DEFAULT 0,
    -- 枚举约束
    CONSTRAINT ck_peds_project_type    CHECK (project_type IN ('FIXED_PRICE','T_M','OUTSOURCING','PRODUCT','MAINTENANCE','CONSULTING','TRAINING','OTHER')),
    CONSTRAINT ck_peds_category        CHECK (delivery_category IN ('DOC','CODE','MODEL','RUNBOOK','REPORT','OTHER')),
    CONSTRAINT ck_peds_stage           CHECK (stage IN ('CD1_KICKOFF','CD2_DESIGN','CD3_BUILD','CD4_UAT','CD5_GO_LIVE')),
    CONSTRAINT ck_peds_required        CHECK (required IN (0, 1)),
    CONSTRAINT ck_peds_trigger_tr      CHECK (trigger_tr IN (0, 1)),
    CONSTRAINT ck_peds_deleted_enum    CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_execution_delivery_standard IS '交付物标准库: 8 类项目类型 × 5 个门径阶段（CD1/CD2/CD3/CD4/CD5）的标准交付物定义,新建项目时按类型/级别自动生成交付物清单';

COMMENT ON COLUMN pmis_execution_delivery_standard.project_type IS '项目类型: FIXED_PRICE / T_M / OUTSOURCING / PRODUCT / MAINTENANCE / CONSULTING / TRAINING / OTHER';

COMMENT ON COLUMN pmis_execution_delivery_standard.project_level IS '项目级别: L1-L18,NULL 表示全级别适用';

COMMENT ON COLUMN pmis_execution_delivery_standard.delivery_name IS '交付物名称: 例如 SRS 需求规格';

COMMENT ON COLUMN pmis_execution_delivery_standard.delivery_category IS '交付物类别: DOC 文档 / CODE 代码 / MODEL 模型 / RUNBOOK 运维手册 / REPORT 报告 / OTHER 其他';

COMMENT ON COLUMN pmis_execution_delivery_standard.stage IS '所属门径阶段: CD1_KICKOFF 启动 / CD2_DESIGN 设计 / CD3_BUILD 建设 / CD4_UAT UAT / CD5_GO_LIVE 上线';

COMMENT ON COLUMN pmis_execution_delivery_standard.required IS '是否必交付: 1=必交付,0=可选';

COMMENT ON COLUMN pmis_execution_delivery_standard.trigger_tr IS '是否触发技术评审(TR): 0=否,1=是';

COMMENT ON COLUMN pmis_execution_delivery_standard.acceptance_criteria IS '验收标准: 文本描述,例如"客户签字"';

COMMENT ON COLUMN pmis_execution_delivery_standard.template_ref IS '模板引用: 关联的模板路径或编码';

COMMENT ON COLUMN pmis_execution_delivery_standard.remark IS '备注';

COMMENT ON COLUMN pmis_execution_delivery_standard.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_execution_delivery_standard.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_execution_delivery_standard.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_peds_type_level / idx_peds_stage)
CREATE INDEX IF NOT EXISTS idx_peds_tenant_type_level
    ON pmis_execution_delivery_standard(tenant_id, project_type, project_level)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_peds_tenant_stage
    ON pmis_execution_delivery_standard(tenant_id, stage)
    WHERE deleted = 0;

-- =====================================================
-- 4. 交付物实例表 pmis_execution_delivery_item

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_execution_delivery_item(
    id                    VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    item_code             VARCHAR(64)  NOT NULL,
    initiation_id         VARCHAR(20)       NOT NULL,
    standard_id           VARCHAR(20),
    project_type          VARCHAR(32),
    project_level         VARCHAR(16),
    delivery_name         VARCHAR(256) NOT NULL,
    delivery_category     VARCHAR(32)  NOT NULL DEFAULT 'DOC',
    stage                 VARCHAR(32)  NOT NULL,
    required              SMALLINT     NOT NULL DEFAULT 1,
    planned_submit_date   DATE,
    actual_submit_date    DATE,
    accepted_date         DATE,
    submitter_id          VARCHAR(20),
    submitter_name        VARCHAR(64),
    reviewer_id           VARCHAR(20),
    reviewer_name         VARCHAR(64),
    review_comment        TEXT,
    status                VARCHAR(32)  NOT NULL DEFAULT 'PENDING',  -- DeliveryItemStatus
    tr_required           SMALLINT     NOT NULL DEFAULT 0,
    tr_completed          SMALLINT     NOT NULL DEFAULT 0,
    file_ids              VARCHAR(2048) NOT NULL DEFAULT '[]',
    remark                TEXT,
    tenant_id             VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id     VARCHAR(64)  NOT NULL DEFAULT '',
    created_by            VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pedi_code            UNIQUE (item_code, deleted),
    CONSTRAINT ck_pedi_project_type    CHECK (project_type IS NULL OR project_type IN ('FIXED_PRICE','T_M','OUTSOURCING','PRODUCT','MAINTENANCE','CONSULTING','TRAINING','OTHER')),
    CONSTRAINT ck_pedi_category        CHECK (delivery_category IN ('DOC','CODE','MODEL','RUNBOOK','REPORT','OTHER')),
    CONSTRAINT ck_pedi_stage           CHECK (stage IN ('CD1_KICKOFF','CD2_DESIGN','CD3_BUILD','CD4_UAT','CD5_GO_LIVE')),
    CONSTRAINT ck_pedi_status_enum     CHECK (status IN ('PENDING','SUBMITTED','IN_REVIEW','ACCEPTED','REJECTED','REVISION')),
    CONSTRAINT ck_pedi_required        CHECK (required IN (0, 1)),
    CONSTRAINT ck_pedi_tr_required     CHECK (tr_required IN (0, 1)),
    CONSTRAINT ck_pedi_tr_completed    CHECK (tr_completed IN (0, 1)),
    CONSTRAINT ck_pedi_deleted_enum    CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_execution_delivery_item IS '交付物实例表: 项目立项后,按交付物标准库自动生成具体交付物实例,跟踪提交/验收全过程';

COMMENT ON COLUMN pmis_execution_delivery_item.item_code IS '交付物实例编码: 业务唯一,如 DI-2026-001';

COMMENT ON COLUMN pmis_execution_delivery_item.initiation_id IS '所属立项 ID';

COMMENT ON COLUMN pmis_execution_delivery_item.standard_id IS '关联交付物标准 ID: 追溯到标准库';

COMMENT ON COLUMN pmis_execution_delivery_item.project_type IS '项目类型: 冗余字段,便于快速查询';

COMMENT ON COLUMN pmis_execution_delivery_item.project_level IS '项目级别: 冗余字段';

COMMENT ON COLUMN pmis_execution_delivery_item.delivery_name IS '交付物名称';

COMMENT ON COLUMN pmis_execution_delivery_item.delivery_category IS '交付物类别: DOC/CODE/MODEL/RUNBOOK/REPORT/OTHER';

COMMENT ON COLUMN pmis_execution_delivery_item.stage IS '所属门径阶段: CD1-CD5';

COMMENT ON COLUMN pmis_execution_delivery_item.required IS '是否必交付: 0=否,1=是';

COMMENT ON COLUMN pmis_execution_delivery_item.planned_submit_date IS '计划提交日期';

COMMENT ON COLUMN pmis_execution_delivery_item.actual_submit_date IS '实际提交日期';

COMMENT ON COLUMN pmis_execution_delivery_item.accepted_date IS '验收日期';

COMMENT ON COLUMN pmis_execution_delivery_item.submitter_id IS '提交人 ID';

COMMENT ON COLUMN pmis_execution_delivery_item.submitter_name IS '提交人姓名（冗余）';

COMMENT ON COLUMN pmis_execution_delivery_item.reviewer_id IS '验收人 ID';

COMMENT ON COLUMN pmis_execution_delivery_item.reviewer_name IS '验收人姓名（冗余）';

COMMENT ON COLUMN pmis_execution_delivery_item.review_comment IS '验收意见';

COMMENT ON COLUMN pmis_execution_delivery_item.status IS '交付物状态: PENDING 待提交 / SUBMITTED 已提交 / IN_REVIEW 验收中 / ACCEPTED 已验收 / REJECTED 已驳回 / REVISION 待修订';

COMMENT ON COLUMN pmis_execution_delivery_item.tr_required IS '是否需要技术评审: 0=否,1=是';

COMMENT ON COLUMN pmis_execution_delivery_item.tr_completed IS '技术评审是否完成: 0=否,1=是';

COMMENT ON COLUMN pmis_execution_delivery_item.file_ids IS '关联文件 ID 列表 JSON 数组: 引用 pmis_file_metadata.id';

COMMENT ON COLUMN pmis_execution_delivery_item.remark IS '备注';

COMMENT ON COLUMN pmis_execution_delivery_item.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_execution_delivery_item.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_execution_delivery_item.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_pedi_initiation / idx_pedi_stage / idx_pedi_status)
CREATE INDEX IF NOT EXISTS idx_pedi_tenant_initiation
    ON pmis_execution_delivery_item(tenant_id, initiation_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pedi_tenant_initiation_stage
    ON pmis_execution_delivery_item(tenant_id, initiation_id, stage)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pedi_tenant_status
    ON pmis_execution_delivery_item(tenant_id, status)
    WHERE deleted = 0;

-- =====================================================
-- 5. 项目结项主表 pmis_execution_closure

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_execution_closure(
    id                       VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    closure_code             VARCHAR(64)  NOT NULL,
    initiation_id            VARCHAR(20)       NOT NULL,
    closure_type             VARCHAR(32)  NOT NULL,            -- FORMAL/PRE_CLOSURE/FORCED
    closure_reason           TEXT,
    contract_amount          NUMERIC(15,2) NOT NULL DEFAULT 0,
    received_amount          NUMERIC(15,2) NOT NULL DEFAULT 0,
    received_ratio           NUMERIC(5,4) NOT NULL DEFAULT 0,
    cpi                      NUMERIC(5,2) NOT NULL DEFAULT 1.0,
    spi                      NUMERIC(5,2) NOT NULL DEFAULT 1.0,
    gross_margin             NUMERIC(5,4) NOT NULL DEFAULT 0,
    progress_pct             NUMERIC(5,2) NOT NULL DEFAULT 0,
    total_cost               NUMERIC(15,2) NOT NULL DEFAULT 0,
    warranty_months          INTEGER      NOT NULL DEFAULT 0,
    warranty_start_date      DATE,
    warranty_end_date        DATE,
    planned_archive_date     DATE,
    actual_archive_date      DATE,
    archive_file_ids         VARCHAR(2048) NOT NULL DEFAULT '[]',
    locked                   SMALLINT     NOT NULL DEFAULT 0,
    status                   VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',  -- ClosureStatus
    remark                   TEXT,
    applicant_id             VARCHAR(20),
    applicant_name           VARCHAR(64),
    approver_id              VARCHAR(20),
    approver_name            VARCHAR(64),
    submitted_at             TIMESTAMPTZ,
    approved_at              TIMESTAMPTZ,
    archived_at              TIMESTAMPTZ,
    approval_comment         TEXT,
    tenant_id                VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id        VARCHAR(64)  NOT NULL DEFAULT '',
    created_by               VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by               VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                  SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pec_code              UNIQUE (closure_code, deleted),
    CONSTRAINT ck_pec_closure_type      CHECK (closure_type IN ('FORMAL','PRE_CLOSURE','FORCED')),
    CONSTRAINT ck_pec_status_enum       CHECK (status IN ('DRAFT','SUBMITTED','APPROVING','APPROVED','REJECTED','ARCHIVED')),
    CONSTRAINT ck_pec_locked            CHECK (locked IN (0, 1)),
    CONSTRAINT ck_pec_deleted_enum      CHECK (deleted IN (0, 1)),
    -- 数值/比例范围
    CONSTRAINT ck_pec_received_ratio    CHECK (received_ratio >= 0 AND received_ratio <= 1),
    CONSTRAINT ck_pec_gross_margin      CHECK (gross_margin >= -1 AND gross_margin <= 1),
    CONSTRAINT ck_pec_progress          CHECK (progress_pct >= 0 AND progress_pct <= 100),
    CONSTRAINT ck_pec_warranty_months   CHECK (warranty_months >= 0)
);

COMMENT ON TABLE  pmis_execution_closure IS '项目结项主表: 3 种结项类型（FORMAL/PRE_CLOSURE/FORCED）,由 ClosureAdmissionValidator 按类型校验准入条件';

COMMENT ON COLUMN pmis_execution_closure.closure_code IS '结项单号: 业务唯一,如 PC-2026-001';

COMMENT ON COLUMN pmis_execution_closure.initiation_id IS '所属立项 ID: 一对一关联';

COMMENT ON COLUMN pmis_execution_closure.closure_type IS '结项类型: FORMAL 正式结项 / PRE_CLOSURE 预结项 / FORCED 强制结项';

COMMENT ON COLUMN pmis_execution_closure.closure_reason IS '结项原因';

COMMENT ON COLUMN pmis_execution_closure.contract_amount IS '合同总金额(元)';

COMMENT ON COLUMN pmis_execution_closure.received_amount IS '已回款金额(元)';

COMMENT ON COLUMN pmis_execution_closure.received_ratio IS '回款完成率: 0.85=85%';

COMMENT ON COLUMN pmis_execution_closure.cpi IS '成本绩效指数 CPI: >1 节约,<1 超支,1.0 为基准';

COMMENT ON COLUMN pmis_execution_closure.spi IS '进度绩效指数 SPI: >1 提前,<1 滞后,1.0 为基准';

COMMENT ON COLUMN pmis_execution_closure.gross_margin IS '毛利率: 0.25=25%';

COMMENT ON COLUMN pmis_execution_closure.progress_pct IS '完成进度百分比: 0-100';

COMMENT ON COLUMN pmis_execution_closure.total_cost IS '累计成本(元)';

COMMENT ON COLUMN pmis_execution_closure.warranty_months IS '质保期(月): 0=无质保';

COMMENT ON COLUMN pmis_execution_closure.warranty_start_date IS '质保期开始日期';

COMMENT ON COLUMN pmis_execution_closure.warranty_end_date IS '质保期结束日期';

COMMENT ON COLUMN pmis_execution_closure.planned_archive_date IS '计划归档日期';

COMMENT ON COLUMN pmis_execution_closure.actual_archive_date IS '实际归档日期';

COMMENT ON COLUMN pmis_execution_closure.archive_file_ids IS '归档文件 ID 列表 JSON: 引用 pmis_file_metadata.id';

COMMENT ON COLUMN pmis_execution_closure.locked IS '是否锁定: 0=否,1=是（结项后锁定,防止修改）';

COMMENT ON COLUMN pmis_execution_closure.status IS '结项状态: DRAFT 草稿 / SUBMITTED 已提交 / APPROVING 审批中 / APPROVED 已批准 / REJECTED 已驳回 / ARCHIVED 已归档';

COMMENT ON COLUMN pmis_execution_closure.remark IS '备注';

COMMENT ON COLUMN pmis_execution_closure.applicant_id IS '申请人 ID';

COMMENT ON COLUMN pmis_execution_closure.applicant_name IS '申请人姓名（冗余）';

COMMENT ON COLUMN pmis_execution_closure.approver_id IS '审批人 ID';

COMMENT ON COLUMN pmis_execution_closure.approver_name IS '审批人姓名（冗余）';

COMMENT ON COLUMN pmis_execution_closure.submitted_at IS '提交时间';

COMMENT ON COLUMN pmis_execution_closure.approved_at IS '审批通过时间';

COMMENT ON COLUMN pmis_execution_closure.archived_at IS '归档时间';

COMMENT ON COLUMN pmis_execution_closure.approval_comment IS '审批意见';

COMMENT ON COLUMN pmis_execution_closure.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_execution_closure.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_execution_closure.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_pec_initiation / idx_pec_type_status)
CREATE INDEX IF NOT EXISTS idx_pec_tenant_initiation
    ON pmis_execution_closure(tenant_id, initiation_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pec_tenant_type_status
    ON pmis_execution_closure(tenant_id, closure_type, status)
    WHERE deleted = 0;

-- =====================================================
-- 7. 初始化 8 类项目类型的默认交付物标准（CD1-CD5）
-- =====================================================
INSERT INTO pmis_execution_delivery_standard
    (project_type, project_level, delivery_name, delivery_category, stage, required, trigger_tr, acceptance_criteria, tenant_id)
VALUES
    -- 固定总价 FIXED_PRICE
    ('FIXED_PRICE', NULL, '项目章程',          'DOC',     'CD1_KICKOFF', 1, 0, 'PMO 评审通过', 1),
    ('FIXED_PRICE', NULL, 'WBS 拆解',          'DOC',     'CD2_DESIGN',  1, 0, '事业部评审通过', 1),
    ('FIXED_PRICE', NULL, 'SRS 需求规格',      'DOC',     'CD2_DESIGN',  1, 1, '客户/PMO 评审通过', 1),
    ('FIXED_PRICE', NULL, '系统设计说明书',    'DOC',     'CD2_DESIGN',  1, 1, '架构师评审通过', 1),
    ('FIXED_PRICE', NULL, '核心代码',          'CODE',    'CD3_BUILD',   1, 0, '通过 SonarQube 质量门禁', 1),
    ('FIXED_PRICE', NULL, '测试报告',          'REPORT',  'CD4_UAT',     1, 0, '缺陷清零', 1),
    ('FIXED_PRICE', NULL, '验收报告',          'DOC',     'CD5_GO_LIVE', 1, 0, '客户签字', 1),
    ('FIXED_PRICE', NULL, '运维手册',          'RUNBOOK', 'CD5_GO_LIVE', 1, 0, '运维团队评审', 1),

    -- T&M 人月 T_M
    ('T_M', NULL, '人月报价单',              'DOC',     'CD1_KICKOFF', 1, 0, '客户确认', 1),
    ('T_M', NULL, '人员入场确认',            'DOC',     'CD1_KICKOFF', 1, 0, '客户签字', 1),
    ('T_M', NULL, '月度工作报告',            'REPORT',  'CD4_UAT',     1, 0, '客户确认', 1),
    ('T_M', NULL, '客户确认人天单',          'DOC',     'CD5_GO_LIVE', 1, 0, '双方签字', 1),
    ('T_M', NULL, '服务总结报告',            'REPORT',  'CD5_GO_LIVE', 1, 0, 'PMO 评审', 1),

    -- 人力外包 OUTSOURCING
    ('OUTSOURCING', NULL, '外包人员简历',      'DOC',     'CD1_KICKOFF', 1, 0, '客户资质审核通过', 1),
    ('OUTSOURCING', NULL, '外包合同',          'DOC',     'CD1_KICKOFF', 1, 0, '法务审核通过', 1),
    ('OUTSOURCING', NULL, '人员入场确认',      'DOC',     'CD2_DESIGN',  1, 0, '客户签字', 1),
    ('OUTSOURCING', NULL, '月度人天确认单',    'DOC',     'CD4_UAT',     1, 0, '双方签字', 1),
    ('OUTSOURCING', NULL, '服务总结',          'REPORT',  'CD5_GO_LIVE', 1, 0, 'PMO 评审', 1),

    -- 产品销售 PRODUCT
    ('PRODUCT', NULL, '产品手册',             'DOC',     'CD1_KICKOFF', 1, 0, '产品评审', 1),
    ('PRODUCT', NULL, 'License 授权',         'DOC',     'CD1_KICKOFF', 1, 0, '客户签字', 1),
    ('PRODUCT', NULL, '安装部署报告',         'REPORT',  'CD3_BUILD',   1, 0, '通过部署验证', 1),
    ('PRODUCT', NULL, '用户培训记录',         'REPORT',  'CD4_UAT',     1, 0, '客户签字', 1),
    ('PRODUCT', NULL, '验收报告',             'DOC',     'CD5_GO_LIVE', 1, 0, '客户签字', 1),

    -- 运维服务 MAINTENANCE
    ('MAINTENANCE', NULL, 'SLA 协议',          'DOC',     'CD1_KICKOFF', 1, 0, '双方签字', 1),
    ('MAINTENANCE', NULL, '运维值班表',        'DOC',     'CD2_DESIGN',  1, 0, '团队确认', 1),
    ('MAINTENANCE', NULL, '月度运维报告',      'REPORT',  'CD4_UAT',     1, 0, '客户确认', 1),
    ('MAINTENANCE', NULL, '故障处理报告',      'REPORT',  'CD4_UAT',     0, 0, '客户确认', 1),
    ('MAINTENANCE', NULL, '服务总结',          'REPORT',  'CD5_GO_LIVE', 1, 0, '客户确认', 1),

    -- 咨询服务 CONSULTING
    ('CONSULTING', NULL, '咨询方案',           'DOC',     'CD2_DESIGN',  1, 0, '客户确认', 1),
    ('CONSULTING', NULL, '调研报告',           'REPORT',  'CD2_DESIGN',  1, 0, '客户确认', 1),
    ('CONSULTING', NULL, '诊断报告',           'REPORT',  'CD3_BUILD',   1, 0, '客户确认', 1),
    ('CONSULTING', NULL, '实施咨询报告',       'REPORT',  'CD4_UAT',     1, 0, '客户确认', 1),
    ('CONSULTING', NULL, '咨询总结',           'REPORT',  'CD5_GO_LIVE', 1, 0, '客户签字', 1),

    -- 培训服务 TRAINING
    ('TRAINING', NULL, '培训计划',            'DOC',     'CD1_KICKOFF', 1, 0, '客户确认', 1),
    ('TRAINING', NULL, '培训教材',            'DOC',     'CD2_DESIGN',  1, 0, '产品专家评审', 1),
    ('TRAINING', NULL, '学员考勤表',          'DOC',     'CD3_BUILD',   1, 0, '双方签字', 1),
    ('TRAINING', NULL, '培训效果评估',        'REPORT',  'CD4_UAT',     1, 0, '客户确认', 1),
    ('TRAINING', NULL, '培训结业证书',        'DOC',     'CD5_GO_LIVE', 1, 0, '颁发证书', 1),

    -- 其他 OTHER
    ('OTHER', NULL, '项目章程',               'DOC',     'CD1_KICKOFF', 1, 0, 'PMO 评审', 1),
    ('OTHER', NULL, '交付物清单',             'DOC',     'CD2_DESIGN',  1, 0, '客户确认', 1),
    ('OTHER', NULL, '工作成果',               'DOC',     'CD3_BUILD',   1, 0, '内部评审', 1),
        ('OTHER', NULL, '验收报告',               'DOC',     'CD5_GO_LIVE', 1, 0, '客户签字', 1) ON CONFLICT DO NOTHING;

-- =====================================================
-- 8. 初始化 8 类项目类型的默认合同模板
-- =====================================================
INSERT INTO pmis_project_contract_template
    (template_code, template_name, contract_type, version, payment_terms, default_payment_days, default_penalty_rate, sla_description, deliverables, status, tenant_id)
VALUES
    ('TPL-FIX-001',  '固定总价标准合同',          'FIXED_PRICE',  '1.0.0', '3-3-3-1（预付款30%/启动30%/UAT30%/质保10%）', 30, 0.0010, 'P1 4小时响应/P2 1个工作日/P3 3个工作日', '系统源码/设计文档/验收报告', 'PUBLISHED', 1),
    ('TPL-TM-001',   'T&M 人月标准合同',          'T_M',          '1.0.0', '月结60天（按人天计费）',                          60, 0.0005, '工作日 8 小时响应',                  '月度人天确认单/服务总结',     'PUBLISHED', 1),
    ('TPL-OUT-001',  '人力外包标准合同',          'OUTSOURCING',  '1.0.0', '月结90天',                                        90, 0.0005, 'P1 1小时响应/P2 4小时响应',         '外包人员简历/月度人天单',     'PUBLISHED', 1),
    ('TPL-PRD-001',  '产品销售标准合同',          'PRODUCT',      '1.0.0', '签约付全款（License 永久授权）',                   0,  0.0020, '产品质保期 1 年',                   'License 授权/产品手册',       'PUBLISHED', 1),
    ('TPL-MNT-001',  '运维服务标准合同',          'MAINTENANCE',  '1.0.0', '季付',                                            90, 0.0010, '可用性≥99.9%/故障处理 SLA',         'SLA 协议/月度运维报告',       'PUBLISHED', 1),
    ('TPL-CON-001',  '咨询服务标准合同',          'CONSULTING',   '1.0.0', '5-4-1（启动50%/中期40%/验收10%）',                30, 0.0010, '不限响应时间（按时交付）',          '调研报告/咨询方案/实施报告',   'PUBLISHED', 1),
    ('TPL-TRN-001',  '培训服务标准合同',          'TRAINING',     '1.0.0', '培训前付 50%/结束后 50%',                          0,  0.0010, '培训出勤率≥80%',                    '培训教材/考勤/效果评估',       'PUBLISHED', 1),
        ('TPL-OTH-001',  '通用合同模板',              'OTHER',        '1.0.0', '5-5（启动50%/验收50%）',                            30, 0.0010, '依项目类型',                       '项目章程/交付物清单/验收报告', 'PUBLISHED', 1) ON CONFLICT DO NOTHING;

-- --------------------------------------------------------------------

-- ============================ [012] init pmis finance schema ============================

-- =====================================================
-- PMIS 批次9 DDL：开票/回款/客户信用
-- 版本: V1.0.0_012
-- 描述: 发票主表(pmis_finance_invoice)、回款主表(pmis_finance_payment)、
--       客户信用表(pmis_finance_customer_credit)
-- =====================================================

-- =====================================================
-- 1. 发票主表 pmis_finance_invoice

-- =====================================================
-- P1-6: 已废弃（无需 DROP），标记保留以记录历史。DROP TABLE IF EXISTS pmis_evm_measure; -- 已废弃
CREATE TABLE IF NOT EXISTS pmis_evm_measure(
    id                  VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    initiation_id       VARCHAR(20)       NOT NULL,
    wbs_task_id         VARCHAR(20),                                -- 可空：项目级度量
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
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pem_init_period       UNIQUE (initiation_id, wbs_task_id, period, deleted),
    CONSTRAINT ck_pem_period_fmt        CHECK (period ~ '^\d{4}-(0[1-9]|1[0-2])$'),
    CONSTRAINT ck_pem_alert_level       CHECK (alert_level IN ('NORMAL','YELLOW','RED','INFO')),
    CONSTRAINT ck_pem_amounts_nonneg    CHECK (pv >= 0 AND ev >= 0 AND ac >= 0 AND bac >= 0 AND eac >= 0),
    CONSTRAINT ck_pem_cpi_range         CHECK (cpi > 0 AND cpi <= 10),
    CONSTRAINT ck_pem_spi_range         CHECK (spi > 0 AND spi <= 10),
    CONSTRAINT ck_pem_tcpi_range        CHECK (tcpi > 0),
    CONSTRAINT ck_pem_deleted_enum      CHECK (deleted IN (0, 1))
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

-- 复合/部分索引(替代零散的 idx_pem_initiation / idx_pem_wbs / idx_pem_period / idx_pem_alert)
CREATE INDEX IF NOT EXISTS idx_pem_tenant_initiation_measure_date
    ON pmis_evm_measure(tenant_id, initiation_id, measure_date DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pem_tenant_wbs_measure_date
    ON pmis_evm_measure(tenant_id, wbs_task_id, measure_date DESC)
    WHERE deleted = 0 AND wbs_task_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pem_tenant_alert_measure_date
    ON pmis_evm_measure(tenant_id, alert_level, measure_date DESC)
    WHERE deleted = 0 AND alert_level IN ('YELLOW','RED');

-- =====================================================
-- 2. 对外报价费率表 pmis_rate_card

-- =====================================================
-- P1-6: 已废弃（无需 DROP），标记保留以记录历史。DROP TABLE IF EXISTS pmis_rate_card; -- 已废弃
CREATE TABLE IF NOT EXISTS pmis_rate_card(
    id                  VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
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
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_prc_code              UNIQUE (rate_code, deleted),
    CONSTRAINT ck_prc_billing_unit      CHECK (billing_unit IN ('DAY','HOUR')),
    CONSTRAINT ck_prc_project_type      CHECK (project_type IS NULL OR project_type IN ('FIXED_PRICE','T_M','OUTSOURCING','PRODUCT','MAINTENANCE','CONSULTING','TRAINING','OTHER')),
    CONSTRAINT ck_prc_customer_level    CHECK (customer_level IS NULL OR customer_level IN ('A','B','C','D')),
    CONSTRAINT ck_prc_status_enum       CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_prc_rate_nonneg       CHECK (rate_amount >= 0),
    CONSTRAINT ck_prc_expiry_after_eff  CHECK (expiry_date IS NULL OR expiry_date >= effective_date),
    CONSTRAINT ck_prc_deleted_enum      CHECK (deleted IN (0, 1))
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

-- 复合/部分索引(替代零散的 idx_prc_level / idx_prc_status)
-- 主要查询: tenant + level + project_type + customer_level + 在效期内 + ACTIVE
CREATE INDEX IF NOT EXISTS idx_prc_tenant_level_match
    ON pmis_rate_card(tenant_id, level_code, project_type, customer_level)
    WHERE deleted = 0 AND status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_prc_tenant_status_effective
    ON pmis_rate_card(tenant_id, status, effective_date DESC)
    WHERE deleted = 0;

-- =====================================================
-- 3. 对内成本费率表 pmis_rate_internal

-- =====================================================
-- P1-6: 已废弃,无需 DROP
CREATE TABLE IF NOT EXISTS pmis_rate_internal(
    id                  VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    rate_code           VARCHAR(64)  NOT NULL,
    level_code          VARCHAR(16)  NOT NULL,
    department_id       VARCHAR(20),                                -- 事业部/部门
    department_name     VARCHAR(256),
    billing_unit        VARCHAR(16)  NOT NULL DEFAULT 'DAY',
    cost_amount         NUMERIC(15,2) NOT NULL DEFAULT 0,
    currency            VARCHAR(16)  NOT NULL DEFAULT 'CNY',
    effective_date      DATE         NOT NULL,
    expiry_date         DATE,
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    remark              TEXT,
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pri_code              UNIQUE (rate_code, deleted),
    CONSTRAINT ck_pri_billing_unit      CHECK (billing_unit IN ('DAY','HOUR')),
    CONSTRAINT ck_pri_status_enum       CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_pri_cost_nonneg       CHECK (cost_amount >= 0),
    CONSTRAINT ck_pri_expiry_after_eff  CHECK (expiry_date IS NULL OR expiry_date >= effective_date),
    CONSTRAINT ck_pri_deleted_enum      CHECK (deleted IN (0, 1))
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

-- 复合/部分索引(替代零散的 idx_pri_level_dept / idx_pri_status)
CREATE INDEX IF NOT EXISTS idx_pri_tenant_level_dept
    ON pmis_rate_internal(tenant_id, level_code, department_id)
    WHERE deleted = 0 AND status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_pri_tenant_status_effective
    ON pmis_rate_internal(tenant_id, status, effective_date DESC)
    WHERE deleted = 0;

-- =====================================================
-- 4. 利润测算版本表 pmis_profit_simulation

-- ============================================================

-- ----------------------------
-- 1) 质保期
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_warranty (
    id                  VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    warranty_code       VARCHAR(64)  NOT NULL,
    initiation_id       VARCHAR(20)       NOT NULL,
    contract_id         VARCHAR(20),
    project_type        VARCHAR(32),
    project_level       VARCHAR(8),
    start_date          DATE         NOT NULL,
    end_date            DATE         NOT NULL,
    duration_months     INT          NOT NULL DEFAULT 12,
    notice_days         INT          NOT NULL DEFAULT 30,
    notice_sent         BOOLEAN      NOT NULL DEFAULT FALSE,
    notice_sent_at      TIMESTAMPTZ,
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    terminated_at       TIMESTAMPTZ,
    terminated_reason   VARCHAR(256),
    contact_name        VARCHAR(64),
    contact_phone       VARCHAR(32),
    remark              VARCHAR(512),
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id   VARCHAR(64),
    created_by          VARCHAR(20),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_warranty_code           UNIQUE (warranty_code, deleted),
    CONSTRAINT ck_warranty_status         CHECK (status IN ('ACTIVE','EXPIRING','EXPIRED','TERMINATED')),
    CONSTRAINT ck_warranty_project_type   CHECK (project_type IS NULL OR project_type IN ('FIXED_PRICE','T_M','OUTSOURCING','PRODUCT','MAINTENANCE','CONSULTING','TRAINING','OTHER')),
    CONSTRAINT ck_warranty_duration       CHECK (duration_months >= 0),
    CONSTRAINT ck_warranty_notice_days    CHECK (notice_days >= 0),
    CONSTRAINT ck_warranty_end_after      CHECK (end_date >= start_date),
    CONSTRAINT ck_warranty_deleted        CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_warranty IS '项目质保期表: 项目结项后自动创建,到期前 N 天提醒客户,status 状态机 ACTIVE/EXPIRING/EXPIRED/TERMINATED';

COMMENT ON COLUMN pmis_warranty.warranty_code IS '质保单号: 业务唯一,如 WAR-2026-001';

COMMENT ON COLUMN pmis_warranty.initiation_id IS '所属立项 ID';

COMMENT ON COLUMN pmis_warranty.contract_id IS '所属合同 ID';

COMMENT ON COLUMN pmis_warranty.project_type IS '项目类型: 冗余字段';

COMMENT ON COLUMN pmis_warranty.project_level IS '项目级别: L1-L18,冗余字段';

COMMENT ON COLUMN pmis_warranty.start_date IS '质保开始日期';

COMMENT ON COLUMN pmis_warranty.end_date IS '质保结束日期';

COMMENT ON COLUMN pmis_warranty.duration_months IS '质保期时长(月): 默认 12';

COMMENT ON COLUMN pmis_warranty.notice_days IS '提前提醒天数: 到期前 N 天发通知,默认 30';

COMMENT ON COLUMN pmis_warranty.notice_sent IS '是否已发送到期提醒: true=已发';

COMMENT ON COLUMN pmis_warranty.notice_sent_at IS '提醒发送时间';

COMMENT ON COLUMN pmis_warranty.status IS '状态: ACTIVE 生效中 / EXPIRING 即将到期 / EXPIRED 已到期 / TERMINATED 已终止';

COMMENT ON COLUMN pmis_warranty.terminated_at IS '终止时间';

COMMENT ON COLUMN pmis_warranty.terminated_reason IS '终止原因';

COMMENT ON COLUMN pmis_warranty.contact_name IS '客户联系人姓名';

COMMENT ON COLUMN pmis_warranty.contact_phone IS '客户联系人电话';

COMMENT ON COLUMN pmis_warranty.remark IS '备注';

COMMENT ON COLUMN pmis_warranty.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_warranty.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_warranty.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_warranty_*)
CREATE INDEX IF NOT EXISTS idx_warranty_tenant_initiation
    ON pmis_warranty(tenant_id, initiation_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_warranty_tenant_status_end
    ON pmis_warranty(tenant_id, status, end_date)
    WHERE deleted = 0;

-- ----------------------------
-- 2) 运维工单
-- ----------------------------

CREATE TABLE IF NOT EXISTS pmis_ops_ticket (
    id                  VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    ticket_code         VARCHAR(64)  NOT NULL,
    initiation_id       VARCHAR(20)       NOT NULL,
    warranty_id         VARCHAR(20),
    title               VARCHAR(128) NOT NULL,
    description         TEXT,
    category            VARCHAR(32)  NOT NULL DEFAULT 'OTHER',
    priority            VARCHAR(8)   NOT NULL DEFAULT 'P3',
    status              VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    reporter_id         VARCHAR(20),
    reporter_name       VARCHAR(64),
    reporter_phone      VARCHAR(32),
    assignee_id         VARCHAR(20),
    assignee_name       VARCHAR(64),
    accepted_at         TIMESTAMPTZ,
    started_at          TIMESTAMPTZ,
    resolved_at         TIMESTAMPTZ,
    closed_at           TIMESTAMPTZ,
    response_due_at     TIMESTAMPTZ  NOT NULL,
    resolve_due_at      TIMESTAMPTZ  NOT NULL,
    response_breached   BOOLEAN      NOT NULL DEFAULT FALSE,
    resolve_breached    BOOLEAN      NOT NULL DEFAULT FALSE,
    resolution_note     TEXT,
    customer_score      INT,
    customer_comment    VARCHAR(512),
    file_ids            VARCHAR(1024),
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id   VARCHAR(64),
    created_by          VARCHAR(20),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_ops_ticket_code           UNIQUE (ticket_code, deleted),
    CONSTRAINT ck_ops_category              CHECK (category IN ('BUG','CHANGE','CONSULT','COMPLAINT','OTHER')),
    CONSTRAINT ck_ops_priority              CHECK (priority IN ('P1','P2','P3','P4')),
    CONSTRAINT ck_ops_status                CHECK (status IN ('OPEN','ACCEPTED','IN_PROGRESS','RESOLVED','CLOSED','CANCELLED')),
    CONSTRAINT ck_ops_customer_score        CHECK (customer_score IS NULL OR customer_score BETWEEN 1 AND 5),
    CONSTRAINT ck_ops_due_order             CHECK (resolve_due_at >= response_due_at),
    CONSTRAINT ck_ops_deleted               CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_ops_ticket IS '运维工单表: 项目售后全流程闭环,SLA 跟踪（P1 4h/P2 1d/P3 3d/P4 7d）';

COMMENT ON COLUMN pmis_ops_ticket.ticket_code IS '工单号: 业务唯一,如 TK-2026-001';

COMMENT ON COLUMN pmis_ops_ticket.initiation_id IS '所属立项 ID';

COMMENT ON COLUMN pmis_ops_ticket.warranty_id IS '所属质保期 ID';

COMMENT ON COLUMN pmis_ops_ticket.title IS '工单标题';

COMMENT ON COLUMN pmis_ops_ticket.description IS '问题描述';

COMMENT ON COLUMN pmis_ops_ticket.category IS '工单类别: BUG 缺陷 / CHANGE 变更 / CONSULT 咨询 / COMPLAINT 投诉 / OTHER 其他';

COMMENT ON COLUMN pmis_ops_ticket.priority IS '优先级: P1 紧急 / P2 高 / P3 中 / P4 低';

COMMENT ON COLUMN pmis_ops_ticket.status IS '工单状态: OPEN 待受理 / ACCEPTED 已受理 / IN_PROGRESS 处理中 / RESOLVED 已解决 / CLOSED 已关闭 / CANCELLED 已取消';

COMMENT ON COLUMN pmis_ops_ticket.reporter_id IS '报修人 ID';

COMMENT ON COLUMN pmis_ops_ticket.reporter_name IS '报修人姓名（冗余）';

COMMENT ON COLUMN pmis_ops_ticket.reporter_phone IS '报修人电话';

COMMENT ON COLUMN pmis_ops_ticket.assignee_id IS '处理人 ID';

COMMENT ON COLUMN pmis_ops_ticket.assignee_name IS '处理人姓名（冗余）';

COMMENT ON COLUMN pmis_ops_ticket.accepted_at IS '受理时间';

COMMENT ON COLUMN pmis_ops_ticket.started_at IS '开始处理时间';

COMMENT ON COLUMN pmis_ops_ticket.resolved_at IS '解决时间';

COMMENT ON COLUMN pmis_ops_ticket.closed_at IS '关闭时间';

COMMENT ON COLUMN pmis_ops_ticket.response_due_at IS '响应 SLA 截止时间';

COMMENT ON COLUMN pmis_ops_ticket.resolve_due_at IS '解决 SLA 截止时间';

COMMENT ON COLUMN pmis_ops_ticket.response_breached IS '响应 SLA 是否超期: true=超期';

COMMENT ON COLUMN pmis_ops_ticket.resolve_breached IS '解决 SLA 是否超期: true=超期';

COMMENT ON COLUMN pmis_ops_ticket.resolution_note IS '解决方案说明';

COMMENT ON COLUMN pmis_ops_ticket.customer_score IS '客户评分: 1-5';

COMMENT ON COLUMN pmis_ops_ticket.customer_comment IS '客户评价';

COMMENT ON COLUMN pmis_ops_ticket.file_ids IS '附件 ID 列表: 引用 pmis_file_metadata.id';

COMMENT ON COLUMN pmis_ops_ticket.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_ops_ticket.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_ops_ticket.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_ops_*)
CREATE INDEX IF NOT EXISTS idx_ops_tenant_initiation
    ON pmis_ops_ticket(tenant_id, initiation_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ops_tenant_warranty
    ON pmis_ops_ticket(tenant_id, warranty_id)
    WHERE deleted = 0 AND warranty_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ops_tenant_status
    ON pmis_ops_ticket(tenant_id, status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ops_tenant_assignee_status
    ON pmis_ops_ticket(tenant_id, assignee_id, status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ops_tenant_priority_status
    ON pmis_ops_ticket(tenant_id, priority, status)
    WHERE deleted = 0;

-- ----------------------------
-- 3) 满意度评价
-- ----------------------------

CREATE TABLE IF NOT EXISTS pmis_satisfaction (
    id                  VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    survey_code         VARCHAR(64)  NOT NULL,
    initiation_id       VARCHAR(20)       NOT NULL,
    ticket_id           VARCHAR(20),
    warranty_id         VARCHAR(20),
    score               INT          NOT NULL,
    level               VARCHAR(16)  NOT NULL,
    professionalism     INT,
    timeliness          INT,
    quality             INT,
    attitude            INT,
    comments            VARCHAR(1024),
    suggest             VARCHAR(1024),
    anonymous           BOOLEAN      NOT NULL DEFAULT FALSE,
    evaluator_id        VARCHAR(20),
    evaluator_name      VARCHAR(64),
    evaluated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    follow_up           BOOLEAN      NOT NULL DEFAULT FALSE,
    follow_up_note      VARCHAR(512),
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id   VARCHAR(64),
    created_by          VARCHAR(20),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_satisfaction_code       UNIQUE (survey_code, deleted),
    CONSTRAINT ck_satisfaction_level      CHECK (level IN ('VERY_SATISFIED','SATISFIED','NEUTRAL','UNSATISFIED','VERY_UNSATISFIED')),
    CONSTRAINT ck_satisfaction_score      CHECK (score BETWEEN 1 AND 5),
    CONSTRAINT ck_satisfaction_pro        CHECK (professionalism IS NULL OR professionalism BETWEEN 1 AND 5),
    CONSTRAINT ck_satisfaction_timeliness CHECK (timeliness      IS NULL OR timeliness      BETWEEN 1 AND 5),
    CONSTRAINT ck_satisfaction_quality    CHECK (quality         IS NULL OR quality         BETWEEN 1 AND 5),
    CONSTRAINT ck_satisfaction_attitude   CHECK (attitude        IS NULL OR attitude        BETWEEN 1 AND 5),
    CONSTRAINT ck_satisfaction_deleted    CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_satisfaction IS '服务满意度评价表: 工单关闭/质保期结束可触发,4 维度评分（专业/及时/质量/态度）';

COMMENT ON COLUMN pmis_satisfaction.survey_code IS '评价单号: 业务唯一,如 SAT-2026-001';

COMMENT ON COLUMN pmis_satisfaction.initiation_id IS '所属立项 ID';

COMMENT ON COLUMN pmis_satisfaction.ticket_id IS '关联工单 ID: 工单触发的评价';

COMMENT ON COLUMN pmis_satisfaction.warranty_id IS '关联质保期 ID: 质保期触发的评价';

COMMENT ON COLUMN pmis_satisfaction.score IS '总评分: 1-5';

COMMENT ON COLUMN pmis_satisfaction.level IS '评价等级: VERY_SATISFIED 非常满意 / SATISFIED 满意 / NEUTRAL 一般 / UNSATISFIED 不满意 / VERY_UNSATISFIED 非常不满意';

COMMENT ON COLUMN pmis_satisfaction.professionalism IS '专业度评分: 1-5';

COMMENT ON COLUMN pmis_satisfaction.timeliness IS '及时性评分: 1-5';

COMMENT ON COLUMN pmis_satisfaction.quality IS '质量评分: 1-5';

COMMENT ON COLUMN pmis_satisfaction.attitude IS '态度评分: 1-5';

COMMENT ON COLUMN pmis_satisfaction.comments IS '评价内容';

COMMENT ON COLUMN pmis_satisfaction.suggest IS '改进建议';

COMMENT ON COLUMN pmis_satisfaction.anonymous IS '是否匿名: true=匿名';

COMMENT ON COLUMN pmis_satisfaction.evaluator_id IS '评价人 ID';

COMMENT ON COLUMN pmis_satisfaction.evaluator_name IS '评价人姓名（冗余）';

COMMENT ON COLUMN pmis_satisfaction.evaluated_at IS '评价时间';

COMMENT ON COLUMN pmis_satisfaction.follow_up IS '是否需要回访: true=需要';

COMMENT ON COLUMN pmis_satisfaction.follow_up_note IS '回访记录';

COMMENT ON COLUMN pmis_satisfaction.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_satisfaction.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_satisfaction.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_satisfaction_*)
CREATE INDEX IF NOT EXISTS idx_satisfaction_tenant_initiation
    ON pmis_satisfaction(tenant_id, initiation_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_satisfaction_tenant_ticket
    ON pmis_satisfaction(tenant_id, ticket_id)
    WHERE deleted = 0 AND ticket_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_satisfaction_tenant_level
    ON pmis_satisfaction(tenant_id, level)
    WHERE deleted = 0;

-- --------------------------------------------------------------------

-- ============================ [018] init pmis smart p4 2 schema ============================

-- ============================================================
-- V1.0.0_018  智能化升级 P4-1/P4-2/P4-3  脚本
-- ============================================================
-- 说明：批次 15 智能化升级-系统内部数据管理（PRD 4.2）
-- 1) 工时表新增 billable 字段（可计费标识）
-- 2) 预警分级推送表 pmis_alert_dispatch（DDL 见文件末尾 P1-10，从 V1.0.0_cronjob.sql 迁入）
-- 3) 每日对账表 pmis_reconcile_daily

-- ====================================================================

CREATE TABLE IF NOT EXISTS pmis_billable_utilization_snapshot (
    id               VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    period           VARCHAR(7)  NOT NULL,                          -- yyyy-MM
    employee_id      VARCHAR(20)      NOT NULL,
    employee_name    VARCHAR(64) DEFAULT '',
    level_code       VARCHAR(16) DEFAULT '',                        -- L1-L18
    department       VARCHAR(64) DEFAULT '',
    total_hours      NUMERIC(12,2) NOT NULL DEFAULT 0,             -- 全部工时
    billable_hours   NUMERIC(12,2) NOT NULL DEFAULT 0,             -- 可计费
    overtime_hours   NUMERIC(12,2) NOT NULL DEFAULT 0,
    leave_hours      NUMERIC(12,2) NOT NULL DEFAULT 0,
    training_hours   NUMERIC(12,2) NOT NULL DEFAULT 0,
    bench_hours      NUMERIC(12,2) NOT NULL DEFAULT 0,             -- 闲置
    utilization_pct  NUMERIC(6,4)  NOT NULL DEFAULT 0,             -- 0-1
    grade            VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',        -- EXCELLENT/GOOD/NORMAL/WARN/CRITICAL
    range_from       DATE         NOT NULL,
    range_to         DATE         NOT NULL,
    snapshot_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source           VARCHAR(16)  NOT NULL DEFAULT 'CRONJOB',    -- CRONJOB / MANUAL / RETRO
    tenant_id        VARCHAR(20)       DEFAULT 1,
    deleted          SMALLINT     NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT uq_billable_period_emp UNIQUE (period, employee_id, deleted),
    CONSTRAINT ck_bill_period_fmt     CHECK (period ~ '^\d{4}-(0[1-9]|1[0-2])$'),
    CONSTRAINT ck_bill_grade_enum     CHECK (grade IN ('EXCELLENT','GOOD','NORMAL','WARN','CRITICAL')),
    CONSTRAINT ck_bill_source_enum    CHECK (source IN ('CRONJOB','MANUAL','RETRO')),
    CONSTRAINT ck_bill_hours_nonneg   CHECK (total_hours >= 0 AND billable_hours >= 0 AND overtime_hours >= 0
                                              AND leave_hours >= 0 AND training_hours >= 0 AND bench_hours >= 0),
    CONSTRAINT ck_bill_billable_le_total CHECK (billable_hours <= total_hours),
    CONSTRAINT ck_bill_util_range     CHECK (utilization_pct >= 0 AND utilization_pct <= 1),
    CONSTRAINT ck_bill_range_order    CHECK (range_to >= range_from),
    CONSTRAINT ck_bill_level_fmt      CHECK (level_code IS NULL OR level_code ~ '^L([1-9]|1[0-8])$'),
    CONSTRAINT ck_bill_deleted        CHECK (deleted IN (0, 1))
);

-- 复合/部分索引(替代零散的单列索引,引入 tenant_id + WHERE deleted=0 优化)
CREATE INDEX IF NOT EXISTS idx_billable_tenant_period
    ON pmis_billable_utilization_snapshot (tenant_id, period DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_billable_tenant_dept_period
    ON pmis_billable_utilization_snapshot (tenant_id, department, period)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_billable_tenant_grade_period
    ON pmis_billable_utilization_snapshot (tenant_id, grade, period)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_billable_tenant_level_period
    ON pmis_billable_utilization_snapshot (tenant_id, level_code, period)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_billable_tenant_range
    ON pmis_billable_utilization_snapshot (tenant_id, range_from, range_to)
    WHERE deleted = 0;

COMMENT ON TABLE  pmis_billable_utilization_snapshot IS '可计费利用率快照表: cronjob 每日计算并持久化,驾驶舱/排行榜/趋势分析均读快照,避免实时聚合大表';

COMMENT ON COLUMN pmis_billable_utilization_snapshot.period IS '统计周期: 格式 yyyy-MM,例如 2026-06';

COMMENT ON COLUMN pmis_billable_utilization_snapshot.employee_id IS '员工 ID';

COMMENT ON COLUMN pmis_billable_utilization_snapshot.employee_name IS '员工姓名（冗余）';

COMMENT ON COLUMN pmis_billable_utilization_snapshot.level_code IS '职级: L1-L18';

COMMENT ON COLUMN pmis_billable_utilization_snapshot.department IS '所属部门: 冗余字段';

COMMENT ON COLUMN pmis_billable_utilization_snapshot.total_hours IS '全部工时(小时)';

COMMENT ON COLUMN pmis_billable_utilization_snapshot.billable_hours IS '可计费工时(小时): 仅 billable=1 的工时';

COMMENT ON COLUMN pmis_billable_utilization_snapshot.overtime_hours IS '加班工时(小时)';

COMMENT ON COLUMN pmis_billable_utilization_snapshot.leave_hours IS '请假工时(小时)';

COMMENT ON COLUMN pmis_billable_utilization_snapshot.training_hours IS '培训工时(小时): training window 30 天上限';

COMMENT ON COLUMN pmis_billable_utilization_snapshot.bench_hours IS 'Bench 闲置工时(小时)';

COMMENT ON COLUMN pmis_billable_utilization_snapshot.utilization_pct IS '可计费利用率: 0-1,billable_hours / total_hours';

COMMENT ON COLUMN pmis_billable_utilization_snapshot.grade IS '考核等级: EXCELLENT 优秀(>=0.9) / GOOD 良好(>=0.75) / NORMAL 正常(>=0.6) / WARN 警告(>=0.4) / CRITICAL 严重(<0.4)';

COMMENT ON COLUMN pmis_billable_utilization_snapshot.range_from IS '统计区间开始日期';

COMMENT ON COLUMN pmis_billable_utilization_snapshot.range_to IS '统计区间结束日期';

COMMENT ON COLUMN pmis_billable_utilization_snapshot.snapshot_at IS '快照生成时间';

COMMENT ON COLUMN pmis_billable_utilization_snapshot.source IS '数据来源: SCHEDULER 定时任务 / MANUAL 手动 / RETRO 追溯重算';

COMMENT ON COLUMN pmis_billable_utilization_snapshot.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_billable_utilization_snapshot.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- --------------------------------------------------------------------

-- ============================ [024] add version to core tables ============================

-- ========================================================
-- P1-12 乐观锁（@Version）覆盖核心实体
--
-- 为 10 张核心业务表添加 version 列，配合 MyBatis-Plus
-- OptimisticLockerInnerInterceptor 实现乐观锁控制。
--
-- 涉及表：
--   pmis_project 项目域：initiation / contract / contract_change / project_change
--   pmis_finance 财务域：invoice / payment / customer_credit
--   pmis_execution 执行域：wbs_task / purchase / ops_ticket
--
-- 默认值 0：所有现有记录初始版本号为 0，下一次 UPDATE 时自动 +1。
-- NOT NULL 约束：避免 NULL 导致乐观锁失效。
-- ========================================================

-- ========== 项目域 ==========
ALTER TABLE pmis_project_initiation
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pmis_project_contract
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pmis_project_contract_change
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pmis_project_change
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

-- ========== 财务域 ==========
-- 早期版本误加 pmis_finance. schema 前缀，但所有表均建在 public schema
-- （与上方 project/execution 域的写法保持一致），执行时会报
-- "模式 pmis_finance 不存在" 错误，故去除 schema 前缀。
ALTER TABLE pmis_finance_invoice
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pmis_finance_payment
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pmis_finance_customer_credit
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

-- ========== 执行域 ==========
-- 早期版本把 pmis_cost_purchase 误写为 pmis_execution_purchase,
-- 把 pmis_ops_ticket 误写为 pmis_execution_ops_ticket。修正为实际
-- 表名（@TableName 定义）以避免 "关系不存在" 错误。
ALTER TABLE pmis_execution_wbs_task
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pmis_cost_purchase
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pmis_ops_ticket
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

-- ========== 同步更新 init schema 脚本中的字段注释（仅文档作用，不影响运行） ==========
COMMENT ON COLUMN pmis_project_initiation.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';

COMMENT ON COLUMN pmis_project_contract.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';

COMMENT ON COLUMN pmis_project_contract_change.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';

COMMENT ON COLUMN pmis_project_change.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';

COMMENT ON COLUMN pmis_finance_invoice.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';

COMMENT ON COLUMN pmis_finance_payment.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';

COMMENT ON COLUMN pmis_finance_customer_credit.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';

COMMENT ON COLUMN pmis_execution_wbs_task.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';

COMMENT ON COLUMN pmis_cost_purchase.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';

COMMENT ON COLUMN pmis_ops_ticket.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';

-- 2. 执行链路追踪表

CREATE TABLE IF NOT EXISTS pmis_alert_dispatch (
    id                  VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    alert_code          VARCHAR(64)  NOT NULL UNIQUE,
    alert_type          VARCHAR(32)  NOT NULL,
    alert_level         VARCHAR(8)   NOT NULL,
    source_type         VARCHAR(32)  NOT NULL,
    source_id           VARCHAR(20),
    title               VARCHAR(256) NOT NULL,
    content             TEXT,
    target_role         VARCHAR(64),
    target_user_ids     VARCHAR(1024),
    push_channels       VARCHAR(64)  NOT NULL DEFAULT 'INAPP',
    -- [P3-1-merge] cronjob 告警专用字段（source_type='CRONJOB' 时使用）
    rule_id             VARCHAR(20),
    trigger_log_id      VARCHAR(20),
    trigger_value       VARCHAR(128),
    threshold           BIGINT,
    dispatched_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dispatched_by       VARCHAR(64),
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    sent_at             TIMESTAMPTZ,
    fail_reason         VARCHAR(512),
    retry_count         INT          NOT NULL DEFAULT 0,
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id   VARCHAR(64),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    -- 枚举约束（P3-1-merge: 扩展支持 cronjob 告警类型和级别）
    CONSTRAINT ck_pad_alert_type       CHECK (alert_type  IN ('BUDGET','EVM','SLA','RISK','PROFIT','BENCH','UTILIZATION','OTHER','FAIL','TIMEOUT','SLOW','FAIL_RATE','DURATION_P95')),
    CONSTRAINT ck_pad_alert_level      CHECK (alert_level IN ('YELLOW','RED','INFO','WARN','ERROR','CRITICAL')),
    CONSTRAINT ck_pad_source_type      CHECK (source_type IN ('PROJECT','EVM','TICKET','BENCH','CONFIG','OTHER','CRONJOB')),
    CONSTRAINT ck_pad_push_channels    CHECK (push_channels ~ '^(INAPP|EMAIL|SMS|WECHAT|DINGTALK|WECOM|WEBHOOK)(,(INAPP|EMAIL|SMS|WECHAT|DINGTALK|WECOM|WEBHOOK))*$'),
    CONSTRAINT ck_pad_status_enum      CHECK (status IN ('PENDING','SENT','FAILED','RETRYING','SUCCESS','PARTIAL','SUCCESS_RECOVERY','PARTIAL_RECOVERY','FAILED_RECOVERY')),
    CONSTRAINT ck_pad_retry_count      CHECK (retry_count >= 0 AND retry_count <= 10),
    CONSTRAINT ck_pad_deleted          CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_alert_dispatch IS '预警分级推送表: 黄/红不同层级触达,失败自动重试（最大 3 次,硬上限 10 次）';

COMMENT ON COLUMN pmis_alert_dispatch.alert_code IS '预警编码: 业务唯一,如 ALERT-2026-001';

COMMENT ON COLUMN pmis_alert_dispatch.alert_type IS '预警类型: BUDGET 预算 / EVM 挣值 / SLA 工单 / RISK 风险 / PROFIT 利润 / BENCH 闲置 / UTILIZATION 利用率 / OTHER 其他';

COMMENT ON COLUMN pmis_alert_dispatch.alert_level IS '预警等级: YELLOW 黄色 / RED 红色';

COMMENT ON COLUMN pmis_alert_dispatch.source_type IS '触发源类型: PROJECT/EVM/TICKET/BENCH/CONFIG/OTHER';

COMMENT ON COLUMN pmis_alert_dispatch.source_id IS '触发源业务 ID';

COMMENT ON COLUMN pmis_alert_dispatch.title IS '预警标题';

COMMENT ON COLUMN pmis_alert_dispatch.content IS '预警内容（已渲染的模板）';

COMMENT ON COLUMN pmis_alert_dispatch.target_role IS '目标角色: PM/PMO/CFO 等';

COMMENT ON COLUMN pmis_alert_dispatch.target_user_ids IS '目标用户 ID 列表: 逗号分隔,精确触达';

COMMENT ON COLUMN pmis_alert_dispatch.push_channels IS '推送渠道: INAPP 站内信 / EMAIL 邮件 / SMS 短信 / WECHAT 微信,逗号分隔';

COMMENT ON COLUMN pmis_alert_dispatch.dispatched_at IS '派发时间';

COMMENT ON COLUMN pmis_alert_dispatch.dispatched_by IS '派发人: 定时任务 / 系统 / 用户';

COMMENT ON COLUMN pmis_alert_dispatch.status IS '发送状态: PENDING 待发送 / SENT 已发送 / FAILED 失败 / RETRYING 重试中';

COMMENT ON COLUMN pmis_alert_dispatch.sent_at IS '发送成功时间';

COMMENT ON COLUMN pmis_alert_dispatch.fail_reason IS '失败原因';

COMMENT ON COLUMN pmis_alert_dispatch.retry_count IS '重试次数: 业务最大 3 次,硬上限 10 次';

COMMENT ON COLUMN pmis_alert_dispatch.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_alert_dispatch.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_alert_dispatch.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的单列索引)
CREATE INDEX IF NOT EXISTS idx_pad_tenant_level_status
    ON pmis_alert_dispatch(tenant_id, alert_level, status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pad_tenant_type_dispatched
    ON pmis_alert_dispatch(tenant_id, alert_type, dispatched_at DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pad_tenant_source
    ON pmis_alert_dispatch(tenant_id, source_type, source_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pad_tenant_target
    ON pmis_alert_dispatch(tenant_id, target_role)
    WHERE deleted = 0;

-- 分散索引（从 V1.0.0_cronjob.sql 迁入）
CREATE INDEX IF NOT EXISTS idx_pmis_alert_dispatch_recipient
    ON pmis_alert_dispatch (target_role, sent_at DESC)
    WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX IF NOT EXISTS idx_pmis_alert_dispatch_retry
    ON pmis_alert_dispatch (retry_count, sent_at DESC)
    WHERE status = 'FAILED' AND retry_count < 3;

ANALYZE pmis_alert_dispatch;

CREATE INDEX IF NOT EXISTS idx_pmis_alert_dispatch_trace
    ON pmis_alert_dispatch (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

