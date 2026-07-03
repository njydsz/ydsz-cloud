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
COMMENT ON COLUMN pmis_project_opportunity.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_project_opportunity.created_by IS '创建人 ID';
COMMENT ON COLUMN pmis_project_opportunity.created_at IS '创建时间';
COMMENT ON COLUMN pmis_project_opportunity.updated_by IS '最后修改人 ID';
COMMENT ON COLUMN pmis_project_opportunity.updated_at IS '最后修改时间';
COMMENT ON COLUMN pmis_project_opportunity.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

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
COMMENT ON COLUMN pmis_project_opportunity_follow.created_at IS '创建时间';
COMMENT ON COLUMN pmis_project_opportunity_follow.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';
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
    workflow_id       VARCHAR(64),                       -- 关联自研工作流流程实例
    tenant_id         BIGINT        NOT NULL DEFAULT 1,
    created_by        BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        BIGINT        NOT NULL DEFAULT 0,
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_ppi_code UNIQUE (project_code, deleted)
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
COMMENT ON COLUMN pmis_project_initiation.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_project_initiation.created_by IS '创建人 ID';
COMMENT ON COLUMN pmis_project_initiation.created_at IS '创建时间';
COMMENT ON COLUMN pmis_project_initiation.updated_by IS '最后修改人 ID';
COMMENT ON COLUMN pmis_project_initiation.updated_at IS '最后修改时间';
COMMENT ON COLUMN pmis_project_initiation.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

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
COMMENT ON COLUMN pmis_project_budget_item.created_at IS '创建时间';
COMMENT ON COLUMN pmis_project_budget_item.updated_at IS '最后修改时间';
COMMENT ON COLUMN pmis_project_budget_item.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';
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
COMMENT ON COLUMN pmis_project_gate_review.created_at IS '创建时间';
COMMENT ON COLUMN pmis_project_gate_review.updated_at IS '最后修改时间';
COMMENT ON COLUMN pmis_project_gate_review.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';
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
COMMENT ON COLUMN pmis_project_contract.billing_cycle IS '结算周期(MONTHLY 月结 / QUARTERLY 季结 / MILESTONE 里程碑)';
COMMENT ON COLUMN pmis_project_contract.tax_rate IS '适用税率(0.0000-1.0000)';
COMMENT ON COLUMN pmis_project_contract.status IS '合同状态: DRAFT 草稿 / SUBMITTED 已提交 / APPROVING 审批中 / ACTIVE 执行中 / SUSPENDED 暂停 / EXPIRED 已到期 / TERMINATED 已终止';
COMMENT ON COLUMN pmis_project_contract.risk_level IS '风险等级: LOW 低 / MEDIUM 中 / HIGH 高';
COMMENT ON COLUMN pmis_project_contract.risk_notes IS '风险说明';
COMMENT ON COLUMN pmis_project_contract.owner_id IS '合同负责人 ID(销售/客户经理)';
COMMENT ON COLUMN pmis_project_contract.owner_name IS '合同负责人姓名';
COMMENT ON COLUMN pmis_project_contract.contract_file_id IS '合同文件 ID(关联 pmis_file.id)';
COMMENT ON COLUMN pmis_project_contract.workflow_id IS '审批流程实例 ID';
COMMENT ON COLUMN pmis_project_contract.remark IS '备注';
COMMENT ON COLUMN pmis_project_contract.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_project_contract.created_by IS '创建人 ID';
COMMENT ON COLUMN pmis_project_contract.created_at IS '创建时间';
COMMENT ON COLUMN pmis_project_contract.updated_by IS '最后修改人 ID';
COMMENT ON COLUMN pmis_project_contract.updated_at IS '最后修改时间';
COMMENT ON COLUMN pmis_project_contract.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

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
COMMENT ON COLUMN pmis_project_contract_supplement.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_project_contract_supplement.created_by IS '创建人 ID';
COMMENT ON COLUMN pmis_project_contract_supplement.created_at IS '创建时间';
COMMENT ON COLUMN pmis_project_contract_supplement.updated_by IS '最后修改人 ID';
COMMENT ON COLUMN pmis_project_contract_supplement.updated_at IS '最后修改时间';
COMMENT ON COLUMN pmis_project_contract_supplement.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';
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
COMMENT ON COLUMN pmis_project_contract_change.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_project_contract_change.created_by IS '创建人 ID';
COMMENT ON COLUMN pmis_project_contract_change.created_at IS '创建时间';
COMMENT ON COLUMN pmis_project_contract_change.updated_by IS '最后修改人 ID';
COMMENT ON COLUMN pmis_project_contract_change.updated_at IS '最后修改时间';
COMMENT ON COLUMN pmis_project_contract_change.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';
CREATE INDEX idx_ppcc_contract ON pmis_project_contract_change (contract_id);
CREATE INDEX idx_ppcc_status   ON pmis_project_contract_change (status);
