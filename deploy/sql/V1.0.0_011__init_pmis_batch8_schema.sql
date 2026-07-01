-- =====================================================
-- PMIS 批次8 DDL：合同模板/项目变更/项目交付/项目结项/AI智能体
-- 版本: V1.0.0_011
-- 描述: 合同模板(Project)、项目变更(Project)、交付物标准(Execution)、
--       交付物实例(Execution)、项目结项(Execution)、AI预测(Agent)
-- =====================================================

-- =====================================================
-- 1. 合同模板表 pmis_project_contract_template
-- =====================================================
DROP TABLE IF EXISTS pmis_project_contract_template;
CREATE TABLE pmis_project_contract_template (
    id                     BIGSERIAL PRIMARY KEY,
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
    author_id              BIGINT,
    author_name            VARCHAR(64),
    remark                 TEXT,
    tenant_id              BIGINT       NOT NULL DEFAULT 1,
    created_by             BIGINT       NOT NULL DEFAULT 0,
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by             BIGINT       NOT NULL DEFAULT 0,
    updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_ppct_code UNIQUE (template_code, deleted)
);
COMMENT ON TABLE pmis_project_contract_template IS '合同模板表（8类项目类型）';
CREATE INDEX idx_ppct_type_status ON pmis_project_contract_template(contract_type, status);
CREATE INDEX idx_ppct_tenant ON pmis_project_contract_template(tenant_id);

-- =====================================================
-- 2. 项目变更主表 pmis_project_change
-- =====================================================
DROP TABLE IF EXISTS pmis_project_change;
CREATE TABLE pmis_project_change (
    id                       BIGSERIAL PRIMARY KEY,
    change_code              VARCHAR(64)  NOT NULL,
    initiation_id            BIGINT       NOT NULL,
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
    applicant_id             BIGINT       NOT NULL,
    applicant_name           VARCHAR(64),
    contract_id              BIGINT,
    workflow_id              VARCHAR(64),
    status                   VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',  -- ChangeStatus
    submitted_at             TIMESTAMP,
    approved_at              TIMESTAMP,
    executed_at              TIMESTAMP,
    remark                   TEXT,
    tenant_id                BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id        VARCHAR(64)  NOT NULL DEFAULT '',
    created_by               BIGINT       NOT NULL DEFAULT 0,
    created_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by               BIGINT       NOT NULL DEFAULT 0,
    updated_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                  SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pch_code UNIQUE (change_code, deleted)
);
COMMENT ON TABLE pmis_project_change IS '项目变更主表（5类变更）';
CREATE INDEX idx_pch_initiation ON pmis_project_change(initiation_id);
CREATE INDEX idx_pch_type_status ON pmis_project_change(change_type, status);
CREATE INDEX idx_pch_major ON pmis_project_change(initiation_id, major_flag);

-- =====================================================
-- 3. 交付物标准表 pmis_execution_delivery_standard
-- =====================================================
DROP TABLE IF EXISTS pmis_execution_delivery_standard;
CREATE TABLE pmis_execution_delivery_standard (
    id                    BIGSERIAL PRIMARY KEY,
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
    tenant_id             BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id     VARCHAR(64)  NOT NULL DEFAULT '',
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               SMALLINT     NOT NULL DEFAULT 0
);
COMMENT ON TABLE pmis_execution_delivery_standard IS '交付物标准表（8类项目类型 × 5门径阶段）';
CREATE INDEX idx_peds_type_level ON pmis_execution_delivery_standard(project_type, project_level);
CREATE INDEX idx_peds_stage ON pmis_execution_delivery_standard(stage);

-- =====================================================
-- 4. 交付物实例表 pmis_execution_delivery_item
-- =====================================================
DROP TABLE IF EXISTS pmis_execution_delivery_item;
CREATE TABLE pmis_execution_delivery_item (
    id                    BIGSERIAL PRIMARY KEY,
    item_code             VARCHAR(64)  NOT NULL,
    initiation_id         BIGINT       NOT NULL,
    standard_id           BIGINT,
    project_type          VARCHAR(32),
    project_level         VARCHAR(16),
    delivery_name         VARCHAR(256) NOT NULL,
    delivery_category     VARCHAR(32)  NOT NULL DEFAULT 'DOC',
    stage                 VARCHAR(32)  NOT NULL,
    required              SMALLINT     NOT NULL DEFAULT 1,
    planned_submit_date   DATE,
    actual_submit_date    DATE,
    accepted_date         DATE,
    submitter_id          BIGINT,
    submitter_name        VARCHAR(64),
    reviewer_id           BIGINT,
    reviewer_name         VARCHAR(64),
    review_comment        TEXT,
    status                VARCHAR(32)  NOT NULL DEFAULT 'PENDING',  -- DeliveryItemStatus
    tr_required           SMALLINT     NOT NULL DEFAULT 0,
    tr_completed          SMALLINT     NOT NULL DEFAULT 0,
    file_ids              VARCHAR(2048) NOT NULL DEFAULT '[]',
    remark                TEXT,
    tenant_id             BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id     VARCHAR(64)  NOT NULL DEFAULT '',
    created_by            BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            BIGINT       NOT NULL DEFAULT 0,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pedi_code UNIQUE (item_code, deleted)
);
COMMENT ON TABLE pmis_execution_delivery_item IS '交付物实例表';
CREATE INDEX idx_pedi_initiation ON pmis_execution_delivery_item(initiation_id);
CREATE INDEX idx_pedi_stage ON pmis_execution_delivery_item(initiation_id, stage);
CREATE INDEX idx_pedi_status ON pmis_execution_delivery_item(status);

-- =====================================================
-- 5. 项目结项主表 pmis_execution_closure
-- =====================================================
DROP TABLE IF EXISTS pmis_execution_closure;
CREATE TABLE pmis_execution_closure (
    id                       BIGSERIAL PRIMARY KEY,
    closure_code             VARCHAR(64)  NOT NULL,
    initiation_id            BIGINT       NOT NULL,
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
    applicant_id             BIGINT,
    applicant_name           VARCHAR(64),
    approver_id              BIGINT,
    approver_name            VARCHAR(64),
    submitted_at             TIMESTAMP,
    approved_at              TIMESTAMP,
    archived_at              TIMESTAMP,
    approval_comment         TEXT,
    tenant_id                BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id        VARCHAR(64)  NOT NULL DEFAULT '',
    created_by               BIGINT       NOT NULL DEFAULT 0,
    created_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by               BIGINT       NOT NULL DEFAULT 0,
    updated_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                  SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pec_code UNIQUE (closure_code, deleted)
);
COMMENT ON TABLE pmis_execution_closure IS '项目结项主表（正式/预结项/强制）';
CREATE INDEX idx_pec_initiation ON pmis_execution_closure(initiation_id);
CREATE INDEX idx_pec_type_status ON pmis_execution_closure(closure_type, status);

-- =====================================================
-- 6. AI 智能体预测/推荐结果表 pmis_agent_prediction
-- =====================================================
DROP TABLE IF EXISTS pmis_agent_prediction;
CREATE TABLE pmis_agent_prediction (
    id                  BIGSERIAL PRIMARY KEY,
    task_code           VARCHAR(64)  NOT NULL,
    agent_type          VARCHAR(32)  NOT NULL,                  -- RISK_WARNING/RESOURCE_RECOMMEND/PROFIT_FORECAST/WIN_RATE_PREDICT/TIMESHEET_ANOMALY
    biz_type            VARCHAR(32),                            -- PROJECT/OPPORTUNITY/TIMESHEET/STAFF
    biz_id              BIGINT,
    biz_ref             VARCHAR(256),
    input_snapshot      TEXT,                                   -- 输入数据 JSON
    output_result       TEXT,                                   -- 输出数据 JSON
    alert_level         VARCHAR(16)  NOT NULL DEFAULT 'NORMAL', -- INFO/YELLOW/RED/NORMAL/RECOMMEND
    score               NUMERIC(7,2) NOT NULL DEFAULT 0,        -- 0-100
    confidence          NUMERIC(4,2) NOT NULL DEFAULT 0,        -- 0-1
    suggestion          TEXT,
    matched_rules       VARCHAR(2048) NOT NULL DEFAULT '[]',   -- 命中规则 JSON
    cost_ms             BIGINT       NOT NULL DEFAULT 0,
    model_version       VARCHAR(32)  NOT NULL DEFAULT 'v1.0.0',
    status              VARCHAR(32)  NOT NULL DEFAULT 'PENDING',  -- PENDING/RUNNING/SUCCESS/FAILED
    error_msg           TEXT,
    caller_id           BIGINT,
    caller_name         VARCHAR(64),
    source              VARCHAR(32)  NOT NULL DEFAULT 'MANUAL', -- MANUAL/SCHEDULED/EVENT
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pap_code UNIQUE (task_code, deleted)
);
COMMENT ON TABLE pmis_agent_prediction IS 'AI 智能体预测/推荐结果表';
CREATE INDEX idx_pap_biz ON pmis_agent_prediction(biz_type, biz_id);
CREATE INDEX idx_pap_agent_level ON pmis_agent_prediction(agent_type, alert_level);
CREATE INDEX idx_pap_status ON pmis_agent_prediction(status);

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
    ('OTHER', NULL, '验收报告',               'DOC',     'CD5_GO_LIVE', 1, 0, '客户签字', 1);

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
    ('TPL-OTH-001',  '通用合同模板',              'OTHER',        '1.0.0', '5-5（启动50%/验收50%）',                            30, 0.0010, '依项目类型',                       '项目章程/交付物清单/验收报告', 'PUBLISHED', 1);
