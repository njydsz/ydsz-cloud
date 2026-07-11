-- ============================================================
-- PMIS workflow module SQL
-- Auto-generated from V1.0.0.sql
-- ============================================================
-- 本脚本 DDL 对应后端 workflow 服务 (ydsz-pmis-workflow) 的 Mapper / DO,
--   物理 Mapper 实际所在模块即表归属。跨服务引用禁止直连,统一走
--   Feign + NameAssembler(在 CommonAutoConfiguration 注册)。
-- --------------------------------------------------------------------

-- ============================ [023] init pmis flow engine ============================

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
-- 0. 流程分类表（P1-6: 对标钉钉/飞书审批的分类管理）
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_flow_category (
    id                  VARCHAR(20)       PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    category_code       VARCHAR(64)       NOT NULL,
    category_name       VARCHAR(128)      NOT NULL,
    parent_id           VARCHAR(20),
    sort_num            INTEGER           NOT NULL DEFAULT 0,
    icon                VARCHAR(64),
    remark              VARCHAR(500),
    deleted             SMALLINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP         NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP         NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(20),
    updated_by          VARCHAR(20)
);

COMMENT ON TABLE  pmis_flow_category IS 'P1-6: 流程分类表';

COMMENT ON COLUMN pmis_flow_category.category_code IS '分类编码';

COMMENT ON COLUMN pmis_flow_category.category_name IS '分类名称';

COMMENT ON COLUMN pmis_flow_category.parent_id IS '父分类 ID';

CREATE UNIQUE INDEX IF NOT EXISTS uk_pfc_code
    ON pmis_flow_category (tenant_id, category_code) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfc_parent
    ON pmis_flow_category (parent_id) WHERE parent_id IS NOT NULL AND deleted = 0;

-- -----------------------------------------------------
-- 1. 流程定义表（对标 Warm-Flow flow_definition）
--    记录流程的整体信息（编码、名称、版本、表单路径、模型 JSON）
-- -----------------------------------------------------
-- P1-6: 已废弃,无需 DROP
CREATE TABLE IF NOT EXISTS pmis_flow_definition(
    id                 VARCHAR(20)    PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    flow_code          VARCHAR(64)  NOT NULL,
    flow_name          VARCHAR(128) NOT NULL,
    category           VARCHAR(64),
    flow_version       VARCHAR(20)  NOT NULL DEFAULT '1.0',
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
    -- P2-4: 设计器协同编辑锁定（对标钉钉/飞书流程设计器"编辑锁定"）
    locked_by          VARCHAR(20),                               -- 当前持锁人 ID（NULL=未锁定）
    locked_at          TIMESTAMPTZ,                                -- 加锁时间（用于超时自动释放判断）
    created_by         VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT     NOT NULL DEFAULT 0,
    tenant_id          VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id  VARCHAR(64),
    version            INTEGER      NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_pfd_status_enum       CHECK (status IN ('ENABLED','DISABLED')),
    CONSTRAINT ck_pfd_model_value       CHECK (model_value IN ('CLASSICS','MIMIC')),
    CONSTRAINT ck_pfd_form_custom       CHECK (form_custom IN ('Y','N')),
    CONSTRAINT ck_pfd_activity_status   CHECK (activity_status IN (0, 1)),
    CONSTRAINT ck_pfd_is_publish        CHECK (is_publish IN (0, 1, 9)),
    CONSTRAINT ck_pfd_version_nonneg    CHECK (version >= 0),
    CONSTRAINT ck_pfd_deleted           CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_flow_definition IS '流程定义表: 记录流程的整体信息(编码/名称/版本/表单路径/模型 JSON)';

COMMENT ON COLUMN pmis_flow_definition.flow_code IS '流程编码(业务语义: project_initiation/contract_change/...)';

COMMENT ON COLUMN pmis_flow_definition.flow_name IS '流程名称';

COMMENT ON COLUMN pmis_flow_definition.category IS '流程分类: project/contract/closure/admin';

COMMENT ON COLUMN pmis_flow_definition.flow_version IS '流程版本(语义化版本,1.0/1.1/2.0)';

COMMENT ON COLUMN pmis_flow_definition.version IS '乐观锁版本号';

COMMENT ON COLUMN pmis_flow_definition.model_value IS '设计器模型: CLASSICS 经典 / MIMIC 仿钉钉';

COMMENT ON COLUMN pmis_flow_definition.form_custom IS '审批表单是否自定义: Y 是 / N 否';

COMMENT ON COLUMN pmis_flow_definition.form_path IS '审批表单路径(前端路由或外置表单 URL)';

COMMENT ON COLUMN pmis_flow_definition.activity_status IS '激活状态: 0 挂起 / 1 激活';

COMMENT ON COLUMN pmis_flow_definition.is_publish IS '发布状态: 0 未发布 / 1 已发布 / 9 失效';

COMMENT ON COLUMN pmis_flow_definition.listener_type IS '监听器类型(START/TASK/END 等枚举字符串)';

COMMENT ON COLUMN pmis_flow_definition.listener_path IS '监听器 Spring Bean 路径';

COMMENT ON COLUMN pmis_flow_definition.ext IS '扩展字段(业务自定义 JSON 字符串)';

COMMENT ON COLUMN pmis_flow_definition.description IS '流程描述';

COMMENT ON COLUMN pmis_flow_definition.status IS '状态: ENABLED 启用 / DISABLED 停用';

COMMENT ON COLUMN pmis_flow_definition.locked_by IS 'P2-4: 当前持锁人 ID（设计器协同编辑锁定，NULL=未锁定）';

COMMENT ON COLUMN pmis_flow_definition.locked_at IS 'P2-4: 加锁时间（超过 lock-timeout-minutes 自动释放，默认 30 分钟）';

COMMENT ON COLUMN pmis_flow_definition.deleted IS '逻辑删除: 0=未删除,1=已删除';

COMMENT ON COLUMN pmis_flow_definition.provider_trace_id IS '链路追踪 ID(来自调用方或自生成)';

COMMENT ON COLUMN pmis_flow_definition.tenant_id IS '租户 ID: 多租户隔离';

-- 复合/部分索引(替代零散的单列索引)
CREATE UNIQUE INDEX IF NOT EXISTS uk_pfd_tenant_code_version
    ON pmis_flow_definition(tenant_id, flow_code, version)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfd_tenant_category
    ON pmis_flow_definition(tenant_id, category)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfd_tenant_publish
    ON pmis_flow_definition(tenant_id, is_publish)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfd_tenant_status
    ON pmis_flow_definition(tenant_id, status)
    WHERE deleted = 0;

-- -----------------------------------------------------
-- 2. 流程节点表（对标 Warm-Flow flow_node）
--    流程中的各个节点：开始/审批/会签/网关/结束
-- -----------------------------------------------------
-- P1-6: 已废弃,无需 DROP
CREATE TABLE IF NOT EXISTS pmis_flow_node(
    id                 VARCHAR(20)    PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    definition_id      VARCHAR(20)       NOT NULL,
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
    created_by         VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT     NOT NULL DEFAULT 0,
    tenant_id          VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id  VARCHAR(64),
    -- 数据完整性约束
    CONSTRAINT ck_pfn_status_enum       CHECK (status IN ('ENABLED','DISABLED')),
    CONSTRAINT ck_pfn_node_type         CHECK (node_type IN (0, 1, 2, 3, 4, 5, 6, 7)),
    CONSTRAINT ck_pfn_deleted           CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_flow_node IS '流程节点表: 流程中的各个节点(开始/审批/会签/网关/结束)';

COMMENT ON COLUMN pmis_flow_node.definition_id IS '所属流程定义 ID';

COMMENT ON COLUMN pmis_flow_node.flow_code IS '流程编码(冗余)';

COMMENT ON COLUMN pmis_flow_node.node_type IS '节点类型: 0 开始 / 1 审批 / 2 抄送 / 3 条件 / 4 并行网关 / 5 互斥网关 / 6 结束 / 7 子流程';

COMMENT ON COLUMN pmis_flow_node.node_code IS '节点编码(流程内唯一)';

COMMENT ON COLUMN pmis_flow_node.node_name IS '节点名称';

COMMENT ON COLUMN pmis_flow_node.permission_flag IS '办理人权限标识: role:hr / dept:10 / user:1001 / ${spel}';

COMMENT ON COLUMN pmis_flow_node.skip_any_node IS '任意跳转目标节点编码';

COMMENT ON COLUMN pmis_flow_node.coordinate IS '设计器坐标 JSON {x,y,width,height}';

COMMENT ON COLUMN pmis_flow_node.skip_list IS '节点跳转路由集合 JSON';

COMMENT ON COLUMN pmis_flow_node.ext IS '扩展字段 JSON';

COMMENT ON COLUMN pmis_flow_node.status IS '状态: ENABLED 启用 / DISABLED 停用';

COMMENT ON COLUMN pmis_flow_node.deleted IS '逻辑删除: 0=未删除,1=已删除';

COMMENT ON COLUMN pmis_flow_node.tenant_id IS '租户 ID: 多租户隔离';

-- 复合/部分索引(替代零散的单列索引)
CREATE UNIQUE INDEX IF NOT EXISTS uk_pfn_tenant_def_code
    ON pmis_flow_node(tenant_id, definition_id, node_code)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfn_tenant_def_type
    ON pmis_flow_node(tenant_id, definition_id, node_type)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfn_tenant_code
    ON pmis_flow_node(tenant_id, flow_code)
    WHERE deleted = 0;

-- -----------------------------------------------------
-- 3. 节点跳转关联表（对标 Warm-Flow flow_skip）
--    节点之间的有向边：顺序流 / 条件分支 / 退回
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_flow_skip(
    id                 VARCHAR(20)    PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    definition_id      VARCHAR(20)       NOT NULL,
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
    created_by         VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT     NOT NULL DEFAULT 0,
    tenant_id          VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id  VARCHAR(64),
    -- 数据完整性约束
    CONSTRAINT ck_pfs_skip_type         CHECK (skip_type IN ('PASS','REJECT','FORWARD','BACK')),
    CONSTRAINT ck_pfs_status_enum       CHECK (status IN ('ENABLED','DISABLED')),
    CONSTRAINT ck_pfs_next_node_type    CHECK (next_node_type IS NULL OR next_node_type IN (0, 1, 2, 3, 4, 5, 6, 7)),
    CONSTRAINT ck_pfs_deleted           CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_flow_skip IS '节点跳转关联表: 节点之间的有向边,顺序流 / 条件分支 / 退回';

COMMENT ON COLUMN pmis_flow_skip.definition_id IS '所属流程定义 ID';

COMMENT ON COLUMN pmis_flow_skip.flow_code IS '流程编码';

COMMENT ON COLUMN pmis_flow_skip.skip_name IS '跳转名称';

COMMENT ON COLUMN pmis_flow_skip.skip_type IS '跳转类型: PASS 通过 / REJECT 退回 / FORWARD 前加签 / BACK 后加签';

COMMENT ON COLUMN pmis_flow_skip.coordinate IS '设计器坐标 JSON {x,y,width,height}';

COMMENT ON COLUMN pmis_flow_skip.skip_condition IS '跳转条件表达式(SpEL 或 ${var == value})';

COMMENT ON COLUMN pmis_flow_skip.next_node_code IS '下一节点编码';

COMMENT ON COLUMN pmis_flow_skip.next_node_type IS '下一节点类型: 同 pmis_flow_node.node_type';

COMMENT ON COLUMN pmis_flow_skip.coordinate_next IS '下一节点坐标 JSON';

COMMENT ON COLUMN pmis_flow_skip.skip_list IS '跳转路由集合 JSON';

COMMENT ON COLUMN pmis_flow_skip.status IS '状态: ENABLED 启用 / DISABLED 停用';

COMMENT ON COLUMN pmis_flow_skip.deleted IS '逻辑删除: 0=未删除,1=已删除';

COMMENT ON COLUMN pmis_flow_skip.tenant_id IS '租户 ID: 多租户隔离';

-- 复合/部分索引(替代零散的单列索引)
CREATE INDEX IF NOT EXISTS idx_pfs_tenant_def_next
    ON pmis_flow_skip(tenant_id, definition_id, next_node_code)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfs_tenant_def_type
    ON pmis_flow_skip(tenant_id, definition_id, skip_type)
    WHERE deleted = 0;

-- -----------------------------------------------------
-- 4. 流程实例表（对标 Warm-Flow flow_instance）
--    每次启动流程生成一条实例记录
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_flow_instance(
    id                 VARCHAR(20)    PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    flow_code          VARCHAR(64)  NOT NULL,
    flow_name          VARCHAR(128),
    definition_id      VARCHAR(20)       NOT NULL,
    flow_version       VARCHAR(20)  NOT NULL DEFAULT '1.0',
    business_type      VARCHAR(64)  NOT NULL,
    business_id        VARCHAR(20)  NOT NULL,
    business_no        VARCHAR(128),
    title              VARCHAR(256),
    initiator_id       VARCHAR(20),
    initiator_name     VARCHAR(64),
    current_node_code  VARCHAR(64),
    current_node_name  VARCHAR(128),
    variable           TEXT,
    flow_status        VARCHAR(32)  NOT NULL DEFAULT 'RUNNING',
    activity_status    SMALLINT     NOT NULL DEFAULT 1,
    start_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_at             TIMESTAMPTZ,
    duration_ms        BIGINT,
    status             VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_by         VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT     NOT NULL DEFAULT 0,
    tenant_id          VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id  VARCHAR(64),
    parent_instance_id VARCHAR(20),
    parent_node_code   VARCHAR(64),
    reject_reason      TEXT,
    due_at             TIMESTAMPTZ,
    version            INT          NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_pfi_flow_status       CHECK (flow_status IN ('RUNNING','SUSPENDED','COMPLETED','TERMINATED','REJECTED','DRAFT')),
    CONSTRAINT ck_pfi_activity_status   CHECK (activity_status IN (0, 1)),
    CONSTRAINT ck_pfi_status_enum       CHECK (status IN ('ENABLED','DISABLED')),
    CONSTRAINT ck_pfi_end_after_start   CHECK (end_at IS NULL OR end_at >= start_at),
    CONSTRAINT ck_pfi_duration_nonneg   CHECK (duration_ms IS NULL OR duration_ms >= 0),
    CONSTRAINT ck_pfi_deleted           CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_flow_instance IS '流程实例表: 每次启动流程生成一条实例记录,记录审批全过程';

COMMENT ON COLUMN pmis_flow_instance.flow_code IS '流程编码';

COMMENT ON COLUMN pmis_flow_instance.flow_name IS '流程名称(冗余,便于查询)';

COMMENT ON COLUMN pmis_flow_instance.definition_id IS '所属流程定义 ID';

COMMENT ON COLUMN pmis_flow_instance.flow_version IS '流程版本号';

COMMENT ON COLUMN pmis_flow_instance.business_type IS '业务类型: PROJECT_INITIATION / CONTRACT_CHANGE / CLOSURE / LEAVE';

COMMENT ON COLUMN pmis_flow_instance.business_id IS '业务对象 ID';

COMMENT ON COLUMN pmis_flow_instance.business_no IS '业务单号: 例如立项编号';

COMMENT ON COLUMN pmis_flow_instance.title IS '流程实例标题';

COMMENT ON COLUMN pmis_flow_instance.initiator_id IS '发起人 ID';

COMMENT ON COLUMN pmis_flow_instance.initiator_name IS '发起人姓名(冗余)';

COMMENT ON COLUMN pmis_flow_instance.current_node_code IS '当前节点编码';

COMMENT ON COLUMN pmis_flow_instance.current_node_name IS '当前节点名称';

COMMENT ON COLUMN pmis_flow_instance.variable IS '流程变量 JSON';

COMMENT ON COLUMN pmis_flow_instance.flow_status IS '实例状态: RUNNING/SUSPENDED/COMPLETED/TERMINATED/REJECTED';

COMMENT ON COLUMN pmis_flow_instance.activity_status IS '激活状态: 0 挂起 / 1 激活';

COMMENT ON COLUMN pmis_flow_instance.start_at IS '开始时间';

COMMENT ON COLUMN pmis_flow_instance.end_at IS '结束时间';

COMMENT ON COLUMN pmis_flow_instance.duration_ms IS '总耗时(毫秒)';

COMMENT ON COLUMN pmis_flow_instance.status IS '记录状态: ENABLED 启用 / DISABLED 停用';

COMMENT ON COLUMN pmis_flow_instance.deleted IS '逻辑删除: 0=未删除,1=已删除';

COMMENT ON COLUMN pmis_flow_instance.tenant_id IS '租户 ID: 多租户隔离';

COMMENT ON COLUMN pmis_flow_instance.parent_instance_id IS 'GAP-P1: 父流程实例 ID（子流程场景，可空）';

COMMENT ON COLUMN pmis_flow_instance.parent_node_code IS 'GAP-P1: 父流程中触发子流程的节点编码（可空）';

COMMENT ON COLUMN pmis_flow_instance.reject_reason IS '退回原因（最近一次 REJECT 操作的备注，重审时清空）';

COMMENT ON COLUMN pmis_flow_instance.due_at IS '子流程超时时间（超时自动终止子流程，可空）';

COMMENT ON COLUMN pmis_flow_instance.version IS '乐观锁版本号（P1-2）';

-- 说明：早期版本使用 pfi_ 前缀与 V1.0.0_012 (pmis_finance_invoice) 的
--      索引同名 (idx_pfi_status),触发"关系已存在"报错。改为
--      flow_instance_ 前缀以彻底避免跨模块索引名冲突。
-- 复合/部分索引(替代零散的单列索引)
CREATE UNIQUE INDEX IF NOT EXISTS uk_flow_instance_tenant_biz
    ON pmis_flow_instance(tenant_id, business_type, business_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_flow_instance_tenant_def
    ON pmis_flow_instance(tenant_id, definition_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_flow_instance_tenant_code_status
    ON pmis_flow_instance(tenant_id, flow_code, flow_status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_flow_instance_tenant_status
    ON pmis_flow_instance(tenant_id, flow_status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_flow_instance_tenant_initiator
    ON pmis_flow_instance(tenant_id, initiator_id, start_at DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_flow_instance_tenant_start
    ON pmis_flow_instance(tenant_id, start_at DESC)
    WHERE deleted = 0;

-- =====================================================
-- 5. 待办任务运行态表 pmis_flow_run_task
--    (2026-07-06 由 pmis_flow_task 重命名,与 pmis_flow_his_task 区分)
-- =====================================================
-- 表结构要点:
--   1. 核心索引:
--      - idx_pft_assignee    (assignee_id, task_status)           待办箱按办理人+状态
--      - idx_pft_due         (due_at) WHERE task_status='PENDING' SLA 扫描
--      - idx_pfrt_foreach_iter UNIQUE (instance_id, node_code, iter_var) WHERE iter_var IS NOT NULL
--        (由 UK 约束自动创建,FOREACH 节点防止重复创建 task)
--      - idx_pmis_flow_run_task_priority_todo (priority DESC, created_at ASC) WHERE task_status IN ('PENDING','CLAIMED')
--        待办按优先级+时间排序(P1-1,主索引块内创建)
--   2. GAP-P1:version 是 MyBatis-Plus @Version 乐观锁,会签并发安全
--   3. P1-5:vote_pass_rate 票签通过率阈值(0~1),performType='VOTE' 时生效
--   4. P1-6:SLA 催办:reminder_count / last_reminded_at / sla_action / sla_escalated
--   5. GAP-P2-10:iter_var FOREACH 循环节点的当前迭代元素值,非循环节点为 NULL
--      - UNIQUE 约束 (instance_id, node_code, iter_var) WHERE iter_var IS NOT NULL
--        防止 FOREACH 对同一元素重复创建 task
--      - 由于逻辑删除,加 deleted=0 过滤:被 soft-delete 的不参与唯一性判定
--   6. CHECK 约束:
--      - vote_pass_rate 必须在 [0, 1] 之间
--      - priority 必须在 [1, 100] 之间(P1-1 取值约定)
--      - approve_finished <= approve_count(已通过不能多于要求)
--      - approve_count / approve_finished 非负
CREATE TABLE IF NOT EXISTS pmis_flow_run_task(
    id                 VARCHAR(20)    PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    instance_id        VARCHAR(20)       NOT NULL,
    flow_code          VARCHAR(64)  NOT NULL,
    definition_id      VARCHAR(20)       NOT NULL,
    node_code          VARCHAR(64)  NOT NULL,
    node_name          VARCHAR(128),
    node_type          SMALLINT     NOT NULL,
    business_type      VARCHAR(64),
    business_id        VARCHAR(20),
    business_no        VARCHAR(128),
    flow_name          VARCHAR(128),
    title              VARCHAR(256),
    assignor_id        VARCHAR(20),
    assignor_name      VARCHAR(64),
    assignee_type      VARCHAR(16)  NOT NULL DEFAULT 'USER',
    assignee_id        VARCHAR(20)  NOT NULL,
    assignee_name      VARCHAR(64),
    permission_flag    VARCHAR(512),
    perform_type       VARCHAR(16)  NOT NULL DEFAULT 'OR',
    approve_count      INT          NOT NULL DEFAULT 1,
    approve_finished   INT          NOT NULL DEFAULT 0,
    vote_pass_rate     DECIMAL(5,4) NOT NULL DEFAULT 0.5,
    task_status        VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    comment            TEXT,
    status             VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_by         VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claim_at           TIMESTAMPTZ,
    finish_at          TIMESTAMPTZ,
    duration_ms        BIGINT,
    due_at             TIMESTAMPTZ,
    priority           INT          NOT NULL DEFAULT 50,
    reminder_count     INT          NOT NULL DEFAULT 0,
    last_reminded_at   TIMESTAMPTZ,
    sla_action         VARCHAR(32),
    sla_escalated      SMALLINT     NOT NULL DEFAULT 0,
    deleted            SMALLINT     NOT NULL DEFAULT 0,
    tenant_id          VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id  VARCHAR(64),
    -- GAP-P2-10: FOREACH 循环节点当前迭代元素值（非循环节点为 NULL）
    iter_var           VARCHAR(255),
    -- 乐观锁版本号(GAP-P1: 会签并发安全,MyBatis-Plus @Version 自动维护)
    version            INT          NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_pfrt_assignee_type    CHECK (assignee_type IN ('USER','ROLE','DEPT','SPEL','FOREACH_PARALLEL')),
    CONSTRAINT ck_pfrt_perform_type     CHECK (perform_type  IN ('OR','SEQUENTIAL','PARALLEL','VOTE','FOREACH_PARALLEL')),
    CONSTRAINT ck_pfrt_task_status      CHECK (task_status   IN ('PENDING','CLAIMED','COMPLETED','REJECTED','SKIPPED','CANCELLED','TIMEOUT','FROZEN')),
    CONSTRAINT ck_pfrt_status_enum      CHECK (status        IN ('ENABLED','DISABLED')),
    CONSTRAINT ck_pfrt_node_type        CHECK (node_type     IN (0, 1, 2, 3, 4, 5, 6, 7)),
    CONSTRAINT ck_pfrt_sla_action       CHECK (sla_action IS NULL OR sla_action IN ('REMIND','ESCALATE','AUTO_PASS','AUTO_REJECT')),
    CONSTRAINT ck_pfrt_sla_escalated    CHECK (sla_escalated IN (0, 1)),
    CONSTRAINT ck_pfrt_vote_pass_rate   CHECK (vote_pass_rate >= 0 AND vote_pass_rate <= 1),
    CONSTRAINT ck_pfrt_priority_range   CHECK (priority >= 1 AND priority <= 100),
    CONSTRAINT ck_pfrt_approve_nonneg   CHECK (approve_count >= 0 AND approve_finished >= 0),
    CONSTRAINT ck_pfrt_approve_bounded  CHECK (approve_finished <= approve_count),
    CONSTRAINT ck_pfrt_reminder_nonneg  CHECK (reminder_count >= 0),
    CONSTRAINT ck_pfrt_version_nonneg   CHECK (version >= 0),
    CONSTRAINT ck_pfrt_duration_nonneg  CHECK (duration_ms IS NULL OR duration_ms >= 0),
    CONSTRAINT ck_pfrt_deleted          CHECK (deleted IN (0, 1))
    -- FOREACH 唯一性:同一实例+节点+迭代元素只能有一条未删除 task
    -- PG 不支持 UNIQUE 约束的 WHERE 子句,改为 partial unique index(建在 CREATE TABLE 之后)
);

COMMENT ON TABLE  pmis_flow_run_task IS '待办任务运行态表: 实例推进过程中产生的待办切片(运行态),办理人待办箱核心表,完成后归档到 pmis_flow_his_task';

COMMENT ON COLUMN pmis_flow_run_task.instance_id IS '所属流程实例 ID';

COMMENT ON COLUMN pmis_flow_run_task.flow_code IS '流程编码(冗余)';

COMMENT ON COLUMN pmis_flow_run_task.definition_id IS '所属流程定义 ID';

COMMENT ON COLUMN pmis_flow_run_task.node_code IS '当前节点编码';

COMMENT ON COLUMN pmis_flow_run_task.node_name IS '当前节点名称';

COMMENT ON COLUMN pmis_flow_run_task.node_type IS '节点类型(同 pmis_flow_node.node_type)';

COMMENT ON COLUMN pmis_flow_run_task.business_type IS '业务类型';

COMMENT ON COLUMN pmis_flow_run_task.business_id IS '业务对象 ID';

COMMENT ON COLUMN pmis_flow_run_task.business_no IS '业务单号';

COMMENT ON COLUMN pmis_flow_run_task.flow_name IS '流程名称';

COMMENT ON COLUMN pmis_flow_run_task.title IS '任务标题';

COMMENT ON COLUMN pmis_flow_run_task.assignor_id IS '转交人 ID: 上一步操作人';

COMMENT ON COLUMN pmis_flow_run_task.assignor_name IS '转交人姓名(冗余)';

COMMENT ON COLUMN pmis_flow_run_task.assignee_type IS '办理人类型: USER/ROLE/DEPT/SPEL/FOREACH_PARALLEL';

COMMENT ON COLUMN pmis_flow_run_task.assignee_id IS '办理人 ID(按 type 解析)';

COMMENT ON COLUMN pmis_flow_run_task.assignee_name IS '办理人姓名(冗余)';

COMMENT ON COLUMN pmis_flow_run_task.permission_flag IS '权限标识: role:hr / dept:10 / user:1001 / ${spel}';

COMMENT ON COLUMN pmis_flow_run_task.perform_type IS '会签类型: OR 或签 / SEQUENTIAL 顺序会签 / PARALLEL 并行会签 / VOTE 票签 / FOREACH_PARALLEL 多实例并行';

COMMENT ON COLUMN pmis_flow_run_task.approve_count IS '会签所需通过人数(仅会签节点有效)';

COMMENT ON COLUMN pmis_flow_run_task.approve_finished IS '会签当前已通过人数';

COMMENT ON COLUMN pmis_flow_run_task.vote_pass_rate IS 'P1-5: VOTE 票签模式通过率阈值(0~1,默认 0.5 表示过半数),performType=VOTE 时生效';

COMMENT ON COLUMN pmis_flow_run_task.task_status IS '任务状态: PENDING/CLAIMED/COMPLETED/REJECTED/SKIPPED/CANCELLED/TIMEOUT/FROZEN';

COMMENT ON COLUMN pmis_flow_run_task.comment IS '审批意见';

COMMENT ON COLUMN pmis_flow_run_task.status IS '记录状态: ENABLED 启用 / DISABLED 停用';

COMMENT ON COLUMN pmis_flow_run_task.claim_at IS '签收时间';

COMMENT ON COLUMN pmis_flow_run_task.finish_at IS '完成时间';

COMMENT ON COLUMN pmis_flow_run_task.duration_ms IS '处理耗时(毫秒)';

COMMENT ON COLUMN pmis_flow_run_task.due_at IS '截止时间: SLA 预警依据';

COMMENT ON COLUMN pmis_flow_run_task.priority IS 'P1-1: 任务优先级(1-100,默认50),待办默认按 priority DESC, created_at ASC 排序';

COMMENT ON COLUMN pmis_flow_run_task.reminder_count IS 'P1-6: 已发送的 SLA 催办次数';

COMMENT ON COLUMN pmis_flow_run_task.last_reminded_at IS 'P1-6: 最近一次催办时间';

COMMENT ON COLUMN pmis_flow_run_task.sla_action IS 'P1-6: 最终触发的 SLA 动作(REMIND/ESCALATE/AUTO_PASS/AUTO_REJECT)';

COMMENT ON COLUMN pmis_flow_run_task.sla_escalated IS 'P1-6: 是否已升级(0 否 / 1 是,避免重复升级)';

COMMENT ON COLUMN pmis_flow_run_task.deleted IS '逻辑删除: 0=未删除,1=已删除';

COMMENT ON COLUMN pmis_flow_run_task.iter_var IS 'GAP-P2-10: FOREACH 当前迭代元素值(循环节点每条独立 task 对应的集合元素,非循环节点为 NULL),UK 约束 (instance_id, node_code, iter_var) 防止重复创建';

COMMENT ON COLUMN pmis_flow_run_task.version IS 'GAP-P1: 乐观锁版本号 — 会签并发安全,MyBatis-Plus @Version 自动维护';

COMMENT ON COLUMN pmis_flow_run_task.tenant_id IS '租户 ID: 多租户隔离';

-- 复合/部分索引(替代零散的单列索引,引入 tenant_id + WHERE deleted=0)
CREATE INDEX IF NOT EXISTS idx_pft_tenant_assignee_status
    ON pmis_flow_run_task(tenant_id, assignee_id, task_status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pft_tenant_instance
    ON pmis_flow_run_task(tenant_id, instance_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pft_tenant_biz
    ON pmis_flow_run_task(tenant_id, business_type, business_id)
    WHERE deleted = 0 AND business_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pft_tenant_node
    ON pmis_flow_run_task(tenant_id, node_code)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pft_tenant_status
    ON pmis_flow_run_task(tenant_id, task_status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pft_tenant_due
    ON pmis_flow_run_task(tenant_id, due_at)
    WHERE deleted = 0 AND task_status = 'PENDING';

-- P1-1: 待办按优先级+时间排序(仅 PENDING/CLAIMED 走索引)
CREATE INDEX IF NOT EXISTS idx_pmis_flow_run_task_priority_todo
    ON pmis_flow_run_task (tenant_id, priority DESC, created_at ASC)
    WHERE task_status IN ('PENDING', 'CLAIMED')
      AND status = 'ENABLED'
      AND deleted = 0;

-- -----------------------------------------------------
-- 6. 历史任务表（对标 Warm-Flow flow_his_task）
--    已完成任务的归档，避免主表膨胀
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_flow_his_task(
    id                 VARCHAR(20)    PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    instance_id        VARCHAR(20)       NOT NULL,
    task_id            VARCHAR(20)       NOT NULL,
    flow_code          VARCHAR(64)  NOT NULL,
    definition_id      VARCHAR(20)       NOT NULL,
    node_code          VARCHAR(64)  NOT NULL,
    node_name          VARCHAR(128),
    node_type          SMALLINT     NOT NULL,
    business_type      VARCHAR(64),
    business_id        VARCHAR(20),
    business_no        VARCHAR(128),
    flow_name          VARCHAR(128),
    title              VARCHAR(256),
    assignee_type      VARCHAR(16)  NOT NULL,
    assignee_id        VARCHAR(20)  NOT NULL,
    assignee_name      VARCHAR(64),
    perform_type       VARCHAR(16)  NOT NULL,
    approve_count      INT          NOT NULL DEFAULT 1,
    approve_finished   INT          NOT NULL DEFAULT 0,
    vote_pass_rate     DECIMAL(5,4) NOT NULL DEFAULT 0.5,
    task_status        VARCHAR(32)  NOT NULL,
    comment            TEXT,
    status             VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_by         VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claim_at           TIMESTAMPTZ,
    finish_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    duration_ms        BIGINT,
    deleted            SMALLINT     NOT NULL DEFAULT 0,
    tenant_id          VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id  VARCHAR(64),
    -- GAP-P2-10: FOREACH 归档追溯(从 pmis_flow_run_task 复制)
    iter_var           VARCHAR(255),
    -- 数据完整性约束
    CONSTRAINT ck_pfht_assignee_type    CHECK (assignee_type IN ('USER','ROLE','DEPT','SPEL','FOREACH_PARALLEL')),
    CONSTRAINT ck_pfht_perform_type     CHECK (perform_type  IN ('OR','SEQUENTIAL','PARALLEL','VOTE','FOREACH_PARALLEL')),
    CONSTRAINT ck_pfht_task_status      CHECK (task_status   IN ('COMPLETED','REJECTED','SKIPPED','CANCELLED','TIMEOUT')),
    CONSTRAINT ck_pfht_status_enum      CHECK (status        IN ('ENABLED','DISABLED')),
    CONSTRAINT ck_pfht_node_type        CHECK (node_type     IN (0, 1, 2, 3, 4, 5, 6, 7)),
    CONSTRAINT ck_pfht_vote_pass_rate   CHECK (vote_pass_rate >= 0 AND vote_pass_rate <= 1),
    CONSTRAINT ck_pfht_approve_nonneg   CHECK (approve_count >= 0 AND approve_finished >= 0),
    CONSTRAINT ck_pfht_approve_bounded  CHECK (approve_finished <= approve_count),
    CONSTRAINT ck_pfht_duration_nonneg  CHECK (duration_ms IS NULL OR duration_ms >= 0),
    CONSTRAINT ck_pfht_deleted          CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_flow_his_task IS '历史任务表: 已完成任务的归档,避免主表膨胀,审批历史追溯';

COMMENT ON COLUMN pmis_flow_his_task.instance_id IS '所属流程实例 ID';

COMMENT ON COLUMN pmis_flow_his_task.task_id IS '原始任务 ID: 引用 pmis_flow_run_task.id';

COMMENT ON COLUMN pmis_flow_his_task.flow_code IS '流程编码';

COMMENT ON COLUMN pmis_flow_his_task.definition_id IS '所属流程定义 ID';

COMMENT ON COLUMN pmis_flow_his_task.node_code IS '节点编码';

COMMENT ON COLUMN pmis_flow_his_task.node_name IS '节点名称';

COMMENT ON COLUMN pmis_flow_his_task.node_type IS '节点类型';

COMMENT ON COLUMN pmis_flow_his_task.business_type IS '业务类型';

COMMENT ON COLUMN pmis_flow_his_task.business_id IS '业务对象 ID';

COMMENT ON COLUMN pmis_flow_his_task.business_no IS '业务单号';

COMMENT ON COLUMN pmis_flow_his_task.flow_name IS '流程名称';

COMMENT ON COLUMN pmis_flow_his_task.title IS '任务标题';

COMMENT ON COLUMN pmis_flow_his_task.assignee_type IS '办理人类型';

COMMENT ON COLUMN pmis_flow_his_task.assignee_id IS '办理人 ID';

COMMENT ON COLUMN pmis_flow_his_task.assignee_name IS '办理人姓名';

COMMENT ON COLUMN pmis_flow_his_task.perform_type IS '会签类型';

COMMENT ON COLUMN pmis_flow_his_task.approve_count IS '会签所需通过人数';

COMMENT ON COLUMN pmis_flow_his_task.approve_finished IS '会签已通过人数';

COMMENT ON COLUMN pmis_flow_his_task.vote_pass_rate IS 'P1-5: VOTE 票签通过率阈值(0~1,从 pmis_flow_run_task 归档)';

COMMENT ON COLUMN pmis_flow_his_task.task_status IS '任务终态: COMPLETED/REJECTED/SKIPPED/CANCELLED/TIMEOUT';

COMMENT ON COLUMN pmis_flow_his_task.comment IS '审批意见';

COMMENT ON COLUMN pmis_flow_his_task.status IS '记录状态: ENABLED 启用 / DISABLED 停用';

COMMENT ON COLUMN pmis_flow_his_task.claim_at IS '签收时间';

COMMENT ON COLUMN pmis_flow_his_task.finish_at IS '完成时间';

COMMENT ON COLUMN pmis_flow_his_task.duration_ms IS '处理耗时(毫秒)';

COMMENT ON COLUMN pmis_flow_his_task.deleted IS '逻辑删除: 0=未删除,1=已删除';

COMMENT ON COLUMN pmis_flow_his_task.iter_var IS 'GAP-P2-10: FOREACH 归档时的迭代元素值(从 pmis_flow_run_task 复制,审批历史追溯)';

COMMENT ON COLUMN pmis_flow_his_task.tenant_id IS '租户 ID: 多租户隔离';

-- 复合/部分索引(替代零散的单列索引,引入 tenant_id + WHERE deleted=0)
CREATE INDEX IF NOT EXISTS idx_pfht_tenant_instance_finish
    ON pmis_flow_his_task(tenant_id, instance_id, finish_at DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfht_tenant_assignee_finish
    ON pmis_flow_his_task(tenant_id, assignee_id, finish_at DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfht_tenant_biz
    ON pmis_flow_his_task(tenant_id, business_type, business_id)
    WHERE deleted = 0 AND business_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pfht_tenant_finish
    ON pmis_flow_his_task(tenant_id, finish_at DESC)
    WHERE deleted = 0;

-- -----------------------------------------------------
-- 7. 流程用户表（对标 Warm-Flow flow_user）
--    任务多办理人扩展（一个 task 可挂多个用户）
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_flow_user(
    id                 VARCHAR(20)    PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    task_id            VARCHAR(20)       NOT NULL,
    instance_id        VARCHAR(20)       NOT NULL,
    node_code          VARCHAR(64)  NOT NULL,
    user_type          VARCHAR(16)  NOT NULL,
    user_id            VARCHAR(20)  NOT NULL,
    user_name          VARCHAR(64),
    processed          SMALLINT     NOT NULL DEFAULT 0,
    process_at         TIMESTAMPTZ,
    comment            TEXT,
    status             VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_by         VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT     NOT NULL DEFAULT 0,
    tenant_id          VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id  VARCHAR(64),
    -- 数据完整性约束
    CONSTRAINT ck_pfu_user_type         CHECK (user_type  IN ('USER','ROLE','DEPT')),
    CONSTRAINT ck_pfu_status_enum       CHECK (status     IN ('ENABLED','DISABLED')),
    CONSTRAINT ck_pfu_processed         CHECK (processed  IN (0, 1)),
    CONSTRAINT ck_pfu_process_after     CHECK (processed = 0 OR process_at IS NOT NULL),
    CONSTRAINT ck_pfu_deleted           CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_flow_user IS '流程用户表: 会签多办理人,一个 task 可挂多个用户';

COMMENT ON COLUMN pmis_flow_user.task_id IS '所属任务 ID';

COMMENT ON COLUMN pmis_flow_user.instance_id IS '所属流程实例 ID';

COMMENT ON COLUMN pmis_flow_user.node_code IS '节点编码';

COMMENT ON COLUMN pmis_flow_user.user_type IS '用户类型: USER/ROLE/DEPT';

COMMENT ON COLUMN pmis_flow_user.user_id IS '用户 ID(按 type 解析)';

COMMENT ON COLUMN pmis_flow_user.user_name IS '用户姓名(冗余)';

COMMENT ON COLUMN pmis_flow_user.processed IS '是否已处理: 0 否 / 1 是';

COMMENT ON COLUMN pmis_flow_user.process_at IS '处理时间';

COMMENT ON COLUMN pmis_flow_user.comment IS '处理意见';

COMMENT ON COLUMN pmis_flow_user.status IS '记录状态: ENABLED 启用 / DISABLED 停用';

COMMENT ON COLUMN pmis_flow_user.deleted IS '逻辑删除: 0=未删除,1=已删除';

COMMENT ON COLUMN pmis_flow_user.tenant_id IS '租户 ID: 多租户隔离';

-- 复合/部分索引(替代零散的单列索引,引入 tenant_id + WHERE deleted=0)
CREATE UNIQUE INDEX IF NOT EXISTS uk_pfu_tenant_task_user
    ON pmis_flow_user(tenant_id, task_id, user_id, user_type)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfu_tenant_instance
    ON pmis_flow_user(tenant_id, instance_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfu_tenant_user_processed
    ON pmis_flow_user(tenant_id, user_id, processed)
    WHERE deleted = 0;

-- 流程定义唯一性:同租户+flow_code+flow_version 唯一(支持 ON CONFLICT 幂等)
CREATE UNIQUE INDEX IF NOT EXISTS uk_pmis_flow_definition_code_version
    ON pmis_flow_definition (flow_code, flow_version, tenant_id)
    WHERE deleted = 0;

-- =====================================================
-- 初始化数据：PMIS 业务流定义
-- =====================================================
INSERT INTO pmis_flow_definition
    (flow_code, flow_name, category, flow_version, model_value, form_custom, form_path,
     activity_status, is_publish, description, status, tenant_id, provider_trace_id,
     created_by, updated_by, version)
VALUES
    ('project_initiation', '项目立项审批', 'project',  '1.0', 'CLASSICS', 'N',
     '/project/initiation/detail', 1, 1,
     '项目立项审批：申请人 → 部门负责人 → 分管领导 → 总经理', 'ENABLED', 1, 'init_v1', 0, 0, 1),
    ('contract_change',    '合同变更审批', 'contract', '1.0', 'CLASSICS', 'N',
     '/contract/change/detail', 1, 1,
     '合同变更审批：申请人 → 法务 → 财务 → 总经理', 'ENABLED', 1, 'init_v1', 0, 0, 1),
    ('project_closure',    '项目销项审批', 'closure',  '1.0', 'CLASSICS', 'N',
     '/closure/detail', 1, 1,
     '项目销项审批：PM → 部门负责人 → 财务 → 分管领导', 'ENABLED', 1, 'init_v1', 0, 0, 1),
    ('pmis_leave',         'PMIS 通用请假', 'admin',    '1.0', 'CLASSICS', 'N',
     '/admin/leave/detail', 1, 1,
     'PMIS 通用请假：申请人 → 直属上级 → 人事', 'ENABLED', 1, 'init_v1', 0, 0, 1)
ON CONFLICT (flow_code, flow_version, tenant_id) WHERE deleted = 0 DO NOTHING;

CREATE TABLE IF NOT EXISTS pmis_flow_audit_log(
    id                 VARCHAR(20)    NOT NULL,
    instance_id        VARCHAR(20)       NOT NULL,
    task_id            VARCHAR(20),
    flow_code          VARCHAR(64)  NOT NULL,
    business_type      VARCHAR(64),
    business_id        VARCHAR(20),
    node_code          VARCHAR(64),
    node_name          VARCHAR(128),
    action             VARCHAR(32)  NOT NULL,
    operator_id        VARCHAR(20),
    operator_name      VARCHAR(64),
    target_id          VARCHAR(20),
    target_name        VARCHAR(64),
    comment            TEXT,
    operated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status             VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_by         VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT     NOT NULL DEFAULT 0,
    tenant_id          VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id  VARCHAR(64),
    -- 数据完整性约束
    CONSTRAINT ck_pfal_status_enum      CHECK (status IN ('ENABLED','DISABLED')),
    CONSTRAINT ck_pfal_action_enum      CHECK (action IN ('START','PASS','REJECT','TRANSFER','DELEGATE','COUNTERSIGN_BEFORE','COUNTERSIGN_AFTER',
                                                            'RECALL','URGE','TERMINATE','SUSPEND','ACTIVATE','CLAIM','DELEGATE_RETURN',
                                                            'PARALLEL_PASS','SEQUENTIAL_PASS','VOTE_PASS','AUTO_PASS','COUNTERSIGN_REMOVE',
                                                            'MARK_READ','COMMUNICATE','SLA_TIMEOUT','SLA_ESCALATE')),
    CONSTRAINT ck_pfal_deleted          CHECK (deleted IN (0, 1)),
    -- 分区表主键必须包含分区键
    PRIMARY KEY (id, operated_at)
) PARTITION BY RANGE (operated_at);

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

-- 复合/部分索引(替代零散的单列索引,引入 tenant_id + WHERE deleted=0)
-- P1-4: 父表索引,自动传播到所有月度分区
CREATE INDEX IF NOT EXISTS idx_pfal_tenant_instance_operated
    ON pmis_flow_audit_log(tenant_id, instance_id, operated_at DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfal_tenant_task_operated
    ON pmis_flow_audit_log(tenant_id, task_id, operated_at DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfal_tenant_operator_operated
    ON pmis_flow_audit_log(tenant_id, operator_id, operated_at DESC)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfal_tenant_biz
    ON pmis_flow_audit_log(tenant_id, business_type, business_id)
    WHERE deleted = 0 AND business_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pfal_tenant_action_operated
    ON pmis_flow_audit_log(tenant_id, action, operated_at DESC)
    WHERE deleted = 0;

-- P1-4: BRIN 索引(父表,自动传播) — 流程审计时间范围扫描
CREATE INDEX IF NOT EXISTS idx_pmis_flow_audit_log_brin
    ON pmis_flow_audit_log USING BRIN (operated_at)
    WITH (pages_per_range = 32);

-- P1-4: provider_trace_id 索引(全链路追踪)
CREATE INDEX IF NOT EXISTS idx_pfal_provider_trace
    ON pmis_flow_audit_log (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- --------------------------------------------------------------------

-- ============================ [026] add pmis flow cc ============================

-- =============================================================
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
CREATE TABLE IF NOT EXISTS pmis_flow_cc(
    id                 VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id          VARCHAR(20)       NOT NULL,
    instance_id        VARCHAR(20)       NOT NULL,
    task_id            VARCHAR(20),
    node_code          VARCHAR(64)  NOT NULL,
    node_name          VARCHAR(128),
    flow_code          VARCHAR(64)  NOT NULL,
    flow_name          VARCHAR(128),
    business_key       VARCHAR(128),
    cc_user_id         VARCHAR(20)       NOT NULL,
    cc_user_name       VARCHAR(64),
    cc_type            VARCHAR(16)  NOT NULL DEFAULT 'CC_NODE',
    trigger_user_id    VARCHAR(20),
    trigger_user_name  VARCHAR(64),
    title              VARCHAR(255),
    content            TEXT,
    read_status        VARCHAR(16)  NOT NULL DEFAULT 'UNREAD',
    read_at            TIMESTAMPTZ,
    provider_trace_id  VARCHAR(64),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT     NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_pfcc_cc_type          CHECK (cc_type     IN ('CC_NODE','MANUAL_CC','AUTO_CC')),
    CONSTRAINT ck_pfcc_read_status      CHECK (read_status IN ('UNREAD','READ')),
    CONSTRAINT ck_pfcc_read_at          CHECK ((read_status = 'UNREAD' AND read_at IS NULL) OR read_status = 'READ'),
    CONSTRAINT ck_pfcc_deleted          CHECK (deleted IN (0, 1))
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
CREATE INDEX IF NOT EXISTS idx_pmis_flow_cc_tenant_user
    ON pmis_flow_cc (tenant_id, cc_user_id, read_status, deleted)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pmis_flow_cc_instance
    ON pmis_flow_cc (tenant_id, instance_id, deleted)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pmis_flow_cc_biz
    ON pmis_flow_cc (tenant_id, business_key, deleted)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pmis_flow_cc_created
    ON pmis_flow_cc (tenant_id, created_at DESC)
    WHERE deleted = 0;

-- -------------------------------------------
-- 2. 抄送触发配置表（cc 配置由用户/系统预置，无需触发时由节点类型决定）
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_flow_cc_rule(
    id                 VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id          VARCHAR(20)       NOT NULL,
    flow_code          VARCHAR(64)  NOT NULL,
    node_code          VARCHAR(64)  NOT NULL,
    rule_type          VARCHAR(16)  NOT NULL,
    rule_target        VARCHAR(255) NOT NULL,
    enabled            SMALLINT     NOT NULL DEFAULT 1,
    provider_trace_id  VARCHAR(64),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT     NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_pfccr_rule_type       CHECK (rule_type IN ('USER','ROLE','DEPT','SPEL')),
    CONSTRAINT ck_pfccr_enabled         CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_pfccr_deleted         CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_flow_cc_rule IS '流程抄送规则配置 - 自动抄送规则（如：变更金额>1万自动抄送 CEO）';

COMMENT ON COLUMN pmis_flow_cc_rule.rule_type IS '规则类型：USER/ROLE/DEPT/SPEL';

COMMENT ON COLUMN pmis_flow_cc_rule.rule_target IS '规则目标：用户/角色/部门/SpEL 表达式';

COMMENT ON COLUMN pmis_flow_cc_rule.enabled IS '是否启用 0=停用 1=启用';

CREATE INDEX IF NOT EXISTS idx_pmis_flow_cc_rule_tenant
    ON pmis_flow_cc_rule (tenant_id, flow_code, node_code, deleted)
    WHERE deleted = 0;

ALTER TABLE pmis_flow_node ADD COLUMN IF NOT EXISTS sla_config TEXT;

COMMENT ON COLUMN pmis_flow_node.form_fields_config IS 'GAP-P0: 表单字段权限 JSON — {"fieldKey":"EDIT|READONLY|HIDDEN",...}';

COMMENT ON COLUMN pmis_flow_node.sla_config IS 'GAP-P1: SLA 超时配置 JSON — {"timeoutMinutes":120,"action":"REMIND|ESCALATE|AUTO_PASS|AUTO_REJECT","reminderCount":3,"adminUserId":1}';

-- -------------------------------------------
-- 2. pmis_flow_instance 新增子流程字段
-- -------------------------------------------
ALTER TABLE pmis_flow_instance ADD COLUMN IF NOT EXISTS parent_instance_id VARCHAR(20);

ALTER TABLE pmis_flow_instance ADD COLUMN IF NOT EXISTS parent_node_code VARCHAR(64);

ALTER TABLE pmis_flow_instance ADD COLUMN IF NOT EXISTS reject_reason      TEXT;

ALTER TABLE pmis_flow_instance ADD COLUMN IF NOT EXISTS due_at             TIMESTAMPTZ;

ALTER TABLE pmis_flow_instance ADD COLUMN IF NOT EXISTS version            INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN pmis_flow_instance.parent_instance_id IS 'GAP-P1: 父流程实例 ID（子流程场景，可空）';

COMMENT ON COLUMN pmis_flow_instance.parent_node_code IS 'GAP-P1: 父流程中触发子流程的节点编码（可空）';

COMMENT ON COLUMN pmis_flow_instance.reject_reason IS '退回原因（最近一次 REJECT 操作的备注，重审时清空）';

COMMENT ON COLUMN pmis_flow_instance.due_at IS '子流程超时时间（超时自动终止子流程，可空）';

COMMENT ON COLUMN pmis_flow_instance.version IS '乐观锁版本号（P1-2）';

CREATE INDEX IF NOT EXISTS idx_pmis_flow_instance_parent
    ON pmis_flow_instance (parent_instance_id)
    WHERE parent_instance_id IS NOT NULL;

-- -------------------------------------------
-- 3. pmis_flow_run_task 新增乐观锁版本号
-- -------------------------------------------
ALTER TABLE pmis_flow_run_task ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN pmis_flow_run_task.version IS 'GAP-P1: 乐观锁版本号 — 会签并发安全，防止多线程同时推进';

-- -------------------------------------------
-- 4. pmis_flow_audit_log 新增 action 枚举值扩展（无需 DDL，仅文档说明）
-- 新增 action 值: AUTO_PASS / COUNTERSIGN_REMOVE / MARK_READ / COMMUNICATE / SLA_TIMEOUT / SLA_ESCALATE
-- -------------------------------------------

-- --------------------------------------------------------------------

-- ============================ [029] add pmis flow timer ============================

-- =============================================================
-- 工作流定时器节点 + 边界定时器
--
-- P1-2: 工作流定时器
--   1. 中间定时器（intermediateTimer）: 流程到达此节点后等待指定时间再继续
--   2. 边界定时器（boundaryTimer）: 挂在 userTask 上，到达时间未完成则触发超时分支
--
-- 设计：
--   - pmis_flow_timer: 定时器实例表（每创建一个定时器节点实例时写入一行）
--   - timer_status: PENDING / FIRED / CANCELLED
--   - fire_at: 到点时间，cronjob 每 30s 扫描一次到点的 PENDING 记录并触发
--   - boundary_task_id: 边界定时器关联的 userTask ID
-- =============================================================

-- -------------------------------------------
-- 1. 定时器实例表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_flow_timer(
    id                 VARCHAR(20) PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id          VARCHAR(20)       NOT NULL,
    instance_id        VARCHAR(20)       NOT NULL,
    definition_id      VARCHAR(20)       NOT NULL,
    flow_code          VARCHAR(64)  NOT NULL,
    node_code          VARCHAR(64)  NOT NULL,
    node_name          VARCHAR(128),
    -- 中间定时器 INTERMEDIATE / 边界定时器 BOUNDARY
    timer_type         VARCHAR(16)  NOT NULL DEFAULT 'INTERMEDIATE',
    -- 边界定时器关联的 userTask
    boundary_task_id   VARCHAR(20),
    -- 触发时间
    fire_at            TIMESTAMPTZ  NOT NULL,
    -- CRON 表达式（可空，仅用于循环定时器）
    cycle              VARCHAR(64),
    -- 状态: PENDING / FIRED / CANCELLED
    timer_status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    -- 触发时间
    fired_at           TIMESTAMPTZ,
    -- 取消原因（userTask 完成时关闭）
    cancel_reason      VARCHAR(255),
    provider_trace_id  VARCHAR(64),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT     NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_pft_timer_type        CHECK (timer_type   IN ('INTERMEDIATE','BOUNDARY')),
    CONSTRAINT ck_pft_timer_status      CHECK (timer_status IN ('PENDING','FIRED','CANCELLED')),
    CONSTRAINT ck_pft_fired_status      CHECK ((timer_status = 'PENDING' AND fired_at IS NULL) OR timer_status IN ('FIRED','CANCELLED')),
    CONSTRAINT ck_pft_deleted           CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_flow_timer IS '工作流定时器 - 中间定时器/边界定时器调度表';

COMMENT ON COLUMN pmis_flow_timer.timer_type IS 'INTERMEDIATE 中间定时器 / BOUNDARY 边界定时器';

COMMENT ON COLUMN pmis_flow_timer.timer_status IS 'PENDING 待执行 / FIRED 已触发 / CANCELLED 已取消';

COMMENT ON COLUMN pmis_flow_timer.fire_at IS '到点时间，扫描器按此字段选取待执行记录';

-- 复合/部分索引(替代零散的单列索引,引入 tenant_id + WHERE deleted=0)
-- 索引：扫描器按 fire_at + status 选取
CREATE INDEX IF NOT EXISTS idx_pmis_flow_timer_tenant_scan
    ON pmis_flow_timer (tenant_id, timer_status, fire_at)
    WHERE deleted = 0;

-- 索引：实例维度查询
CREATE INDEX IF NOT EXISTS idx_pmis_flow_timer_tenant_instance
    ON pmis_flow_timer (tenant_id, instance_id)
    WHERE deleted = 0;

-- 索引：边界定时器反向关联 userTask
CREATE INDEX IF NOT EXISTS idx_pmis_flow_timer_tenant_boundary
    ON pmis_flow_timer (tenant_id, boundary_task_id)
    WHERE deleted = 0 AND boundary_task_id IS NOT NULL;

-- --------------------------------------------------------------------

-- ============================ [030] add pmis flow delegate auth ============================

-- =============================================================
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
CREATE TABLE IF NOT EXISTS pmis_flow_delegate_auth(
    id                    VARCHAR(20)    PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id             VARCHAR(20)       NOT NULL DEFAULT '1',
    owner_user_id         VARCHAR(20)       NOT NULL,
    owner_user_name       VARCHAR(64),
    delegate_user_id      VARCHAR(20)       NOT NULL,
    delegate_user_name    VARCHAR(64),
    -- 匹配模式: ALL/FLOW/FLOW_NODE/ROLE
    scope_type            VARCHAR(16)  NOT NULL,
    -- 流程编码（scopeType=FLOW/FLOW_NODE 时必填）
    flow_code             VARCHAR(64),
    -- 节点编码（scopeType=FLOW_NODE 时必填）
    node_code             VARCHAR(64),
    -- 角色编码（scopeType=ROLE 时必填）
    role_code             VARCHAR(64),
    start_time            TIMESTAMPTZ  NOT NULL,
    end_time              TIMESTAMPTZ  NOT NULL,
    -- 状态: ENABLED=启用 DISABLED=停用 EXPIRED=已过期 REVOKED=已撤回
    auth_status           VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    reason                VARCHAR(255),
    provider_trace_id     VARCHAR(64),
    created_by            VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               SMALLINT     NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_pfda_scope_type         CHECK (scope_type  IN ('ALL','FLOW','FLOW_NODE','ROLE')),
    CONSTRAINT ck_pfda_auth_status        CHECK (auth_status IN ('ENABLED','DISABLED','EXPIRED','REVOKED')),
    CONSTRAINT ck_pfda_time_range         CHECK (end_time > start_time),
    CONSTRAINT ck_pfda_scope_consistency  CHECK (
        (scope_type = 'ALL'      AND flow_code IS NULL AND node_code IS NULL AND role_code IS NULL) OR
        (scope_type = 'FLOW'     AND flow_code IS NOT NULL AND node_code IS NULL AND role_code IS NULL) OR
        (scope_type = 'FLOW_NODE' AND flow_code IS NOT NULL AND node_code IS NOT NULL AND role_code IS NULL) OR
        (scope_type = 'ROLE'     AND flow_code IS NULL AND node_code IS NULL AND role_code IS NOT NULL)
    ),
    CONSTRAINT ck_pfda_distinct_users     CHECK (owner_user_id <> delegate_user_id),
    CONSTRAINT ck_pfda_deleted            CHECK (deleted IN (0, 1))
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
CREATE INDEX IF NOT EXISTS idx_pmis_flow_delegate_auth_owner
    ON pmis_flow_delegate_auth (tenant_id, owner_user_id, auth_status, deleted)
    WHERE deleted = 0;

-- 索引：按 delegate 查询代理给我的任务（创建任务时反向匹配）
CREATE INDEX IF NOT EXISTS idx_pmis_flow_delegate_auth_delegate
    ON pmis_flow_delegate_auth (tenant_id, delegate_user_id, auth_status, deleted)
    WHERE deleted = 0;

-- 索引：按生效时间扫描待生效/已过期记录
CREATE INDEX IF NOT EXISTS idx_pmis_flow_delegate_auth_time
    ON pmis_flow_delegate_auth (tenant_id, start_time, end_time, deleted)
    WHERE deleted = 0;

-- 索引：按流程编码匹配（创建任务时）
CREATE INDEX IF NOT EXISTS idx_pmis_flow_delegate_auth_flow
    ON pmis_flow_delegate_auth (tenant_id, flow_code, auth_status, deleted)
    WHERE deleted = 0;

-- -------------------------------------------
-- 2. 委派代理使用日志（审计追溯：谁在什么时间被代理处理了什么任务）
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_flow_delegate_log(
    id                 VARCHAR(20)    PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id          VARCHAR(20)       NOT NULL DEFAULT '1',
    auth_id            VARCHAR(20)       NOT NULL,
    instance_id        VARCHAR(20)       NOT NULL,
    task_id            VARCHAR(20)       NOT NULL,
    node_code          VARCHAR(64),
    owner_user_id      VARCHAR(20)       NOT NULL,
    delegate_user_id   VARCHAR(20)       NOT NULL,
    -- 操作类型: ACT=代理办理 VIEW=代理查看
    op_type            VARCHAR(16)  NOT NULL,
    -- 实际处理动作：PASS/REJECT/CLAIM/TRANSFER/...
    action             VARCHAR(16),
    comment            TEXT,
    provider_trace_id  VARCHAR(64),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT     NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_pfdl_op_type          CHECK (op_type IN ('ACT','VIEW')),
    CONSTRAINT ck_pfdl_op_action        CHECK (action IS NULL OR action IN ('PASS','REJECT','CLAIM','TRANSFER','DELEGATE','COMMUNICATE')),
    CONSTRAINT ck_pfdl_deleted          CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_flow_delegate_log IS '流程委派代理使用日志 - 审计代理操作';

COMMENT ON COLUMN pmis_flow_delegate_log.auth_id IS '关联的授权 ID';

COMMENT ON COLUMN pmis_flow_delegate_log.task_id IS '被代理的任务 ID';

COMMENT ON COLUMN pmis_flow_delegate_log.op_type IS '操作类型：ACT=办理 / VIEW=查看';

COMMENT ON COLUMN pmis_flow_delegate_log.action IS '办理动作：PASS/REJECT/CLAIM/TRANSFER';

COMMENT ON COLUMN pmis_flow_delegate_log.comment IS '办理意见';

CREATE INDEX IF NOT EXISTS idx_pmis_flow_delegate_log_auth
    ON pmis_flow_delegate_log (tenant_id, auth_id, deleted)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pmis_flow_delegate_log_task
    ON pmis_flow_delegate_log (tenant_id, task_id, deleted)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pmis_flow_delegate_log_delegate
    ON pmis_flow_delegate_log (tenant_id, delegate_user_id, created_at DESC)
    WHERE deleted = 0;

-- --------------------------------------------------------------------

-- ============================ [033] add pmis flow weight ============================

-- =============================================================
-- 流程多实例会签权重 + VOTE 通过率
--
-- P1-5: 多实例会签权重（per-user 权重）+ VOTE 通过率（可配置阈值）
--      对标钉钉/飞书的会签权重：财务总监 3 票，普通员工 1 票。
--      默认阈值 50% + 1（即过半数通过），支持节点 ext 配置 passRate（0~1）。
-- =============================================================

-- -------------------------------------------
-- 1. pmis_flow_user 增加 weight 字段（每个办理人的票数/权重）
-- -----------------------------------
ALTER TABLE pmis_flow_user
    ADD COLUMN IF NOT EXISTS weight INTEGER NOT NULL DEFAULT 1;

COMMENT ON COLUMN pmis_flow_user.weight IS '办理人权重（默认 1，可配置 2/3 等）';

-- -----------------------------------
-- GAP-P0-3: pmis_flow_user 增加 sign_type 字段（区分原始审批人与加签人）
-- -----------------------------------
ALTER TABLE pmis_flow_user
    ADD COLUMN IF NOT EXISTS sign_type VARCHAR(16) NOT NULL DEFAULT 'ORIGINAL';

COMMENT ON COLUMN pmis_flow_user.sign_type IS '加签类型: ORIGINAL 原始审批人 / BEFORE 前加签 / AFTER 后加签 / PARALLEL 并加签 / ADD 追加处理人';

-- -------------------------------------------
-- 2. pmis_flow_run_task 增加 vote_pass_rate 字段（VOTE 模式下的通过率阈值）
-- -------------------------------------------
ALTER TABLE pmis_flow_run_task
    ADD COLUMN IF NOT EXISTS vote_pass_rate DECIMAL(5, 4) NOT NULL DEFAULT 0.5;

COMMENT ON COLUMN pmis_flow_run_task.vote_pass_rate IS 'VOTE 模式通过率阈值（0~1，默认 0.5 表示过半数）';

-- --------------------------------------------------------------------

-- ============================ [034] add pmis flow sla reminder ============================

-- =============================================================
-- 流程 SLA 超时自动策略 + 催办计数
--
-- P1-6: 后端超时自动策略（PASS/REJECT/NOTIFY/ESCALATE）
--      对标钉钉/飞书的审批超时自动化能力：
--      1. 节点可配 slaConfig.timeoutMinutes（超时阈值）
--      2. 节点可配 slaConfig.action（REMIND/ESCALATE/AUTO_PASS/AUTO_REJECT）
--      3. 节点可配 slaConfig.reminderIntervalMinutes（重复提醒间隔，默认 60）
--      4. 节点可配 slaConfig.maxReminders（最大提醒次数，默认 3）
--      5. 节点可配 slaConfig.escalateUserId（升级目标用户，可空=管理员）
--      6. 任务表 pmis_flow_run_task 记录 reminder_count / last_reminded_at / sla_action
-- =============================================================

-- -------------------------------------------
-- 1. pmis_flow_run_task 增加 SLA 跟踪字段
-- -------------------------------------------
ALTER TABLE pmis_flow_run_task
    ADD COLUMN IF NOT EXISTS reminder_count   INTEGER       NOT NULL DEFAULT 0;

ALTER TABLE pmis_flow_run_task
    ADD COLUMN IF NOT EXISTS last_reminded_at TIMESTAMP     DEFAULT NULL;

ALTER TABLE pmis_flow_run_task
    ADD COLUMN IF NOT EXISTS sla_action       VARCHAR(32)   DEFAULT NULL;

ALTER TABLE pmis_flow_run_task
    ADD COLUMN IF NOT EXISTS sla_escalated    SMALLINT      NOT NULL DEFAULT 0;

COMMENT ON COLUMN pmis_flow_run_task.reminder_count   IS '已发送的 SLA 催办次数';

COMMENT ON COLUMN pmis_flow_run_task.last_reminded_at IS '最近一次催办时间';

COMMENT ON COLUMN pmis_flow_run_task.sla_action       IS '最终触发的 SLA 动作（REMIND/ESCALATE/AUTO_PASS/AUTO_REJECT）';

COMMENT ON COLUMN pmis_flow_run_task.sla_escalated    IS '是否已升级（0 否 / 1 是，避免重复升级）';

-- --------------------------------------------------------------------

-- ============================ [037] init pmis flow archive ============================

-- ============================================================
-- V1.0.0_037  P2-3 流程历史归档表
-- ============================================================
-- 说明：流程实例/任务/变量归档到冷存储表，减小主表压力。
--   - pmis_flow_his_instance：归档的流程实例（已完成且超过 retention 天数）
--   - pmis_flow_his_variable：归档的流程变量（独立表，instance 归档时同步迁移）
--   - 触发：FlowHistoryArchiveJobHandler 每天 03:00 扫描
--   - 默认归档阈值：30 天（可在 pmis_job.params 配置）
-- ============================================================

-- 归档实例表（结构与 pmis_flow_instance 一致 + archived_at 字段）
CREATE TABLE IF NOT EXISTS pmis_flow_his_instance(
    id                 VARCHAR(20)    PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    flow_code          VARCHAR(64)  NOT NULL,
    flow_name          VARCHAR(128),
    definition_id      VARCHAR(20),
    flow_version       VARCHAR(20),
    business_type      VARCHAR(64),
    business_id        VARCHAR(20),
    business_no        VARCHAR(64),
    title              VARCHAR(256),
    initiator_id       VARCHAR(20),
    initiator_name     VARCHAR(64),
    current_node_code  VARCHAR(64),
    current_node_name  VARCHAR(128),
    variable           TEXT,
    flow_status        VARCHAR(16)  NOT NULL,
    activity_status    SMALLINT     NOT NULL DEFAULT 1,
    start_at           TIMESTAMPTZ,
    end_at             TIMESTAMPTZ,
    duration_ms        BIGINT,
    created_by         VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_by         VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at         TIMESTAMPTZ  NOT NULL,
    archived_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tenant_id          VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id  VARCHAR(64),
    -- 数据完整性约束
    CONSTRAINT ck_pfhi_flow_status     CHECK (flow_status IN ('RUNNING','COMPLETED','TERMINATED','SUSPENDED')),
    CONSTRAINT ck_pfhi_activity_status CHECK (activity_status IN (0, 1)),
    CONSTRAINT ck_pfhi_duration_nonneg CHECK (duration_ms IS NULL OR duration_ms >= 0),
    CONSTRAINT ck_pfhi_time_range      CHECK (end_at IS NULL OR start_at IS NULL OR end_at >= start_at)
);

COMMENT ON TABLE  pmis_flow_his_instance IS '流程实例归档表: 已完成且超过 retention 天数的实例迁移至此';

COMMENT ON COLUMN pmis_flow_his_instance.archived_at IS '归档时间';

-- 复合/部分索引
CREATE INDEX IF NOT EXISTS idx_pfhi_tenant_business
    ON pmis_flow_his_instance(tenant_id, business_type, business_id);

CREATE INDEX IF NOT EXISTS idx_pfhi_tenant_flow_code_status
    ON pmis_flow_his_instance(tenant_id, flow_code, flow_status);

CREATE INDEX IF NOT EXISTS idx_pfhi_tenant_initiator_archived
    ON pmis_flow_his_instance(tenant_id, initiator_id, archived_at DESC);

CREATE INDEX IF NOT EXISTS idx_pfhi_tenant_end_at
    ON pmis_flow_his_instance(tenant_id, end_at DESC)
    WHERE end_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pfhi_tenant_archived_at
    ON pmis_flow_his_instance(tenant_id, archived_at DESC);

-- 归档变量表（用于归档 instance 时同步迁移 variable 字段中的大 JSON）
CREATE TABLE IF NOT EXISTS pmis_flow_his_variable(
    id            VARCHAR(20)    PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id     VARCHAR(20)       NOT NULL DEFAULT '1',
    instance_id   VARCHAR(20)       NOT NULL,
    var_key       VARCHAR(128) NOT NULL,
    var_value     TEXT,
    archived_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE pmis_flow_his_variable IS '流程变量归档表: instance.variable JSON 拆分到独立行';

CREATE INDEX IF NOT EXISTS idx_pfhv_tenant_instance
    ON pmis_flow_his_variable(tenant_id, instance_id);

CREATE INDEX IF NOT EXISTS idx_pfhv_tenant_instance_key
    ON pmis_flow_his_variable(tenant_id, instance_id, var_key);

-- 归档统计视图（管理员可见：实例总数/已归档/未归档）
CREATE OR REPLACE VIEW pmis_view_flow_archive_stats
    WITH (security_invoker = true) AS
SELECT
    COALESCE(main.flow_code, his.flow_code)   AS flow_code,
    COALESCE(main.tenant_id, his.tenant_id)   AS tenant_id,
    COALESCE(main.cnt_main, 0)                AS active_count,
    COALESCE(his.cnt_his, 0)                  AS archived_count
FROM
    (SELECT flow_code, tenant_id, COUNT(*) AS cnt_main
     FROM pmis_flow_instance
     WHERE deleted = 0
     GROUP BY flow_code, tenant_id) main
FULL OUTER JOIN
    (SELECT flow_code, tenant_id, COUNT(*) AS cnt_his
     FROM pmis_flow_his_instance
     GROUP BY flow_code, tenant_id) his
    ON main.flow_code = his.flow_code AND main.tenant_id = his.tenant_id;

COMMENT ON VIEW pmis_view_flow_archive_stats IS '流程归档统计: active_count 主表实例数 / archived_count 已归档实例数';

-- --------------------------------------------------------------------

-- ============================ [038] add pmis flow canary ============================

-- ============================================================
-- V1.0.0_038  P3-1 流程定义灰度发布字段
-- ============================================================
-- 说明：为流程定义增加灰度发布（canary release）能力：
--   - canary_percent: 灰度比例 0-100，0 表示全量走稳定版，100 表示全量走灰度版
--   - canary_status: 灰度状态 NONE / CANARYING / PROMOTED / ROLLED_BACK
--   - canary_strategy: 灰度切流策略 USER_HASH（按发起人ID hash分流）/ RANDOM（随机）/ WHITELIST（白名单）
--   - canary_rollout_log: 灰度发布历史（JSON 数组），记录每次调整比例的操作人/时间/百分比/备注
--
-- 业务流程：
--   1. 发布新版本后，调用 publishCanary(defId, percent) 将其标记为灰度版本
--   2. 启动流程实例时，FlowDefinitionService.getEffectiveDefinition()
--      根据 canary_percent + canary_strategy 决定使用稳定版或灰度版
--   3. 运营人员调用 promoteCanary(defId, percent) 提升灰度比例，最终 promoteCanary(defId, 100) 完成全量
--   4. 出现问题可调用 rollbackCanary(defId) 回滚到稳定版
-- ============================================================

ALTER TABLE pmis_flow_definition
    ADD COLUMN IF NOT EXISTS canary_percent          SMALLINT     NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS canary_status           VARCHAR(16)  NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS canary_strategy         VARCHAR(16)  NOT NULL DEFAULT 'USER_HASH',
    ADD COLUMN IF NOT EXISTS canary_rollout_log      TEXT;

COMMENT ON COLUMN pmis_flow_definition.canary_percent IS
    '灰度比例 0-100（0=稳定版 / 100=全量灰度版）';

COMMENT ON COLUMN pmis_flow_definition.canary_status IS
    '灰度状态: NONE 无 / CANARYING 灰度中 / PROMOTED 已全量 / ROLLED_BACK 已回滚';

COMMENT ON COLUMN pmis_flow_definition.canary_strategy IS
    '灰度切流策略: USER_HASH 按发起人ID hash / RANDOM 随机 / WHITELIST 白名单';

COMMENT ON COLUMN pmis_flow_definition.canary_rollout_log IS
    '灰度发布历史 JSON 数组[{operatorId,operatorName,fromPercent,toPercent,operateAt,note}]';

-- 灰度索引（按状态快速查询正在灰度中的定义）
CREATE INDEX IF NOT EXISTS idx_pfd_canary_status
    ON pmis_flow_definition(canary_status)
    WHERE deleted = 0 AND canary_status <> 'NONE';

-- --------------------------------------------------------------------

-- ============================ [046] add pmis flow event subscription ============================

-- ============================================================
-- V1.0.0_046  P0-1 BPMN 事件运行时 — 事件订阅表
-- ============================================================
-- 说明：为 BPMN 错误事件(errorEvent)和消息事件(messageEvent)提供运行时支持。
--   当流程推进到"事件捕获节点"(intermediateCatchEvent / boundaryEvent)时，
--   在本表插入一行 WAITING 记录，流程进入等待状态（不创建人工任务）。
--   外部系统通过 correlateMessage / throwError API 触发事件，
--   匹配到 WAITING 订阅后标记 COMPLETED 并推进流程到下游节点。
--
-- 事件类型：
--   MESSAGE — 消息事件（中间捕获 / 边界），通过 messageName + correlationKey 匹配
--   ERROR   — 错误事件（边界），通过 errorCode 匹配，由 serviceTask 或 API 抛出
--   SIGNAL  — 信号事件（预留，暂不实现运行时）
-- ============================================================

CREATE TABLE IF NOT EXISTS pmis_flow_event_subscription (
    id                  VARCHAR(20)       PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id           VARCHAR(20)          NOT NULL DEFAULT '1',
    instance_id         VARCHAR(20)          NOT NULL,
    definition_id       VARCHAR(20)          NOT NULL,
    flow_code           VARCHAR(64)     NOT NULL,
    node_code           VARCHAR(64)     NOT NULL,
    node_name           VARCHAR(128),
    event_type          VARCHAR(16)     NOT NULL,   -- MESSAGE / ERROR / SIGNAL
    event_ref           VARCHAR(128),               -- messageRef / errorRef / signalRef
    correlation_key     VARCHAR(256),               -- 消息关联键（业务标识，可空）
    boundary_task_id    VARCHAR(20),                     -- 边界事件关联的 userTask ID
    subscription_status VARCHAR(16)     NOT NULL DEFAULT 'WAITING', -- WAITING / COMPLETED / CANCELLED
    payload             TEXT,                       -- 触发时携带的业务数据 JSON
    triggered_at        TIMESTAMPTZ,                -- 实际触发时间
    trigger_source      VARCHAR(128),               -- 触发来源（API / SERVICE_TASK / BOUNDARY）
    cancel_reason       VARCHAR(256),               -- 取消原因
    -- 审计字段
    status              VARCHAR(16)     NOT NULL DEFAULT 'ENABLED',
    created_by          VARCHAR(20)          NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by          VARCHAR(20)          NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted             SMALLINT        NOT NULL DEFAULT 0,
    provider_trace_id   VARCHAR(64),
    -- 数据完整性约束
    CONSTRAINT ck_pfes_event_type        CHECK (event_type IN ('MESSAGE','ERROR','SIGNAL')),
    CONSTRAINT ck_pfes_subscription_status CHECK (subscription_status IN ('WAITING','COMPLETED','CANCELLED')),
    CONSTRAINT ck_pfes_status            CHECK (status IN ('ENABLED','DISABLED')),
    CONSTRAINT ck_pfes_deleted           CHECK (deleted IN (0, 1)),
    CONSTRAINT ck_pfes_trigger_consistency CHECK (
        (subscription_status = 'WAITING'    AND triggered_at IS NULL) OR
        (subscription_status = 'COMPLETED'  AND triggered_at IS NOT NULL) OR
        (subscription_status = 'CANCELLED'  AND triggered_at IS NULL)
    )
);

COMMENT ON TABLE pmis_flow_event_subscription IS '工作流事件订阅表 — BPMN 错误/消息事件运行时';

COMMENT ON COLUMN pmis_flow_event_subscription.event_type IS '事件类型: MESSAGE 消息 / ERROR 错误 / SIGNAL 信号';

COMMENT ON COLUMN pmis_flow_event_subscription.event_ref IS '事件引用标识（messageRef / errorRef / signalRef）';

COMMENT ON COLUMN pmis_flow_event_subscription.correlation_key IS '消息关联键，用于业务级消息匹配';

COMMENT ON COLUMN pmis_flow_event_subscription.boundary_task_id IS '边界事件关联的 userTask ID（中间事件为 NULL）';

COMMENT ON COLUMN pmis_flow_event_subscription.subscription_status IS '订阅状态: WAITING 等待中 / COMPLETED 已触发 / CANCELLED 已取消';

-- 复合/部分索引
CREATE INDEX IF NOT EXISTS idx_pfes_tenant_instance
    ON pmis_flow_event_subscription(tenant_id, instance_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfes_tenant_event_match
    ON pmis_flow_event_subscription(tenant_id, event_type, event_ref, subscription_status)
    WHERE deleted = 0 AND subscription_status = 'WAITING';

CREATE INDEX IF NOT EXISTS idx_pfes_tenant_boundary
    ON pmis_flow_event_subscription(tenant_id, boundary_task_id)
    WHERE boundary_task_id IS NOT NULL AND deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfes_tenant_correlation
    ON pmis_flow_event_subscription(tenant_id, correlation_key, subscription_status)
    WHERE deleted = 0 AND correlation_key IS NOT NULL;

-- --------------------------------------------------------------------

-- ============================ [048] add pmis flow run task priority ============================

-- ============================================================
-- P1-1: 任务优先级 priority 字段落地
-- ============================================================
-- 用途：
--   1. 待办列表按 priority DESC, created_at ASC 默认排序（高优先级 + 先到先审）
--   2. 节点 ext.priority 由 BpmnXmlParser 解析 BPMN flowable:priority 写入
--   3. 任务创建时从 node.ext.priority 拷贝到 task.priority
--
-- 取值范围：1~100，默认 50（中等优先级）
--   1-25: 低
--   26-50: 中
--   51-75: 高
--   76-100: 紧急
-- ============================================================

ALTER TABLE pmis_flow_run_task
    ADD COLUMN IF NOT EXISTS priority INT NOT NULL DEFAULT 50;

COMMENT ON COLUMN pmis_flow_run_task.priority IS 'P1-1: 任务优先级（1-100，默认 50），待办默认按 priority DESC, created_at ASC 排序';

-- --------------------------------------------------------------------

-- ============================ [050] add pmis flow notify outbox ============================

-- ============================================================
-- [DEPRECATED] V1.0.0_050  P2-1 可靠消息投递 — 工作流通知外发箱（pmis_flow_notify_outbox）
-- ============================================================
-- 说明：工作流通知已统一迁移到 ydsz-pmis-message 模块（pmis_msg_* 表）。
--   本表为历史遗留设计，无 Java 实现（无 Mapper/DO/Service），不应再使用。
--   工作流通知请通过 MessageServiceClient (common/feign) 调用 message 服务。
-- P4 架构优化：统一通知模板，删除 workflow 废弃通知表。
-- 保留此 DDL 仅作参考，新部署不执行。实际使用请删除本段。
-- ============================================================

CREATE TABLE IF NOT EXISTS pmis_flow_notify_outbox (
    id                  VARCHAR(20)       PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id           VARCHAR(20)          NOT NULL DEFAULT '1',
    -- 事件标识
    event_type          VARCHAR(64)     NOT NULL,               -- TASK_CREATED / TASK_COMPLETED / INSTANCE_TERMINATED 等
    biz_type            VARCHAR(64)     NOT NULL,               -- 业务类型: WORKFLOW_TASK / WORKFLOW_INSTANCE / WORKFLOW_CC
    biz_id              VARCHAR(20),                                 -- 业务 ID（taskId / instanceId）
    instance_id         VARCHAR(20),                                 -- 流程实例 ID（便于按实例查询）
    task_id             VARCHAR(20),                                 -- 任务 ID（便于按任务查询）
    -- 消息内容
    payload             TEXT            NOT NULL,               -- JSON 载荷（接收方解析）
    target_channels     VARCHAR(128),                           -- 投递通道: INAPP / IM / EMAIL / SMS（逗号分隔，空表示按 event_type 默认）
    target_user_ids     VARCHAR(512),                           -- 接收用户 ID 列表（逗号分隔，空表示由 payload 自行决定）
    -- 投递状态
    status              VARCHAR(16)     NOT NULL DEFAULT 'PENDING', -- PENDING / SENT / DEAD
    retry_count         INT             NOT NULL DEFAULT 0,
    max_retries         INT             NOT NULL DEFAULT 5,     -- 默认最大重试 5 次
    next_retry_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(), -- 下次重试时间（指数退避）
    sent_at             TIMESTAMPTZ,                            -- 实际投递成功时间
    error_msg           VARCHAR(1024),                          -- 最近一次失败原因
    -- 链路追踪
    provider_trace_id   VARCHAR(64),
    -- 审计字段
    created_by          VARCHAR(20)          NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by          VARCHAR(20)          NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted             SMALLINT        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_pfno_status          CHECK (status IN ('PENDING','SENT','DEAD')),
    CONSTRAINT ck_pfno_retry_count     CHECK (retry_count >= 0),
    CONSTRAINT ck_pfno_max_retries     CHECK (max_retries >= 0),
    CONSTRAINT ck_pfno_retry_bounded   CHECK (retry_count <= max_retries + 1),
    CONSTRAINT ck_pfno_deleted         CHECK (deleted IN (0, 1)),
    CONSTRAINT ck_pfno_status_consistency CHECK (
        (status = 'PENDING' AND sent_at IS NULL) OR
        (status = 'SENT'    AND sent_at IS NOT NULL) OR
        (status = 'DEAD'    AND sent_at IS NULL)
    )
);

COMMENT ON TABLE pmis_flow_notify_outbox IS '工作流通知外发箱 — 可靠消息投递（Outbox Pattern，P2-1 阶段一），由扫描任务异步投递到 NotificationClient / IM / 邮件 / 短信';

COMMENT ON COLUMN pmis_flow_notify_outbox.event_type IS '事件类型: TASK_CREATED / TASK_COMPLETED / INSTANCE_TERMINATED 等';

COMMENT ON COLUMN pmis_flow_notify_outbox.biz_type IS '业务类型: WORKFLOW_TASK / WORKFLOW_INSTANCE / WORKFLOW_CC';

COMMENT ON COLUMN pmis_flow_notify_outbox.payload IS 'JSON 载荷，由接收方解析';

COMMENT ON COLUMN pmis_flow_notify_outbox.target_channels IS '投递通道: INAPP / IM / EMAIL / SMS（逗号分隔）';

COMMENT ON COLUMN pmis_flow_notify_outbox.target_user_ids IS '接收用户 ID 列表（逗号分隔）';

COMMENT ON COLUMN pmis_flow_notify_outbox.status IS '投递状态: PENDING 待投递 / SENT 已投递 / DEAD 死信';

COMMENT ON COLUMN pmis_flow_notify_outbox.retry_count IS '已重试次数';

COMMENT ON COLUMN pmis_flow_notify_outbox.max_retries IS '最大重试次数（默认 5）';

COMMENT ON COLUMN pmis_flow_notify_outbox.next_retry_at IS '下次重试时间（指数退避：30s/60s/120s/300s/600s）';

COMMENT ON COLUMN pmis_flow_notify_outbox.error_msg IS '最近一次失败原因';

-- 复合/部分索引
CREATE INDEX IF NOT EXISTS idx_pfno_tenant_pending_scan
    ON pmis_flow_notify_outbox(tenant_id, status, next_retry_at)
    WHERE deleted = 0 AND status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_pfno_tenant_biz
    ON pmis_flow_notify_outbox(tenant_id, biz_type, biz_id)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfno_tenant_instance
    ON pmis_flow_notify_outbox(tenant_id, instance_id)
    WHERE deleted = 0 AND instance_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pfno_tenant_trace
    ON pmis_flow_notify_outbox(tenant_id, provider_trace_id)
    WHERE deleted = 0 AND provider_trace_id IS NOT NULL;

-- ============================================================
-- 五、pmis_flow_notify_outbox 表 tenant_id 索引（H2.5）
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_peo_tenant_status
    ON pmis_flow_notify_outbox(tenant_id, status, next_retry_at) WHERE deleted = 0;

-- 流程历史变量归档表
ALTER TABLE pmis_flow_his_variable ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(20) NOT NULL DEFAULT '1';

CREATE INDEX IF NOT EXISTS idx_flow_his_var_tenant ON pmis_flow_his_variable(tenant_id);

ANALYZE pmis_flow_notify_outbox;

ANALYZE pmis_flow_his_variable;

-- ====================================================================

-- ============================ [058] init pmis flow third party ============================

-- =============================================================
-- 三方审批账号映射表 + 回调日志表
--
-- P0-2: 三方审批 SDK（钉钉/飞书/企微）回调接入
--   1. pmis_flow_third_party_account — 系统用户与三方平台账号的映射关系，
--      并缓存 access_token / refresh_token（加密存储），供回调时反查系统用户。
--   2. pmis_flow_third_party_log — 三方审批回调原始数据落库，便于重放/排障/对账。
--
-- 兼容性：
--   - 全部使用 IF NOT EXISTS，可重复执行
--   - 审计字段与 BaseDO 对齐（created_by/created_at/updated_by/updated_at/deleted）
--   - tenant_id 默认值 1，单租户部署不影响数据
-- =============================================================

-- -------------------------------------------
-- 1. 三方审批账号映射表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_flow_third_party_account (
    id                 VARCHAR(20)       PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id          VARCHAR(20)          NOT NULL DEFAULT '1',
    user_id            VARCHAR(20)          NOT NULL,
    platform           VARCHAR(20)     NOT NULL,
    open_id            VARCHAR(128),
    union_id           VARCHAR(128),
    corp_id            VARCHAR(128),
    agent_id           VARCHAR(128),
    access_token       VARCHAR(512),
    refresh_token      VARCHAR(512),
    token_expire_at    TIMESTAMPTZ,
    status             VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    provider_trace_id  VARCHAR(64),
    created_by         VARCHAR(20)          NOT NULL DEFAULT 'SYSTEM',
    created_at         TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(20)          NOT NULL DEFAULT 'SYSTEM',
    updated_at         TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_pftpa_status         CHECK (status IN ('ACTIVE','INACTIVE','REVOKED')),
    CONSTRAINT ck_pftpa_platform       CHECK (platform IN ('DINGTALK','FEISHU','WECOM')),
    CONSTRAINT ck_pftpa_deleted        CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_flow_third_party_account IS 'P0-2: 三方审批账号映射表（钉钉/飞书/企微）';

COMMENT ON COLUMN pmis_flow_third_party_account.user_id IS '系统用户 ID';

COMMENT ON COLUMN pmis_flow_third_party_account.platform IS '平台: DINGTALK/FEISHU/WECOM';

COMMENT ON COLUMN pmis_flow_third_party_account.open_id IS '三方 openId';

COMMENT ON COLUMN pmis_flow_third_party_account.union_id IS '三方 unionId';

COMMENT ON COLUMN pmis_flow_third_party_account.corp_id IS '企业 ID';

COMMENT ON COLUMN pmis_flow_third_party_account.agent_id IS '应用 ID';

COMMENT ON COLUMN pmis_flow_third_party_account.access_token IS '访问令牌(加密存储)';

COMMENT ON COLUMN pmis_flow_third_party_account.refresh_token IS '刷新令牌(加密存储)';

COMMENT ON COLUMN pmis_flow_third_party_account.token_expire_at IS '令牌过期时间';

COMMENT ON COLUMN pmis_flow_third_party_account.status IS '状态: ACTIVE/INACTIVE/REVOKED';

COMMENT ON COLUMN pmis_flow_third_party_account.deleted IS '逻辑删除标记 0=未删 1=已删';

-- 复合/部分索引
CREATE UNIQUE INDEX IF NOT EXISTS uk_pftpa_tenant_user_platform
    ON pmis_flow_third_party_account(tenant_id, user_id, platform)
    WHERE deleted = 0;

CREATE UNIQUE INDEX IF NOT EXISTS uk_pftpa_tenant_platform_openid
    ON pmis_flow_third_party_account(tenant_id, platform, open_id)
    WHERE deleted = 0 AND open_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pftpa_tenant_platform_union
    ON pmis_flow_third_party_account(tenant_id, platform, union_id)
    WHERE union_id IS NOT NULL;

-- -------------------------------------------
-- 2. 三方审批回调日志表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_flow_third_party_log (
    id                  VARCHAR(20)       PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id           VARCHAR(20)          NOT NULL DEFAULT '1',
    platform            VARCHAR(20)     NOT NULL,
    event_type          VARCHAR(64)     NOT NULL,
    process_instance_id VARCHAR(128),
    business_type       VARCHAR(64),
    business_id         VARCHAR(128),
    callback_data       TEXT,
    handle_status       VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    error_msg           VARCHAR(512),
    -- P2-6: 双向同步 — 本地→三方回撤状态与结果
    sync_back_status    VARCHAR(20)     NOT NULL DEFAULT 'NOT_REQUIRED',
    sync_back_msg       VARCHAR(512),
    provider_trace_id   VARCHAR(64),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 数据完整性约束
    CONSTRAINT ck_pftpl_platform         CHECK (platform IN ('DINGTALK','FEISHU','WECOM')),
    CONSTRAINT ck_pftpl_handle_status    CHECK (handle_status IN ('PENDING','SUCCESS','FAIL')),
    CONSTRAINT ck_pftpl_sync_back_status CHECK (sync_back_status IN ('NOT_REQUIRED','PENDING','SUCCESS','FAIL'))
);

COMMENT ON TABLE pmis_flow_third_party_log IS 'P0-2: 三方审批回调日志表';

COMMENT ON COLUMN pmis_flow_third_party_log.platform IS '平台: DINGTALK/FEISHU/WECOM';

COMMENT ON COLUMN pmis_flow_third_party_log.event_type IS '事件类型';

COMMENT ON COLUMN pmis_flow_third_party_log.process_instance_id IS '三方流程实例 ID';

COMMENT ON COLUMN pmis_flow_third_party_log.business_type IS '业务类型';

COMMENT ON COLUMN pmis_flow_third_party_log.business_id IS '业务 ID';

COMMENT ON COLUMN pmis_flow_third_party_log.callback_data IS '回调原始数据';

COMMENT ON COLUMN pmis_flow_third_party_log.handle_status IS '处理状态: PENDING/SUCCESS/FAIL';

COMMENT ON COLUMN pmis_flow_third_party_log.error_msg IS '处理失败原因';

-- 复合/部分索引
CREATE INDEX IF NOT EXISTS idx_pftpl_tenant_platform_created
    ON pmis_flow_third_party_log(tenant_id, platform, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pftpl_tenant_status
    ON pmis_flow_third_party_log(tenant_id, handle_status, created_at DESC)
    WHERE handle_status = 'PENDING';

-- --------------------------------------------------------------------

-- ============================ [059] init pmis flow dmn ============================

-- =============================================================
-- DMN 决策表定义表
--
-- P0-4: DMN 决策表引擎（对标 Camunda/Flowable DMN）
--   1. pmis_flow_dmn_table — 决策表定义主表，存储输入/输出列与规则行的 JSON。
--   2. 命中策略支持 UNIQUE/FIRST/PRIORITY/ANY/COLLECT 五种模式。
--   3. 规则行/列定义以 JSON 存储，便于前端动态编辑，无需 DDL 变更。
--
-- 兼容性：
--   - 全部使用 IF NOT EXISTS，可重复执行
--   - 审计字段与 BaseDO 对齐（created_by/created_at/updated_by/updated_at/deleted）
--   - tenant_id 默认值 1，单租户部署不影响数据
-- =============================================================

-- -------------------------------------------
-- DMN 决策表定义表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_flow_dmn_table (
    id                VARCHAR(20)       PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id         VARCHAR(20)          NOT NULL DEFAULT '1',
    table_key         VARCHAR(128)    NOT NULL,
    table_name        VARCHAR(128)    NOT NULL,
    description       VARCHAR(512),
    hit_policy        VARCHAR(20)     NOT NULL DEFAULT 'UNIQUE',
    collect_operator  VARCHAR(20)     DEFAULT 'LIST',
    inputs_json       TEXT,
    outputs_json      TEXT,
    rules_json        TEXT,
    version           INT             NOT NULL DEFAULT 1,
    status            VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    provider_trace_id VARCHAR(64),
    created_by        VARCHAR(20)          NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(20)          NOT NULL DEFAULT 'SYSTEM',
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT uk_pfdt_tenant_key         UNIQUE (tenant_id, table_key, deleted),
    -- P2-10: 新增 RULE_ORDER / OUTPUT_ORDER 命中策略
    CONSTRAINT ck_pfdt_hit_policy         CHECK (hit_policy IN ('UNIQUE','FIRST','PRIORITY','ANY','COLLECT','RULE_ORDER','OUTPUT_ORDER')),
    CONSTRAINT ck_pfdt_collect_operator   CHECK (collect_operator IS NULL OR collect_operator IN ('LIST','SUM','MIN','MAX','COUNT')),
    CONSTRAINT ck_pfdt_status             CHECK (status IN ('DRAFT','PUBLISHED','DEPRECATED')),
    CONSTRAINT ck_pfdt_version            CHECK (version > 0),
    CONSTRAINT ck_pfdt_deleted            CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_flow_dmn_table IS 'P0-4: DMN 决策表定义; P2-10: 扩展命中策略至 7 种';

COMMENT ON COLUMN pmis_flow_dmn_table.table_key IS '决策表唯一标识';

COMMENT ON COLUMN pmis_flow_dmn_table.table_name IS '决策表名称';

COMMENT ON COLUMN pmis_flow_dmn_table.description IS '决策表描述';

COMMENT ON COLUMN pmis_flow_dmn_table.hit_policy IS '命中策略: UNIQUE/FIRST/PRIORITY/ANY/COLLECT/RULE_ORDER/OUTPUT_ORDER';

COMMENT ON COLUMN pmis_flow_dmn_table.collect_operator IS 'COLLECT 聚合运算符: LIST/SUM/MIN/MAX/COUNT';

COMMENT ON COLUMN pmis_flow_dmn_table.inputs_json IS '输入列定义(JSON)';

COMMENT ON COLUMN pmis_flow_dmn_table.outputs_json IS '输出列定义(JSON)';

COMMENT ON COLUMN pmis_flow_dmn_table.rules_json IS '规则行定义(JSON)';

COMMENT ON COLUMN pmis_flow_dmn_table.version IS '版本号';

COMMENT ON COLUMN pmis_flow_dmn_table.status IS '状态: DRAFT/PUBLISHED/DEPRECATED';

COMMENT ON COLUMN pmis_flow_dmn_table.tenant_id IS '租户 ID（多租户隔离）';

COMMENT ON COLUMN pmis_flow_dmn_table.deleted IS '逻辑删除标记 0=未删 1=已删';

-- 复合/部分索引
CREATE INDEX IF NOT EXISTS idx_pfdt_tenant_status
    ON pmis_flow_dmn_table (tenant_id, status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfdt_tenant_name
    ON pmis_flow_dmn_table (tenant_id, table_name)
    WHERE deleted = 0;

-- --------------------------------------------------------------------

-- ====================================================================
-- >>>>>>>>>> SUPPLEMENT: code-discovered tables (no migration script yet)
--   The following tables are referenced by MyBatis-Plus entities /
--   mappers in ydsz-pmis-backend, but no migration script has been
--   created yet. They are appended here for completeness so the
--   single-file initialization can be used on a fresh database.
--   Once a migration is published for each of them, this
--   block can be removed.
-- ====================================================================

-- ----------------------------------------------------------------
-- pmis_flow_template -- P3-1: process template marketplace
-- P2-9: 增加 parent_template_id / version / version_label /
--       inherit_type / is_latest 字段，支持模板继承与版本化
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_flow_template (
    id              VARCHAR(20)       PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id       VARCHAR(20)          NOT NULL DEFAULT '1',
    template_code   VARCHAR(128)    NOT NULL,
    template_name   VARCHAR(256)    NOT NULL,
    category        VARCHAR(64),
    description     VARCHAR(512),
    icon            VARCHAR(256),
    bpmn_xml        TEXT,
    form_path       VARCHAR(256),
    use_count       INTEGER         NOT NULL DEFAULT 0,
    sort_order      INTEGER         NOT NULL DEFAULT 0,
    -- P2-9: 模板继承与版本化字段
    parent_template_id VARCHAR(20),
    version         INTEGER         NOT NULL DEFAULT 1,
    version_label   VARCHAR(32),
    inherit_type    VARCHAR(16)     NOT NULL DEFAULT 'STANDALONE',
    is_latest       SMALLINT        NOT NULL DEFAULT 1,
    provider_trace_id VARCHAR(64),
    created_by      VARCHAR(20)          NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)          NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    -- P2-9: 唯一约束加入 version 维度，同一 template_code 可存在多版本
    CONSTRAINT uk_pft_tenant_code_version  UNIQUE (tenant_id, template_code, version, deleted),
    CONSTRAINT ck_pft_use_count            CHECK (use_count >= 0),
    CONSTRAINT ck_pft_deleted              CHECK (deleted IN (0, 1)),
    CONSTRAINT ck_pft_version              CHECK (version >= 1),
    CONSTRAINT ck_pft_is_latest            CHECK (is_latest IN (0, 1)),
    CONSTRAINT ck_pft_inherit_type         CHECK (inherit_type IN ('STANDALONE', 'CLONE', 'INHERIT'))
);

CREATE INDEX IF NOT EXISTS idx_pft_tenant_category_sort
    ON pmis_flow_template (tenant_id, category, sort_order)
    WHERE deleted = 0 AND is_latest = 1;

-- P2-9: 按 template_code 查最新版本的高效索引
CREATE INDEX IF NOT EXISTS idx_pft_tenant_code_latest
    ON pmis_flow_template (tenant_id, template_code, is_latest)
    WHERE deleted = 0;

-- P2-9: 按父模板反查继承关系
CREATE INDEX IF NOT EXISTS idx_pft_parent_template
    ON pmis_flow_template (tenant_id, parent_template_id)
    WHERE deleted = 0 AND parent_template_id IS NOT NULL;

COMMENT ON TABLE  pmis_flow_template IS 'P3-1: 流程模板市场表, 预置常用流程模板供一键导入; P2-9: 支持模板继承与版本化';

COMMENT ON COLUMN pmis_flow_template.id IS '主键 ID';

COMMENT ON COLUMN pmis_flow_template.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_flow_template.template_code IS '模板编码 (租户内 + 版本内唯一)';

COMMENT ON COLUMN pmis_flow_template.template_name IS '模板名称';

COMMENT ON COLUMN pmis_flow_template.category IS '分类 (HR/FINANCE/ADMIN/PROJECT/GENERAL)';

COMMENT ON COLUMN pmis_flow_template.description IS '模板描述';

COMMENT ON COLUMN pmis_flow_template.icon IS '图标 URL';

COMMENT ON COLUMN pmis_flow_template.bpmn_xml IS 'BPMN 2.0 XML 流程定义';

COMMENT ON COLUMN pmis_flow_template.form_path IS '关联表单路径';

COMMENT ON COLUMN pmis_flow_template.use_count IS '使用次数';

COMMENT ON COLUMN pmis_flow_template.sort_order IS '排序值, 升序';

COMMENT ON COLUMN pmis_flow_template.parent_template_id IS 'P2-9: 父模板 ID (跨模板继承关系, STANDALONE 时为空)';

COMMENT ON COLUMN pmis_flow_template.version IS 'P2-9: 模板版本号 (从 1 开始单调递增)';

COMMENT ON COLUMN pmis_flow_template.version_label IS 'P2-9: 版本标签 (如 v1.0 / v2.0-rc1)';

COMMENT ON COLUMN pmis_flow_template.inherit_type IS 'P2-9: 继承类型 STANDALONE=独立 / CLONE=克隆 / INHERIT=继承';

COMMENT ON COLUMN pmis_flow_template.is_latest IS 'P2-9: 是否当前 template_code 下最新版本 0=否 1=是';

COMMENT ON COLUMN pmis_flow_template.created_at IS '创建时间';

COMMENT ON COLUMN pmis_flow_template.updated_at IS '更新时间';

COMMENT ON COLUMN pmis_flow_template.deleted IS '逻辑删除 0=未删 1=已删';

-- ----------------------------------------------------------------
-- pmis_flow_auto_trigger -- P3-2: process auto-trigger
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_flow_auto_trigger (
    id                   VARCHAR(20)       PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id            VARCHAR(20)          NOT NULL DEFAULT '1',
    source_flow_code     VARCHAR(64)     NOT NULL,
    target_flow_code     VARCHAR(64)     NOT NULL,
    condition_expression VARCHAR(1024),
    description          VARCHAR(512),
    enabled              INTEGER         NOT NULL DEFAULT 1,
    sort_order           INTEGER         NOT NULL DEFAULT 0,
    provider_trace_id    VARCHAR(64),
    created_by           VARCHAR(20)          NOT NULL DEFAULT 'SYSTEM',
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by           VARCHAR(20)          NOT NULL DEFAULT 'SYSTEM',
    updated_at           TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted              SMALLINT        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_pfat_enabled              CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_pfat_deleted              CHECK (deleted IN (0, 1))
);

CREATE INDEX IF NOT EXISTS idx_pfat_tenant_src_enabled
    ON pmis_flow_auto_trigger (tenant_id, source_flow_code, enabled)
    WHERE deleted = 0;

COMMENT ON TABLE  pmis_flow_auto_trigger IS 'P3-2: 流程完成时自动触发下游流程的规则表';

COMMENT ON COLUMN pmis_flow_auto_trigger.id IS '主键 ID';

COMMENT ON COLUMN pmis_flow_auto_trigger.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_flow_auto_trigger.source_flow_code IS '源流程编码 (触发方)';

COMMENT ON COLUMN pmis_flow_auto_trigger.target_flow_code IS '目标流程编码 (被触发方)';

COMMENT ON COLUMN pmis_flow_auto_trigger.condition_expression IS 'Aviator 条件表达式;为空则无条件触发';

COMMENT ON COLUMN pmis_flow_auto_trigger.description IS '触发规则说明';

COMMENT ON COLUMN pmis_flow_auto_trigger.enabled IS '是否启用 1=启用 0=禁用';

COMMENT ON COLUMN pmis_flow_auto_trigger.sort_order IS '触发顺序';

COMMENT ON COLUMN pmis_flow_auto_trigger.created_by IS '创建人';

COMMENT ON COLUMN pmis_flow_auto_trigger.created_at IS '创建时间';

COMMENT ON COLUMN pmis_flow_auto_trigger.updated_by IS '更新人';

COMMENT ON COLUMN pmis_flow_auto_trigger.updated_at IS '更新时间';

COMMENT ON COLUMN pmis_flow_auto_trigger.deleted IS '逻辑删除 0=未删 1=已删';

-- ----------------------------------------------------------------
-- [DEPRECATED] pmis_flow_notify_channel -- P3-3: notification channel config
-- 工作流通知已统一迁移到 ydsz-pmis-message 模块（pmis_msg_* 表），本表无 Java 实现，不应再使用。
-- 保留此 DDL 仅作参考，新部署不执行。实际使用请删除本段。
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_flow_notify_channel (
    id                VARCHAR(20)       PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id         VARCHAR(20)          NOT NULL DEFAULT '1',
    channel_type      VARCHAR(32)     NOT NULL,
    channel_name      VARCHAR(128)    NOT NULL,
    config            TEXT,
    enabled           SMALLINT        NOT NULL DEFAULT 1,
    provider_trace_id VARCHAR(64),
    created_by        VARCHAR(20)          NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(20)          NOT NULL DEFAULT 'SYSTEM',
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_pfnc_channel_type        CHECK (channel_type IN ('INAPP','EMAIL','SMS','WEBHOOK','DINGTALK','WECHAT','FEISHU')),
    CONSTRAINT ck_pfnc_enabled             CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_pfnc_deleted             CHECK (deleted IN (0, 1))
);

CREATE INDEX IF NOT EXISTS idx_pfnc_tenant_type_enabled
    ON pmis_flow_notify_channel (tenant_id, channel_type, enabled)
    WHERE deleted = 0;

COMMENT ON TABLE  pmis_flow_notify_channel IS 'P3-3: 工作流通知通道配置表 (INAPP/EMAIL/SMS/WEBHOOK/DINGTALK/WECHAT)';

COMMENT ON COLUMN pmis_flow_notify_channel.id IS '主键 ID';

COMMENT ON COLUMN pmis_flow_notify_channel.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_flow_notify_channel.channel_type IS '通道类型 (INAPP/EMAIL/SMS/WEBHOOK/DINGTALK/WECHAT/FEISHU)';

COMMENT ON COLUMN pmis_flow_notify_channel.channel_name IS '通道名称';

COMMENT ON COLUMN pmis_flow_notify_channel.config IS '配置 JSON (Webhook URL, 短信模板编码等)';

COMMENT ON COLUMN pmis_flow_notify_channel.enabled IS '是否启用 1=启用 0=禁用';

COMMENT ON COLUMN pmis_flow_notify_channel.created_by IS '创建人';

COMMENT ON COLUMN pmis_flow_notify_channel.created_at IS '创建时间';

COMMENT ON COLUMN pmis_flow_notify_channel.updated_by IS '更新人';

COMMENT ON COLUMN pmis_flow_notify_channel.updated_at IS '更新时间';

COMMENT ON COLUMN pmis_flow_notify_channel.deleted IS '逻辑删除 0=未删 1=已删';

-- ----------------------------------------------------------------
-- pmis_flow_task_comment -- P1-3: task comment thread
-- ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pmis_flow_task_comment (
    id              VARCHAR(20)       PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id       VARCHAR(20)          NOT NULL DEFAULT '1',
    instance_id     VARCHAR(20)          NOT NULL,
    task_id         VARCHAR(20)          NOT NULL,
    node_code       VARCHAR(64),
    user_id         VARCHAR(20)          NOT NULL,
    user_name       VARCHAR(128),
    content         TEXT,
    type            VARCHAR(16)     NOT NULL DEFAULT 'COMMENT',
    parent_id       VARCHAR(20),
    provider_trace_id VARCHAR(64),
    created_by      VARCHAR(20)          NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)          NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_pftc_type          CHECK (type IN ('COMMENT','QUESTION','REPLY')),
    CONSTRAINT ck_pftc_deleted       CHECK (deleted IN (0, 1))
);

CREATE INDEX IF NOT EXISTS idx_pftc_tenant_task_created
    ON pmis_flow_task_comment (tenant_id, task_id, created_at)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pftc_tenant_parent
    ON pmis_flow_task_comment (tenant_id, parent_id)
    WHERE deleted = 0 AND parent_id IS NOT NULL;

COMMENT ON TABLE  pmis_flow_task_comment IS 'P1-3: 工作流任务评论表 (楼中楼, 通过 parent_id 形成嵌套回复)';

COMMENT ON COLUMN pmis_flow_task_comment.id IS '主键 ID';

COMMENT ON COLUMN pmis_flow_task_comment.tenant_id IS '租户 ID';

COMMENT ON COLUMN pmis_flow_task_comment.instance_id IS '流程实例 ID';

COMMENT ON COLUMN pmis_flow_task_comment.task_id IS '任务 ID';

COMMENT ON COLUMN pmis_flow_task_comment.node_code IS '节点编码';

COMMENT ON COLUMN pmis_flow_task_comment.user_id IS '评论人 ID';

COMMENT ON COLUMN pmis_flow_task_comment.user_name IS '评论人姓名 (冗余)';

COMMENT ON COLUMN pmis_flow_task_comment.content IS '评论内容';

COMMENT ON COLUMN pmis_flow_task_comment.type IS '评论类型: COMMENT/QUESTION/REPLY';

COMMENT ON COLUMN pmis_flow_task_comment.parent_id IS '父评论 ID (楼中楼, 0=根评论)';

COMMENT ON COLUMN pmis_flow_task_comment.created_by IS '创建人';

COMMENT ON COLUMN pmis_flow_task_comment.created_at IS '创建时间';

COMMENT ON COLUMN pmis_flow_task_comment.updated_by IS '更新人';

COMMENT ON COLUMN pmis_flow_task_comment.updated_at IS '更新时间';

COMMENT ON COLUMN pmis_flow_task_comment.deleted IS '逻辑删除 0=未删 1=已删';

-- ----------------------------------------------------------------------------
-- 1) pmis_flow_run_task.assignor_id BIGINT -> VARCHAR(20),与 assignee_id 对齐
--    pmis_flow_his_task 补齐 assignor_id 列
-- ----------------------------------------------------------------------------
ALTER TABLE pmis_flow_run_task ALTER COLUMN assignor_id TYPE VARCHAR(20) USING assignor_id::VARCHAR(20);

ALTER TABLE pmis_flow_his_task ADD COLUMN IF NOT EXISTS assignor_id VARCHAR(20);

ALTER TABLE pmis_flow_his_task ADD COLUMN IF NOT EXISTS assignor_name VARCHAR(64);

COMMENT ON COLUMN pmis_flow_run_task.assignor_id IS '原审批人 ID(VARCHAR(20) 雪花 ID,与 assignee_id 对齐)';

COMMENT ON COLUMN pmis_flow_his_task.assignor_id IS '原审批人 ID(VARCHAR(20) 雪花 ID,与 assignee_id 对齐)';

COMMENT ON COLUMN pmis_flow_his_task.assignor_name IS '原审批人姓名';

-- 同步主表与历史表 assignor_id 索引(若已存在则跳过)
CREATE INDEX IF NOT EXISTS idx_pfrt_assignor
    ON pmis_flow_run_task (assignor_id)
    WHERE deleted = 0 AND assignor_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pfht_assignor
    ON pmis_flow_his_task (assignor_id)
    WHERE deleted = 0 AND assignor_id IS NOT NULL;

-- FOREACH 节点 partial unique index(替代原 UNIQUE ... WHERE 约束,PG 不支持该约束语法)
CREATE UNIQUE INDEX IF NOT EXISTS uk_pfrt_foreach_iter
    ON pmis_flow_run_task (instance_id, node_code, iter_var)
    WHERE iter_var IS NOT NULL AND deleted = 0;

ANALYZE pmis_flow_run_task;

ANALYZE pmis_flow_his_task;

-- DEFAULT 兜底分区
CREATE TABLE IF NOT EXISTS pmis_flow_audit_log_default
    PARTITION OF pmis_flow_audit_log DEFAULT;

COMMENT ON TABLE pmis_flow_audit_log_default IS
    'pmis_flow_audit_log 的 DEFAULT 兜底分区:'
    '接收超出已建月份范围的流程审计数据,运维需监控并及时创建对应月份分区;'
    '建表语句不可独立 DROP,需先 ALTER TABLE ... DETACH PARTITION';

ANALYZE pmis_flow_audit_log;

-- 5) 工作流核心(11 张)
CREATE INDEX IF NOT EXISTS idx_pmis_flow_definition_trace
    ON pmis_flow_definition (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pmis_flow_node_trace
    ON pmis_flow_node (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pmis_flow_skip_trace
    ON pmis_flow_skip (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pmis_flow_instance_trace
    ON pmis_flow_instance (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pmis_flow_run_task_trace
    ON pmis_flow_run_task (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pmis_flow_his_task_trace
    ON pmis_flow_his_task (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pmis_flow_his_instance_trace
    ON pmis_flow_his_instance (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pmis_flow_user_trace
    ON pmis_flow_user (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pmis_flow_cc_trace
    ON pmis_flow_cc (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pmis_flow_cc_rule_trace
    ON pmis_flow_cc_rule (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pmis_flow_timer_trace
    ON pmis_flow_timer (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pmis_flow_delegate_auth_trace
    ON pmis_flow_delegate_auth (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pmis_flow_delegate_log_trace
    ON pmis_flow_delegate_log (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pmis_flow_event_subscription_trace
    ON pmis_flow_event_subscription (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- 8) 工作流扩展(8 张: 第三方/模板/DMN/触发器等)
CREATE INDEX IF NOT EXISTS idx_pmis_flow_third_party_account_trace
    ON pmis_flow_third_party_account (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pmis_flow_third_party_log_trace
    ON pmis_flow_third_party_log (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pmis_flow_dmn_table_trace
    ON pmis_flow_dmn_table (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pmis_flow_template_trace
    ON pmis_flow_template (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pmis_flow_auto_trigger_trace
    ON pmis_flow_auto_trigger (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pmis_flow_notify_channel_trace
    ON pmis_flow_notify_channel (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_pmis_flow_task_comment_trace
    ON pmis_flow_task_comment (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- 3) 给出 P3 启用模板(注释,真正启用时去掉 -- 即可)
-- ----------------------------------------------------------------------------
-- [TEMPLATE] P3-14 启用:在 pmis_employee 加密敏感字段
-- ALTER TABLE pmis_employee
--     ADD COLUMN IF NOT EXISTS id_card_cipher VARCHAR(512) NOT NULL DEFAULT '',
--     ADD COLUMN IF NOT EXISTS id_card_hash   VARCHAR(64)  NOT NULL DEFAULT '',
--     ADD COLUMN IF NOT EXISTS phone_cipher   VARCHAR(512) NOT NULL DEFAULT '',
--     ADD COLUMN IF NOT EXISTS phone_hash     VARCHAR(64)  NOT NULL DEFAULT '';
-- CREATE UNIQUE INDEX IF NOT EXISTS uk_pmis_employee_id_card_hash
--     ON pmis_employee (tenant_id, id_card_hash) WHERE deleted = 0 AND id_card_hash <> '';
-- CREATE UNIQUE INDEX IF NOT EXISTS uk_pmis_employee_phone_hash
--     ON pmis_employee (tenant_id, phone_hash) WHERE deleted = 0 AND phone_hash <> '';
-- ----------------------------------------------------------------------------
-- [TEMPLATE] P3-15 启用: pmis_data_export_audit 接入 OPLOG
-- ALTER TABLE pmis_data_export_audit
--     ADD COLUMN IF NOT EXISTS op_log_id   VARCHAR(20),
--     ADD COLUMN IF NOT EXISTS op_log_type VARCHAR(32) NOT NULL DEFAULT '';
-- CREATE INDEX IF NOT EXISTS idx_pmis_data_export_audit_oplog
--     ON pmis_data_export_audit (op_log_id) WHERE op_log_id IS NOT NULL;
-- ----------------------------------------------------------------------------

-- ====================================================================
-- ============================ [DEPRECATED] [067] P1-2 工作流通知模板表 ============================
-- ====================================================================
-- GAP-38: 通知内容模板化管理，替代硬编码
-- 支持 ${flowName}/${nodeName}/${assigneeName} 等变量占位符
-- [DEPRECATED] 工作流通知已统一迁移到 ydsz-pmis-message 模块（pmis_msg_* 表），
--   本表无 Java 实现，通知模板请使用 pmis_msg_template。保留此 DDL 仅作参考，新部署不执行。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pmis_flow_notify_template (
    id                  VARCHAR(20)       PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id           VARCHAR(20)          NOT NULL DEFAULT '1',
    template_code       VARCHAR(64)     NOT NULL,               -- 模板编码: TASK_CREATED / TASK_URGED / TASK_TIMEOUT 等
    template_name       VARCHAR(128)    NOT NULL,               -- 模板名称
    channel             VARCHAR(32)     NOT NULL DEFAULT 'INAPP', -- 通道: INAPP / EMAIL / SMS / WEBHOOK
    locale              VARCHAR(10)     NOT NULL DEFAULT 'zh_CN', -- P1-5: 语言区域: zh_CN / en_US 等
    title               VARCHAR(256)    NOT NULL,               -- 标题模板（支持 ${var} 占位符）
    content             TEXT            NOT NULL,               -- 内容模板（支持 ${var} 占位符）
    enabled             SMALLINT        NOT NULL DEFAULT 1,     -- 1=启用 0=禁用
    description         VARCHAR(512),
    -- 审计字段
    created_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT        NOT NULL DEFAULT 0,
    provider_trace_id   VARCHAR(64),
    version             INTEGER        NOT NULL DEFAULT 0,
    -- 约束
    CONSTRAINT uk_pfnt_tenant_code_channel_locale UNIQUE (tenant_id, template_code, channel, locale, deleted),
    CONSTRAINT ck_pfnt_channel CHECK (channel IN ('INAPP','EMAIL','SMS','WEBHOOK','DINGTALK','FEISHU','WECOM')),
    CONSTRAINT ck_pfnt_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_pfnt_deleted CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_flow_notify_template IS 'P1-2: 工作流通知模板表 — 通知内容模板化管理，支持 ${var} 变量占位符。P1-5: locale 字段支持多语言';

COMMENT ON COLUMN pmis_flow_notify_template.template_code IS '模板编码: TASK_CREATED / TASK_COMPLETED / TASK_REJECTED / TASK_URGED / TASK_TIMEOUT / INSTANCE_TERMINATED / CC_CREATED 等';

COMMENT ON COLUMN pmis_flow_notify_template.channel IS '通知通道: INAPP / EMAIL / SMS / WEBHOOK / DINGTALK / FEISHU / WECOM';

COMMENT ON COLUMN pmis_flow_notify_template.locale IS 'P1-5: 语言区域（zh_CN / en_US 等），同一 templateCode+channel 可配置多语言模板，默认 zh_CN';

COMMENT ON COLUMN pmis_flow_notify_template.title IS '标题模板（支持 ${flowName} ${nodeName} ${assigneeName} ${instanceId} ${taskId} 等占位符）';

COMMENT ON COLUMN pmis_flow_notify_template.content IS '内容模板（支持与 title 相同的占位符）';

COMMENT ON COLUMN pmis_flow_notify_template.enabled IS '是否启用: 1=启用 0=禁用';

CREATE INDEX IF NOT EXISTS idx_pfnt_tenant_code_locale
    ON pmis_flow_notify_template (tenant_id, template_code, channel, locale)
    WHERE deleted = 0 AND enabled = 1;

CREATE INDEX IF NOT EXISTS idx_pfnt_tenant_enabled
    ON pmis_flow_notify_template (tenant_id, enabled)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfnt_trace
    ON pmis_flow_notify_template (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- 初始化默认模板（P1-5: locale 默认 zh_CN）
INSERT INTO pmis_flow_notify_template (id, tenant_id, template_code, template_name, channel, locale, title, content, description)
VALUES
    ('1', '1', 'TASK_CREATED', '任务创建通知', 'INAPP', 'zh_CN',
     '您有一个新的审批待办',
     '流程【${flowName}】节点【${nodeName}】需要您处理，请尽快审批。',
     '任务创建时通知办理人'),
    ('2', '1', 'TASK_COMPLETED', '任务通过通知', 'INAPP', 'zh_CN',
     '审批已通过',
     '流程【${flowName}】节点【${nodeName}】已由 ${operatorName} 通过审批。',
     '任务通过时通知发起人'),
    ('3', '1', 'TASK_REJECTED', '任务驳回通知', 'INAPP', 'zh_CN',
     '审批被驳回',
     '流程【${flowName}】节点【${nodeName}】被 ${operatorName} 驳回，请查看并修改后重新提交。',
     '任务驳回时通知发起人'),
    ('4', '1', 'TASK_URGED', '催办通知', 'INAPP', 'zh_CN',
     '您有待办被催办',
     '流程【${flowName}】的审批任务被催办，请尽快处理。${comment}',
     '催办时通知办理人'),
    ('5', '1', 'TASK_TIMEOUT', '任务超时提醒', 'INAPP', 'zh_CN',
     '审批任务即将超时',
     '【${flowName}】${nodeName} 已超过截止时间 ${dueAt}，请尽快处理（第 ${reminderCount}/${maxReminders} 次提醒）。',
     'SLA 超时提醒办理人'),
    ('6', '1', 'INSTANCE_TERMINATED', '流程终止通知', 'INAPP', 'zh_CN',
     '流程已终止',
     '流程【${flowName}】已被终止，原因：${reason}。',
     '实例终止时通知发起人'),
    ('7', '1', 'CC_CREATED', '抄送通知', 'INAPP', 'zh_CN',
     '您有新的抄送',
     '流程【${flowName}】节点【${nodeName}】抄送给您，请查阅。',
     '抄送时通知接收人'),
    -- P1-5: 英文模板（en_US）
    ('101', '1', 'TASK_CREATED', 'Task Created', 'INAPP', 'en_US',
     'You have a new approval task',
     'Workflow [${flowName}] node [${nodeName}] requires your action. Please review promptly.',
     'Notify assignee when task is created'),
    ('102', '1', 'TASK_COMPLETED', 'Task Approved', 'INAPP', 'en_US',
     'Approval passed',
     'Workflow [${flowName}] node [${nodeName}] has been approved by ${operatorName}.',
     'Notify initiator when task is approved'),
    ('103', '1', 'TASK_REJECTED', 'Task Rejected', 'INAPP', 'en_US',
     'Approval rejected',
     'Workflow [${flowName}] node [${nodeName}] has been rejected by ${operatorName}. Please review and resubmit.',
     'Notify initiator when task is rejected'),
    ('104', '1', 'TASK_URGED', 'Task Urged', 'INAPP', 'en_US',
     'You have an urged task',
     'Workflow [${flowName}] approval task has been urged. Please process it ASAP. ${comment}',
     'Notify assignee when task is urged'),
    ('105', '1', 'TASK_TIMEOUT', 'Task Timeout Reminder', 'INAPP', 'en_US',
     'Approval task is about to timeout',
     '[${flowName}] ${nodeName} has exceeded the deadline ${dueAt}. Please process it ASAP (reminder ${reminderCount}/${maxReminders}).',
     'SLA timeout reminder for assignee'),
    ('106', '1', 'INSTANCE_TERMINATED', 'Instance Terminated', 'INAPP', 'en_US',
     'Workflow terminated',
     'Workflow [${flowName}] has been terminated. Reason: ${reason}.',
     'Notify initiator when instance is terminated'),
    ('107', '1', 'CC_CREATED', 'CC Notification', 'INAPP', 'en_US',
     'You have a new CC',
     'Workflow [${flowName}] node [${nodeName}] has been CC-ed to you for your reference.',
     'Notify receiver when CC is created')
ON CONFLICT DO NOTHING;

-- ====================================================================
-- ====================== [067B] P1-6 工作流 Webhook 订阅表 ======================
-- ====================================================================
-- P1-6: Webhook 事件订阅 — 外部系统注册回调 URL，订阅工作流事件
-- 投递流程：事件触发 → 查匹配订阅 → HMAC-SHA256 签名 → 写入 outbox → 异步 HTTP POST

CREATE TABLE IF NOT EXISTS pmis_flow_webhook_subscription (
    id                  VARCHAR(20)       PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id           VARCHAR(20)          NOT NULL DEFAULT '1',
    name                VARCHAR(128)    NOT NULL,               -- 订阅名称
    callback_url        VARCHAR(512)    NOT NULL,               -- 回调 URL（HTTPS 推荐）
    secret              VARCHAR(256),                            -- 签名密钥（HMAC-SHA256）
    event_types         VARCHAR(512),                            -- 订阅事件类型（逗号分隔，空=全部）
    enabled             SMALLINT        NOT NULL DEFAULT 1,     -- 1=启用 0=禁用
    description         VARCHAR(512),
    -- 审计字段
    created_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT        NOT NULL DEFAULT 0,
    provider_trace_id   VARCHAR(64),
    version             INTEGER        NOT NULL DEFAULT 0,
    -- 约束
    CONSTRAINT uk_pfws_tenant_name UNIQUE (tenant_id, name, deleted),
    CONSTRAINT ck_pfws_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_pfws_deleted CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_flow_webhook_subscription IS 'P1-6: 工作流 Webhook 事件订阅表 — 外部系统注册回调 URL 订阅工作流事件';

COMMENT ON COLUMN pmis_flow_webhook_subscription.callback_url IS '回调 URL（HTTPS 推荐），投递时 HTTP POST 该 URL';

COMMENT ON COLUMN pmis_flow_webhook_subscription.secret IS 'HMAC-SHA256 签名密钥，投递时以 X-Webhook-Signature: sha256=<hex> 头部携带签名';

COMMENT ON COLUMN pmis_flow_webhook_subscription.event_types IS '订阅事件类型（逗号分隔，如 TASK_CREATED,TASK_COMPLETED），空表示订阅全部事件';

COMMENT ON COLUMN pmis_flow_webhook_subscription.enabled IS '是否启用: 1=启用 0=禁用';

CREATE INDEX IF NOT EXISTS idx_pfws_tenant_enabled
    ON pmis_flow_webhook_subscription (tenant_id, enabled)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfws_trace
    ON pmis_flow_webhook_subscription (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- ====================================================================
-- ====================== [DEPRECATED] [067C] P1-7 工作流通知偏好表 ======================
-- ====================================================================
-- P1-7: 免打扰时段 / 通知聚合 — 用户可配置 quietHours（免打扰时段）和 digestMode（聚合模式）
-- 免打扰时段内（支持跨午夜，如 22:00→08:00）+ digestMode=1 时，站内推送延迟到时段结束后投递
-- 每个用户在租户内至多一条偏好记录（uk_pfnp_tenant_user）
-- [DEPRECATED] 工作流通知已统一迁移到 ydsz-pmis-message 模块（pmis_msg_* 表），
--   本表无 Java 实现，通知偏好请使用 pmis_msg_preference。保留此 DDL 仅作参考，新部署不执行。

CREATE TABLE IF NOT EXISTS pmis_flow_notify_preference (
    id                  VARCHAR(20)       PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id           VARCHAR(20)          NOT NULL DEFAULT '1',
    user_id             VARCHAR(20)       NOT NULL,               -- 用户 ID
    quiet_hours_start   VARCHAR(8),                               -- 免打扰开始时间 HH:mm（如 22:00），NULL=不启用
    quiet_hours_end     VARCHAR(8),                               -- 免打扰结束时间 HH:mm（如 08:00），NULL=不启用
    digest_mode         SMALLINT         NOT NULL DEFAULT 0,     -- 1=启用聚合（免打扰时段内延迟投递） 0=立即投递
    -- 审计字段
    created_by          VARCHAR(20)          NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)          NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT        NOT NULL DEFAULT 0,
    provider_trace_id   VARCHAR(64),
    version             INTEGER        NOT NULL DEFAULT 0,
    -- 约束
    CONSTRAINT uk_pfnp_tenant_user UNIQUE (tenant_id, user_id, deleted),
    CONSTRAINT ck_pfnp_digest CHECK (digest_mode IN (0, 1)),
    CONSTRAINT ck_pfnp_deleted CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_flow_notify_preference IS 'P1-7: 工作流通知偏好表 — 用户免打扰时段与通知聚合配置';

COMMENT ON COLUMN pmis_flow_notify_preference.quiet_hours_start IS '免打扰开始时间 HH:mm（如 22:00），NULL 表示不启用免打扰';

COMMENT ON COLUMN pmis_flow_notify_preference.quiet_hours_end IS '免打扰结束时间 HH:mm（如 08:00），支持跨午夜（start > end 时跨次日）';

COMMENT ON COLUMN pmis_flow_notify_preference.digest_mode IS '1=启用聚合（免打扰时段内延迟投递） 0=立即逐条投递（忽略免打扰）';

CREATE INDEX IF NOT EXISTS idx_pfnp_tenant_user
    ON pmis_flow_notify_preference (tenant_id, user_id)
    WHERE deleted = 0;

-- ====================================================================
-- ============================ [068] P1-6 工作流审批附件表 ============================
-- ====================================================================
-- ====================================================================
-- GAP-51: 审批附件支持（对标钉钉/飞书审批附件能力）
-- 审批时提交的附件（图片/文档/视频等）统一落库，支持查询与下载
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pmis_flow_attachment (
    id                  VARCHAR(20)       PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id           VARCHAR(20)          NOT NULL DEFAULT '1',
    instance_id         VARCHAR(20)       NOT NULL,               -- 关联流程实例
    task_id             VARCHAR(20),                              -- 关联任务（可为空：实例级附件）
    node_code           VARCHAR(64),                             -- 关联节点编码
    biz_type            VARCHAR(32)     NOT NULL DEFAULT 'TASK',  -- TASK=任务附件 / INSTANCE=实例附件 / COMMENT=意见附件
    file_name           VARCHAR(256)    NOT NULL,                -- 原始文件名
    file_ext            VARCHAR(16),                             -- 文件扩展名（jpg/pdf...）
    file_size           BIGINT          NOT NULL DEFAULT 0,      -- 字节大小
    content_type        VARCHAR(128),                            -- MIME 类型
    storage_key         VARCHAR(512)    NOT NULL,                -- 存储 key（OSS/COS/MinIO 对象 key 或本地相对路径）
    storage_type        VARCHAR(16)     NOT NULL DEFAULT 'OSS',  -- OSS / MINIO / LOCAL
    uploader_id         VARCHAR(20)     NOT NULL,                -- 上传人 ID
    uploader_name       VARCHAR(64),                             -- 上传人姓名
    download_url        VARCHAR(1024),                           -- 临时下载地址（可选，前端可直接展示）
    md5                 VARCHAR(64),                             -- 文件 MD5（去重/校验）
    deleted             SMALLINT        NOT NULL DEFAULT 0,
    created_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    provider_trace_id   VARCHAR(64),
    version             INTEGER        NOT NULL DEFAULT 0,
    CONSTRAINT ck_pffa_biz_type   CHECK (biz_type   IN ('TASK','INSTANCE','COMMENT')),
    CONSTRAINT ck_pffa_store_type CHECK (storage_type IN ('OSS','MINIO','LOCAL')),
    CONSTRAINT ck_pffa_deleted    CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_flow_attachment IS 'P1-6: 工作流审批附件表 — 审批时提交的图片/文档/视频等附件统一落库';

COMMENT ON COLUMN pmis_flow_attachment.instance_id IS '关联流程实例 ID';

COMMENT ON COLUMN pmis_flow_attachment.task_id IS '关联任务 ID（实例级附件可为空）';

COMMENT ON COLUMN pmis_flow_attachment.biz_type IS '附件业务类型: TASK=任务附件 / INSTANCE=实例附件 / COMMENT=意见附件';

COMMENT ON COLUMN pmis_flow_attachment.storage_key IS '存储对象的 key（OSS/COS/MinIO 对象 key 或本地相对路径）';

COMMENT ON COLUMN pmis_flow_attachment.download_url IS '临时下载地址（前端可直接展示，可能过期）';

CREATE INDEX IF NOT EXISTS idx_pffa_instance
    ON pmis_flow_attachment (instance_id, deleted) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pffa_task
    ON pmis_flow_attachment (task_id, deleted) WHERE task_id IS NOT NULL AND deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pffa_trace
    ON pmis_flow_attachment (provider_trace_id) WHERE provider_trace_id IS NOT NULL;

-- ====================================================================
-- ============================ [069] P2-1 委派沟通记录表 ============================
-- ====================================================================
-- GAP-08: 委派沟通记录保留（对标钉钉/飞书委托沟通）
-- 委托人与被委托人之间可在被委托任务上留言沟通，沟通记录持久化留存
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pmis_flow_delegate_message (
    id                  VARCHAR(20)       PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id           VARCHAR(20)          NOT NULL DEFAULT '1',
    task_id             VARCHAR(20)       NOT NULL,               -- 关联被委托任务
    instance_id         VARCHAR(20)       NOT NULL,               -- 关联流程实例
    node_code           VARCHAR(64),                             -- 关联节点编码
    sender_id           VARCHAR(20)       NOT NULL,              -- 发送人 ID
    sender_name         VARCHAR(64),                             -- 发送人姓名
    sender_role         VARCHAR(16)     NOT NULL DEFAULT 'OWNER', -- OWNER=委托人 / DELEGATE=被委托人
    content             TEXT            NOT NULL,                -- 沟通内容
    attachment_key      VARCHAR(512),                           -- 可选附件存储 key
    read_flag           SMALLINT        NOT NULL DEFAULT 0,     -- 0=未读 1=已读
    deleted             SMALLINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    provider_trace_id   VARCHAR(64),
    CONSTRAINT ck_pfdm_sender_role CHECK (sender_role IN ('OWNER','DELEGATE')),
    CONSTRAINT ck_pfdm_read_flag    CHECK (read_flag IN (0, 1)),
    CONSTRAINT ck_pfdm_deleted      CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_flow_delegate_message IS 'P2-1: 委派沟通记录表 — 委托人与被委托人之间的留言沟通，持久化留存';

COMMENT ON COLUMN pmis_flow_delegate_message.task_id IS '关联被委托任务 ID';

COMMENT ON COLUMN pmis_flow_delegate_message.sender_role IS '发送人角色: OWNER=委托人 / DELEGATE=被委托人';

COMMENT ON COLUMN pmis_flow_delegate_message.read_flag IS '是否已读: 0=未读 1=已读';

CREATE INDEX IF NOT EXISTS idx_pfdm_task
    ON pmis_flow_delegate_message (task_id, deleted) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfdm_instance
    ON pmis_flow_delegate_message (instance_id, deleted) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfdm_trace
    ON pmis_flow_delegate_message (provider_trace_id) WHERE provider_trace_id IS NOT NULL;

-- ====================================================================
-- ============================ [070] P2-2 流程评论多级回复表 ============================
-- ====================================================================
-- GAP-15: 审批评论多级回复（对标钉钉/飞书审批评论区）
-- 独立于 pmis_flow_audit_log（审计日志是操作轨迹，不可变），
-- 评论是讨论（可回复、可删除），关注点正交。
-- 支持：
--   1) 一级评论（parent_comment_id = NULL）
--   2) 多级回复（parent_comment_id 指向父评论，reply_to_user_id 标记被回复人）
--   3) 软删除（deleted=1 保留层级结构，前端显示"该评论已删除"）
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pmis_flow_comment (
    id                  VARCHAR(20)       PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id           VARCHAR(20)          NOT NULL DEFAULT '1',
    instance_id         VARCHAR(20)       NOT NULL,               -- 关联流程实例
    task_id             VARCHAR(20),                              -- 关联任务（可为空：实例级评论）
    node_code           VARCHAR(64),                             -- 关联节点编码
    user_id             VARCHAR(20)     NOT NULL,                 -- 评论人 ID
    user_name           VARCHAR(64),                             -- 评论人姓名（冗余）
    content             TEXT            NOT NULL,                 -- 评论内容
    parent_comment_id   VARCHAR(20),                              -- 父评论 ID（一级评论为 NULL）
    reply_to_user_id    VARCHAR(20),                              -- 被回复人 ID（回复某条评论时标记）
    reply_to_user_name  VARCHAR(64),                              -- 被回复人姓名（冗余）
    -- 审计字段
    created_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT        NOT NULL DEFAULT 0,
    provider_trace_id   VARCHAR(64),
    version             INTEGER        NOT NULL DEFAULT 0,
    -- 约束
    CONSTRAINT ck_pfc_deleted CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_flow_comment IS 'P2-2: 流程评论表 — 审批评论多级回复（对标钉钉/飞书审批评论区）';

COMMENT ON COLUMN pmis_flow_comment.instance_id IS '关联流程实例 ID';

COMMENT ON COLUMN pmis_flow_comment.task_id IS '关联任务 ID（实例级评论可为空）';

COMMENT ON COLUMN pmis_flow_comment.content IS '评论内容（富文本/纯文本）';

COMMENT ON COLUMN pmis_flow_comment.parent_comment_id IS '父评论 ID（一级评论为 NULL，二级及以下回复指向父评论 ID）';

COMMENT ON COLUMN pmis_flow_comment.reply_to_user_id IS '被回复人 ID（回复某条评论时标记，一级评论为 NULL）';

COMMENT ON COLUMN pmis_flow_comment.reply_to_user_name IS '被回复人姓名（冗余，便于前端展示）';

CREATE INDEX IF NOT EXISTS idx_pfc_instance
    ON pmis_flow_comment (tenant_id, instance_id, created_at) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfc_parent
    ON pmis_flow_comment (parent_comment_id, created_at) WHERE parent_comment_id IS NOT NULL AND deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfc_task
    ON pmis_flow_comment (task_id, created_at) WHERE task_id IS NOT NULL AND deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfc_trace
    ON pmis_flow_comment (provider_trace_id) WHERE provider_trace_id IS NOT NULL;

-- ====================================================================
-- ===================== P1-2: 审批常用语表 ===========================
-- ====================================================================
-- P1-2: 对标钉钉/飞书审批的"常用语"能力，用户可预设常用审批意见。
-- 系统预设（is_system=1）全局共享，用户自定义（is_system=0）按用户隔离。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pmis_flow_quick_comment (
    id                  VARCHAR(20)       PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    user_id             VARCHAR(20)       NOT NULL,               -- 所属用户（系统预设时为 'SYSTEM'）
    content             VARCHAR(500)      NOT NULL,               -- 常用语内容
    comment_type        VARCHAR(20),                              -- 意见分类：AGREE/DISAGREE/SUGGEST/INQUIRE
    sort_num            INTEGER           NOT NULL DEFAULT 0,     -- 排序号（越小越靠前）
    use_count           INTEGER           NOT NULL DEFAULT 0,     -- 使用次数
    is_system           SMALLINT          NOT NULL DEFAULT 0,     -- 是否系统预设（1=是，0=否）
    deleted             SMALLINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP         NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP         NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(20),
    updated_by          VARCHAR(20)
);

COMMENT ON TABLE  pmis_flow_quick_comment IS 'P1-2: 审批常用语表 — 用户预设常用审批意见，一键填入';

COMMENT ON COLUMN pmis_flow_quick_comment.user_id IS '所属用户 ID（系统预设为 SYSTEM）';

COMMENT ON COLUMN pmis_flow_quick_comment.content IS '常用语内容';

COMMENT ON COLUMN pmis_flow_quick_comment.comment_type IS '意见分类：AGREE/DISAGREE/SUGGEST/INQUIRE';

COMMENT ON COLUMN pmis_flow_quick_comment.sort_num IS '排序号（越小越靠前）';

COMMENT ON COLUMN pmis_flow_quick_comment.use_count IS '使用次数（统计用）';

COMMENT ON COLUMN pmis_flow_quick_comment.is_system IS '是否系统预设（1=是，0=否）';

CREATE INDEX IF NOT EXISTS idx_pfqc_user
    ON pmis_flow_quick_comment (tenant_id, user_id, deleted);

CREATE INDEX IF NOT EXISTS idx_pfqc_system
    ON pmis_flow_quick_comment (tenant_id, is_system, deleted) WHERE is_system = 1;

-- 系统预设常用语初始化数据
INSERT INTO pmis_flow_quick_comment (id, tenant_id, user_id, content, comment_type, sort_num, use_count, is_system, created_at, updated_at)
SELECT '1', '1', 'SYSTEM', '同意', 'AGREE', 1, 0, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM pmis_flow_quick_comment WHERE id = '1');

INSERT INTO pmis_flow_quick_comment (id, tenant_id, user_id, content, comment_type, sort_num, use_count, is_system, created_at, updated_at)
SELECT '2', '1', 'SYSTEM', '已阅', NULL, 2, 0, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM pmis_flow_quick_comment WHERE id = '2');

INSERT INTO pmis_flow_quick_comment (id, tenant_id, user_id, content, comment_type, sort_num, use_count, is_system, created_at, updated_at)
SELECT '3', '1', 'SYSTEM', '不同意，请补充材料后重新提交', 'DISAGREE', 3, 0, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM pmis_flow_quick_comment WHERE id = '3');

INSERT INTO pmis_flow_quick_comment (id, tenant_id, user_id, content, comment_type, sort_num, use_count, is_system, created_at, updated_at)
SELECT '4', '1', 'SYSTEM', '请确认金额是否正确', 'INQUIRE', 4, 0, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM pmis_flow_quick_comment WHERE id = '4');

INSERT INTO pmis_flow_quick_comment (id, tenant_id, user_id, content, comment_type, sort_num, use_count, is_system, created_at, updated_at)
SELECT '5', '1', 'SYSTEM', '建议优化方案后重新审批', 'SUGGEST', 5, 0, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM pmis_flow_quick_comment WHERE id = '5');

-- ====================================================================
-- ============================ [071] P3-3 AI 推荐审批人反馈记录表 ============================
-- ====================================================================
-- P3-3: 推荐审批人反馈闭环 — 记录用户对 AI 推荐审批人的反馈行为
-- 用于统计 AI 推荐准确率（接受率/拒绝率），并为后续推荐提供历史反馈数据
-- 反馈动作：
--   ACCEPTED     — 用户接受了 AI 推荐的审批人
--   REJECTED     — 用户拒绝了 AI 推荐的审批人
--   CHOSEN_OTHER — 用户选择了非推荐列表中的其他人
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pmis_flow_ai_feedback (
    id                      VARCHAR(20)       PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id               VARCHAR(20)          NOT NULL DEFAULT '1',
    trace_id                VARCHAR(64)      NOT NULL,               -- 推荐调用追踪 ID（关联一次 recommendApprovers 调用）
    task_id                 VARCHAR(20),                             -- 任务 ID（可空，草稿态无任务）
    instance_id             VARCHAR(20),                             -- 流程实例 ID
    flow_code               VARCHAR(64),                             -- 流程编码
    node_code               VARCHAR(64),                             -- 节点编码
    recommended_user_id     VARCHAR(20)      NOT NULL,               -- AI 推荐的审批人 ID
    recommended_user_name   VARCHAR(128),                             -- AI 推荐的审批人姓名
    recommended_score       DECIMAL(5,4),                             -- 推荐得分 0.0000~1.0000
    recommended_rank        SMALLINT,                                 -- 推荐排名（1=第一推荐）
    action                  VARCHAR(16)      NOT NULL,               -- 反馈动作：ACCEPTED/REJECTED/CHOSEN_OTHER
    actual_user_id          VARCHAR(20),                             -- 实际选择的审批人 ID（CHOSEN_OTHER 时有值）
    actual_user_name        VARCHAR(128),                             -- 实际选择的审批人姓名
    feedback_source         VARCHAR(16)      NOT NULL DEFAULT 'USER_EXPLICIT', -- USER_EXPLICIT/SYSTEM_INFERRED
    remark                  VARCHAR(512),                             -- 备注
    provider_trace_id       VARCHAR(64),                             -- 链路追踪 ID
    created_by              VARCHAR(20)          NOT NULL DEFAULT 'SYSTEM',
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by              VARCHAR(20)          NOT NULL DEFAULT 'SYSTEM',
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                 SMALLINT        NOT NULL DEFAULT 0,
    -- 数据完整性约束
    CONSTRAINT ck_pfaf_action          CHECK (action IN ('ACCEPTED','REJECTED','CHOSEN_OTHER')),
    CONSTRAINT ck_pfaf_source          CHECK (feedback_source IN ('USER_EXPLICIT','SYSTEM_INFERRED')),
    CONSTRAINT ck_pfaf_deleted         CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE  pmis_flow_ai_feedback IS 'P3-3: AI 推荐审批人反馈记录表 — 记录用户对 AI 推荐的反馈，形成推荐-反馈闭环';

COMMENT ON COLUMN pmis_flow_ai_feedback.trace_id IS '推荐调用追踪 ID（关联一次 recommendApprovers 调用，所有推荐项共享）';

COMMENT ON COLUMN pmis_flow_ai_feedback.recommended_score IS '推荐得分 0.0000~1.0000（来自 Agent 返回）';

COMMENT ON COLUMN pmis_flow_ai_feedback.recommended_rank IS '推荐排名（1=第一推荐，来自 recommendApprovers 返回）';

COMMENT ON COLUMN pmis_flow_ai_feedback.action IS '反馈动作: ACCEPTED=接受 / REJECTED=拒绝 / CHOSEN_OTHER=选择其他人';

COMMENT ON COLUMN pmis_flow_ai_feedback.actual_user_id IS '实际选择的审批人 ID（action=CHOSEN_OTHER 时有值）';

COMMENT ON COLUMN pmis_flow_ai_feedback.feedback_source IS '反馈来源: USER_EXPLICIT=用户显式反馈 / SYSTEM_INFERRED=系统推断';

CREATE INDEX IF NOT EXISTS idx_pfaf_tenant_trace
    ON pmis_flow_ai_feedback (tenant_id, trace_id) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfaf_tenant_task_created
    ON pmis_flow_ai_feedback (tenant_id, task_id, created_at) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfaf_tenant_user_action
    ON pmis_flow_ai_feedback (tenant_id, recommended_user_id, action) WHERE deleted = 0;

-- ====================================================================
-- ============================ [072] 通知体系精简 + 影子表补齐 ============
-- ====================================================================
-- 通知基础设施（outbox/template/channel/preference/webhook/inbox/mention）
-- 已移除，通知能力由独立的消息通知引擎 ydsz-pmis-message 承载。
-- 工作流模块仅保留 FlowNotificationService 作为 Feign 适配器。
-- ----------------------------------------------------------------------------

-- 1. 补齐 pmis_flow_admin_role DDL（P1-6 影子表，DO 已存在但无 DDL）
CREATE TABLE IF NOT EXISTS pmis_flow_admin_role (
    id              VARCHAR(20)       PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id       VARCHAR(20)       NOT NULL DEFAULT '1',
    user_id         VARCHAR(20)       NOT NULL,
    role_code       VARCHAR(64)       NOT NULL,
    enabled         SMALLINT          NOT NULL DEFAULT 1,
    granted_by      VARCHAR(20),
    granted_at      TIMESTAMPTZ       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_at       TIMESTAMPTZ,
    created_by      VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT          NOT NULL DEFAULT 0,
    provider_trace_id VARCHAR(64),
    CONSTRAINT ck_pfar_role_code   CHECK (role_code IN ('FLOW_ADMIN','FLOW_DESIGNER','FLOW_AUDITOR')),
    CONSTRAINT ck_pfar_enabled     CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_pfar_deleted     CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_flow_admin_role IS 'P1-6: 流程管理员角色映射表 — 用户与流程管理员角色的映射关系';

COMMENT ON COLUMN pmis_flow_admin_role.role_code IS '角色编码: FLOW_ADMIN/FLOW_DESIGNER/FLOW_AUDITOR';

COMMENT ON COLUMN pmis_flow_admin_role.enabled IS '是否启用 1=是 0=否';

COMMENT ON COLUMN pmis_flow_admin_role.granted_by IS '授权人 ID';

COMMENT ON COLUMN pmis_flow_admin_role.granted_at IS '授权时间';

COMMENT ON COLUMN pmis_flow_admin_role.expire_at IS '过期时间（NULL=永不过期）';

CREATE UNIQUE INDEX IF NOT EXISTS uk_pfar_tenant_user_role
    ON pmis_flow_admin_role (tenant_id, user_id, role_code)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfar_tenant_role
    ON pmis_flow_admin_role (tenant_id, role_code, enabled)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pfar_trace
    ON pmis_flow_admin_role (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- 2. 移除通知体系表（幂等 DROP，已删除的表不影响）
DROP TABLE IF EXISTS pmis_flow_notify_outbox CASCADE;
DROP TABLE IF EXISTS pmis_flow_notify_template CASCADE;
DROP TABLE IF EXISTS pmis_flow_notify_channel CASCADE;
DROP TABLE IF EXISTS pmis_flow_notify_preference CASCADE;
DROP TABLE IF EXISTS pmis_flow_webhook_subscription CASCADE;
DROP TABLE IF EXISTS pmis_flow_inbox CASCADE;
DROP TABLE IF EXISTS pmis_flow_mention CASCADE;

-- 3. 合并 pmis_flow_task_comment → pmis_flow_comment（统一评论表）
--    pmis_flow_comment 增加 type 列（COMMENT/QUESTION/REPLY），吸收 task_comment 功能
ALTER TABLE pmis_flow_comment ADD COLUMN IF NOT EXISTS type VARCHAR(16) NOT NULL DEFAULT 'COMMENT';

COMMENT ON COLUMN pmis_flow_comment.type IS '评论类型: COMMENT / QUESTION / REPLY（默认 COMMENT）';

DROP TABLE IF EXISTS pmis_flow_task_comment CASCADE;

-- 4. 合并 pmis_flow_delegate_log → pmis_flow_audit_log（统一审计日志）
--    委派代理操作日志不再独立建表，写入 audit_log 时 businessType=DELEGATE_PROXY 标识
DROP TABLE IF EXISTS pmis_flow_delegate_log CASCADE;

-- 5. 移除 pmis_flow_delegate_message（委派沟通留言合并到 pmis_flow_comment）
DROP TABLE IF EXISTS pmis_flow_delegate_message CASCADE;

-- 6. 移除 pmis_flow_his_variable（归档变量以 JSON blob 存储在 his_instance.variable 中）
DROP TABLE IF EXISTS pmis_flow_his_variable CASCADE;

