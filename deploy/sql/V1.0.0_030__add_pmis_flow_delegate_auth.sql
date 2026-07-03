-- =============================================================
-- V1.0.0_030__add_pmis_flow_delegate_auth.sql
-- 流程委派代理（长期授权）
--
-- P1-4: 长期授权委派（对标钉钉/飞书的"代理人"功能）
--      与"单任务委派"不同：用户预先设置规则，
--      在 [startTime, endTime] 区间内到达的指定流程/节点/角色 自动转给被代理人。
--      支持多种匹配模式：
--        - ALL: 全部流程
--        - FLOW: 指定流程编码
--        - FLOW_NODE: 指定流程+节点
--        - ROLE: 指定角色任务
--      支持撤回/启用停用、审计追溯（被代理操作时任务 assigneeId 仍记录被委派人）。
-- =============================================================

-- -------------------------------------------
-- 1. 委派代理主表
-- -------------------------------------------
DROP TABLE IF EXISTS pmis_flow_delegate_auth;
CREATE TABLE pmis_flow_delegate_auth (
    id                    BIGSERIAL PRIMARY KEY,
    tenant_id             BIGINT       NOT NULL,
    owner_user_id         BIGINT       NOT NULL,
    owner_user_name       VARCHAR(64),
    delegate_user_id      BIGINT       NOT NULL,
    delegate_user_name    VARCHAR(64),
    -- 匹配模式: ALL/FLOW/FLOW_NODE/ROLE
    scope_type            VARCHAR(16)  NOT NULL,
    -- 流程编码（scopeType=FLOW/FLOW_NODE 时必填）
    flow_code             VARCHAR(64),
    -- 节点编码（scopeType=FLOW_NODE 时必填）
    node_code             VARCHAR(64),
    -- 角色编码（scopeType=ROLE 时必填）
    role_code             VARCHAR(64),
    start_time            TIMESTAMP    NOT NULL,
    end_time              TIMESTAMP    NOT NULL,
    -- 状态: ENABLED=启用 DISABLED=停用 EXPIRED=已过期 REVOKED=已撤回
    auth_status           VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    reason                VARCHAR(255),
    provider_trace_id     VARCHAR(64),
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               SMALLINT     NOT NULL DEFAULT 0
);

COMMENT ON TABLE pmis_flow_delegate_auth IS '流程委派代理（长期授权）- 预置规则区间内任务自动转给被委派人';
COMMENT ON COLUMN pmis_flow_delegate_auth.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_flow_delegate_auth.owner_user_id IS '授权人（原办理人）ID';
COMMENT ON COLUMN pmis_flow_delegate_auth.owner_user_name IS '授权人姓名';
COMMENT ON COLUMN pmis_flow_delegate_auth.delegate_user_id IS '被授权人（代理人）ID';
COMMENT ON COLUMN pmis_flow_delegate_auth.delegate_user_name IS '被授权人姓名';
COMMENT ON COLUMN pmis_flow_delegate_auth.scope_type IS '匹配模式：ALL=全部 / FLOW=指定流程 / FLOW_NODE=指定流程+节点 / ROLE=指定角色';
COMMENT ON COLUMN pmis_flow_delegate_auth.flow_code IS '流程编码（FLOW/FLOW_NODE 模式必填）';
COMMENT ON COLUMN pmis_flow_delegate_auth.node_code IS '节点编码（FLOW_NODE 模式必填）';
COMMENT ON COLUMN pmis_flow_delegate_auth.role_code IS '角色编码（ROLE 模式必填）';
COMMENT ON COLUMN pmis_flow_delegate_auth.start_time IS '生效开始时间';
COMMENT ON COLUMN pmis_flow_delegate_auth.end_time IS '生效结束时间';
COMMENT ON COLUMN pmis_flow_delegate_auth.auth_status IS '状态：ENABLED/DISABLED/EXPIRED/REVOKED';
COMMENT ON COLUMN pmis_flow_delegate_auth.reason IS '授权原因（出差/休假/授权）';
COMMENT ON COLUMN pmis_flow_delegate_auth.provider_trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_flow_delegate_auth.deleted IS '逻辑删除标记';

-- 索引：按 owner 查询我的授权记录
CREATE INDEX idx_pmis_flow_delegate_auth_owner
    ON pmis_flow_delegate_auth (tenant_id, owner_user_id, auth_status, deleted)
    WHERE deleted = 0;

-- 索引：按 delegate 查询代理给我的任务（创建任务时反向匹配）
CREATE INDEX idx_pmis_flow_delegate_auth_delegate
    ON pmis_flow_delegate_auth (tenant_id, delegate_user_id, auth_status, deleted)
    WHERE deleted = 0;

-- 索引：按生效时间扫描待生效/已过期记录
CREATE INDEX idx_pmis_flow_delegate_auth_time
    ON pmis_flow_delegate_auth (tenant_id, start_time, end_time, deleted)
    WHERE deleted = 0;

-- 索引：按流程编码匹配（创建任务时）
CREATE INDEX idx_pmis_flow_delegate_auth_flow
    ON pmis_flow_delegate_auth (tenant_id, flow_code, auth_status, deleted)
    WHERE deleted = 0;

-- -------------------------------------------
-- 2. 委派代理使用日志（审计追溯：谁在什么时间被代理处理了什么任务）
-- -------------------------------------------
DROP TABLE IF EXISTS pmis_flow_delegate_log;
CREATE TABLE pmis_flow_delegate_log (
    id                 BIGSERIAL PRIMARY KEY,
    tenant_id          BIGINT       NOT NULL,
    auth_id            BIGINT       NOT NULL,
    instance_id        BIGINT       NOT NULL,
    task_id            BIGINT       NOT NULL,
    node_code          VARCHAR(64),
    owner_user_id      BIGINT       NOT NULL,
    delegate_user_id   BIGINT       NOT NULL,
    -- 操作类型: ACT=代理办理 VIEW=代理查看
    op_type            VARCHAR(16)  NOT NULL,
    -- 实际处理动作：PASS/REJECT/CLAIM/TRANSFER/...
    action             VARCHAR(16),
    comment            TEXT,
    provider_trace_id  VARCHAR(64),
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT     NOT NULL DEFAULT 0
);

COMMENT ON TABLE pmis_flow_delegate_log IS '流程委派代理使用日志 - 审计代理操作';
COMMENT ON COLUMN pmis_flow_delegate_log.auth_id IS '关联的授权 ID';
COMMENT ON COLUMN pmis_flow_delegate_log.task_id IS '被代理的任务 ID';
COMMENT ON COLUMN pmis_flow_delegate_log.op_type IS '操作类型：ACT=办理 / VIEW=查看';
COMMENT ON COLUMN pmis_flow_delegate_log.action IS '办理动作：PASS/REJECT/CLAIM/TRANSFER';
COMMENT ON COLUMN pmis_flow_delegate_log.comment IS '办理意见';

CREATE INDEX idx_pmis_flow_delegate_log_auth
    ON pmis_flow_delegate_log (tenant_id, auth_id, deleted)
    WHERE deleted = 0;
CREATE INDEX idx_pmis_flow_delegate_log_task
    ON pmis_flow_delegate_log (tenant_id, task_id, deleted)
    WHERE deleted = 0;
CREATE INDEX idx_pmis_flow_delegate_log_delegate
    ON pmis_flow_delegate_log (tenant_id, delegate_user_id, created_at DESC)
    WHERE deleted = 0;
