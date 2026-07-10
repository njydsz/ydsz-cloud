-- ============================================================
-- PMIS project module SQL
-- Auto-generated from V1.0.0.sql
-- ============================================================

-- --------------------------------------------------------------------

-- ====================================================================
-- V1.0.0_008 已优化内联至 V1.0.0_001 的 pmis_operation_log 定义中
-- (字段已统一为 http_method/method_signature/client_ip/params_json/response_json/trace_id,
--  并补齐 V1.0.0_040 审计差异字段 before_data/after_data)
-- ====================================================================

-- ============================ [009] init pmis project schema ============================
-- [INLINE-OPT] 已统一为单文件 V1.0.0.sql 的最终形态:
--   1) 时间字段 TIMESTAMP → TIMESTAMPTZ
--   2) 全部审计字段统一为 created_by/created_at/updated_by/updated_at
--   3) tenant_id NOT NULL DEFAULT 1;tenant_id 字段统一放在审计字段之后
--   4) 内联 status/level/category/deleted CHECK 约束
--   5) 内联 (tenant_id, created_at DESC) WHERE deleted = 0 复合部分索引
--   6) status/customer/owner 类索引全部加 WHERE deleted = 0 部分条件
--   7) version 字段添加非负 CHECK 约束(乐观锁)
-- =====================================================
-- PMIS 项目全生命周期模块 DDL
-- 版本: V1.0.0_009 (merged into V1.0.0.sql)
-- 描述: 商机、立项、合同、补充协议、合同变更
-- =====================================================

-- =====================================================
-- 1. 商机主表 pmis_project_opportunity
-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_project_opportunity(
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(left(replace(gen_random_uuid()::text,'-',''),20),20),
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
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(left(replace(gen_random_uuid()::text,'-',''),20),20),
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
CREATE TABLE IF NOT EXISTS pmis_project_initiation(
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(left(replace(gen_random_uuid()::text,'-',''),20),20),
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
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(left(replace(gen_random_uuid()::text,'-',''),20),20),
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
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(left(replace(gen_random_uuid()::text,'-',''),20),20),
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
CREATE TABLE IF NOT EXISTS pmis_project_contract(
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(left(replace(gen_random_uuid()::text,'-',''),20),20),
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
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(left(replace(gen_random_uuid()::text,'-',''),20),20),
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
    id                VARCHAR(20)      PRIMARY KEY DEFAULT left(left(replace(gen_random_uuid()::text,'-',''),20),20),
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
CREATE TABLE IF NOT EXISTS pmis_project_contract_template(
    id                     VARCHAR(20) PRIMARY KEY DEFAULT left(left(replace(gen_random_uuid()::text,'-',''),20),20),
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
-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_project_change(
    id                       VARCHAR(20) PRIMARY KEY DEFAULT left(left(replace(gen_random_uuid()::text,'-',''),20),20),
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

-- =====================================================
-- 2. 对外报价费率表 pmis_rate_card
-- =====================================================
-- P1-6: 宸插簾寮?鏃犻渶 DROP), 鏍囪淇濈暀浠ヨ褰曞巻鍙?DROP TABLE IF EXISTS pmis_rate_card; -- 已废弃
CREATE TABLE IF NOT EXISTS pmis_rate_card(
    id                  VARCHAR(20) PRIMARY KEY DEFAULT left(left(replace(gen_random_uuid()::text,'-',''),20),20),
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
    id                  VARCHAR(20) PRIMARY KEY DEFAULT left(left(replace(gen_random_uuid()::text,'-',''),20),20),
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

-- =====================================================
-- 1. 资源池主表 pmis_resource_pool
-- =====================================================
-- P1-6: 宸插簾寮?鏃犻渶 DROP), 鏍囪淇濈暀浠ヨ褰曞巻鍙?DROP TABLE IF EXISTS pmis_resource_pool; -- 已废弃
CREATE TABLE IF NOT EXISTS pmis_resource_pool(
    id                  VARCHAR(20) PRIMARY KEY DEFAULT left(left(replace(gen_random_uuid()::text,'-',''),20),20),
    pool_code           VARCHAR(64)  NOT NULL,
    pool_name           VARCHAR(256) NOT NULL,
    pool_type           VARCHAR(32)  NOT NULL,                 -- HQ/DIVISION/RESERVE
    department_id       VARCHAR(20),                                -- 事业部/部门
    department_name     VARCHAR(256),
    level_range         VARCHAR(32),                           -- L1-L3 / L4-L12 / L13+
    headcount           INTEGER      NOT NULL DEFAULT 0,
    billable_target     INTEGER      NOT NULL DEFAULT 0,
    description         TEXT,
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_prp_code              UNIQUE (pool_code, deleted),
    CONSTRAINT ck_prp_pool_type         CHECK (pool_type IN ('HQ','DIVISION','RESERVE')),
    CONSTRAINT ck_prp_status_enum       CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_prp_headcount_nonneg  CHECK (headcount >= 0),
    CONSTRAINT ck_prp_bill_target_nonneg CHECK (billable_target >= 0),
    CONSTRAINT ck_prp_deleted_enum      CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_resource_pool IS '资源池表: 3 级资源池（HQ 总部 / DIVISION 事业部 / RESERVE 储备）,PoolType.inferByLevel 按职级自动分配';

COMMENT ON COLUMN pmis_resource_pool.pool_code IS '资源池编码: 业务唯一,如 POOL-HQ-GLOBAL';

COMMENT ON COLUMN pmis_resource_pool.pool_name IS '资源池名称';

COMMENT ON COLUMN pmis_resource_pool.pool_type IS '资源池类型: HQ 总部 / DIVISION 事业部 / RESERVE 储备,按职级自动映射';

COMMENT ON COLUMN pmis_resource_pool.department_id IS '所属部门 ID: 池归属的事业部/部门';

COMMENT ON COLUMN pmis_resource_pool.department_name IS '所属部门名称（冗余）';

COMMENT ON COLUMN pmis_resource_pool.level_range IS '职级范围: L1-L3 / L4-L12 / L13+';

COMMENT ON COLUMN pmis_resource_pool.headcount IS '当前人数';

COMMENT ON COLUMN pmis_resource_pool.billable_target IS '计费人头目标: 期望投入计费项目的人数';

COMMENT ON COLUMN pmis_resource_pool.description IS '资源池描述';

COMMENT ON COLUMN pmis_resource_pool.status IS '状态: ACTIVE 启用 / INACTIVE 停用';

COMMENT ON COLUMN pmis_resource_pool.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_resource_pool.provider_trace_id IS '链路追踪 ID';

COMMENT ON COLUMN pmis_resource_pool.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- 复合/部分索引(替代零散的 idx_prp_type_status / idx_prp_dept)
CREATE INDEX IF NOT EXISTS idx_prp_tenant_type_status
    ON pmis_resource_pool(tenant_id, pool_type, status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_prp_tenant_dept
    ON pmis_resource_pool(tenant_id, department_id)
    WHERE deleted = 0 AND department_id IS NOT NULL;

-- =====================================================
-- 5. 初始化三级资源池（HQ/DIVISION/RESERVE）
-- =====================================================
INSERT INTO pmis_resource_pool
    (pool_code, pool_name, pool_type, department_id, department_name, level_range, headcount, billable_target, status, tenant_id, provider_trace_id)
VALUES
    ('POOL-HQ-GLOBAL',        '总部高级资源池',   'HQ',       1, '总部',  'L13+', 0, 0, 'ACTIVE', 1, 'init'),
    ('POOL-DIV-CONSULTING',   '咨询事业部池',    'DIVISION', 2, '咨询事业部', 'L4-L12', 0, 0, 'ACTIVE', 1, 'init'),
    ('POOL-DIV-IMPL',         '实施事业部池',    'DIVISION', 3, '实施事业部', 'L4-L12', 0, 0, 'ACTIVE', 1, 'init'),
        ('POOL-RESERVE-TRAINING', '储备培训池',      'RESERVE',  1, '总部',  'L1-L3', 0, 0, 'ACTIVE', 1, 'init') ON CONFLICT DO NOTHING;

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
-- 1) 质保期
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_warranty (
    id                  VARCHAR(20) PRIMARY KEY DEFAULT left(left(replace(gen_random_uuid()::text,'-',''),20),20),
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
-- 3) 每日对账表
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_reconcile_daily (
    id                  VARCHAR(20) PRIMARY KEY DEFAULT left(left(replace(gen_random_uuid()::text,'-',''),20),20),
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

-- ========== 同步更新 init schema 脚本中的字段注释（仅文档作用，不影响运行） ==========
COMMENT ON COLUMN pmis_project_initiation.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';

COMMENT ON COLUMN pmis_project_contract.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';

COMMENT ON COLUMN pmis_project_contract_change.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';

COMMENT ON COLUMN pmis_project_change.version IS '乐观锁版本号（P1-12），MyBatis-Plus @Version 自动维护';

-- =====================================================================
--  1) 通用审计字段索引（created_at 范围查询 + tenant_id 等值）
-- =====================================================================

-- 项目立项表
CREATE INDEX IF NOT EXISTS idx_pmis_initiation_tenant_created
    ON pmis_project_initiation (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pmis_initiation_status_created
    ON pmis_project_initiation (stage, created_at DESC)
    WHERE deleted = 0;

-- 项目变更表（4.1.1）
CREATE INDEX IF NOT EXISTS idx_pmis_change_initiation_status
    ON pmis_project_change (initiation_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pmis_change_major_flag
    ON pmis_project_change (initiation_id, major_flag)
    WHERE major_flag = 1;

CREATE INDEX IF NOT EXISTS idx_pmis_change_change_code
    ON pmis_project_change (change_code);

CREATE INDEX IF NOT EXISTS idx_pmis_change_provider_trace
    ON pmis_project_change (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- =====================================================================
--  8) 表达式索引（状态名/类型名查询）
-- =====================================================================
CREATE INDEX IF NOT EXISTS idx_pmis_initiation_status_lower
    ON pmis_project_initiation (lower(stage));

CREATE INDEX IF NOT EXISTS idx_pmis_change_status_lower
    ON pmis_project_change (lower(status));

-- =====================================================================
--  9) 统计信息更新
-- =====================================================================
ANALYZE pmis_project_initiation;

ANALYZE pmis_project_change;

-- 商机跟进记录
ALTER TABLE pmis_project_opportunity_follow ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_ppof_tenant ON pmis_project_opportunity_follow(tenant_id);

-- 项目预算明细
ALTER TABLE pmis_project_budget_item ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_ppbi_tenant ON pmis_project_budget_item(tenant_id);

-- 门径评审记录
ALTER TABLE pmis_project_gate_review ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_ppgr_tenant ON pmis_project_gate_review(tenant_id);

ANALYZE pmis_project_opportunity_follow;

ANALYZE pmis_project_budget_item;

ANALYZE pmis_project_gate_review;

-- ====================================================================
-- ============================ [064] P1-7 provider_trace_id 索引补齐 ============================
-- ====================================================================
-- V1.0.0_064  P1-7  provider_trace_id 索引全量补齐
-- ----------------------------------------------------------------------------
-- 背景:
--   互联网大厂标准要求所有携带 provider_trace_id 的业务表必须有专用索引,
--   以支持"按服务商回执 trace 反查单据"的 O(log n) 性能。
--   现状扫描结果: 75 张表携带该字段,12 张已建索引,63 张缺失。
--   本节一次性补齐 63 张缺失表的 partial index(仅索引用得到的值)。
--
-- 设计:
--   - NULLABLE 字段   -> partial index WHERE provider_trace_id IS NOT NULL
--   - NOT NULL DEFAULT '' -> partial index WHERE provider_trace_id <> ''
--   - 索引命名: idx_pmis_<table>_trace,与既有规则一致
--   - 触发器/外键/CHECK 约束: 不新增(本节仅补齐索引,无 schema 变更)
--   - 性能影响: 每张表一个 partial index,索引体积可控
-- ----------------------------------------------------------------------------
-- 涉及表(63 张,按业务模块分组):

-- 1) 项目/执行(7 张)
CREATE INDEX IF NOT EXISTS idx_pmis_project_change_trace
    ON pmis_project_change (provider_trace_id)
    WHERE provider_trace_id <> '';

CREATE INDEX IF NOT EXISTS idx_pmis_rate_card_trace
    ON pmis_rate_card (provider_trace_id)
    WHERE provider_trace_id <> '';

CREATE INDEX IF NOT EXISTS idx_pmis_rate_internal_trace
    ON pmis_rate_internal (provider_trace_id)
    WHERE provider_trace_id <> '';

CREATE INDEX IF NOT EXISTS idx_pmis_resource_pool_trace
    ON pmis_resource_pool (provider_trace_id)
    WHERE provider_trace_id <> '';

-- 4) 运维/告警/工单(5 张)
CREATE INDEX IF NOT EXISTS idx_pmis_warranty_trace
    ON pmis_warranty (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pmis_reconcile_daily_trace
    ON pmis_reconcile_daily (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

