-- =====================================================
-- PMIS 工作流审计日志表 DDL（对标竞品审批轨迹能力）
-- 版本: V1.0.0_025
-- 描述: 新增 pmis_flow_audit_log 表，记录审批全操作轨迹
--       覆盖：START/PASS/REJECT/TRANSFER/DELEGATE/COUNTERSIGN/RECALL/URGE/TERMINATE/SUSPEND/ACTIVATE/CLAIM
-- 设计参考: 钉钉/飞书审批操作日志 + Warm-Flow audit_log
-- 适用场景: 审批轨迹查询、合规审计、操作回溯
-- =====================================================

-- -----------------------------------------------------
-- 8. 流程审计日志表（pmis_flow_audit_log）
--    记录流程全生命周期的操作轨迹：谁在何时对哪个实例/任务做了什么操作
-- -----------------------------------------------------
DROP TABLE IF EXISTS pmis_flow_audit_log;
CREATE TABLE pmis_flow_audit_log (
    id                 BIGSERIAL    PRIMARY KEY,
    instance_id        BIGINT       NOT NULL,
    task_id            BIGINT,
    flow_code          VARCHAR(64)  NOT NULL,
    business_type      VARCHAR(64),
    business_id        VARCHAR(64),
    node_code          VARCHAR(64),
    node_name          VARCHAR(128),
    action             VARCHAR(32)  NOT NULL,
    operator_id        BIGINT,
    operator_name      VARCHAR(64),
    target_id          BIGINT,
    target_name        VARCHAR(64),
    comment            TEXT,
    operated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status             VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_by         BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         BIGINT       NOT NULL DEFAULT 0,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT     NOT NULL DEFAULT 0,
    tenant_id          BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id  VARCHAR(64)
);

COMMENT ON TABLE  pmis_flow_audit_log IS '流程审计日志表: 记录流程全生命周期的操作轨迹(谁在何时对哪个实例/任务做了什么操作)';
COMMENT ON COLUMN pmis_flow_audit_log.instance_id IS '流程实例 ID';
COMMENT ON COLUMN pmis_flow_audit_log.task_id IS '任务 ID(可为空,实例级操作如 START/RECALL 没有对应任务)';
COMMENT ON COLUMN pmis_flow_audit_log.flow_code IS '流程编码(冗余,便于查询)';
COMMENT ON COLUMN pmis_flow_audit_log.business_type IS '业务类型: PROJECT_INITIATION/CONTRACT_CHANGE/CLOSURE 等';
COMMENT ON COLUMN pmis_flow_audit_log.business_id IS '业务对象 ID';
COMMENT ON COLUMN pmis_flow_audit_log.node_code IS '节点编码(操作发生的节点)';
COMMENT ON COLUMN pmis_flow_audit_log.node_name IS '节点名称';
COMMENT ON COLUMN pmis_flow_audit_log.action IS '操作类型: START/PASS/REJECT/TRANSFER/DELEGATE/COUNTERSIGN_BEFORE/COUNTERSIGN_AFTER/RECALL/URGE/TERMINATE/SUSPEND/ACTIVATE/CLAIM/DELEGATE_RETURN/PARALLEL_PASS/SEQUENTIAL_PASS/VOTE_PASS';
COMMENT ON COLUMN pmis_flow_audit_log.operator_id IS '操作人 ID';
COMMENT ON COLUMN pmis_flow_audit_log.operator_name IS '操作人姓名(冗余)';
COMMENT ON COLUMN pmis_flow_audit_log.target_id IS '目标人 ID(转办/委派/加签的目标人)';
COMMENT ON COLUMN pmis_flow_audit_log.target_name IS '目标人姓名';
COMMENT ON COLUMN pmis_flow_audit_log.comment IS '审批意见 / 操作备注';
COMMENT ON COLUMN pmis_flow_audit_log.operated_at IS '操作时间';
COMMENT ON COLUMN pmis_flow_audit_log.status IS '记录状态: ENABLED 启用 / DISABLED 停用';
COMMENT ON COLUMN pmis_flow_audit_log.deleted IS '逻辑删除: 0=未删除,1=已删除';
COMMENT ON COLUMN pmis_flow_audit_log.tenant_id IS '租户 ID(默认 1)';
COMMENT ON COLUMN pmis_flow_audit_log.provider_trace_id IS '链路追踪 ID(来自调用方或自生成)';

CREATE INDEX idx_pfal_instance   ON pmis_flow_audit_log(instance_id);
CREATE INDEX idx_pfal_task       ON pmis_flow_audit_log(task_id);
CREATE INDEX idx_pfal_operator   ON pmis_flow_audit_log(operator_id);
CREATE INDEX idx_pfal_biz        ON pmis_flow_audit_log(business_type, business_id);
CREATE INDEX idx_pfal_action     ON pmis_flow_audit_log(action);
CREATE INDEX idx_pfal_operated   ON pmis_flow_audit_log(operated_at);
CREATE INDEX idx_pfal_tenant     ON pmis_flow_audit_log(tenant_id);
