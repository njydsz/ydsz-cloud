-- =============================================================
-- V1.0.0_026__add_pmis_flow_cc.sql
-- 流程抄送表
--
-- P0-3: 抄送中心（对标钉钉/飞书的"抄送我的"独立 Tab）。
--      CC 节点是工作流的核心协作模型之一。
--      抄送记录独立成表，区别于任务表（无需办理动作），便于：
--        1. 独立查询"抄送我的"
--        2. 抄送接收人可读/未读
--        3. 抄送触发站内信触达
-- =============================================================

-- -------------------------------------------
-- 1. 抄送主表
-- -------------------------------------------
DROP TABLE IF EXISTS pmis_flow_cc;
CREATE TABLE pmis_flow_cc (
    id                 BIGSERIAL PRIMARY KEY,
    tenant_id          BIGINT       NOT NULL,
    instance_id        BIGINT       NOT NULL,
    task_id            BIGINT,
    node_code          VARCHAR(64)  NOT NULL,
    node_name          VARCHAR(128),
    flow_code          VARCHAR(64)  NOT NULL,
    flow_name          VARCHAR(128),
    business_key       VARCHAR(128),
    cc_user_id         BIGINT       NOT NULL,
    cc_user_name       VARCHAR(64),
    cc_type            VARCHAR(16)  NOT NULL DEFAULT 'CC_NODE',
    trigger_user_id    BIGINT,
    trigger_user_name  VARCHAR(64),
    title              VARCHAR(255),
    content            TEXT,
    read_status        VARCHAR(16)  NOT NULL DEFAULT 'UNREAD',
    read_at            TIMESTAMP,
    provider_trace_id  VARCHAR(64),
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT     NOT NULL DEFAULT 0
);

COMMENT ON TABLE pmis_flow_cc IS '流程抄送记录 - 抄送中心查询主体（对标钉钉/飞书）';
COMMENT ON COLUMN pmis_flow_cc.tenant_id IS '租户 ID（多租户隔离）';
COMMENT ON COLUMN pmis_flow_cc.instance_id IS '流程实例 ID';
COMMENT ON COLUMN pmis_flow_cc.task_id IS '触发的任务 ID（CC 节点任务，可空）';
COMMENT ON COLUMN pmis_flow_cc.node_code IS '触发抄送的节点编码';
COMMENT ON COLUMN pmis_flow_cc.node_name IS '节点名称';
COMMENT ON COLUMN pmis_flow_cc.flow_code IS '流程定义编码';
COMMENT ON COLUMN pmis_flow_cc.flow_name IS '流程名称';
COMMENT ON COLUMN pmis_flow_cc.business_key IS '业务单据 ID';
COMMENT ON COLUMN pmis_flow_cc.cc_user_id IS '抄送接收人 ID';
COMMENT ON COLUMN pmis_flow_cc.cc_user_name IS '抄送接收人姓名';
COMMENT ON COLUMN pmis_flow_cc.cc_type IS '抄送类型：CC_NODE=抄送节点 / MANUAL_CC=人工抄送 / AUTO_CC=自动抄送（如发起人）';
COMMENT ON COLUMN pmis_flow_cc.trigger_user_id IS '触发抄送的人（发起人/审批人）';
COMMENT ON COLUMN pmis_flow_cc.title IS '抄送标题';
COMMENT ON COLUMN pmis_flow_cc.content IS '抄送内容/意见';
COMMENT ON COLUMN pmis_flow_cc.read_status IS '已读状态：UNREAD / READ';
COMMENT ON COLUMN pmis_flow_cc.read_at IS '已读时间';
COMMENT ON COLUMN pmis_flow_cc.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_flow_cc.deleted IS '逻辑删除标记 0=未删 1=已删';

-- 索引：抄送中心查询优化
CREATE INDEX idx_pmis_flow_cc_tenant_user
    ON pmis_flow_cc (tenant_id, cc_user_id, read_status, deleted)
    WHERE deleted = 0;
CREATE INDEX idx_pmis_flow_cc_instance
    ON pmis_flow_cc (tenant_id, instance_id, deleted)
    WHERE deleted = 0;
CREATE INDEX idx_pmis_flow_cc_biz
    ON pmis_flow_cc (tenant_id, business_key, deleted)
    WHERE deleted = 0;
CREATE INDEX idx_pmis_flow_cc_created
    ON pmis_flow_cc (tenant_id, created_at DESC)
    WHERE deleted = 0;

-- -------------------------------------------
-- 2. 抄送触发配置表（cc 配置由用户/系统预置，无需触发时由节点类型决定）
-- -------------------------------------------
DROP TABLE IF EXISTS pmis_flow_cc_rule;
CREATE TABLE pmis_flow_cc_rule (
    id                 BIGSERIAL PRIMARY KEY,
    tenant_id          BIGINT       NOT NULL,
    flow_code          VARCHAR(64)  NOT NULL,
    node_code          VARCHAR(64)  NOT NULL,
    rule_type          VARCHAR(16)  NOT NULL,
    rule_target        VARCHAR(255) NOT NULL,
    enabled            SMALLINT     NOT NULL DEFAULT 1,
    provider_trace_id  VARCHAR(64),
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT     NOT NULL DEFAULT 0
);

COMMENT ON TABLE pmis_flow_cc_rule IS '流程抄送规则配置 - 自动抄送规则（如：变更金额>1万自动抄送 CEO）';
COMMENT ON COLUMN pmis_flow_cc_rule.rule_type IS '规则类型：USER/ROLE/DEPT/SPEL';
COMMENT ON COLUMN pmis_flow_cc_rule.rule_target IS '规则目标：用户/角色/部门/SpEL 表达式';
COMMENT ON COLUMN pmis_flow_cc_rule.enabled IS '是否启用 0=停用 1=启用';

CREATE INDEX idx_pmis_flow_cc_rule_tenant
    ON pmis_flow_cc_rule (tenant_id, flow_code, node_code, deleted)
    WHERE deleted = 0;
