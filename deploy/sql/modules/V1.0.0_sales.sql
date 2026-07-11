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

