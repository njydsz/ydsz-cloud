-- ----------------------------------------------------------------------------
-- 模块名   : ydsz-workflow（工作流模块）
-- 说明     : 基于 ydsz-workflow-infra 实体类整理的完整建表脚本
--            （流程分类/定义/模板、节点/跳转、实例、待办任务、定时器、
--              事件订阅、历史归档、评论/常用语、抄送/抄送规则、附件、
--              委派授权、管理员角色、自动触发、审计日志）
-- 日期     : 2026-08-25
-- @author  : ydsz-team
-- ----------------------------------------------------------------------------
-- 说明：
--   1. 表名统一前缀 ydsz_flow_。
--   2. 继承 MpBaseEntity<String> 的实体含全量公共列
--      （tenant_id / status / deleted / revision / created_by / created_at /
--       updated_by / updated_at）。
--   3. 历史归档/审计类实体（FlowHisTask / FlowHisInstance / FlowAuditLog）
--      同样继承 MpBaseEntity<String>，含全量公共列。
-- ----------------------------------------------------------------------------

-- ============================================================================
-- 流程定义 / 模板（模板层）
-- ============================================================================

-- 流程分类表（按业务线分组的树形分类，流程定义引用）
CREATE TABLE IF NOT EXISTS ydsz_flow_category (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    category_code   VARCHAR(64)     NOT NULL COMMENT '分类编码（唯一，业务语义，建议 snake_case）',
    category_name   VARCHAR(128)    NOT NULL COMMENT '分类名称（前端展示）',
    parent_id       VARCHAR(32)     DEFAULT NULL COMMENT '父分类 ID（支持多级树形结构，顶级为 NULL）',
    sort_num        INT             NOT NULL DEFAULT 0 COMMENT '排序号（越小越靠前）',
    icon            VARCHAR(128)    DEFAULT NULL COMMENT '图标（前端展示用，如 Element Plus icon 名称）',
    remark          VARCHAR(512)    DEFAULT NULL COMMENT '备注（说明分类的业务用途）',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT uk_category_code UNIQUE (category_code, tenant_id),
    INDEX idx_parent_id (parent_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程分类表（树形结构，流程定义分组归类）';

-- 流程定义表（工作流模板层，支持灰度发布与协同编辑锁定）
CREATE TABLE IF NOT EXISTS ydsz_flow_definition (
    id                  VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id           VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    flow_code           VARCHAR(64)     NOT NULL COMMENT '流程编码（业务语义，如 project_initiation / contract_change）',
    flow_name           VARCHAR(128)    NOT NULL COMMENT '流程名称（前端展示）',
    category            VARCHAR(64)     DEFAULT NULL COMMENT '流程类别（用于分类筛选，如「项目类」「合同类」「人事类」）',
    flow_version        VARCHAR(32)     NOT NULL COMMENT '流程版本号（如 v1 / v2，同一 flowCode 下不同版本独立发布）',
    model_value         VARCHAR(32)     DEFAULT NULL COMMENT '设计器模型（CLASSICS=经典横向流转图，MIMIC=纵向审批面板）',
    form_custom         VARCHAR(8)      DEFAULT NULL COMMENT '审批表单是否自定义（Y=自定义表单，N=系统内置表单）',
    form_path           VARCHAR(1024)   DEFAULT NULL COMMENT '审批表单路径（formCustom=Y 时为 Vue 组件路径，否则为表单定义 ID）',
    activity_status     INT             NOT NULL DEFAULT 1 COMMENT '激活状态（0=挂起不可发起新实例，1=激活正常接收新实例）',
    is_publish          INT             NOT NULL DEFAULT 0 COMMENT '发布状态（0=未发布草稿，1=已发布可发起，9=失效已废弃）',
    listener_type       VARCHAR(32)     DEFAULT NULL COMMENT '监听器类型（NONE=无，GLOBAL=全局，FLOW=流程级）',
    listener_path       VARCHAR(128)    DEFAULT NULL COMMENT '监听器 Spring Bean 路径（如 projectFlowListener）',
    ext                 JSON            DEFAULT NULL COMMENT '扩展字段 JSON（业务侧自定义元数据：超时配置/抄送规则/审批人默认值等）',
    description         VARCHAR(512)    DEFAULT NULL COMMENT '流程描述（说明流程的业务用途与适用场景）',
    provider_trace_id   VARCHAR(64)     DEFAULT NULL COMMENT '链路追踪 ID（跨系统全链路追踪，与 ydsz_provider_trace_id 协议对齐）',
    canary_percent      INT             NOT NULL DEFAULT 0 COMMENT '灰度比例 0-100（0=全量稳定版，100=全量灰度版，1-99 按策略切流）',
    canary_status       VARCHAR(32)     DEFAULT NULL COMMENT '灰度状态（NONE=未启用，CANARYING=灰度中，PROMOTED=已全量，ROLLED_BACK=已回滚）',
    canary_strategy     VARCHAR(32)     DEFAULT NULL COMMENT '灰度切流策略（USER_HASH=按发起人取模，RANDOM=随机，WHITELIST=白名单）',
    canary_rollout_log  JSON            DEFAULT NULL COMMENT '灰度发布历史 JSON 数组（[{operatorId,operatorName,fromPercent,toPercent,operateAt,note}]）',
    locked_by           VARCHAR(32)     DEFAULT NULL COMMENT '当前持锁人 ID（设计器协同编辑锁定，NULL=未锁定）',
    locked_at           DATETIME        DEFAULT NULL COMMENT '加锁时间（超过 30 分钟可强制抢占）',
    status              VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by          VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by          VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT uk_flow_code_version UNIQUE (flow_code, flow_version, tenant_id),
    INDEX idx_category (category),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程定义表（流程模板元数据）';

-- 流程模板表（模板市场预置模板，含 BPMN 2.0 XML，支持继承与版本化）
CREATE TABLE IF NOT EXISTS ydsz_flow_template (
    id                  VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id           VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    template_code       VARCHAR(64)     NOT NULL COMMENT '模板编码（唯一标识，如 hr_leave_approval）',
    template_name       VARCHAR(128)    NOT NULL COMMENT '模板名称（前端展示）',
    category            VARCHAR(32)     DEFAULT NULL COMMENT '分类（HR / FINANCE / ADMIN / PROJECT / GENERAL）',
    description         VARCHAR(512)    DEFAULT NULL COMMENT '模板描述（说明适用场景、必填字段、注意事项）',
    icon                VARCHAR(128)    DEFAULT NULL COMMENT '图标路径（前端展示用）',
    bpmn_xml            LONGTEXT        COMMENT 'BPMN 2.0 XML 流程定义（<bpmn:definitions>...</bpmn:definitions>）',
    form_path           VARCHAR(1024)   DEFAULT NULL COMMENT '默认表单路径（导入后默认关联的审批表单）',
    use_count           INT             NOT NULL DEFAULT 0 COMMENT '使用次数（被导入到流程定义的累计计数，用于热门度排序）',
    sort_order          INT             NOT NULL DEFAULT 0 COMMENT '排序权重（越大越靠前，模板市场首页展示用）',
    parent_template_id  VARCHAR(32)     DEFAULT NULL COMMENT '父模板 ID（跨模板继承关系，STANDALONE 时为 NULL）',
    version             INT             NOT NULL DEFAULT 1 COMMENT '模板版本号（从 1 开始单调递增，同一 templateCode 下唯一）',
    version_label       VARCHAR(32)     DEFAULT NULL COMMENT '版本标签（如 1.0.0 / 1.0.0-rc1，可选可读标识）',
    inherit_type        VARCHAR(32)     DEFAULT NULL COMMENT '继承类型（STANDALONE=独立，CLONE=克隆，INHERIT=继承）',
    is_latest           INT             NOT NULL DEFAULT 0 COMMENT '是否当前 templateCode 下最新版本（0=否，1=是）',
    status              VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by          VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by          VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT uk_template_code_version UNIQUE (template_code, version, tenant_id),
    INDEX idx_category (category),
    INDEX idx_parent_template_id (parent_template_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程模板表（模板市场预置模板，BPMN 2.0 XML）';

-- 流程节点表（流程定义中的节点：开始/审批/网关/结束/子流程/抄送）
CREATE TABLE IF NOT EXISTS ydsz_flow_node (
    id                  VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id           VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    definition_id       VARCHAR(32)     NOT NULL COMMENT '所属流程定义 ID（关联 ydsz_flow_definition.id）',
    flow_code           VARCHAR(64)     NOT NULL COMMENT '流程编码（冗余字段，避免 JOIN 流程定义）',
    node_type           INT             NOT NULL COMMENT '节点类型（0=开始，1=审批，2=网关，3=结束，4=子流程，5=抄送）',
    node_code           VARCHAR(64)     NOT NULL COMMENT '节点编码（流程内唯一，用于 SpEL 引用与跳转）',
    node_name           VARCHAR(128)    NOT NULL COMMENT '节点名称（设计器展示与审批页标题）',
    permission_flag     VARCHAR(512)    DEFAULT NULL COMMENT '办理人权限标识（role:/dept:/user:/post:/initiator/initiatorLeader/${spel}，逗号分隔）',
    skip_any_node       VARCHAR(512)    DEFAULT NULL COMMENT '任意跳转目标节点编码集合（逗号分隔，空=不允许任意跳转）',
    coordinate          JSON            DEFAULT NULL COMMENT '设计器坐标 JSON（{"x":100,"y":200,"width":120,"height":60}，bpmn-js 生成）',
    skip_list           JSON            DEFAULT NULL COMMENT '节点跳转路由集合 JSON（[{toNodeCode,condition,priority},...]，按优先级匹配）',
    ext                 JSON            DEFAULT NULL COMMENT '扩展字段 JSON（priority/emptyStrategy/collection/votePassRate/userWeights/autoDedup/freeJump）',
    form_fields_config  JSON            DEFAULT NULL COMMENT '表单字段权限配置 JSON（{"fieldKey":"EDIT|READONLY|HIDDEN",...}）',
    sla_config          JSON            DEFAULT NULL COMMENT 'SLA 超时配置 JSON（{"timeoutMinutes":120,"action":"REMIND|ESCALATE|AUTO_PASS|AUTO_REJECT",...}）',
    provider_trace_id   VARCHAR(64)     DEFAULT NULL COMMENT '链路追踪 ID（关联 MDC traceId，用于跨服务追踪）',
    status              VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by          VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by          VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT uk_definition_node_code UNIQUE (definition_id, node_code),
    INDEX idx_flow_code (flow_code),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程节点表（流程定义结构最小单元）';

-- 节点跳转关联表（流程图有向边，对应 BPMN sequenceFlow）
CREATE TABLE IF NOT EXISTS ydsz_flow_skip (
    id                  VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id           VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    definition_id       VARCHAR(32)     NOT NULL COMMENT '所属流程定义 ID',
    flow_code           VARCHAR(64)     NOT NULL COMMENT '流程编码（冗余字段）',
    skip_name           VARCHAR(128)    DEFAULT NULL COMMENT '跳转名称（线上标签，如「同意」「金额 > 1万」）',
    skip_type           VARCHAR(32)     NOT NULL COMMENT '跳转类型（FlowSkipType 枚举名，如 PASS/REJECT）',
    coordinate          JSON            DEFAULT NULL COMMENT '设计器坐标 JSON（当前节点端点）',
    skip_condition      VARCHAR(512)    DEFAULT NULL COMMENT '跳转条件表达式（SpEL 或 ${var} 语法）',
    next_node_code      VARCHAR(64)     NOT NULL COMMENT '下一节点编码',
    source_node_code    VARCHAR(64)     DEFAULT NULL COMMENT '源节点编码（独立列，便于 REJECT 回退场景索引与联查）',
    next_node_type      INT             DEFAULT NULL COMMENT '下一节点类型（FlowNodeType.code）',
    coordinate_next     JSON            DEFAULT NULL COMMENT '下一节点坐标 JSON（设计器渲染终点）',
    skip_list           JSON            DEFAULT NULL COMMENT '跳转路由集合 JSON',
    ext                 JSON            DEFAULT NULL COMMENT '扩展字段 JSON（存储 sourceRef / sequenceFlowId 等 BPMN 派生信息）',
    provider_trace_id   VARCHAR(64)     DEFAULT NULL COMMENT '链路追踪 ID',
    status              VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by          VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by          VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    INDEX idx_definition_id (definition_id),
    INDEX idx_flow_code (flow_code),
    INDEX idx_source_node_code (source_node_code),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节点跳转关联表（流程图有向边）';

-- 流程自动触发规则表（源流程终态后按条件自动启动目标流程）
CREATE TABLE IF NOT EXISTS ydsz_flow_auto_trigger (
    id                      VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id               VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    source_flow_code        VARCHAR(64)     NOT NULL COMMENT '源流程编码（触发方）',
    target_flow_code        VARCHAR(64)     NOT NULL COMMENT '目标流程编码（被触发方）',
    condition_expression    VARCHAR(512)    DEFAULT NULL COMMENT '条件表达式（Aviator 语法，为空则无条件触发）',
    description             VARCHAR(512)    DEFAULT NULL COMMENT '规则描述（说明触发场景与业务背景）',
    enabled                 INT             NOT NULL DEFAULT 1 COMMENT '是否启用（0=禁用，1=启用）',
    sort_order              INT             NOT NULL DEFAULT 0 COMMENT '排序权重（升序执行）',
    status                  VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted                 TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision                INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by              VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by              VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    INDEX idx_source_flow_code (source_flow_code),
    INDEX idx_target_flow_code (target_flow_code),
    INDEX idx_enabled (enabled),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程自动触发规则表（流程触发流程的自动化配置）';

-- ============================================================================
-- 流程实例 / 运行态任务
-- ============================================================================

-- 流程实例表（一次完整流程审批的运行时上下文）
CREATE TABLE IF NOT EXISTS ydsz_flow_instance (
    id                  VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id           VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    flow_code           VARCHAR(64)     NOT NULL COMMENT '流程编码（业务侧使用，如 project_initiation）',
    flow_name           VARCHAR(128)    NOT NULL COMMENT '流程名称（冗余，避免 JOIN 流程定义）',
    definition_id       VARCHAR(32)     NOT NULL COMMENT '流程定义 ID（关联 ydsz_flow_definition.id）',
    flow_version        VARCHAR(32)     NOT NULL COMMENT '流程版本（关联 ydsz_flow_definition.flow_version）',
    business_type       VARCHAR(64)     NOT NULL COMMENT '业务类型（如 PROJECT / CONTRACT / LEAVE）',
    business_id         VARCHAR(64)     NOT NULL COMMENT '业务单据 ID（业务侧主键）',
    business_no         VARCHAR(64)     DEFAULT NULL COMMENT '业务单据编号（业务侧编号，可读）',
    title               VARCHAR(128)    DEFAULT NULL COMMENT '流程标题（展示用，默认为「{业务类型}-{业务编号}」）',
    initiator_id        VARCHAR(32)     NOT NULL COMMENT '发起人 ID（关联 ydsz_user_account.id）',
    initiator_name      VARCHAR(64)     DEFAULT NULL COMMENT '发起人姓名（冗余）',
    current_node_code   VARCHAR(64)     DEFAULT NULL COMMENT '当前节点编码（流程图高亮 + 进度提示）',
    current_node_name   VARCHAR(128)    DEFAULT NULL COMMENT '当前节点名称（冗余）',
    variable            JSON            DEFAULT NULL COMMENT '流程变量 JSON（动态参数）',
    flow_status         VARCHAR(32)     NOT NULL COMMENT '实例状态（FlowInstanceStatus 枚举名，如 RUNNING/APPROVED/REJECTED）',
    activity_status     INT             NOT NULL DEFAULT 1 COMMENT '激活状态（0=挂起，1=激活，与 flowStatus 解耦）',
    start_at            DATETIME        DEFAULT NULL COMMENT '启动时间',
    end_at              DATETIME        DEFAULT NULL COMMENT '结束时间（终态实例有值，活跃实例为 NULL）',
    duration_ms         BIGINT          DEFAULT NULL COMMENT '流程耗时（毫秒，endAt - startAt，结束时由引擎填充）',
    parent_instance_id  VARCHAR(32)     DEFAULT NULL COMMENT '父流程实例 ID（子流程场景，可空）',
    parent_node_code    VARCHAR(64)     DEFAULT NULL COMMENT '父流程中触发子流程的节点编码（可空）',
    provider_trace_id   VARCHAR(64)     DEFAULT NULL COMMENT '链路追踪 ID（关联 MDC traceId，用于跨服务追踪）',
    due_at              DATETIME        DEFAULT NULL COMMENT '子流程超时时间（超时自动终止子流程，可空）',
    reject_reason       VARCHAR(512)    DEFAULT NULL COMMENT '退回原因（最近一次 REJECT 操作的备注，重审时清空）',
    status              VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by          VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by          VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT uk_business_type_id UNIQUE (business_type, business_id),
    INDEX idx_initiator_id (initiator_id),
    INDEX idx_flow_status (flow_status),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例表（一次完整流程审批的运行时上下文）';

-- 待办任务运行表（我的待办核心查询表，任务完成后归档至 ydsz_flow_his_task）
CREATE TABLE IF NOT EXISTS ydsz_flow_run_task (
    id                  VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id           VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    instance_id         VARCHAR(32)     NOT NULL COMMENT '流程实例 ID（关联 ydsz_flow_instance.id）',
    flow_code           VARCHAR(64)     NOT NULL COMMENT '流程编码（冗余字段）',
    definition_id       VARCHAR(32)     NOT NULL COMMENT '流程定义 ID',
    node_code           VARCHAR(64)     NOT NULL COMMENT '节点编码',
    node_name           VARCHAR(128)    DEFAULT NULL COMMENT '节点名称（冗余）',
    node_type           INT             DEFAULT NULL COMMENT '节点类型（FlowNodeType.code）',
    business_type       VARCHAR(64)     DEFAULT NULL COMMENT '业务类型',
    business_id         VARCHAR(64)     DEFAULT NULL COMMENT '业务单据 ID',
    business_no         VARCHAR(64)     DEFAULT NULL COMMENT '业务单据编号',
    flow_name           VARCHAR(128)    DEFAULT NULL COMMENT '流程名称（冗余）',
    title               VARCHAR(128)    DEFAULT NULL COMMENT '任务标题（默认为「{流程名}-{节点名}-{业务编号}」）',
    assignor_id         VARCHAR(32)     DEFAULT NULL COMMENT '委托人 ID（委托操作产生，被委托人完成任务后回填）',
    assignor_name       VARCHAR(64)     DEFAULT NULL COMMENT '委托人姓名（冗余）',
    assignee_type       VARCHAR(32)     DEFAULT NULL COMMENT '办理人类型（FlowAssigneeType 枚举名：USER/ROLE/DEPT/POST）',
    assignee_id         VARCHAR(64)     NOT NULL COMMENT '办理人 ID（按 type 解析，USER 传 userId，ROLE 传 roleCode）',
    assignee_name       VARCHAR(64)     DEFAULT NULL COMMENT '办理人姓名（冗余）',
    permission_flag     VARCHAR(512)    DEFAULT NULL COMMENT '办理人权限标识（原始 SpEL 表达式，存档便于回溯）',
    perform_type        VARCHAR(32)     DEFAULT NULL COMMENT '会签类型（FlowPerformType 枚举名：OR=或签，PARALLEL=并行会签，WEIGHTED=票签）',
    approve_count       INT             DEFAULT NULL COMMENT '会签所需通过人数（PARALLEL 模式：会签总人数）',
    approve_finished    INT             DEFAULT NULL COMMENT '会签当前已通过人数',
    vote_pass_rate      DECIMAL(20,6)   DEFAULT NULL COMMENT '通过率阈值（0~1，默认 0.5 表示过半数）',
    user_weight         INT             DEFAULT NULL COMMENT '当前办理人的权重值（票签模式，未配置默认 1）',
    approve_weight      INT             DEFAULT NULL COMMENT '累计已通过权重（票签模式：每次通过时累加 userWeight）',
    total_weight        INT             DEFAULT NULL COMMENT '节点总权重（票签模式：所有办理人权重之和）',
    task_status         VARCHAR(32)     NOT NULL COMMENT '任务状态（FlowTaskStatus 枚举名）',
    comment             VARCHAR(512)    DEFAULT NULL COMMENT '审批意见',
    claim_at            DATETIME        DEFAULT NULL COMMENT '签收时间（多人会签时记录每个办理人的签收时间）',
    finish_at           DATETIME        DEFAULT NULL COMMENT '完成时间',
    effective_time      DATETIME        DEFAULT NULL COMMENT '生效时间（P2-1 穿越时空/补录审批，NULL=即时生效）',
    duration_ms         BIGINT          DEFAULT NULL COMMENT '耗时（毫秒，finishAt - claimAt）',
    due_at              DATETIME        DEFAULT NULL COMMENT '截止时间（SLA 阈值，由 slaConfig.timeoutMinutes 计算）',
    priority            INT             NOT NULL DEFAULT 50 COMMENT '任务优先级（1-100，默认 50，待办按 priority DESC, created_at ASC 排序）',
    urge_count          INT             NOT NULL DEFAULT 0 COMMENT '已发送的 SLA 催办次数（超过配置上限后停止）',
    last_urged_at       DATETIME        DEFAULT NULL COMMENT '最近一次催办时间',
    sla_action          VARCHAR(32)     DEFAULT NULL COMMENT '最终触发的 SLA 动作（REMIND/ESCALATE/AUTO_PASS/AUTO_REJECT）',
    sla_escalated       INT             NOT NULL DEFAULT 0 COMMENT '是否已升级（0=否，1=是，避免重复升级）',
    iter_var            VARCHAR(128)    DEFAULT NULL COMMENT 'FOREACH 节点当前迭代元素值（如 userId/deptId，非循环节点为 NULL）',
    provider_trace_id   VARCHAR(64)     DEFAULT NULL COMMENT '链路追踪 ID',
    status              VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by          VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by          VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT uk_instance_node_assignee UNIQUE (instance_id, node_code, assignee_id, iter_var),
    INDEX idx_assignee_id (assignee_id),
    INDEX idx_business (business_type, business_id),
    INDEX idx_due_at (due_at),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待办任务运行表（我的待办核心查询表）';

-- 流程任务-办理人关系表（会签多办理人、加签/减签多对多关系）
CREATE TABLE IF NOT EXISTS ydsz_flow_user (
    id                  VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id           VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    task_id             VARCHAR(32)     NOT NULL COMMENT '任务 ID（关联 ydsz_flow_run_task.id）',
    instance_id         VARCHAR(32)     NOT NULL COMMENT '流程实例 ID（冗余便于查询）',
    node_code           VARCHAR(64)     NOT NULL COMMENT '节点编码',
    user_type           VARCHAR(32)     DEFAULT NULL COMMENT '用户类型（USER=具体用户，ROLE=角色展开，DEPT=部门展开）',
    user_id             VARCHAR(64)     NOT NULL COMMENT '用户/角色/部门 ID',
    user_name           VARCHAR(64)     DEFAULT NULL COMMENT '用户姓名（冗余）',
    processed           INT             NOT NULL DEFAULT 0 COMMENT '是否已处理（0=否，1=是）',
    process_at          DATETIME        DEFAULT NULL COMMENT '处理时间',
    comment             VARCHAR(512)    DEFAULT NULL COMMENT '审批意见',
    weight              INT             NOT NULL DEFAULT 1 COMMENT '办理人权重（默认 1，可配置 2/3 等，用于加权会签）',
    sign_type           VARCHAR(32)     NOT NULL DEFAULT 'ORIGINAL' COMMENT '加签类型（ORIGINAL=原始审批人，BEFORE=前加签，AFTER=后加签，PARALLEL=并加签，ADD=追加处理人）',
    provider_trace_id   VARCHAR(64)     DEFAULT NULL COMMENT '链路追踪 ID',
    status              VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by          VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by          VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT uk_task_user (task_id, user_id, sign_type),
    INDEX idx_instance_id (instance_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程任务-办理人关系表（会签/加签多对多关系）';

-- 工作流定时器表（中间定时器 / 边界定时器调度，对标 BPMN TimerEvent）
CREATE TABLE IF NOT EXISTS ydsz_flow_timer (
    id                  VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id           VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    instance_id         VARCHAR(32)     NOT NULL COMMENT '流程实例 ID',
    definition_id       VARCHAR(32)     NOT NULL COMMENT '流程定义 ID',
    flow_code           VARCHAR(64)     DEFAULT NULL COMMENT '流程编码（冗余）',
    node_code           VARCHAR(64)     NOT NULL COMMENT '节点编码',
    node_name           VARCHAR(128)    DEFAULT NULL COMMENT '节点名称（冗余）',
    timer_type          VARCHAR(32)     NOT NULL COMMENT '定时器类型（INTERMEDIATE=中间定时器，BOUNDARY=边界定时器）',
    boundary_task_id    VARCHAR(32)     DEFAULT NULL COMMENT '边界定时器关联的 userTask ID（INTERMEDIATE 为 NULL）',
    fire_at             DATETIME        NOT NULL COMMENT '到点时间（cronjob 按 fire_at <= now() AND timer_status = PENDING 扫描）',
    cycle               VARCHAR(64)     DEFAULT NULL COMMENT 'CRON 表达式（循环定时器，可空）',
    timer_status        VARCHAR(32)     NOT NULL DEFAULT 'PENDING' COMMENT '状态（PENDING=待触发，FIRED=已触发，CANCELLED=已取消）',
    fired_at            DATETIME        DEFAULT NULL COMMENT '实际触发时间',
    cancel_reason       VARCHAR(512)    DEFAULT NULL COMMENT '取消原因（userTask 完成时关闭）',
    provider_trace_id   VARCHAR(64)     DEFAULT NULL COMMENT '链路追踪 ID',
    status              VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by          VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by          VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    INDEX idx_fire_at (fire_at),
    INDEX idx_instance_id (instance_id),
    INDEX idx_timer_status (timer_status),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流定时器表（中间/边界定时器调度）';

-- 工作流事件订阅表（消息/错误/信号事件运行时等待，对标 BPMN CatchEvent）
CREATE TABLE IF NOT EXISTS ydsz_flow_event_subscription (
    id                      VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id               VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    instance_id             VARCHAR(32)     NOT NULL COMMENT '流程实例 ID',
    definition_id           VARCHAR(32)     NOT NULL COMMENT '流程定义 ID',
    flow_code               VARCHAR(64)     DEFAULT NULL COMMENT '流程编码（冗余）',
    node_code               VARCHAR(64)     NOT NULL COMMENT '节点编码（事件捕获节点）',
    node_name               VARCHAR(128)    DEFAULT NULL COMMENT '节点名称（冗余）',
    event_type              VARCHAR(32)     NOT NULL COMMENT '事件类型（MESSAGE=消息，ERROR=错误，SIGNAL=信号）',
    event_ref               VARCHAR(64)     NOT NULL COMMENT '事件引用标识（messageRef / errorRef / signalRef）',
    correlation_key         VARCHAR(64)     DEFAULT NULL COMMENT '消息关联键（业务级匹配，SIGNAL 广播匹配时可空）',
    boundary_task_id        VARCHAR(32)     DEFAULT NULL COMMENT '边界事件关联的 userTask ID（中间事件为 NULL）',
    subscription_status     VARCHAR(32)     NOT NULL DEFAULT 'WAITING' COMMENT '订阅状态（WAITING=等待中，COMPLETED=已完成，CANCELLED=已取消）',
    payload                 JSON            DEFAULT NULL COMMENT '触发时携带的业务数据 JSON',
    triggered_at            DATETIME        DEFAULT NULL COMMENT '实际触发时间',
    trigger_source          VARCHAR(32)     DEFAULT NULL COMMENT '触发来源（API=外部系统调用，SERVICE_TASK=服务任务抛出，BOUNDARY=边界事件超时）',
    cancel_reason           VARCHAR(512)    DEFAULT NULL COMMENT '取消原因',
    provider_trace_id       VARCHAR(64)     DEFAULT NULL COMMENT '链路追踪 ID',
    status                  VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted                 TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision                INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by              VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by              VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    INDEX idx_instance_id (instance_id),
    INDEX idx_event_ref (event_ref),
    INDEX idx_subscription_status (subscription_status),
    INDEX idx_correlation_key (correlation_key),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流事件订阅表（消息/错误/信号事件运行时等待）';

-- ============================================================================
-- 历史归档（继承 MpBaseEntity<String>，含完整公共列）
-- ============================================================================

-- 历史任务表（已完成任务归档，按月分区，审批历史查询表）
CREATE TABLE IF NOT EXISTS ydsz_flow_his_task (
    id                  VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id           VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    instance_id         VARCHAR(32)     NOT NULL COMMENT '流程实例 ID（归档时从 ydsz_flow_run_task.instance_id 复制）',
    task_id             VARCHAR(32)     NOT NULL COMMENT '原始任务 ID（指向源 ydsz_flow_run_task.id，归档后源表清理前可关联）',
    flow_code           VARCHAR(64)     NOT NULL COMMENT '流程编码',
    definition_id       VARCHAR(32)     NOT NULL COMMENT '流程定义 ID',
    node_code           VARCHAR(64)     NOT NULL COMMENT '节点编码',
    node_name           VARCHAR(128)    DEFAULT NULL COMMENT '节点名称（冗余）',
    node_type           INT             DEFAULT NULL COMMENT '节点类型（FlowNodeType.code）',
    business_type       VARCHAR(64)     DEFAULT NULL COMMENT '业务类型',
    business_id         VARCHAR(64)     DEFAULT NULL COMMENT '业务单据 ID',
    business_no         VARCHAR(64)     DEFAULT NULL COMMENT '业务单据编号',
    flow_name           VARCHAR(128)    DEFAULT NULL COMMENT '流程名称（冗余）',
    title               VARCHAR(128)    DEFAULT NULL COMMENT '任务标题',
    assignee_type       VARCHAR(32)     DEFAULT NULL COMMENT '办理人类型（FlowAssigneeType 枚举名）',
    assignee_id         VARCHAR(64)     DEFAULT NULL COMMENT '办理人 ID',
    assignee_name       VARCHAR(64)     DEFAULT NULL COMMENT '办理人姓名（冗余）',
    perform_type        VARCHAR(32)     DEFAULT NULL COMMENT '会签类型（FlowPerformType 枚举名）',
    approve_count       INT             DEFAULT NULL COMMENT '会签所需通过人数',
    approve_finished    INT             DEFAULT NULL COMMENT '会签当前已通过人数',
    vote_pass_rate      DECIMAL(20,6)   DEFAULT NULL COMMENT '通过率阈值（0~1，从源 task 复制）',
    task_status         VARCHAR(32)     NOT NULL COMMENT '任务状态（终态：APPROVED/REJECTED/CANCELED/DELEGATED）',
    comment             VARCHAR(512)    DEFAULT NULL COMMENT '审批意见（终态时填写）',
    claim_at            DATETIME        DEFAULT NULL COMMENT '签收时间',
    finish_at           DATETIME        DEFAULT NULL COMMENT '完成时间（终态时刻）',
    effective_time      DATETIME        DEFAULT NULL COMMENT '生效时间（P2-1 穿越时空/补录审批，从源 task 复制，NULL=即时生效）',
    duration_ms         BIGINT          DEFAULT NULL COMMENT '耗时（毫秒）',
    provider_trace_id   VARCHAR(64)     DEFAULT NULL COMMENT '链路追踪 ID（保留原始 trace 便于历史回溯）',
    iter_var            VARCHAR(128)    DEFAULT NULL COMMENT 'FOREACH 迭代元素值（从源 task 复制，非循环节点为 NULL）',
    status              VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by          VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by          VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    INDEX idx_instance_id (instance_id),
    INDEX idx_assignee_id (assignee_id),
    INDEX idx_business (business_type, business_id),
    INDEX idx_finish_at (finish_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史任务表（已完成任务归档，按月分区）';

-- 历史流程实例表（终态实例冷数据归档，按月分区）
CREATE TABLE IF NOT EXISTS ydsz_flow_his_instance (
    id                  VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id           VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    flow_code           VARCHAR(64)     NOT NULL COMMENT '流程编码',
    flow_name           VARCHAR(128)    DEFAULT NULL COMMENT '流程名称（冗余）',
    definition_id       VARCHAR(32)     NOT NULL COMMENT '流程定义 ID',
    flow_version        VARCHAR(32)     DEFAULT NULL COMMENT '流程版本',
    business_type       VARCHAR(64)     NOT NULL COMMENT '业务类型',
    business_id         VARCHAR(64)     NOT NULL COMMENT '业务单据 ID',
    business_no         VARCHAR(64)     DEFAULT NULL COMMENT '业务单据编号',
    title               VARCHAR(128)    DEFAULT NULL COMMENT '流程标题',
    initiator_id        VARCHAR(32)     NOT NULL COMMENT '发起人 ID',
    initiator_name      VARCHAR(64)     DEFAULT NULL COMMENT '发起人姓名（冗余）',
    current_node_code   VARCHAR(64)     DEFAULT NULL COMMENT '当前节点编码（终态时为结束节点编码）',
    current_node_name   VARCHAR(128)    DEFAULT NULL COMMENT '当前节点名称（冗余）',
    variable            JSON            DEFAULT NULL COMMENT '流程变量 JSON 快照',
    flow_status         VARCHAR(32)     NOT NULL COMMENT '终态状态（APPROVED / REJECTED / CANCELED）',
    activity_status     INT             DEFAULT NULL COMMENT '激活状态（终态时固定为 0）',
    start_at            DATETIME        DEFAULT NULL COMMENT '启动时间',
    end_at              DATETIME        DEFAULT NULL COMMENT '结束时间',
    duration_ms         BIGINT          DEFAULT NULL COMMENT '流程耗时（毫秒）',
    archived_at         DATETIME        DEFAULT NULL COMMENT '归档时间（由调度器在迁移时填充）',
    provider_trace_id   VARCHAR(64)     DEFAULT NULL COMMENT '链路追踪 ID（保留原始 trace 便于历史回溯）',
    status              VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by          VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by          VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT uk_business_type_id UNIQUE (business_type, business_id),
    INDEX idx_archived_at (archived_at),
    INDEX idx_end_at (end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史流程实例表（终态实例冷数据归档，按月分区）';

-- ============================================================================
-- 评论 / 常用语 / 抄送 / 附件
-- ============================================================================

-- 流程评论表（审批人之间的沟通讨论，支持多级回复，可编辑删除）
CREATE TABLE IF NOT EXISTS ydsz_flow_comment (
    id                  VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id           VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    instance_id         VARCHAR(32)     NOT NULL COMMENT '关联流程实例 ID',
    task_id             VARCHAR(32)     DEFAULT NULL COMMENT '关联任务 ID（实例级评论可为空）',
    node_code           VARCHAR(64)     DEFAULT NULL COMMENT '关联节点编码（任务级评论时记录所在节点）',
    user_id             VARCHAR(32)     NOT NULL COMMENT '评论人 ID',
    user_name           VARCHAR(64)     DEFAULT NULL COMMENT '评论人姓名（冗余）',
    content             VARCHAR(2000)   NOT NULL COMMENT '评论内容（TEXT 类型，最大长度 2000）',
    type                VARCHAR(32)     NOT NULL DEFAULT 'COMMENT' COMMENT '评论类型（COMMENT=普通评论，QUESTION=提问，REPLY=回复）',
    parent_comment_id   VARCHAR(32)     DEFAULT NULL COMMENT '父评论 ID（一级评论为 NULL）',
    reply_to_user_id    VARCHAR(32)     DEFAULT NULL COMMENT '被回复人 ID（回复某条评论时标记，一级评论为 NULL）',
    reply_to_user_name  VARCHAR(64)     DEFAULT NULL COMMENT '被回复人姓名（冗余）',
    provider_trace_id   VARCHAR(64)     DEFAULT NULL COMMENT '链路追踪 ID',
    status              VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by          VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by          VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    INDEX idx_instance_id (instance_id),
    INDEX idx_parent_comment_id (parent_comment_id),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程评论表（审批人沟通讨论，支持多级回复）';

-- 审批常用语表（用户预设常用审批意见，按用户隔离）
CREATE TABLE IF NOT EXISTS ydsz_flow_quick_comment (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    user_id         VARCHAR(32)     NOT NULL COMMENT '用户 ID（所属用户，常用语按用户隔离）',
    content         VARCHAR(500)    NOT NULL COMMENT '常用语内容（审批意见文本，最大长度 500）',
    comment_type    VARCHAR(32)     DEFAULT NULL COMMENT '意见分类（AGREE=同意，DISAGREE=不同意，SUGGEST=建议，INQUIRE=询问，可空）',
    sort_num        INT             NOT NULL DEFAULT 0 COMMENT '排序号（越小越靠前，默认 0）',
    use_count       INT             NOT NULL DEFAULT 0 COMMENT '使用次数（统计用，前端可按使用频率排序）',
    is_system       INT             NOT NULL DEFAULT 0 COMMENT '是否系统预设（0=用户自定义，1=系统预置所有用户可见）',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_sort_num (sort_num),
    INDEX idx_use_count (use_count),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批常用语表（用户预设常用审批意见）';

-- 流程抄送表（抄送中心通知记录，仅通知不阻塞流程）
CREATE TABLE IF NOT EXISTS ydsz_flow_cc (
    id                  VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id           VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    instance_id         VARCHAR(32)     NOT NULL COMMENT '流程实例 ID',
    task_id             VARCHAR(32)     DEFAULT NULL COMMENT '触发的任务 ID（CC 节点任务，可空）',
    node_code           VARCHAR(64)     DEFAULT NULL COMMENT '触发抄送的节点编码',
    node_name           VARCHAR(128)    DEFAULT NULL COMMENT '节点名称（冗余）',
    flow_code           VARCHAR(64)     NOT NULL COMMENT '流程编码',
    flow_name           VARCHAR(128)    DEFAULT NULL COMMENT '流程名称（冗余）',
    business_key        VARCHAR(64)     DEFAULT NULL COMMENT '业务单据 ID',
    cc_user_id          VARCHAR(32)     NOT NULL COMMENT '抄送接收人 ID',
    cc_user_name        VARCHAR(64)     DEFAULT NULL COMMENT '抄送接收人姓名（冗余）',
    cc_type             VARCHAR(32)     NOT NULL COMMENT '抄送类型（CC_NODE=CC 节点，MANUAL_CC=人工抄送，AUTO_CC=系统规则）',
    trigger_user_id     VARCHAR(32)     DEFAULT NULL COMMENT '触发抄送的人 ID（AUTO_CC 时为 SYSTEM）',
    trigger_user_name   VARCHAR(64)     DEFAULT NULL COMMENT '触发抄送的人姓名（冗余）',
    title               VARCHAR(128)    DEFAULT NULL COMMENT '抄送标题',
    content             VARCHAR(512)    DEFAULT NULL COMMENT '抄送内容/意见（人工抄送时填写）',
    read_status         VARCHAR(32)     NOT NULL DEFAULT 'UNREAD' COMMENT '已读状态（UNREAD=未读，READ=已读）',
    read_at             DATETIME        DEFAULT NULL COMMENT '已读时间（标记 READ 时由后端填充）',
    provider_trace_id   VARCHAR(64)     DEFAULT NULL COMMENT '链路追踪 ID',
    status              VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by          VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by          VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    INDEX idx_cc_user_id (cc_user_id),
    INDEX idx_instance_id (instance_id),
    INDEX idx_business_key (business_key),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程抄送表（抄送中心通知记录）';

-- 流程抄送规则表（自动抄送规则配置，运行时按规则生成抄送记录）
CREATE TABLE IF NOT EXISTS ydsz_flow_cc_rule (
    id                  VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id           VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    flow_code           VARCHAR(64)     DEFAULT NULL COMMENT '流程编码（NULL=所有流程生效）',
    node_code           VARCHAR(64)     DEFAULT NULL COMMENT '节点编码（NULL=该流程所有节点生效）',
    rule_type           VARCHAR(32)     NOT NULL COMMENT '规则类型（USER=指定用户，ROLE=角色展开，DEPT=部门展开，SPEL=表达式动态解析）',
    rule_target         VARCHAR(512)    DEFAULT NULL COMMENT '规则目标（按 ruleType 解析：USER 传 userId / ROLE 传 roleCode / DEPT 传 deptId / SPEL 传表达式）',
    enabled             INT             NOT NULL DEFAULT 1 COMMENT '是否启用（0=禁用，1=启用）',
    provider_trace_id   VARCHAR(64)     DEFAULT NULL COMMENT '链路追踪 ID',
    status              VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by          VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by          VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    INDEX idx_flow_node (flow_code, node_code),
    INDEX idx_enabled (enabled),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程抄送规则表（自动抄送规则配置）';

-- 审批附件表（附件元数据统一落库，支持 MD5 秒传去重）
CREATE TABLE IF NOT EXISTS ydsz_flow_attachment (
    id                  VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id           VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    instance_id         VARCHAR(32)     NOT NULL COMMENT '关联流程实例 ID',
    task_id             VARCHAR(32)     DEFAULT NULL COMMENT '关联任务 ID（实例级附件可为空）',
    node_code           VARCHAR(64)     DEFAULT NULL COMMENT '关联节点编码',
    biz_type            VARCHAR(32)     NOT NULL COMMENT '附件业务类型（TASK=任务级，INSTANCE=实例级，COMMENT=评论）',
    file_name           VARCHAR(255)    NOT NULL COMMENT '原始文件名（含扩展名）',
    file_ext            VARCHAR(32)     DEFAULT NULL COMMENT '文件扩展名（jpg/pdf/docx...，小写不带点）',
    file_size           BIGINT          NOT NULL COMMENT '字节大小',
    content_type        VARCHAR(128)    DEFAULT NULL COMMENT 'MIME 类型（如 image/jpeg / application/pdf）',
    storage_key         VARCHAR(512)    NOT NULL COMMENT '存储 key（OSS/COS/MinIO 对象 key 或本地相对路径）',
    storage_type        VARCHAR(32)     NOT NULL COMMENT '存储类型（OSS=阿里云，MINIO=自建对象存储，LOCAL=本地文件系统）',
    uploader_id         VARCHAR(32)     NOT NULL COMMENT '上传人 ID',
    uploader_name       VARCHAR(64)     DEFAULT NULL COMMENT '上传人姓名（冗余）',
    download_url        VARCHAR(1024)   DEFAULT NULL COMMENT '临时下载地址（由签名接口刷新，避免长 URL 泄露）',
    md5                 VARCHAR(64)     DEFAULT NULL COMMENT '文件 MD5（去重/校验）',
    provider_trace_id   VARCHAR(64)     DEFAULT NULL COMMENT '链路追踪 ID',
    status              VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by          VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by          VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    INDEX idx_instance_id (instance_id),
    INDEX idx_task_id (task_id),
    INDEX idx_md5 (md5),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批附件表（附件元数据，支持 MD5 秒传）';

-- ============================================================================
-- 委派授权 / 管理员角色 / 审计日志
-- ============================================================================

-- 流程委派代理表（长期授权规则，时间区间内匹配的待办自动转给代理人）
CREATE TABLE IF NOT EXISTS ydsz_flow_delegate_auth (
    id                      VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id               VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    owner_user_id           VARCHAR(32)     NOT NULL COMMENT '授权人（原办理人）ID',
    owner_user_name         VARCHAR(64)     DEFAULT NULL COMMENT '授权人姓名（冗余）',
    delegate_user_id        VARCHAR(32)     NOT NULL COMMENT '被授权人（代理人）ID',
    delegate_user_name      VARCHAR(64)     DEFAULT NULL COMMENT '被授权人姓名（冗余）',
    scope_type              VARCHAR(32)     NOT NULL COMMENT '匹配模式（ALL=所有流程，FLOW=指定流程，FLOW_NODE=指定流程节点，ROLE=指定角色）',
    flow_code               VARCHAR(64)     DEFAULT NULL COMMENT '流程编码（FLOW/FLOW_NODE 模式必填）',
    node_code               VARCHAR(64)     DEFAULT NULL COMMENT '节点编码（FLOW_NODE 模式必填）',
    role_code               VARCHAR(64)     DEFAULT NULL COMMENT '角色编码（ROLE 模式必填）',
    start_time              DATETIME        NOT NULL COMMENT '生效开始时间',
    end_time                DATETIME        NOT NULL COMMENT '生效结束时间',
    auth_status             VARCHAR(32)     NOT NULL DEFAULT 'ENABLED' COMMENT '授权状态（ENABLED=生效中，DISABLED=手动停用，EXPIRED=已过期，REVOKED=已撤销）',
    reason                  VARCHAR(512)    DEFAULT NULL COMMENT '授权原因（如「出差 3 天」「部门调整」）',
    provider_trace_id       VARCHAR(64)     DEFAULT NULL COMMENT '链路追踪 ID',
    status                  VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted                 TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision                INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by              VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by              VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    INDEX idx_owner_user_id (owner_user_id),
    INDEX idx_delegate_user_id (delegate_user_id),
    INDEX idx_status_time (auth_status, end_time),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程委派代理表（长期授权规则）';

-- 流程管理员角色映射表（用户与流程管理员角色多对多，支持临时授权）
CREATE TABLE IF NOT EXISTS ydsz_flow_admin_role (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    user_id         VARCHAR(32)     NOT NULL COMMENT '用户 ID',
    role_code       VARCHAR(64)     NOT NULL COMMENT '角色编码（FLOW_ADMIN=流程管理员，FLOW_DESIGNER=流程设计者，FLOW_AUDITOR=流程审计员）',
    enabled         TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用（0=撤销授权但保留历史记录，1=启用中）',
    granted_by      VARCHAR(32)     DEFAULT NULL COMMENT '授权人 ID（NULL 表示系统预置角色）',
    granted_at      DATETIME        DEFAULT NULL COMMENT '授权时间',
    expire_at       DATETIME        DEFAULT NULL COMMENT '过期时间（NULL 表示永不过期）',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    CONSTRAINT uk_user_role UNIQUE (user_id, role_code),
    INDEX idx_role_code (role_code),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程管理员角色映射表（用户-角色多对多）';

-- 流程审计日志表（全生命周期操作轨迹，只追加，禁止修改删除）
CREATE TABLE IF NOT EXISTS ydsz_flow_audit_log (
    id                  VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id           VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    instance_id         VARCHAR(32)     NOT NULL COMMENT '流程实例 ID',
    task_id             VARCHAR(32)     DEFAULT NULL COMMENT '任务 ID（实例级操作可为空）',
    flow_code           VARCHAR(64)     NOT NULL COMMENT '流程编码',
    business_type       VARCHAR(64)     DEFAULT NULL COMMENT '业务类型',
    business_id         VARCHAR(64)     DEFAULT NULL COMMENT '业务单据 ID',
    node_code           VARCHAR(64)     DEFAULT NULL COMMENT '节点编码',
    node_name           VARCHAR(128)    DEFAULT NULL COMMENT '节点名称（冗余）',
    action              VARCHAR(32)     NOT NULL COMMENT '操作类型（START/PASS/REJECT/TRANSFER/DELEGATE/COUNTERSIGN/RECALL/URGE/TERMINATE/SUSPEND/ACTIVATE/CLAIM）',
    operator_id         VARCHAR(32)     NOT NULL COMMENT '操作人 ID',
    operator_name       VARCHAR(64)     DEFAULT NULL COMMENT '操作人姓名（冗余）',
    target_id           VARCHAR(32)     DEFAULT NULL COMMENT '目标人 ID（转办/委派/加签/抄送时使用）',
    target_name         VARCHAR(64)     DEFAULT NULL COMMENT '目标人姓名（冗余）',
    comment             VARCHAR(512)    DEFAULT NULL COMMENT '审批意见',
    comment_type        VARCHAR(32)     DEFAULT NULL COMMENT '审批意见分类（AGREE=同意，DISAGREE=不同意，SUGGEST=建议，INQUIRE=询问）',
    operated_at         DATETIME        NOT NULL COMMENT '操作时间（精确到毫秒）',
    provider_trace_id   VARCHAR(64)     DEFAULT NULL COMMENT '链路追踪 ID',
    status              VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_by          VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by          VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    INDEX idx_instance_id (instance_id),
    INDEX idx_business (business_type, business_id),
    INDEX idx_operator_id (operator_id),
    INDEX idx_operated_at (operated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程审计日志表（全生命周期操作轨迹，只追加）';
