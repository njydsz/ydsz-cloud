-- ============================================================
-- PMIS project module SQL
-- 项目执行服务 (ydsz-pmis-project, port 9003)
-- ============================================================
-- 本脚本 DDL 对应后端 project 服务的 Mapper / DO,
--   物理 Mapper 实际所在模块即表归属。
--
-- 表归属依据: ydsz-pmis-project/src/main/java/.../infra/mapper/
-- 表数量: 34 张 (20 project + 6 sales + 8 finance)
-- 2026-07-16 合并 sales/finance 模块,sales 的 6 张表已是 pmis_project_* 前缀,
--            finance 的 8 张表已重命名为 pmis_project_* 前缀
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
-- 5. 费用报销表 pmis_project_expense

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
-- 描述: 发票主表(pmis_project_invoice)、回款主表(pmis_project_payment)、
--       客户信用表(pmis_project_customer_credit)
-- =====================================================

-- =====================================================
-- 1. 发票主表 pmis_project_invoice

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
-- 4. 利润测算版本表 pmis_project_profit_simulation

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
-- 3) 每日对账表 pmis_project_reconcile_daily

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
ALTER TABLE pmis_project_invoice
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pmis_project_payment
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE pmis_project_customer_credit
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

COMMENT ON COLUMN pmis_project_invoice.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';

COMMENT ON COLUMN pmis_project_payment.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';

COMMENT ON COLUMN pmis_project_customer_credit.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';

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

-- ============================================================
-- [MERGED FROM V1.0.0_sales.sql] sales 模块 SQL (6 张表,表名已是 pmis_project_* 前缀)
-- ============================================================

-- ============================================================
-- PMIS sales module SQL
-- 商务销售服务 (ydsz-pmis-sales, port 9010)
-- ============================================================
-- 本脚本 DDL 对应后端 sales 服务的 Mapper / DO,
--   物理 Mapper 实际所在模块即表归属。跨服务引用禁止直连,统一走
--   Feign Client (SalesDataClient / FinanceDataClient)。
--
-- 表归属依据: ydsz-pmis-sales/src/main/java/.../infra/mapper/
-- 表数量: 6 张
-- --------------------------------------------------------------------

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_project_opportunity(
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    opportunity_code  VARCHAR(64)    NOT NULL,
    opportunity_name  VARCHAR(256)   NOT NULL,
    customer_id       VARCHAR(20)         NOT NULL,
    customer_name     VARCHAR(256),
    business_dept_id  VARCHAR(20),
    owner_id          VARCHAR(20)         NOT NULL,
    owner_name        VARCHAR(64),
    level             VARCHAR(8)     NOT NULL DEFAULT 'C',
    source            VARCHAR(64),
    industry          VARCHAR(64),
    estimated_amount  NUMERIC(18,2)  NOT NULL DEFAULT 0,
    win_rate          NUMERIC(5,4)   NOT NULL DEFAULT 0,
    expected_sign_date   DATE,
    expected_start_date  DATE,
    expected_end_date    DATE,
    status            VARCHAR(32)    NOT NULL DEFAULT 'FOLLOWING',
    lost_reason       VARCHAR(512),
    competitor        VARCHAR(256),
    remark            TEXT,
    tags              VARCHAR(512),
    created_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT       NOT NULL DEFAULT 0,
    tenant_id         VARCHAR(20)         NOT NULL DEFAULT '1',
    version           INTEGER        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT uk_ppo_code           UNIQUE (opportunity_code, deleted),
    CONSTRAINT ck_ppo_level_enum     CHECK (level IN ('A', 'B', 'C')),
    CONSTRAINT ck_ppo_status_enum    CHECK (status IN ('FOLLOWING', 'QUOTED', 'NEGOTIATING', 'WON', 'LOST', 'INVALID', 'CONVERTED')),
    CONSTRAINT ck_ppo_win_rate_range CHECK (win_rate >= 0 AND win_rate <= 1),
    CONSTRAINT ck_ppo_amount_nonneg  CHECK (estimated_amount >= 0),
    CONSTRAINT ck_ppo_version_nonneg CHECK (version >= 0),
    CONSTRAINT ck_ppo_deleted_enum   CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_project_opportunity IS '商机主表: 销售线索到合同前的漏斗管理,支持赢率/分级/转化立项';

COMMENT ON COLUMN pmis_project_opportunity.id IS '主键 ID';

COMMENT ON COLUMN pmis_project_opportunity.opportunity_code IS '商机编码(全局唯一,如 OPP20260001)';

COMMENT ON COLUMN pmis_project_opportunity.opportunity_name IS '商机名称';

COMMENT ON COLUMN pmis_project_opportunity.customer_id IS '客户 ID';

COMMENT ON COLUMN pmis_project_opportunity.customer_name IS '客户名称(冗余,减少连表)';

COMMENT ON COLUMN pmis_project_opportunity.business_dept_id IS '负责事业部 ID';

COMMENT ON COLUMN pmis_project_opportunity.owner_id IS '商机负责人 ID(销售/BD)';

COMMENT ON COLUMN pmis_project_opportunity.owner_name IS '商机负责人姓名';

COMMENT ON COLUMN pmis_project_opportunity.level IS '商机分级: A 大客户 / B 中型 / C 小型';

COMMENT ON COLUMN pmis_project_opportunity.source IS '商机来源(老客户介绍/展会/官网/招投标/其他)';

COMMENT ON COLUMN pmis_project_opportunity.industry IS '客户所属行业';

COMMENT ON COLUMN pmis_project_opportunity.estimated_amount IS '预计签约金额(元)';

COMMENT ON COLUMN pmis_project_opportunity.win_rate IS '赢率(0.0000-1.0000,用于收入预测)';

COMMENT ON COLUMN pmis_project_opportunity.expected_sign_date IS '预计签约日期';

COMMENT ON COLUMN pmis_project_opportunity.expected_start_date IS '预计项目开始日期';

COMMENT ON COLUMN pmis_project_opportunity.expected_end_date IS '预计项目结束日期';

COMMENT ON COLUMN pmis_project_opportunity.status IS '商机状态: FOLLOWING 跟进中 / QUOTED 已报价 / NEGOTIATING 谈判中 / WON 中标 / LOST 输单 / INVALID 无效 / CONVERTED 已转立项';

COMMENT ON COLUMN pmis_project_opportunity.lost_reason IS '输单原因';

COMMENT ON COLUMN pmis_project_opportunity.competitor IS '竞争对手';

COMMENT ON COLUMN pmis_project_opportunity.remark IS '备注';

COMMENT ON COLUMN pmis_project_opportunity.tags IS '标签(逗号分隔,用于检索)';

COMMENT ON COLUMN pmis_project_opportunity.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_project_opportunity.created_at IS '创建时间';

COMMENT ON COLUMN pmis_project_opportunity.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_project_opportunity.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_project_opportunity.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_project_opportunity.tenant_id IS '租户 ID(单租户部署默认 1)';

COMMENT ON COLUMN pmis_project_opportunity.version IS '乐观锁版本号(更新时自增,防止并发覆盖)';

CREATE INDEX IF NOT EXISTS idx_ppo_customer
    ON pmis_project_opportunity (customer_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ppo_owner
    ON pmis_project_opportunity (owner_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ppo_status
    ON pmis_project_opportunity (status) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ppo_level
    ON pmis_project_opportunity (level) WHERE deleted = 0;

-- [INLINE-OPT] 复合索引:租户 + 客户 + 状态(漏斗视图)
CREATE INDEX IF NOT EXISTS idx_ppo_tenant_customer_status
    ON pmis_project_opportunity (tenant_id, customer_id, status) WHERE deleted = 0;

-- [INLINE-OPT] 复合索引:租户 + 创建时间倒序(商机中心列表)
CREATE INDEX IF NOT EXISTS idx_ppo_tenant_created
    ON pmis_project_opportunity (tenant_id, created_at DESC) WHERE deleted = 0;

-- [INLINE-OPT] 预计签约日期索引(用于赢率加权收入预测扫描)
CREATE INDEX IF NOT EXISTS idx_ppo_expected_sign
    ON pmis_project_opportunity (expected_sign_date) WHERE deleted = 0 AND status IN ('FOLLOWING', 'QUOTED', 'NEGOTIATING');

-- =====================================================
-- 2. 商机跟进记录 pmis_project_opportunity_follow

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_project_opportunity_follow(
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    opportunity_id    VARCHAR(20)         NOT NULL,
    follow_type       VARCHAR(32)    NOT NULL,
    follow_at         TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    follower_id       VARCHAR(20)         NOT NULL,
    follower_name     VARCHAR(64),
    content           TEXT,
    next_step         TEXT,
    next_follow_date  DATE,
    created_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT       NOT NULL DEFAULT 0,
    tenant_id         VARCHAR(20)         NOT NULL DEFAULT '1',
    version           INTEGER        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_ppof_type_enum     CHECK (follow_type IN ('VISIT', 'CALL', 'QUOTE', 'NEGOTIATE', 'OTHER')),
    CONSTRAINT ck_ppof_version_nonneg CHECK (version >= 0),
    CONSTRAINT ck_ppof_deleted_enum  CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_project_opportunity_follow IS '商机跟进记录: 拜访/电话/报价/谈判的痕迹管理,支持时间线回溯';

COMMENT ON COLUMN pmis_project_opportunity_follow.id IS '主键 ID';

COMMENT ON COLUMN pmis_project_opportunity_follow.opportunity_id IS '商机 ID(关联 pmis_project_opportunity.id)';

COMMENT ON COLUMN pmis_project_opportunity_follow.follow_type IS '跟进类型: VISIT 拜访 / CALL 电话 / QUOTE 报价 / NEGOTIATE 谈判 / OTHER 其他';

COMMENT ON COLUMN pmis_project_opportunity_follow.follow_at IS '跟进时间';

COMMENT ON COLUMN pmis_project_opportunity_follow.follower_id IS '跟进人 ID';

COMMENT ON COLUMN pmis_project_opportunity_follow.follower_name IS '跟进人姓名';

COMMENT ON COLUMN pmis_project_opportunity_follow.content IS '跟进内容';

COMMENT ON COLUMN pmis_project_opportunity_follow.next_step IS '下一步计划';

COMMENT ON COLUMN pmis_project_opportunity_follow.next_follow_date IS '下次跟进日期';

COMMENT ON COLUMN pmis_project_opportunity_follow.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_project_opportunity_follow.created_at IS '创建时间';

COMMENT ON COLUMN pmis_project_opportunity_follow.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_project_opportunity_follow.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_project_opportunity_follow.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_project_opportunity_follow.tenant_id IS '租户 ID(单租户部署默认 1)';

COMMENT ON COLUMN pmis_project_opportunity_follow.version IS '乐观锁版本号';

-- [INLINE-OPT] 复合索引:商机 + 跟进时间倒序(时间线展示)
CREATE INDEX IF NOT EXISTS idx_ppof_opp
    ON pmis_project_opportunity_follow (opportunity_id, follow_at DESC) WHERE deleted = 0;

-- [INLINE-OPT] 跟进人 + 时间索引(销售个人工作台)
CREATE INDEX IF NOT EXISTS idx_ppof_follower_at
    ON pmis_project_opportunity_follow (follower_id, follow_at DESC) WHERE deleted = 0;

-- [INLINE-OPT] 下次跟进日期提醒(后台扫描)
CREATE INDEX IF NOT EXISTS idx_ppof_next_date
    ON pmis_project_opportunity_follow (next_follow_date) WHERE deleted = 0 AND next_follow_date IS NOT NULL;

-- =====================================================
-- 3. 立项主表 pmis_project_initiation

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_project_contract(
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    contract_code     VARCHAR(64)    NOT NULL,
    contract_name     VARCHAR(256)   NOT NULL,
    initiation_id     VARCHAR(20),
    customer_id       VARCHAR(20)         NOT NULL,
    customer_name     VARCHAR(256),
    contract_type     VARCHAR(32)    NOT NULL,
    sign_date         DATE,
    effective_date    DATE,
    expire_date       DATE,
    total_amount      NUMERIC(18,2)  NOT NULL DEFAULT 0,
    currency          VARCHAR(8)     NOT NULL DEFAULT 'CNY',
    payment_terms     TEXT,
    billing_cycle     VARCHAR(32),
    tax_rate          NUMERIC(5,4)   NOT NULL DEFAULT 0,
    status            VARCHAR(32)    NOT NULL DEFAULT 'DRAFT',
    risk_level        VARCHAR(8)     NOT NULL DEFAULT 'LOW',
    risk_notes        TEXT,
    owner_id          VARCHAR(20)         NOT NULL,
    owner_name        VARCHAR(64),
    contract_file_id  VARCHAR(20),
    workflow_id       VARCHAR(20),
    remark            TEXT,
    created_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT       NOT NULL DEFAULT 0,
    tenant_id         VARCHAR(20)         NOT NULL DEFAULT '1',
    version           INTEGER        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT uk_ppc_code           UNIQUE (contract_code, deleted),
    CONSTRAINT ck_ppc_type_enum      CHECK (contract_type IN ('FIXED_PRICE', 'T_M', 'OUTSOURCING', 'PRODUCT', 'MAINTENANCE', 'CONSULTING', 'TRAINING', 'OTHER')),
    CONSTRAINT ck_ppc_status_enum    CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVING', 'ACTIVE', 'SUSPENDED', 'EXPIRED', 'TERMINATED')),
    CONSTRAINT ck_ppc_billing_enum   CHECK (billing_cycle IS NULL OR billing_cycle IN ('MONTHLY', 'QUARTERLY', 'MILESTONE', 'ONEOFF')),
    CONSTRAINT ck_ppc_risk_enum      CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_ppc_tax_rate_range CHECK (tax_rate >= 0 AND tax_rate <= 1),
    CONSTRAINT ck_ppc_amount_nonneg  CHECK (total_amount >= 0),
    CONSTRAINT ck_ppc_dates_valid    CHECK (expire_date IS NULL OR effective_date IS NULL OR expire_date >= effective_date),
    CONSTRAINT ck_ppc_version_nonneg CHECK (version >= 0),
    CONSTRAINT ck_ppc_deleted_enum   CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_project_contract IS '合同主表: 项目签约合同,关联立项/客户/付款条款,支撑开票回款';

COMMENT ON COLUMN pmis_project_contract.id IS '主键 ID';

COMMENT ON COLUMN pmis_project_contract.contract_code IS '合同编码(全局唯一,如 CT20260001)';

COMMENT ON COLUMN pmis_project_contract.contract_name IS '合同名称';

COMMENT ON COLUMN pmis_project_contract.initiation_id IS '关联立项 ID(关联 pmis_project_initiation.id)';

COMMENT ON COLUMN pmis_project_contract.customer_id IS '客户 ID';

COMMENT ON COLUMN pmis_project_contract.customer_name IS '客户名称(冗余)';

COMMENT ON COLUMN pmis_project_contract.contract_type IS '合同类型: FIXED_PRICE / T_M / OUTSOURCING / PRODUCT / MAINTENANCE / CONSULTING / TRAINING / OTHER';

COMMENT ON COLUMN pmis_project_contract.sign_date IS '签约日期';

COMMENT ON COLUMN pmis_project_contract.effective_date IS '合同生效日期';

COMMENT ON COLUMN pmis_project_contract.expire_date IS '合同到期日期';

COMMENT ON COLUMN pmis_project_contract.total_amount IS '合同总额(元,含税)';

COMMENT ON COLUMN pmis_project_contract.currency IS '币种(默认 CNY)';

COMMENT ON COLUMN pmis_project_contract.payment_terms IS '付款条款(如 3-3-3-1 预付/启动/UAT/质保)';

COMMENT ON COLUMN pmis_project_contract.billing_cycle IS '结算周期(MONTHLY 月结 / QUARTERLY 季结 / MILESTONE 里程碑 / ONEOFF 一次性)';

COMMENT ON COLUMN pmis_project_contract.tax_rate IS '适用税率(0.0000-1.0000)';

COMMENT ON COLUMN pmis_project_contract.status IS '合同状态: DRAFT 草稿 / SUBMITTED 已提交 / APPROVING 审批中 / ACTIVE 执行中 / SUSPENDED 暂停 / EXPIRED 已到期 / TERMINATED 已终止';

COMMENT ON COLUMN pmis_project_contract.risk_level IS '风险等级: LOW 低 / MEDIUM 中 / HIGH 高';

COMMENT ON COLUMN pmis_project_contract.risk_notes IS '风险说明';

COMMENT ON COLUMN pmis_project_contract.owner_id IS '合同负责人 ID(销售/客户经理)';

COMMENT ON COLUMN pmis_project_contract.owner_name IS '合同负责人姓名';

COMMENT ON COLUMN pmis_project_contract.contract_file_id IS '合同文件 ID(关联 pmis_file.id)';

COMMENT ON COLUMN pmis_project_contract.workflow_id IS '审批流程实例 ID';

COMMENT ON COLUMN pmis_project_contract.remark IS '备注';

COMMENT ON COLUMN pmis_project_contract.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_project_contract.created_at IS '创建时间';

COMMENT ON COLUMN pmis_project_contract.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_project_contract.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_project_contract.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_project_contract.tenant_id IS '租户 ID(单租户部署默认 1)';

COMMENT ON COLUMN pmis_project_contract.version IS '乐观锁版本号';

CREATE INDEX IF NOT EXISTS idx_ppc_customer
    ON pmis_project_contract (customer_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ppc_init
    ON pmis_project_contract (initiation_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ppc_status
    ON pmis_project_contract (status) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ppc_sign
    ON pmis_project_contract (sign_date) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ppc_risk
    ON pmis_project_contract (risk_level) WHERE deleted = 0;

-- [INLINE-OPT] 合同负责人 + 状态(销售合同台账)
CREATE INDEX IF NOT EXISTS idx_ppc_owner_status
    ON pmis_project_contract (owner_id, status) WHERE deleted = 0;

-- [INLINE-OPT] 复合索引:租户 + 到期日期(到期预警扫描)
CREATE INDEX IF NOT EXISTS idx_ppc_tenant_expire
    ON pmis_project_contract (tenant_id, expire_date) WHERE deleted = 0 AND expire_date IS NOT NULL;

-- [INLINE-OPT] 复合索引:租户 + 创建时间倒序(合同中心列表)
CREATE INDEX IF NOT EXISTS idx_ppc_tenant_created
    ON pmis_project_contract (tenant_id, created_at DESC) WHERE deleted = 0;

-- =====================================================
-- 7. 合同补充协议 pmis_project_contract_supplement

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_project_contract_supplement(
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    contract_id       VARCHAR(20)         NOT NULL,
    supplement_code   VARCHAR(64)    NOT NULL,
    supplement_name   VARCHAR(256)   NOT NULL,
    supplement_type   VARCHAR(32)    NOT NULL,
    change_amount     NUMERIC(18,2)  NOT NULL DEFAULT 0,
    new_total_amount  NUMERIC(18,2)  NOT NULL DEFAULT 0,
    effective_date    DATE,
    expire_date       DATE,
    content           TEXT,
    file_id           VARCHAR(20),
    status            VARCHAR(32)    NOT NULL DEFAULT 'DRAFT',
    created_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT       NOT NULL DEFAULT 0,
    tenant_id         VARCHAR(20)         NOT NULL DEFAULT '1',
    version           INTEGER        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT uk_ppcs_code          UNIQUE (supplement_code, deleted),
    CONSTRAINT ck_ppcs_type_enum     CHECK (supplement_type IN ('AMOUNT', 'SCOPE', 'TERM', 'OTHER')),
    CONSTRAINT ck_ppcs_status_enum   CHECK (status IN ('DRAFT', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_ppcs_amount_nonneg CHECK (new_total_amount >= 0),
    CONSTRAINT ck_ppcs_version_nonneg CHECK (version >= 0),
    CONSTRAINT ck_ppcs_deleted_enum  CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_project_contract_supplement IS '合同补充协议: 主合同签订后的金额/范围/工期/其他补充条款,法务备案';

COMMENT ON COLUMN pmis_project_contract_supplement.id IS '主键 ID';

COMMENT ON COLUMN pmis_project_contract_supplement.contract_id IS '主合同 ID(关联 pmis_project_contract.id)';

COMMENT ON COLUMN pmis_project_contract_supplement.supplement_code IS '补充协议编码(全局唯一)';

COMMENT ON COLUMN pmis_project_contract_supplement.supplement_name IS '补充协议名称';

COMMENT ON COLUMN pmis_project_contract_supplement.supplement_type IS '补充类型: AMOUNT 金额 / SCOPE 范围 / TERM 工期 / OTHER 其他';

COMMENT ON COLUMN pmis_project_contract_supplement.change_amount IS '变更金额(可正可负)';

COMMENT ON COLUMN pmis_project_contract_supplement.new_total_amount IS '变更后合同总额';

COMMENT ON COLUMN pmis_project_contract_supplement.effective_date IS '生效日期';

COMMENT ON COLUMN pmis_project_contract_supplement.expire_date IS '到期日期';

COMMENT ON COLUMN pmis_project_contract_supplement.content IS '协议正文';

COMMENT ON COLUMN pmis_project_contract_supplement.file_id IS '协议文件 ID(关联 pmis_file.id)';

COMMENT ON COLUMN pmis_project_contract_supplement.status IS '状态: DRAFT 草稿 / APPROVED 已签 / REJECTED 已驳回';

COMMENT ON COLUMN pmis_project_contract_supplement.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_project_contract_supplement.created_at IS '创建时间';

COMMENT ON COLUMN pmis_project_contract_supplement.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_project_contract_supplement.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_project_contract_supplement.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_project_contract_supplement.tenant_id IS '租户 ID(单租户部署默认 1)';

COMMENT ON COLUMN pmis_project_contract_supplement.version IS '乐观锁版本号';

-- [INLINE-OPT] 复合索引:合同 + 类型(按类型查看补充协议)
CREATE INDEX IF NOT EXISTS idx_ppcs_contract_type
    ON pmis_project_contract_supplement (contract_id, supplement_type) WHERE deleted = 0;

-- [INLINE-OPT] 复合索引:租户 + 状态(补充协议台账)
CREATE INDEX IF NOT EXISTS idx_ppcs_tenant_status
    ON pmis_project_contract_supplement (tenant_id, status) WHERE deleted = 0;

-- =====================================================
-- 8. 合同变更记录 pmis_project_contract_change

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_project_contract_change(
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    contract_id       VARCHAR(20)         NOT NULL,
    change_code       VARCHAR(64)    NOT NULL,
    change_type       VARCHAR(32)    NOT NULL,
    change_reason     TEXT,
    before_value      TEXT,
    after_value       TEXT,
    amount_delta      NUMERIC(18,2)  NOT NULL DEFAULT 0,
    impact_analysis   TEXT,
    status            VARCHAR(32)    NOT NULL DEFAULT 'DRAFT',
    applicant_id      VARCHAR(20),
    applicant_name    VARCHAR(64),
    approver_id       VARCHAR(20),
    approver_name     VARCHAR(64),
    approved_at       TIMESTAMPTZ,
    workflow_id       VARCHAR(20),
    created_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT       NOT NULL DEFAULT 0,
    tenant_id         VARCHAR(20)         NOT NULL DEFAULT '1',
    version           INTEGER        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT uk_ppcc_code          UNIQUE (change_code, deleted),
    CONSTRAINT ck_ppcc_type_enum     CHECK (change_type IN ('SCOPE', 'AMOUNT', 'TERM', 'PERSONNEL', 'PROGRESS')),
    CONSTRAINT ck_ppcc_status_enum   CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_ppcc_version_nonneg CHECK (version >= 0),
    CONSTRAINT ck_ppcc_deleted_enum  CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_project_contract_change IS '合同变更记录: 范围/金额/工期/人员/进度的变更,需走审批流';

COMMENT ON COLUMN pmis_project_contract_change.id IS '主键 ID';

COMMENT ON COLUMN pmis_project_contract_change.contract_id IS '主合同 ID(关联 pmis_project_contract.id)';

COMMENT ON COLUMN pmis_project_contract_change.change_code IS '变更单编码(全局唯一)';

COMMENT ON COLUMN pmis_project_contract_change.change_type IS '变更类型: SCOPE 范围 / AMOUNT 金额 / TERM 工期 / PERSONNEL 人员 / PROGRESS 进度';

COMMENT ON COLUMN pmis_project_contract_change.change_reason IS '变更原因';

COMMENT ON COLUMN pmis_project_contract_change.before_value IS '变更前值';

COMMENT ON COLUMN pmis_project_contract_change.after_value IS '变更后值';

COMMENT ON COLUMN pmis_project_contract_change.amount_delta IS '金额变化(可正可负)';

COMMENT ON COLUMN pmis_project_contract_change.impact_analysis IS '影响分析(范围/工期/成本/风险)';

COMMENT ON COLUMN pmis_project_contract_change.status IS '状态: DRAFT 草稿 / SUBMITTED 已提交 / APPROVING 审批中 / APPROVED 已批准 / REJECTED 已驳回';

COMMENT ON COLUMN pmis_project_contract_change.applicant_id IS '申请人 ID';

COMMENT ON COLUMN pmis_project_contract_change.applicant_name IS '申请人姓名';

COMMENT ON COLUMN pmis_project_contract_change.approver_id IS '审批人 ID';

COMMENT ON COLUMN pmis_project_contract_change.approver_name IS '审批人姓名';

COMMENT ON COLUMN pmis_project_contract_change.approved_at IS '审批时间';

COMMENT ON COLUMN pmis_project_contract_change.workflow_id IS '审批流程实例 ID';

COMMENT ON COLUMN pmis_project_contract_change.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_project_contract_change.created_at IS '创建时间';

COMMENT ON COLUMN pmis_project_contract_change.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_project_contract_change.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_project_contract_change.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_project_contract_change.tenant_id IS '租户 ID(单租户部署默认 1)';

COMMENT ON COLUMN pmis_project_contract_change.version IS '乐观锁版本号';

-- [INLINE-OPT] 复合索引:合同 + 类型(合同变更历史)
CREATE INDEX IF NOT EXISTS idx_ppcc_contract_type
    ON pmis_project_contract_change (contract_id, change_type) WHERE deleted = 0;

-- [INLINE-OPT] 状态索引(待审批工作台)
CREATE INDEX IF NOT EXISTS idx_ppcc_status
    ON pmis_project_contract_change (status) WHERE deleted = 0;

-- [INLINE-OPT] 审批人 + 状态(审批人工作台)
CREATE INDEX IF NOT EXISTS idx_ppcc_approver_status
    ON pmis_project_contract_change (approver_id, status) WHERE deleted = 0;

-- [INLINE-OPT] 复合索引:租户 + 审批时间倒序(变更审计)
CREATE INDEX IF NOT EXISTS idx_ppcc_tenant_approved
    ON pmis_project_contract_change (tenant_id, approved_at DESC) WHERE deleted = 0;

-- --------------------------------------------------------------------

-- ============================ [010] init pmis execution schema ============================
-- [INLINE-OPT] 已统一为单文件 V1.0.0.sql 的最终形态:
--   1) 时间字段 TIMESTAMP → TIMESTAMPTZ
--   2) 全部审计字段统一为 created_by/created_at/updated_by/updated_at
--   3) tenant_id NOT NULL DEFAULT 1
--   4) 内联 status/category/type/deleted/window_check CHECK 约束
--   5) 内联 (tenant_id, created_at DESC) WHERE deleted = 0 复合部分索引
--   6) status/owner/category 类索引全部加 WHERE deleted = 0 部分条件
--   7) 内联 (initiation_id, period) 等业务专用复合索引
-- =====================================================
-- PMIS 项目执行/成本/利润模块 DDL
-- 版本: V1.0.0_010 (merged into V1.0.0.sql)
-- 描述: WBS 任务、工时、成本归集、利润核算
-- =====================================================

-- =====================================================
-- 1. WBS 任务表 pmis_execution_wbs_task

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_project_contract_template(
    id                     VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    template_code          VARCHAR(64)  NOT NULL,
    template_name          VARCHAR(256) NOT NULL,
    contract_type          VARCHAR(32)  NOT NULL,            -- FIXED_PRICE/T_M/OUTSOURCING/PRODUCT/MAINTENANCE/CONSULTING/TRAINING/OTHER
    version                VARCHAR(32)  NOT NULL DEFAULT '1.0.0',
    payment_terms          TEXT,
    default_payment_days   INTEGER      NOT NULL DEFAULT 30,
    default_penalty_rate   NUMERIC(5,4) NOT NULL DEFAULT 0,
    sla_description        TEXT,
    deliverables           TEXT,
    content                TEXT,                              -- 模板正文
    customer_level         VARCHAR(16),                      -- A/B/C/D
    project_level          VARCHAR(16),                      -- L1-L18
    status                 VARCHAR(32)  NOT NULL DEFAULT 'DRAFT', -- DRAFT/PUBLISHED/DEPRECATED
    author_id              VARCHAR(20),
    author_name            VARCHAR(64),
    remark                 TEXT,
    tenant_id              VARCHAR(20)       NOT NULL DEFAULT '1',
    created_by             VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by             VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                SMALLINT     NOT NULL DEFAULT 0,
    -- 业务唯一性: 同租户下 template_code + 软删除位 唯一
    CONSTRAINT uk_ppct_code            UNIQUE (template_code, deleted),
    -- 枚举约束
    CONSTRAINT ck_ppct_contract_type   CHECK (contract_type IN ('FIXED_PRICE','T_M','OUTSOURCING','PRODUCT','MAINTENANCE','CONSULTING','TRAINING','OTHER')),
    CONSTRAINT ck_ppct_customer_level  CHECK (customer_level IS NULL OR customer_level IN ('A','B','C','D')),
    CONSTRAINT ck_ppct_status_enum     CHECK (status IN ('DRAFT','PUBLISHED','DEPRECATED')),
    -- 数值非负 / 比例范围
    CONSTRAINT ck_ppct_payment_days    CHECK (default_payment_days >= 0),
    CONSTRAINT ck_ppct_penalty_range   CHECK (default_penalty_rate >= 0 AND default_penalty_rate <= 1),
    CONSTRAINT ck_ppct_deleted_enum    CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_project_contract_template IS '合同模板表: 8 类项目类型（FIXED_PRICE/T_M/OUTSOURCING/PRODUCT/MAINTENANCE/CONSULTING/TRAINING/OTHER）的标准化合同模板,合同起草时按类型引用';

COMMENT ON COLUMN pmis_project_contract_template.template_code IS '模板编码: 业务唯一,如 TPL-FIX-001';

COMMENT ON COLUMN pmis_project_contract_template.template_name IS '模板名称';

COMMENT ON COLUMN pmis_project_contract_template.contract_type IS '合同类型: FIXED_PRICE 固定总价 / T_M 人月计费 / OUTSOURCING 人力外包 / PRODUCT 产品销售 / MAINTENANCE 运维服务 / CONSULTING 咨询服务 / TRAINING 培训服务 / OTHER 其他';

COMMENT ON COLUMN pmis_project_contract_template.version IS '模板版本号: 语义化版本,默认 1.0.0';

COMMENT ON COLUMN pmis_project_contract_template.payment_terms IS '付款条款: 文本描述,例如"3-3-3-1"分阶段比例';

COMMENT ON COLUMN pmis_project_contract_template.default_payment_days IS '默认账期(天): 0=预付,30=月结30天';

COMMENT ON COLUMN pmis_project_contract_template.default_penalty_rate IS '默认违约金比例: 0.0010=千分之一,作为合同基准';

COMMENT ON COLUMN pmis_project_contract_template.sla_description IS 'SLA 描述: 服务等级协议,例如 P1 4 小时响应';

COMMENT ON COLUMN pmis_project_contract_template.deliverables IS '交付物清单: 合同约定的交付物列表';

COMMENT ON COLUMN pmis_project_contract_template.content IS '模板正文: 含占位符 ${} 的合同正文';

COMMENT ON COLUMN pmis_project_contract_template.customer_level IS '客户级别: A/B/C/D 信用等级,NULL=全级别适用';

COMMENT ON COLUMN pmis_project_contract_template.project_level IS '项目级别: L1-L18 复杂度等级,NULL=全级别适用';

COMMENT ON COLUMN pmis_project_contract_template.status IS '模板状态: DRAFT 草稿 / PUBLISHED 已发布 / DEPRECATED 已废弃,状态机线性';

COMMENT ON COLUMN pmis_project_contract_template.author_id IS '模板作者 ID';

COMMENT ON COLUMN pmis_project_contract_template.author_name IS '模板作者姓名（冗余）';

COMMENT ON COLUMN pmis_project_contract_template.remark IS '备注';

COMMENT ON COLUMN pmis_project_contract_template.tenant_id IS '租户 ID: 多租户隔离';

COMMENT ON COLUMN pmis_project_contract_template.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_ppct_type_status / idx_ppct_tenant)
CREATE INDEX IF NOT EXISTS idx_ppct_tenant_type_status
    ON pmis_project_contract_template(tenant_id, contract_type, status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ppct_tenant_created
    ON pmis_project_contract_template(tenant_id, created_at DESC)
    WHERE deleted = 0;

-- =====================================================
-- 2. 项目变更主表 pmis_project_change

-- ============================================================
-- [MERGED FROM V1.0.0_finance.sql] finance 模块 SQL (8 张表,已重命名为 pmis_project_* 前缀)
-- ============================================================

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
CREATE TABLE IF NOT EXISTS pmis_project_expense(
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

COMMENT ON TABLE pmis_project_expense IS '费用报销表: 差旅/团建/会议/办公等费用报销,可关联项目(影响项目预算)';

COMMENT ON COLUMN pmis_project_expense.id IS '主键 ID';

COMMENT ON COLUMN pmis_project_expense.expense_code IS '报销单编码(全局唯一)';

COMMENT ON COLUMN pmis_project_expense.initiation_id IS '关联立项 ID(项目级费用必填,公司公共费用可空)';

COMMENT ON COLUMN pmis_project_expense.employee_id IS '报销人 ID';

COMMENT ON COLUMN pmis_project_expense.employee_name IS '报销人姓名';

COMMENT ON COLUMN pmis_project_expense.expense_type IS '费用类型: TRAVEL 差旅 / CATERING 餐饮 / MEETING 会议 / SUPPLIES 办公 / COMMUNICATION 通讯 / OTHER 其他';

COMMENT ON COLUMN pmis_project_expense.amount IS '报销金额(元)';

COMMENT ON COLUMN pmis_project_expense.expense_date IS '费用发生日期';

COMMENT ON COLUMN pmis_project_expense.description IS '费用说明';

COMMENT ON COLUMN pmis_project_expense.receipt_url IS '发票/凭证 URL';

COMMENT ON COLUMN pmis_project_expense.status IS '审批状态: DRAFT 草稿 / SUBMITTED 已提交 / APPROVED 已批准 / REJECTED 已驳回 / PAID 已打款';

COMMENT ON COLUMN pmis_project_expense.approver_id IS '审批人 ID';

COMMENT ON COLUMN pmis_project_expense.approver_name IS '审批人姓名';

COMMENT ON COLUMN pmis_project_expense.approved_at IS '审批时间';

COMMENT ON COLUMN pmis_project_expense.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_project_expense.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_project_expense.created_at IS '创建时间';

COMMENT ON COLUMN pmis_project_expense.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_project_expense.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_project_expense.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_project_expense.tenant_id IS '租户 ID(单租户部署默认 1)';

COMMENT ON COLUMN pmis_project_expense.version IS '乐观锁版本号';

CREATE INDEX IF NOT EXISTS idx_pce_initiation
    ON pmis_project_expense (initiation_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pce_employee
    ON pmis_project_expense (employee_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pce_status
    ON pmis_project_expense (status) WHERE deleted = 0;

-- [INLINE-OPT] 员工 + 状态(员工报销台账)
CREATE INDEX IF NOT EXISTS idx_pce_employee_status
    ON pmis_project_expense (employee_id, status) WHERE deleted = 0;

-- [INLINE-OPT] 复合索引:租户 + 费用日期(报销中心时间筛选)
CREATE INDEX IF NOT EXISTS idx_pce_tenant_date
    ON pmis_project_expense (tenant_id, expense_date DESC) WHERE deleted = 0;

-- [INLINE-OPT] 链路追踪 ID 索引
CREATE INDEX IF NOT EXISTS idx_pce_trace
    ON pmis_project_expense (provider_trace_id) WHERE provider_trace_id <> '';

-- =====================================================
-- 6. 收入确认表 pmis_project_revenue

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_project_revenue(
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

COMMENT ON TABLE pmis_project_revenue IS '收入确认表: 按里程碑/百分比/完工法/手动法等多维度确认项目收入';

COMMENT ON COLUMN pmis_project_revenue.id IS '主键 ID';

COMMENT ON COLUMN pmis_project_revenue.contract_id IS '合同 ID(关联 pmis_project_contract.id)';

COMMENT ON COLUMN pmis_project_revenue.initiation_id IS '立项 ID(关联 pmis_project_initiation.id)';

COMMENT ON COLUMN pmis_project_revenue.revenue_code IS '收入确认单编码(全局唯一)';

COMMENT ON COLUMN pmis_project_revenue.recognition_method IS '确认方法: MILESTONE 里程碑法 / PERCENTAGE 比例法 / PERCENT_COMPLETE 完工法 / POINTS 工分法 / MANUAL 手动';

COMMENT ON COLUMN pmis_project_revenue.period IS '所属期间(YYYY-MM)';

COMMENT ON COLUMN pmis_project_revenue.amount IS '确认金额(元)';

COMMENT ON COLUMN pmis_project_revenue.recognition_date IS '确认日期';

COMMENT ON COLUMN pmis_project_revenue.milestone IS '里程碑描述';

COMMENT ON COLUMN pmis_project_revenue.percent_complete IS '完工百分比(0-100,完工法)';

COMMENT ON COLUMN pmis_project_revenue.invoice_id IS '关联开票申请 ID';

COMMENT ON COLUMN pmis_project_revenue.status IS '状态: DRAFT 草稿 / CONFIRMED 已确认 / REVERSED 已冲销';

COMMENT ON COLUMN pmis_project_revenue.confirmed_by IS '确认人 ID';

COMMENT ON COLUMN pmis_project_revenue.confirmed_at IS '确认时间';

COMMENT ON COLUMN pmis_project_revenue.description IS '收入确认说明';

COMMENT ON COLUMN pmis_project_revenue.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_project_revenue.created_by IS '创建人 ID';

COMMENT ON COLUMN pmis_project_revenue.created_at IS '创建时间';

COMMENT ON COLUMN pmis_project_revenue.updated_by IS '最后修改人 ID';

COMMENT ON COLUMN pmis_project_revenue.updated_at IS '最后修改时间';

COMMENT ON COLUMN pmis_project_revenue.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_project_revenue.tenant_id IS '租户 ID(单租户部署默认 1)';

COMMENT ON COLUMN pmis_project_revenue.version IS '乐观锁版本号';

CREATE INDEX IF NOT EXISTS idx_ppr_contract
    ON pmis_project_revenue (contract_id) WHERE deleted = 0;

-- [INLINE-OPT] 复合索引:立项 + 期间(项目月度收入走势)
CREATE INDEX IF NOT EXISTS idx_ppr_initiation
    ON pmis_project_revenue (initiation_id, period) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ppr_status
    ON pmis_project_revenue (status) WHERE deleted = 0;

-- [INLINE-OPT] 复合索引:租户 + 期间(全公司收入月报)
CREATE INDEX IF NOT EXISTS idx_ppr_tenant_period
    ON pmis_project_revenue (tenant_id, period) WHERE deleted = 0;

-- [INLINE-OPT] 关联开票申请 ID
CREATE INDEX IF NOT EXISTS idx_ppr_invoice
    ON pmis_project_revenue (invoice_id) WHERE deleted = 0 AND invoice_id IS NOT NULL;

-- [INLINE-OPT] 链路追踪 ID 索引
CREATE INDEX IF NOT EXISTS idx_ppr_trace
    ON pmis_project_revenue (provider_trace_id) WHERE provider_trace_id <> '';

-- =====================================================
-- 7. 项目利润快照表 pmis_project_profit_snapshot

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_project_profit_snapshot(
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

COMMENT ON TABLE pmis_project_profit_snapshot IS '项目利润快照(按月): 立项 × 期间 唯一约束,周期性滚动生成';

COMMENT ON COLUMN pmis_project_profit_snapshot.id IS '主键 ID';

COMMENT ON COLUMN pmis_project_profit_snapshot.initiation_id IS '立项 ID(关联 pmis_project_initiation.id)';

COMMENT ON COLUMN pmis_project_profit_snapshot.period IS '快照周期(YYYY-MM)';

COMMENT ON COLUMN pmis_project_profit_snapshot.contract_amount IS '合同总额(元)';

COMMENT ON COLUMN pmis_project_profit_snapshot.recognized_revenue IS '已确认收入(元)';

COMMENT ON COLUMN pmis_project_profit_snapshot.billed_amount IS '已开票金额(元)';

COMMENT ON COLUMN pmis_project_profit_snapshot.received_amount IS '已回款金额(元)';

COMMENT ON COLUMN pmis_project_profit_snapshot.labor_cost IS '人力成本(元)';

COMMENT ON COLUMN pmis_project_profit_snapshot.purchase_cost IS '采购成本(元)';

COMMENT ON COLUMN pmis_project_profit_snapshot.expense_cost IS '费用(元)';

COMMENT ON COLUMN pmis_project_profit_snapshot.outsource_cost IS '外包(元)';

COMMENT ON COLUMN pmis_project_profit_snapshot.allocation_cost IS '分摊费用(元)';

COMMENT ON COLUMN pmis_project_profit_snapshot.total_cost IS '总成本(元)';

COMMENT ON COLUMN pmis_project_profit_snapshot.gross_profit IS '毛利(元)';

COMMENT ON COLUMN pmis_project_profit_snapshot.gross_margin IS '毛利率 0.0000-1.0000';

COMMENT ON COLUMN pmis_project_profit_snapshot.progress_pct IS '完工进度(0-100)';

COMMENT ON COLUMN pmis_project_profit_snapshot.billable_hours IS '可计费工时(小时)';

COMMENT ON COLUMN pmis_project_profit_snapshot.non_billable_hours IS '不可计费工时(小时)';

COMMENT ON COLUMN pmis_project_profit_snapshot.snapshot_at IS '快照生成时间';

COMMENT ON COLUMN pmis_project_profit_snapshot.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_project_profit_snapshot.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

COMMENT ON COLUMN pmis_project_profit_snapshot.tenant_id IS '租户 ID(单租户部署默认 1)';

COMMENT ON COLUMN pmis_project_profit_snapshot.version IS '乐观锁版本号';

-- [INLINE-OPT] 复合索引:立项 + 期间(项目利润走势)
CREATE INDEX IF NOT EXISTS idx_pps_initiation
    ON pmis_project_profit_snapshot (initiation_id, period) WHERE deleted = 0;

-- [INLINE-OPT] 复合索引:租户 + 期间(全公司月度利润驾驶舱)
CREATE INDEX IF NOT EXISTS idx_pps_tenant_period
    ON pmis_project_profit_snapshot (tenant_id, period) WHERE deleted = 0;

-- [INLINE-OPT] 链路追踪 ID 索引
CREATE INDEX IF NOT EXISTS idx_pps_trace
    ON pmis_project_profit_snapshot (provider_trace_id) WHERE provider_trace_id <> '';

-- =====================================================
-- 8. 项目风险登记表 pmis_execution_risk

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_project_invoice(
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

COMMENT ON TABLE  pmis_project_invoice IS '发票主表: 支持正常开票与红冲（RED_REVERSED）,执行 InvoiceStatus 状态机校验,invoice_code 唯一,invoice_no 在 ISSUED 时分配';

COMMENT ON COLUMN pmis_project_invoice.invoice_no IS '财务发票号: 税务局分配的纸质/电子发票号,ISSUED 状态时分配';

COMMENT ON COLUMN pmis_project_invoice.invoice_code IS '业务编号: 系统生成的唯一编码,如 INV-2026-001';

COMMENT ON COLUMN pmis_project_invoice.invoice_type IS '发票类型: NORMAL 正常开票 / RED_REVERSE 红冲发票';

COMMENT ON COLUMN pmis_project_invoice.contract_id IS '所属合同 ID';

COMMENT ON COLUMN pmis_project_invoice.initiation_id IS '所属立项 ID';

COMMENT ON COLUMN pmis_project_invoice.customer_id IS '客户 ID';

COMMENT ON COLUMN pmis_project_invoice.customer_name IS '客户名称（冗余）';

COMMENT ON COLUMN pmis_project_invoice.invoice_basis IS '开票依据: MILESTONE 里程碑 / OUTSOURCING 外包人天 / MONTHLY 月度结算 / FINAL 终验 / OTHER 其他';

COMMENT ON COLUMN pmis_project_invoice.amount IS '含税金额(元)';

COMMENT ON COLUMN pmis_project_invoice.tax_amount IS '税额(元)';

COMMENT ON COLUMN pmis_project_invoice.net_amount IS '不含税金额(元)';

COMMENT ON COLUMN pmis_project_invoice.tax_rate IS '税率: 0.06=6%,0.13=13%';

COMMENT ON COLUMN pmis_project_invoice.currency IS '币种: CNY/USD/EUR,默认 CNY';

COMMENT ON COLUMN pmis_project_invoice.invoice_date IS '开票日期';

COMMENT ON COLUMN pmis_project_invoice.tax_period IS '税务所属期: 格式 YYYY-MM,用于税务申报';

COMMENT ON COLUMN pmis_project_invoice.title IS '发票抬头';

COMMENT ON COLUMN pmis_project_invoice.tax_no IS '纳税人识别号: 客户税号';

COMMENT ON COLUMN pmis_project_invoice.bank_info IS '开户行+账号: 客户收票方银行信息';

COMMENT ON COLUMN pmis_project_invoice.address IS '客户地址';

COMMENT ON COLUMN pmis_project_invoice.phone IS '客户电话';

COMMENT ON COLUMN pmis_project_invoice.remark IS '备注';

COMMENT ON COLUMN pmis_project_invoice.status IS '发票状态: DRAFT 草稿 / SUBMITTED 已提交 / ISSUED 已开票 / RED_REVERSED 已红冲 / CANCELLED 已取消,严格状态机';

COMMENT ON COLUMN pmis_project_invoice.reversed_by_id IS '红冲来源发票 ID: 红冲发票指向被红冲的原始发票';

COMMENT ON COLUMN pmis_project_invoice.attachment_id IS '发票扫描件: 引用 pmis_file_metadata.id';

COMMENT ON COLUMN pmis_project_invoice.approval_comment IS '审批意见';

COMMENT ON COLUMN pmis_project_invoice.applied_by IS '申请人 ID';

COMMENT ON COLUMN pmis_project_invoice.approved_by IS '审批人 ID';

COMMENT ON COLUMN pmis_project_invoice.approved_at IS '审批时间';

COMMENT ON COLUMN pmis_project_invoice.issued_by IS '开票人 ID';

COMMENT ON COLUMN pmis_project_invoice.issued_at IS '开票时间';

COMMENT ON COLUMN pmis_project_invoice.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_project_invoice.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_project_invoice.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_pfi_*)
CREATE INDEX IF NOT EXISTS idx_pfi_tenant_contract
    ON pmis_project_invoice(tenant_id, contract_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfi_tenant_initiation
    ON pmis_project_invoice(tenant_id, initiation_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfi_tenant_customer
    ON pmis_project_invoice(tenant_id, customer_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfi_tenant_status_type
    ON pmis_project_invoice(tenant_id, status, invoice_type)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfi_tenant_invoice_date
    ON pmis_project_invoice(tenant_id, invoice_date)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfi_tenant_tax_period
    ON pmis_project_invoice(tenant_id, tax_period)
    WHERE deleted = 0;

-- =====================================================
-- 2. 回款主表 pmis_project_payment

-- =====================================================
-- P1-6: 已废弃,无需 DROP
CREATE TABLE IF NOT EXISTS pmis_project_payment(
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

COMMENT ON TABLE  pmis_project_payment IS '回款主表: 客户回款记录,支持核销发票（allocated_amount/unallocated_amount）,unallocatedAmount=0 时自动转 ALLOCATED';

COMMENT ON COLUMN pmis_project_payment.payment_no IS '回款流水号: 银行流水号或系统生成';

COMMENT ON COLUMN pmis_project_payment.payment_code IS '业务编号: 系统生成的唯一编码,如 PAY-2026-001';

COMMENT ON COLUMN pmis_project_payment.contract_id IS '所属合同 ID';

COMMENT ON COLUMN pmis_project_payment.initiation_id IS '所属立项 ID';

COMMENT ON COLUMN pmis_project_payment.customer_id IS '客户 ID';

COMMENT ON COLUMN pmis_project_payment.customer_name IS '客户名称（冗余）';

COMMENT ON COLUMN pmis_project_payment.amount IS '回款总金额(元)';

COMMENT ON COLUMN pmis_project_payment.currency IS '币种: 默认 CNY';

COMMENT ON COLUMN pmis_project_payment.payment_method IS '支付方式: BANK_TRANSFER 银行转账 / CHECK 支票 / CASH 现金 / OTHER 其他';

COMMENT ON COLUMN pmis_project_payment.payment_date IS '到账日期';

COMMENT ON COLUMN pmis_project_payment.bank_account IS '客户付款账号';

COMMENT ON COLUMN pmis_project_payment.our_bank_account IS '我方收款账号';

COMMENT ON COLUMN pmis_project_payment.bank_reference IS '银行流水号: 银行端的流水标识';

COMMENT ON COLUMN pmis_project_payment.invoice_allocation IS '已分配发票 ID 列表: 逗号分隔';

COMMENT ON COLUMN pmis_project_payment.allocated_amount IS '已核销金额(元): 关联到发票';

COMMENT ON COLUMN pmis_project_payment.unallocated_amount IS '未核销金额(元): amount - allocatedAmount';

COMMENT ON COLUMN pmis_project_payment.status IS '回款状态: PENDING 待确认 / RECEIVED 已到账 / PARTIAL 部分核销 / ALLOCATED 已核销完 / CANCELLED 已取消';

COMMENT ON COLUMN pmis_project_payment.remark IS '备注';

COMMENT ON COLUMN pmis_project_payment.confirmed_by IS '确认人 ID: 财务确认到账';

COMMENT ON COLUMN pmis_project_payment.confirmed_at IS '确认时间';

COMMENT ON COLUMN pmis_project_payment.recorded_by IS '录入人 ID';

COMMENT ON COLUMN pmis_project_payment.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_project_payment.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_project_payment.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_pfp_*)
CREATE INDEX IF NOT EXISTS idx_pfp_tenant_contract
    ON pmis_project_payment(tenant_id, contract_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfp_tenant_initiation
    ON pmis_project_payment(tenant_id, initiation_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfp_tenant_customer_status
    ON pmis_project_payment(tenant_id, customer_id, status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfp_tenant_payment_date
    ON pmis_project_payment(tenant_id, payment_date)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfp_tenant_customer_unalloc
    ON pmis_project_payment(tenant_id, customer_id, unallocated_amount)
    WHERE deleted = 0 AND status IN ('PENDING','RECEIVED','PARTIAL');

-- =====================================================
-- 3. 客户信用表 pmis_project_customer_credit

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_project_customer_credit(
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

COMMENT ON TABLE  pmis_project_customer_credit IS '客户信用表: 客户信用评分与等级（A/B/C/D）,CustomerCreditScoreEvaluator 评分（0-100）';

COMMENT ON COLUMN pmis_project_customer_credit.customer_id IS '客户 ID: 全局唯一';

COMMENT ON COLUMN pmis_project_customer_credit.customer_name IS '客户名称（冗余）';

COMMENT ON COLUMN pmis_project_customer_credit.credit_level IS '信用等级: A=优质(90-100) B=良好(75-89) C=一般(60-74) D=风险(0-59),fromScore() 使用 >= 比较';

COMMENT ON COLUMN pmis_project_customer_credit.credit_score IS '信用分: 0-100,新客户默认 30 分（A 级基线）';

COMMENT ON COLUMN pmis_project_customer_credit.total_contract_amount IS '累计合同金额(元)';

COMMENT ON COLUMN pmis_project_customer_credit.total_invoiced_amount IS '累计开票金额(元)';

COMMENT ON COLUMN pmis_project_customer_credit.total_received_amount IS '累计回款金额(元)';

COMMENT ON COLUMN pmis_project_customer_credit.on_time_rate IS '及时回款率: 0.85=85%';

COMMENT ON COLUMN pmis_project_customer_credit.contract_count IS '合同总数';

COMMENT ON COLUMN pmis_project_customer_credit.overdue_count IS '逾期次数';

COMMENT ON COLUMN pmis_project_customer_credit.last_evaluation_at IS '最近一次评估时间';

COMMENT ON COLUMN pmis_project_customer_credit.evaluator IS '评估人/评估器名称';

COMMENT ON COLUMN pmis_project_customer_credit.remark IS '备注';

COMMENT ON COLUMN pmis_project_customer_credit.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_project_customer_credit.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_project_customer_credit.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_pfcc_level / idx_pfcc_tenant)
CREATE INDEX IF NOT EXISTS idx_pfcc_tenant_level_score
    ON pmis_project_customer_credit(tenant_id, credit_level, credit_score DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfcc_tenant_updated
    ON pmis_project_customer_credit(tenant_id, updated_at DESC)
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
--       对内成本费率(pmis_rate_internal)、利润测算版本(pmis_project_profit_simulation)
-- =====================================================

-- =====================================================
-- 1. EVM 挣值测量表 pmis_evm_measure

-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_project_profit_simulation(
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

COMMENT ON TABLE  pmis_project_profit_simulation IS '利润测算版本表 What-if: 同一立项支持多个测算版本,create() 自动 version=max+1,APPROVED/ARCHIVED 状态禁止删除';

COMMENT ON COLUMN pmis_project_profit_simulation.simulation_code IS '测算单号: 业务唯一,如 SIM-2026-001';

COMMENT ON COLUMN pmis_project_profit_simulation.simulation_name IS '测算名称';

COMMENT ON COLUMN pmis_project_profit_simulation.initiation_id IS '所属立项 ID';

COMMENT ON COLUMN pmis_project_profit_simulation.version IS '版本号: 同立项内递增,create() 时自动 max+1';

COMMENT ON COLUMN pmis_project_profit_simulation.scenario_type IS '场景类型: BASE 基准 / OPTIMISTIC 乐观 / PESSIMISTIC 悲观 / CUSTOM 自定义';

COMMENT ON COLUMN pmis_project_profit_simulation.contract_amount IS '合同金额(元)';

COMMENT ON COLUMN pmis_project_profit_simulation.external_revenue IS '外部收入(元): 对外报价合计';

COMMENT ON COLUMN pmis_project_profit_simulation.internal_cost IS '内部成本(元): 人力 + 采购 + 费用 + 外包';

COMMENT ON COLUMN pmis_project_profit_simulation.expected_hours IS '预计工时(小时)';

COMMENT ON COLUMN pmis_project_profit_simulation.blended_rate IS '综合人天费率(元)';

COMMENT ON COLUMN pmis_project_profit_simulation.gross_profit IS '毛利润(元) = external_revenue - internal_cost';

COMMENT ON COLUMN pmis_project_profit_simulation.gross_margin IS '毛利率: 0.25=25%';

COMMENT ON COLUMN pmis_project_profit_simulation.target_margin IS '目标毛利率: 业务方预设的达标线';

COMMENT ON COLUMN pmis_project_profit_simulation.labor_cost IS '人工成本(元)';

COMMENT ON COLUMN pmis_project_profit_simulation.purchase_cost IS '采购成本(元)';

COMMENT ON COLUMN pmis_project_profit_simulation.expense_cost IS '费用(元)';

COMMENT ON COLUMN pmis_project_profit_simulation.outsource_cost IS '外包成本(元)';

COMMENT ON COLUMN pmis_project_profit_simulation.assumptions IS '假设条件 JSON: 输入参数快照';

COMMENT ON COLUMN pmis_project_profit_simulation.status IS '测算状态: DRAFT 草稿 / SUBMITTED 已提交 / APPROVED 已批准 / REJECTED 已驳回 / ARCHIVED 已归档,REJECTED 可回退到 DRAFT';

COMMENT ON COLUMN pmis_project_profit_simulation.approver_name IS '审批人姓名（冗余）';

COMMENT ON COLUMN pmis_project_profit_simulation.approved_at IS '审批时间';

COMMENT ON COLUMN pmis_project_profit_simulation.remark IS '备注';

COMMENT ON COLUMN pmis_project_profit_simulation.applicant_id IS '申请人 ID';

COMMENT ON COLUMN pmis_project_profit_simulation.applicant_name IS '申请人姓名（冗余）';

COMMENT ON COLUMN pmis_project_profit_simulation.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_project_profit_simulation.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_project_profit_simulation.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_psm_initiation / idx_psm_version / idx_psm_status)
CREATE INDEX IF NOT EXISTS idx_psm_tenant_initiation_version
    ON pmis_project_profit_simulation(tenant_id, initiation_id, version DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_psm_tenant_status_scenario
    ON pmis_project_profit_simulation(tenant_id, status, scenario_type)
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
       COALESCE((SELECT SUM(amount) FROM pmis_project_revenue r
                  WHERE r.initiation_id = i.id AND r.deleted = 0
                    AND r.tenant_id = i.tenant_id), 0)         AS total_revenue,
       COALESCE((SELECT SUM(amount) FROM pmis_project_invoice p
                  WHERE p.initiation_id = i.id AND p.deleted = 0
                    AND p.tenant_id = i.tenant_id), 0)         AS invoiced_amount,
       COALESCE((SELECT SUM(amount) FROM pmis_project_revenue r2
                  WHERE r2.initiation_id = i.id AND r2.deleted = 0
                    AND r2.status = 'CONFIRMED'
                    AND r2.tenant_id = i.tenant_id), 0)        AS confirmed_revenue,
       COALESCE((SELECT SUM(amount) FROM pmis_cost_allocation
                  WHERE initiation_id = i.id AND deleted = 0 AND cost_type = 'LABOR'
                    AND tenant_id = i.tenant_id), 0)           AS labor_cost,
       COALESCE((SELECT SUM(amount) FROM pmis_cost_purchase
                  WHERE initiation_id = i.id AND deleted = 0
                    AND tenant_id = i.tenant_id), 0)          AS purchase_cost,
       COALESCE((SELECT SUM(amount) FROM pmis_project_expense
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
    (SELECT COALESCE(SUM(amount), 0) FROM pmis_project_invoice
        WHERE deleted = 0 AND status IN ('ISSUED','RED_REVERSED')
          AND tenant_id = t.tenant_id)                                            AS total_invoiced,
    (SELECT COALESCE(SUM(allocated_amount), 0) FROM pmis_project_payment
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
CREATE TABLE IF NOT EXISTS pmis_project_reconcile_daily (
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

COMMENT ON TABLE  pmis_project_reconcile_daily IS '每日自动对账表: 成本/收入/回款/开票 跨模块校验,ReconcileServiceImpl 执行';

COMMENT ON COLUMN pmis_project_reconcile_daily.reconcile_date IS '对账日期: 每日 02:00 触发';

COMMENT ON COLUMN pmis_project_reconcile_daily.reconcile_type IS '对账类型: COST 成本 / REVENUE 收入 / PAYMENT 回款 / INVOICE 开票 / TIMESHEET 工时 / PROFIT 利润 / BENCH 闲置 / BUDGET 预算';

COMMENT ON COLUMN pmis_project_reconcile_daily.initiation_id IS '所属立项 ID: 可空,NULL 表示全局维度';

COMMENT ON COLUMN pmis_project_reconcile_daily.expected_amount IS '应计金额(元)';

COMMENT ON COLUMN pmis_project_reconcile_daily.actual_amount IS '实计金额(元)';

COMMENT ON COLUMN pmis_project_reconcile_daily.diff_amount IS '差异金额(元) = actual - expected';

COMMENT ON COLUMN pmis_project_reconcile_daily.diff_pct IS '差异比例: -1 ~ 1,例如 0.05=5%';

COMMENT ON COLUMN pmis_project_reconcile_daily.status IS '对账状态: OK 一致 / WARN 警告（|diff_pct| < 5%）/ FAIL 失败（|diff_pct| >= 5%）';

COMMENT ON COLUMN pmis_project_reconcile_daily.detail IS '对账明细 JSON: 列出差异项';

COMMENT ON COLUMN pmis_project_reconcile_daily.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_project_reconcile_daily.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_project_reconcile_daily.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的单列索引)
CREATE INDEX IF NOT EXISTS idx_prd_tenant_date_type
    ON pmis_project_reconcile_daily(tenant_id, reconcile_date DESC, reconcile_type)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_prd_tenant_init_date
    ON pmis_project_reconcile_daily(tenant_id, initiation_id, reconcile_date DESC)
    WHERE deleted = 0 AND initiation_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_prd_tenant_status_date
    ON pmis_project_reconcile_daily(tenant_id, status, reconcile_date DESC)
    WHERE deleted = 0 AND status IN ('WARN','FAIL');

-- 唯一约束：每天每个维度只能有一条
CREATE UNIQUE INDEX IF NOT EXISTS uk_prd_tenant_date_type_init
    ON pmis_project_reconcile_daily(tenant_id, reconcile_date, reconcile_type, COALESCE(initiation_id, '0'), deleted);

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
