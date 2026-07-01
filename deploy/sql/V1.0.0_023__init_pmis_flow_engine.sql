-- =====================================================
-- PMIS 自建工作流引擎 DDL（对标 Warm-Flow 7 表极简设计）
-- 版本: V1.0.0_023
-- 描述: 自建轻量级工作流引擎，仅 7 张核心表
-- 命名约定: 所有工作流相关表均以 pmis_flow_ 开头
-- 字段规范: 与 V1.0.0_001 对齐（created_by / created_at / updated_by / updated_at / deleted）
-- 设计参考: Dromara Warm-Flow / FlowLong
-- 适用场景: 立项审批、合同变更、销项审批等线性/会签流程
-- =====================================================

-- -----------------------------------------------------
-- 1. 流程定义表（对标 Warm-Flow flow_definition）
--    记录流程的整体信息（编码、名称、版本、表单路径、模型 JSON）
-- -----------------------------------------------------
DROP TABLE IF EXISTS pmis_flow_definition;
CREATE TABLE pmis_flow_definition (
    id                 BIGSERIAL    PRIMARY KEY,
    flow_code          VARCHAR(64)  NOT NULL,
    flow_name          VARCHAR(128) NOT NULL,
    category           VARCHAR(64),
    version            VARCHAR(20)  NOT NULL DEFAULT '1.0',
    model_value        VARCHAR(40)  NOT NULL DEFAULT 'CLASSICS',
    form_custom        CHAR(1)      NOT NULL DEFAULT 'N',
    form_path          VARCHAR(256),
    activity_status    SMALLINT     NOT NULL DEFAULT 1,
    is_publish         SMALLINT     NOT NULL DEFAULT 0,
    listener_type      VARCHAR(64),
    listener_path      VARCHAR(512),
    ext                VARCHAR(1024),
    description        VARCHAR(512),
    status             VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_by         BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         BIGINT       NOT NULL DEFAULT 0,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT     NOT NULL DEFAULT 0,
    tenant_id          BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id  VARCHAR(64)
);

COMMENT ON TABLE  pmis_flow_definition IS '流程定义表（自建引擎，对标 Warm-Flow flow_definition）';
COMMENT ON COLUMN pmis_flow_definition.flow_code IS '流程编码（业务语义：project_initiation/contract_change/...）';
COMMENT ON COLUMN pmis_flow_definition.flow_name IS '流程名称';
COMMENT ON COLUMN pmis_flow_definition.version IS '流程版本（语义化版本，如 1.0/1.1/2.0）';
COMMENT ON COLUMN pmis_flow_definition.model_value IS '设计器模型：CLASSICS 经典 / MIMIC 仿钉钉';
COMMENT ON COLUMN pmis_flow_definition.activity_status IS '激活状态：0 挂起 / 1 激活';
COMMENT ON COLUMN pmis_flow_definition.is_publish IS '发布状态：0 未发布 / 1 已发布 / 9 失效';
COMMENT ON COLUMN pmis_flow_definition.form_custom IS '审批表单是否自定义：Y 是 / N 否';
COMMENT ON COLUMN pmis_flow_definition.form_path IS '审批表单路径（前端路由或外置表单 URL）';
COMMENT ON COLUMN pmis_flow_definition.listener_type IS '监听器类型（START/TASK/END 等枚举字符串）';
COMMENT ON COLUMN pmis_flow_definition.listener_path IS '监听器 Spring Bean 路径';
COMMENT ON COLUMN pmis_flow_definition.ext IS '扩展字段（业务自定义 JSON 字符串）';
COMMENT ON COLUMN pmis_flow_definition.status IS '状态：ENABLED 启用 / DISABLED 停用';
COMMENT ON COLUMN pmis_flow_definition.provider_trace_id IS '链路追踪 ID（来自调用方或自生成）';

CREATE UNIQUE INDEX uk_pfd_code_version ON pmis_flow_definition(flow_code, version, tenant_id) WHERE deleted = 0;
CREATE INDEX        idx_pfd_category    ON pmis_flow_definition(category);
CREATE INDEX        idx_pfd_publish     ON pmis_flow_definition(is_publish);
CREATE INDEX        idx_pfd_tenant      ON pmis_flow_definition(tenant_id);
CREATE INDEX        idx_pfd_status      ON pmis_flow_definition(status) WHERE deleted = 0;

-- -----------------------------------------------------
-- 2. 流程节点表（对标 Warm-Flow flow_node）
--    流程中的各个节点：开始/审批/会签/网关/结束
-- -----------------------------------------------------
DROP TABLE IF EXISTS pmis_flow_node;
CREATE TABLE pmis_flow_node (
    id                 BIGSERIAL    PRIMARY KEY,
    definition_id      BIGINT       NOT NULL,
    flow_code          VARCHAR(64)  NOT NULL,
    node_type          SMALLINT     NOT NULL,
    node_code          VARCHAR(64)  NOT NULL,
    node_name          VARCHAR(128) NOT NULL,
    permission_flag    VARCHAR(512),
    skip_any_node      VARCHAR(64),
    coordinate         VARCHAR(64),
    skip_list          TEXT,
    ext                VARCHAR(1024),
    status             VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_by         BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         BIGINT       NOT NULL DEFAULT 0,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT     NOT NULL DEFAULT 0,
    tenant_id          BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id  VARCHAR(64)
);

COMMENT ON TABLE  pmis_flow_node IS '流程节点表（对标 Warm-Flow flow_node）';
COMMENT ON COLUMN pmis_flow_node.definition_id IS '所属流程定义 ID';
COMMENT ON COLUMN pmis_flow_node.node_type IS '节点类型：0 开始 / 1 审批 / 2 抄送 / 3 条件 / 4 并行网关 / 5 互斥网关 / 6 结束 / 7 子流程';
COMMENT ON COLUMN pmis_flow_node.node_code IS '节点编码（流程内唯一）';
COMMENT ON COLUMN pmis_flow_node.node_name IS '节点名称';
COMMENT ON COLUMN pmis_flow_node.permission_flag IS '办理人权限标识：role:hr / dept:10 / user:1001 / ${spel}';
COMMENT ON COLUMN pmis_flow_node.skip_any_node IS '任意跳转目标节点编码';
COMMENT ON COLUMN pmis_flow_node.coordinate IS '设计器坐标 JSON {x,y,width,height}';
COMMENT ON COLUMN pmis_flow_node.skip_list IS '节点跳转路由集合 JSON';
COMMENT ON COLUMN pmis_flow_node.ext IS '扩展字段 JSON';
COMMENT ON COLUMN pmis_flow_node.status IS '状态：ENABLED 启用 / DISABLED 停用';

CREATE UNIQUE INDEX uk_pfn_def_code ON pmis_flow_node(definition_id, node_code) WHERE deleted = 0;
CREATE INDEX        idx_pfn_def     ON pmis_flow_node(definition_id);
CREATE INDEX        idx_pfn_code    ON pmis_flow_node(flow_code);
CREATE INDEX        idx_pfn_type    ON pmis_flow_node(node_type);

-- -----------------------------------------------------
-- 3. 节点跳转关联表（对标 Warm-Flow flow_skip）
--    节点之间的有向边：顺序流 / 条件分支 / 退回
-- -----------------------------------------------------
DROP TABLE IF EXISTS pmis_flow_skip;
CREATE TABLE pmis_flow_skip (
    id                 BIGSERIAL    PRIMARY KEY,
    definition_id      BIGINT       NOT NULL,
    flow_code          VARCHAR(64)  NOT NULL,
    skip_name          VARCHAR(128),
    skip_type          VARCHAR(16)  NOT NULL,
    coordinate         VARCHAR(64),
    skip_condition     VARCHAR(512),
    next_node_code     VARCHAR(64)  NOT NULL,
    next_node_type     SMALLINT,
    coordinate_next    VARCHAR(64),
    skip_list          TEXT,
    status             VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_by         BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         BIGINT       NOT NULL DEFAULT 0,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT     NOT NULL DEFAULT 0,
    tenant_id          BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id  VARCHAR(64)
);

COMMENT ON TABLE  pmis_flow_skip IS '节点跳转关联表（对标 Warm-Flow flow_skip）';
COMMENT ON COLUMN pmis_flow_skip.skip_type IS '跳转类型：PASS 通过 / REJECT 退回 / FORWARD 前加签 / BACK 后加签';
COMMENT ON COLUMN pmis_flow_skip.skip_condition IS '跳转条件表达式（SpEL 或 ${var == value}）';
COMMENT ON COLUMN pmis_flow_skip.next_node_code IS '下一节点编码';
COMMENT ON COLUMN pmis_flow_skip.status IS '状态：ENABLED 启用 / DISABLED 停用';

CREATE INDEX idx_pfs_def    ON pmis_flow_skip(definition_id);
CREATE INDEX idx_pfs_code   ON pmis_flow_skip(flow_code);
CREATE INDEX idx_pfs_type   ON pmis_flow_skip(skip_type);

-- -----------------------------------------------------
-- 4. 流程实例表（对标 Warm-Flow flow_instance）
--    每次启动流程生成一条实例记录
-- -----------------------------------------------------
DROP TABLE IF EXISTS pmis_flow_instance;
CREATE TABLE pmis_flow_instance (
    id                 BIGSERIAL    PRIMARY KEY,
    flow_code          VARCHAR(64)  NOT NULL,
    flow_name          VARCHAR(128),
    definition_id      BIGINT       NOT NULL,
    flow_version       VARCHAR(20)  NOT NULL DEFAULT '1.0',
    business_type      VARCHAR(64)  NOT NULL,
    business_id        VARCHAR(64)  NOT NULL,
    business_no        VARCHAR(128),
    title              VARCHAR(256),
    initiator_id       BIGINT,
    initiator_name     VARCHAR(64),
    current_node_code  VARCHAR(64),
    current_node_name  VARCHAR(128),
    variable           TEXT,
    flow_status        VARCHAR(32)  NOT NULL DEFAULT 'RUNNING',
    activity_status    SMALLINT     NOT NULL DEFAULT 1,
    start_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_at             TIMESTAMP,
    duration_ms        BIGINT,
    status             VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_by         BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         BIGINT       NOT NULL DEFAULT 0,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT     NOT NULL DEFAULT 0,
    tenant_id          BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id  VARCHAR(64)
);

COMMENT ON TABLE  pmis_flow_instance IS '流程实例表（对标 Warm-Flow flow_instance）';
COMMENT ON COLUMN pmis_flow_instance.flow_status IS '实例状态：RUNNING/SUSPENDED/COMPLETED/TERMINATED/REJECTED';
COMMENT ON COLUMN pmis_flow_instance.activity_status IS '激活状态：0 挂起 / 1 激活';
COMMENT ON COLUMN pmis_flow_instance.variable IS '流程变量 JSON';
COMMENT ON COLUMN pmis_flow_instance.status IS '记录状态：ENABLED 启用 / DISABLED 停用';

CREATE UNIQUE INDEX uk_pfi_biz ON pmis_flow_instance(business_type, business_id) WHERE deleted = 0;
CREATE INDEX        idx_pfi_def         ON pmis_flow_instance(definition_id);
CREATE INDEX        idx_pfi_code        ON pmis_flow_instance(flow_code);
CREATE INDEX        idx_pfi_status      ON pmis_flow_instance(flow_status);
CREATE INDEX        idx_pfi_initiator   ON pmis_flow_instance(initiator_id);
CREATE INDEX        idx_pfi_tenant      ON pmis_flow_instance(tenant_id);
CREATE INDEX        idx_pfi_start       ON pmis_flow_instance(start_at);

-- -----------------------------------------------------
-- 5. 待办任务表（对标 Warm-Flow flow_task）
--    实例推进过程中产生的待办切片
-- -----------------------------------------------------
DROP TABLE IF EXISTS pmis_flow_task;
CREATE TABLE pmis_flow_task (
    id                 BIGSERIAL    PRIMARY KEY,
    instance_id        BIGINT       NOT NULL,
    flow_code          VARCHAR(64)  NOT NULL,
    definition_id      BIGINT       NOT NULL,
    node_code          VARCHAR(64)  NOT NULL,
    node_name          VARCHAR(128),
    node_type          SMALLINT     NOT NULL,
    business_type      VARCHAR(64),
    business_id        VARCHAR(64),
    business_no        VARCHAR(128),
    flow_name          VARCHAR(128),
    title              VARCHAR(256),
    assignor_id        BIGINT,
    assignor_name      VARCHAR(64),
    assignee_type      VARCHAR(16)  NOT NULL DEFAULT 'USER',
    assignee_id        VARCHAR(64)  NOT NULL,
    assignee_name      VARCHAR(64),
    permission_flag    VARCHAR(512),
    perform_type       VARCHAR(16)  NOT NULL DEFAULT 'OR',
    approve_count      INT          NOT NULL DEFAULT 1,
    approve_finished   INT          NOT NULL DEFAULT 0,
    task_status        VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    comment            TEXT,
    status             VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_by         BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         BIGINT       NOT NULL DEFAULT 0,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claim_at           TIMESTAMP,
    finish_at          TIMESTAMP,
    duration_ms        BIGINT,
    due_at             TIMESTAMP,
    deleted            SMALLINT     NOT NULL DEFAULT 0,
    tenant_id          BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id  VARCHAR(64)
);

COMMENT ON TABLE  pmis_flow_task IS '待办任务表（对标 Warm-Flow flow_task）';
COMMENT ON COLUMN pmis_flow_task.node_type IS '节点类型（同 pmis_flow_node.node_type）';
COMMENT ON COLUMN pmis_flow_task.assignee_type IS '办理人类型：USER/ROLE/DEPT/SPEL';
COMMENT ON COLUMN pmis_flow_task.assignee_id IS '办理人 ID（按 type 解析）';
COMMENT ON COLUMN pmis_flow_task.perform_type IS '会签类型：OR 或签 / SEQUENTIAL 顺序会签 / PARALLEL 并行会签 / VOTE 票签';
COMMENT ON COLUMN pmis_flow_task.approve_count IS '会签所需通过人数（仅会签节点有效）';
COMMENT ON COLUMN pmis_flow_task.approve_finished IS '会签当前已通过人数';
COMMENT ON COLUMN pmis_flow_task.task_status IS '任务状态：PENDING/CLAIMED/COMPLETED/REJECTED/SKIPPED/CANCELLED/TIMEOUT';
COMMENT ON COLUMN pmis_flow_task.status IS '记录状态：ENABLED 启用 / DISABLED 停用';

CREATE INDEX idx_pft_instance   ON pmis_flow_task(instance_id);
CREATE INDEX idx_pft_assignee   ON pmis_flow_task(assignee_id, task_status);
CREATE INDEX idx_pft_node       ON pmis_flow_task(node_code);
CREATE INDEX idx_pft_biz        ON pmis_flow_task(business_type, business_id);
CREATE INDEX idx_pft_status     ON pmis_flow_task(task_status);
CREATE INDEX idx_pft_tenant     ON pmis_flow_task(tenant_id);
CREATE INDEX idx_pft_create     ON pmis_flow_task(created_at);
CREATE INDEX idx_pft_due        ON pmis_flow_task(due_at) WHERE task_status = 'PENDING';

-- -----------------------------------------------------
-- 6. 历史任务表（对标 Warm-Flow flow_his_task）
--    已完成任务的归档，避免主表膨胀
-- -----------------------------------------------------
DROP TABLE IF EXISTS pmis_flow_his_task;
CREATE TABLE pmis_flow_his_task (
    id                 BIGSERIAL    PRIMARY KEY,
    instance_id        BIGINT       NOT NULL,
    task_id            BIGINT       NOT NULL,
    flow_code          VARCHAR(64)  NOT NULL,
    definition_id      BIGINT       NOT NULL,
    node_code          VARCHAR(64)  NOT NULL,
    node_name          VARCHAR(128),
    node_type          SMALLINT     NOT NULL,
    business_type      VARCHAR(64),
    business_id        VARCHAR(64),
    business_no        VARCHAR(128),
    flow_name          VARCHAR(128),
    title              VARCHAR(256),
    assignee_type      VARCHAR(16)  NOT NULL,
    assignee_id        VARCHAR(64)  NOT NULL,
    assignee_name      VARCHAR(64),
    perform_type       VARCHAR(16)  NOT NULL,
    approve_count      INT          NOT NULL DEFAULT 1,
    approve_finished   INT          NOT NULL DEFAULT 0,
    task_status        VARCHAR(32)  NOT NULL,
    comment            TEXT,
    status             VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_by         BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         BIGINT       NOT NULL DEFAULT 0,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claim_at           TIMESTAMP,
    finish_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    duration_ms        BIGINT,
    deleted            SMALLINT     NOT NULL DEFAULT 0,
    tenant_id          BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id  VARCHAR(64)
);

COMMENT ON TABLE  pmis_flow_his_task IS '历史任务表（对标 Warm-Flow flow_his_task）';
COMMENT ON COLUMN pmis_flow_his_task.status IS '记录状态：ENABLED 启用 / DISABLED 停用';

CREATE INDEX idx_pfht_instance   ON pmis_flow_his_task(instance_id);
CREATE INDEX idx_pfht_assignee   ON pmis_flow_his_task(assignee_id, task_status);
CREATE INDEX idx_pfht_biz        ON pmis_flow_his_task(business_type, business_id);
CREATE INDEX idx_pfht_finish     ON pmis_flow_his_task(finish_at);
CREATE INDEX idx_pfht_tenant     ON pmis_flow_his_task(tenant_id);

-- -----------------------------------------------------
-- 7. 流程用户表（对标 Warm-Flow flow_user）
--    任务多办理人扩展（一个 task 可挂多个用户）
-- -----------------------------------------------------
DROP TABLE IF EXISTS pmis_flow_user;
CREATE TABLE pmis_flow_user (
    id                 BIGSERIAL    PRIMARY KEY,
    task_id            BIGINT       NOT NULL,
    instance_id        BIGINT       NOT NULL,
    node_code          VARCHAR(64)  NOT NULL,
    user_type          VARCHAR(16)  NOT NULL,
    user_id            VARCHAR(64)  NOT NULL,
    user_name          VARCHAR(64),
    processed          SMALLINT     NOT NULL DEFAULT 0,
    process_at         TIMESTAMP,
    comment            TEXT,
    status             VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_by         BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         BIGINT       NOT NULL DEFAULT 0,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT     NOT NULL DEFAULT 0,
    tenant_id          BIGINT       NOT NULL DEFAULT 1,
    provider_trace_id  VARCHAR(64)
);

COMMENT ON TABLE  pmis_flow_user IS '流程用户表（对标 Warm-Flow flow_user，会签多办理人）';
COMMENT ON COLUMN pmis_flow_user.user_type IS '用户类型：USER/ROLE/DEPT';
COMMENT ON COLUMN pmis_flow_user.processed IS '是否已处理：0 否 / 1 是';
COMMENT ON COLUMN pmis_flow_user.status IS '记录状态：ENABLED 启用 / DISABLED 停用';

CREATE UNIQUE INDEX uk_pfu_task_user ON pmis_flow_user(task_id, user_id, user_type) WHERE deleted = 0;
CREATE INDEX        idx_pfu_instance ON pmis_flow_user(instance_id);
CREATE INDEX        idx_pfu_user     ON pmis_flow_user(user_id, processed);

-- =====================================================
-- 初始化数据：PMIS 业务流定义
-- =====================================================
INSERT INTO pmis_flow_definition
    (flow_code, flow_name, category, version, model_value, form_custom, form_path,
     activity_status, is_publish, description, status, tenant_id, provider_trace_id,
     created_by, updated_by)
VALUES
    ('project_initiation', '项目立项审批', 'project',  '1.0', 'CLASSICS', 'N',
     '/project/initiation/detail', 1, 1,
     '项目立项审批：申请人 → 部门负责人 → 分管领导 → 总经理', 'ENABLED', 1, 'init_v1', 0, 0),
    ('contract_change',    '合同变更审批', 'contract', '1.0', 'CLASSICS', 'N',
     '/contract/change/detail', 1, 1,
     '合同变更审批：申请人 → 法务 → 财务 → 总经理', 'ENABLED', 1, 'init_v1', 0, 0),
    ('project_closure',    '项目销项审批', 'closure',  '1.0', 'CLASSICS', 'N',
     '/closure/detail', 1, 1,
     '项目销项审批：PM → 部门负责人 → 财务 → 分管领导', 'ENABLED', 1, 'init_v1', 0, 0),
    ('pmis_leave',         'PMIS 通用请假', 'admin',    '1.0', 'CLASSICS', 'N',
     '/admin/leave/detail', 1, 1,
     'PMIS 通用请假：申请人 → 直属上级 → 人事', 'ENABLED', 1, 'init_v1', 0, 0)
ON CONFLICT (flow_code, version, tenant_id) WHERE deleted = 0 DO NOTHING;
