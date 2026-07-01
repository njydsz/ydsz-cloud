-- =====================================================
-- PMIS 工作流基础模块 DDL
-- 版本: V1.0.0_004
-- 描述: 流程业务关联表、流程表单定义
-- 注意: Flowable 引擎表(ACT_*)由 Flowable 启动时自动创建
-- =====================================================

-- -----------------------------------------------------
-- 1. 业务流程实例关联表
-- 用于将流程实例与具体业务单据关联，方便反查
-- -----------------------------------------------------
DROP TABLE IF EXISTS pmis_workflow_business;
CREATE TABLE pmis_workflow_business (
    id              BIGSERIAL PRIMARY KEY,
    process_instance_id   VARCHAR(64)  NOT NULL,
    process_definition_key VARCHAR(128) NOT NULL,
    process_definition_id VARCHAR(128),
    business_type   VARCHAR(64)  NOT NULL,
    business_id     VARCHAR(64)  NOT NULL,
    business_no     VARCHAR(128),
    title           VARCHAR(256),
    initiator_id    BIGINT,
    initiator_name  VARCHAR(64),
    current_node    VARCHAR(128),
    status          VARCHAR(32)  NOT NULL DEFAULT 'RUNNING',
    start_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_time        TIMESTAMP,
    duration_ms     BIGINT,
    tenant_id       BIGINT       DEFAULT 1,
    remark          VARCHAR(512),
    create_by       BIGINT,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0
);

COMMENT ON TABLE pmis_workflow_business IS '业务流程实例关联表: 将 Flowable 流程实例与业务单据双向绑定,支持反查与状态同步';
COMMENT ON COLUMN pmis_workflow_business.id IS '主键 ID';
COMMENT ON COLUMN pmis_workflow_business.process_instance_id IS 'Flowable 流程实例 ID(全局唯一)';
COMMENT ON COLUMN pmis_workflow_business.process_definition_key IS '流程定义 KEY(如 project_initiation/contract_change)';
COMMENT ON COLUMN pmis_workflow_business.process_definition_id IS '流程定义 ID(具体版本)';
COMMENT ON COLUMN pmis_workflow_business.business_type IS '业务类型(如 initiation/contract/invoice)';
COMMENT ON COLUMN pmis_workflow_business.business_id IS '业务单据 ID(关联各业务表)';
COMMENT ON COLUMN pmis_workflow_business.business_no IS '业务单据编号(展示用)';
COMMENT ON COLUMN pmis_workflow_business.title IS '流程标题(展示用,如"张三发起-XXX 项目立项")';
COMMENT ON COLUMN pmis_workflow_business.initiator_id IS '发起人 ID(关联 pmis_employee.id)';
COMMENT ON COLUMN pmis_workflow_business.initiator_name IS '发起人姓名';
COMMENT ON COLUMN pmis_workflow_business.current_node IS '当前节点编码';
COMMENT ON COLUMN pmis_workflow_business.status IS '流程状态: RUNNING 进行中 / SUSPENDED 挂起 / COMPLETED 完成 / TERMINATED 终止';
COMMENT ON COLUMN pmis_workflow_business.start_time IS '流程启动时间';
COMMENT ON COLUMN pmis_workflow_business.end_time IS '流程结束时间';
COMMENT ON COLUMN pmis_workflow_business.duration_ms IS '流程总耗时(毫秒)';
COMMENT ON COLUMN pmis_workflow_business.tenant_id IS '租户 ID(多租户隔离)';
COMMENT ON COLUMN pmis_workflow_business.remark IS '备注';
COMMENT ON COLUMN pmis_workflow_business.create_by IS '创建人 ID';
COMMENT ON COLUMN pmis_workflow_business.create_time IS '创建时间';
COMMENT ON COLUMN pmis_workflow_business.update_by IS '最后修改人 ID';
COMMENT ON COLUMN pmis_workflow_business.update_time IS '最后修改时间';
COMMENT ON COLUMN pmis_workflow_business.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

CREATE INDEX idx_wfb_instance ON pmis_workflow_business(process_instance_id);
CREATE INDEX idx_wfb_def_key ON pmis_workflow_business(process_definition_key);
CREATE INDEX idx_wfb_biz ON pmis_workflow_business(business_type, business_id);
CREATE INDEX idx_wfb_initiator ON pmis_workflow_business(initiator_id);
CREATE INDEX idx_wfb_status ON pmis_workflow_business(status);
CREATE INDEX idx_wfb_tenant ON pmis_workflow_business(tenant_id);

-- -----------------------------------------------------
-- 2. 流程表单定义表
-- 用于存储流程启动时需要的表单 Schema
-- -----------------------------------------------------
DROP TABLE IF EXISTS pmis_workflow_form;
CREATE TABLE pmis_workflow_form (
    id                BIGSERIAL PRIMARY KEY,
    form_key          VARCHAR(64)  NOT NULL,
    form_name         VARCHAR(128) NOT NULL,
    process_key       VARCHAR(128) NOT NULL,
    business_type     VARCHAR(64),
    schema_json       TEXT         NOT NULL,
    version           INT          NOT NULL DEFAULT 1,
    status            VARCHAR(32)  NOT NULL DEFAULT 'ENABLED',
    description       VARCHAR(512),
    tenant_id         BIGINT       DEFAULT 1,
    create_by         BIGINT,
    create_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by         BIGINT,
    update_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT     NOT NULL DEFAULT 0
);

COMMENT ON TABLE pmis_workflow_form IS '流程表单定义表: 流程启动时所需的动态表单 Schema(JSON),支持多版本';
COMMENT ON COLUMN pmis_workflow_form.id IS '主键 ID';
COMMENT ON COLUMN pmis_workflow_form.form_key IS '表单 KEY(全局唯一)';
COMMENT ON COLUMN pmis_workflow_form.form_name IS '表单名称';
COMMENT ON COLUMN pmis_workflow_form.process_key IS '绑定的流程定义 KEY';
COMMENT ON COLUMN pmis_workflow_form.business_type IS '绑定的业务类型';
COMMENT ON COLUMN pmis_workflow_form.schema_json IS '表单 Schema(JSON,描述字段/校验/布局)';
COMMENT ON COLUMN pmis_workflow_form.version IS '表单版本号(迭代)';
COMMENT ON COLUMN pmis_workflow_form.status IS '启用状态: ENABLED 启用 / DISABLED 停用';
COMMENT ON COLUMN pmis_workflow_form.description IS '表单说明';
COMMENT ON COLUMN pmis_workflow_form.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_workflow_form.create_by IS '创建人 ID';
COMMENT ON COLUMN pmis_workflow_form.create_time IS '创建时间';
COMMENT ON COLUMN pmis_workflow_form.update_by IS '最后修改人 ID';
COMMENT ON COLUMN pmis_workflow_form.update_time IS '最后修改时间';
COMMENT ON COLUMN pmis_workflow_form.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

CREATE UNIQUE INDEX uk_wff_key ON pmis_workflow_form(form_key);
CREATE INDEX idx_wff_process ON pmis_workflow_form(process_key);
CREATE INDEX idx_wff_biz ON pmis_workflow_form(business_type);
CREATE INDEX idx_wff_tenant ON pmis_workflow_form(tenant_id);

-- -----------------------------------------------------
-- 3. 流程节点配置表(扩展节点审批人/表单权限)
-- -----------------------------------------------------
DROP TABLE IF EXISTS pmis_workflow_node_config;
CREATE TABLE pmis_workflow_node_config (
    id                BIGSERIAL PRIMARY KEY,
    process_key       VARCHAR(128) NOT NULL,
    node_id           VARCHAR(128) NOT NULL,
    node_name         VARCHAR(128),
    assignee_type     VARCHAR(32)  NOT NULL,
    assignee_value    VARCHAR(512),
    form_field_perm   TEXT,
    extra_json        TEXT,
    tenant_id         BIGINT       DEFAULT 1,
    create_by         BIGINT,
    create_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by         BIGINT,
    update_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT     NOT NULL DEFAULT 0
);

COMMENT ON TABLE pmis_workflow_node_config IS '流程节点配置表: 扩展 Flowable 节点的审批人规则/表单字段权限(只读/必填/隐藏)';
COMMENT ON COLUMN pmis_workflow_node_config.id IS '主键 ID';
COMMENT ON COLUMN pmis_workflow_node_config.process_key IS '流程定义 KEY';
COMMENT ON COLUMN pmis_workflow_node_config.node_id IS '节点 ID';
COMMENT ON COLUMN pmis_workflow_node_config.node_name IS '节点名称';
COMMENT ON COLUMN pmis_workflow_node_config.assignee_type IS '审批人类型: USER 指定用户 / ROLE 角色 / DEPT 部门 / EXPRESSION SpEL 表达式 / EMPTY 无人(自动跳过)';
COMMENT ON COLUMN pmis_workflow_node_config.assignee_value IS '审批人值(根据 type 解析:用户 ID/角色编码/部门 ID/SpEL 表达式)';
COMMENT ON COLUMN pmis_workflow_node_config.form_field_perm IS '表单字段权限 JSON(如 {"amount":"READONLY","reason":"REQUIRED"})';
COMMENT ON COLUMN pmis_workflow_node_config.extra_json IS '扩展配置 JSON(如加签/会签/超时策略)';
COMMENT ON COLUMN pmis_workflow_node_config.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_workflow_node_config.create_by IS '创建人 ID';
COMMENT ON COLUMN pmis_workflow_node_config.create_time IS '创建时间';
COMMENT ON COLUMN pmis_workflow_node_config.update_by IS '最后修改人 ID';
COMMENT ON COLUMN pmis_workflow_node_config.update_time IS '最后修改时间';
COMMENT ON COLUMN pmis_workflow_node_config.deleted IS '逻辑删除标记: 0 未删除 / 1 已删除';

CREATE UNIQUE INDEX uk_wfnc_node ON pmis_workflow_node_config(process_key, node_id, tenant_id);
CREATE INDEX idx_wfnc_process ON pmis_workflow_node_config(process_key);
