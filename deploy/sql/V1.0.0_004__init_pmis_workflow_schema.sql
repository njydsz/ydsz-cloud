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

COMMENT ON TABLE pmis_workflow_business IS '业务流程实例关联表';
COMMENT ON COLUMN pmis_workflow_business.process_instance_id IS 'Flowable 流程实例 ID';
COMMENT ON COLUMN pmis_workflow_business.process_definition_key IS '流程定义 KEY';
COMMENT ON COLUMN pmis_workflow_business.business_type IS '业务类型';
COMMENT ON COLUMN pmis_workflow_business.business_id IS '业务单据 ID';
COMMENT ON COLUMN pmis_workflow_business.status IS '状态: RUNNING/SUSPENDED/COMPLETED/TERMINATED';

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

COMMENT ON TABLE pmis_workflow_form IS '流程表单定义表';
COMMENT ON COLUMN pmis_workflow_form.form_key IS '表单 KEY(唯一)';
COMMENT ON COLUMN pmis_workflow_form.process_key IS '对应流程定义 KEY';
COMMENT ON COLUMN pmis_workflow_form.schema_json IS '表单 Schema(JSON)';

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

COMMENT ON TABLE pmis_workflow_node_config IS '流程节点配置表';
COMMENT ON COLUMN pmis_workflow_node_config.assignee_type IS '审批人类型: USER/ROLE/DEPT/EXPRESSION/EMPTY';
COMMENT ON COLUMN pmis_workflow_node_config.assignee_value IS '审批人值(用户ID/角色编码/部门ID/SpEL)';

CREATE UNIQUE INDEX uk_wfnc_node ON pmis_workflow_node_config(process_key, node_id, tenant_id);
CREATE INDEX idx_wfnc_process ON pmis_workflow_node_config(process_key);
