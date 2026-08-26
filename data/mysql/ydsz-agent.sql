-- ============================================================================
-- 模块：ydsz-agent
-- 说明：基于 ydsz-agent-infra 实体类与既有迁移脚本（V1__prompt_template.sql、
--       V2__trace_step_cost.sql）整理的完整建表脚本
-- 日期：2026-08-25
-- @author ydsz-team
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Prompt 模板主表（沿用 V1__prompt_template.sql 定义）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_prompt_template (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    template_code   VARCHAR(64)     NOT NULL COMMENT '模板唯一编码（业务标识，创建后不可变）',
    template_name   VARCHAR(128)    NOT NULL COMMENT '模板名称（展示用）',
    content         TEXT            NOT NULL COMMENT '模板内容，支持 #{var} 占位符',
    description     VARCHAR(512)    DEFAULT NULL COMMENT '模板描述',
    category        VARCHAR(64)     DEFAULT NULL COMMENT '分类（用于分组检索）',
    current_version INT             NOT NULL DEFAULT 1 COMMENT '当前版本号，自 1 起每次更新递增',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',

    -- 索引
    CONSTRAINT uk_template_code UNIQUE (template_code, tenant_id),
    INDEX idx_category (category),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Prompt 模板主表';

-- ----------------------------------------------------------------------------
-- Prompt 模板版本历史表（沿用 V1__prompt_template.sql 定义）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_prompt_version (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    template_code   VARCHAR(64)     NOT NULL COMMENT '所属模板编码（关联 ydsz_prompt_template.template_code）',
    version         INT             NOT NULL COMMENT '版本号（与 template 的 current_version 对应）',
    content         TEXT            NOT NULL COMMENT '该版本的模板内容快照',
    change_note     VARCHAR(512)    DEFAULT NULL COMMENT '版本备注（描述本次变更内容）',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '版本创建时间',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '操作人',
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '版本更新时间',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',

    -- 索引
    CONSTRAINT uk_template_version UNIQUE (template_code, version, tenant_id),
    INDEX idx_template_code (template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Prompt 模板版本历史表';

-- ----------------------------------------------------------------------------
-- Agent 定义表（实体：AgentDefinition extends MpBaseEntity<String>）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_agent_definition (
    id              VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id       VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    agent_code      VARCHAR(64)     NOT NULL COMMENT 'Agent 编码（业务唯一键）',
    agent_name      VARCHAR(128)    NOT NULL COMMENT 'Agent 名称（展示用）',
    agent_type      VARCHAR(32)     NOT NULL COMMENT 'Agent 类型（CHAT/REACT/RAG/PLAN_EXECUTE/ROUTER）',
    description     VARCHAR(512)    DEFAULT NULL COMMENT 'Agent 描述',
    system_prompt   TEXT            DEFAULT NULL COMMENT '系统提示词',
    model_config    JSON            DEFAULT NULL COMMENT '模型配置 JSON（temperature/maxTokens/modelId 等）',
    tool_names      JSON            DEFAULT NULL COMMENT '工具名称列表 JSON（["tool1","tool2"]）',
    temperature     DOUBLE          DEFAULT NULL COMMENT '温度参数',
    max_tokens      INT             DEFAULT NULL COMMENT '最大生成 Token 数',
    status          VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision        INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by      VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by      VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',

    -- 索引
    CONSTRAINT uk_agent_code UNIQUE (agent_code, tenant_id),
    INDEX idx_agent_type (agent_type),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 定义（Agent 的完整配置信息）';

-- ----------------------------------------------------------------------------
-- Agent 执行链路表（实体：AgentTrace，独立主键 trace_id，无公共基类列）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_agent_trace (
    trace_id            VARCHAR(64)     PRIMARY KEY COMMENT '链路唯一 ID（主键，业务生成非自增）',
    conversation_id     VARCHAR(64)     NOT NULL COMMENT '所属对话 ID',
    agent_id            VARCHAR(64)     NOT NULL COMMENT 'Agent 类型标识（CHAT/REACT/RAG/PLAN_EXECUTE/SUPERVISOR）',
    status              VARCHAR(32)     NOT NULL COMMENT '执行状态（RUNNING/SUCCESS/FAILED/MAX_ITERATIONS/GUARDRAIL_REJECTED）',
    total_duration_ms   BIGINT          DEFAULT NULL COMMENT '总耗时（毫秒）',

    -- 索引
    INDEX idx_trace_conversation (conversation_id),
    INDEX idx_trace_agent (agent_id),
    INDEX idx_trace_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 执行链路（记录一次 Agent 执行的完整元数据）';

-- ----------------------------------------------------------------------------
-- Agent 执行链路步骤表（实体：AgentTraceStep；已合并 V2__trace_step_cost.sql 的 cost 列）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_agent_trace_step (
    trace_id        VARCHAR(64)     NOT NULL COMMENT '链路 ID（关联 ydsz_agent_trace.trace_id）',
    step_index      INT             NOT NULL COMMENT '步骤序号（从 0 开始递增）',
    step_type       VARCHAR(32)     NOT NULL COMMENT '步骤类型（LLM_CALL/TOOL_CALL/THOUGHT/OBSERVATION/ROUTE/LLM_CALL_ERROR）',
    content         TEXT            DEFAULT NULL COMMENT '步骤内容描述',
    input_json      JSON            DEFAULT NULL COMMENT '步骤输入（JSON 字符串）',
    output_json     JSON            DEFAULT NULL COMMENT '步骤输出（JSON 字符串）',
    duration_ms     BIGINT          DEFAULT NULL COMMENT '耗时（毫秒）',
    cost            DECIMAL(12, 6)  NOT NULL DEFAULT 0.0 COMMENT 'Token 成本（USD，精确到 6 位小数；非 LLM 调用步骤为 0）',

    -- 索引
    PRIMARY KEY (trace_id, step_index),
    INDEX idx_trace_step_cost (cost)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 执行链路步骤（记录单个执行步骤，支持回放与调试）';

-- ----------------------------------------------------------------------------
-- Agent 人工审批请求表（实体：AgentApproval，独立主键 id，无公共基类列）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_agent_approval (
    id                  VARCHAR(64)     PRIMARY KEY COMMENT '审批请求 ID（主键，业务生成非自增）',
    conversation_id     VARCHAR(64)     DEFAULT NULL COMMENT '所属对话 ID',
    trace_id            VARCHAR(64)     DEFAULT NULL COMMENT '执行链路 ID',
    step_description    VARCHAR(512)    DEFAULT NULL COMMENT '待审批步骤的业务描述',
    context_json        TEXT            DEFAULT NULL COMMENT '审批上下文（JSON 字符串，含用户输入、已有结果等）',
    status              VARCHAR(32)     NOT NULL DEFAULT 'PENDING' COMMENT '审批状态（PENDING/APPROVED/REJECTED/EXPIRED）',
    approver            VARCHAR(64)     DEFAULT NULL COMMENT '审批人标识',
    `comment`           VARCHAR(512)    DEFAULT NULL COMMENT '审批意见',
    tenant_id           VARCHAR(64)     NOT NULL DEFAULT '0' COMMENT '租户 ID',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '请求创建时间',
    resolved_at         DATETIME        DEFAULT NULL COMMENT '审批完成时间',

    -- 索引
    INDEX idx_approval_conversation (conversation_id),
    INDEX idx_approval_trace (trace_id),
    INDEX idx_approval_status (status),
    INDEX idx_approval_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 人工审批请求（Human-in-the-Loop 审批持久化）';

-- ----------------------------------------------------------------------------
-- Token 用量记录表（实体：TokenUsageRecord extends MpBaseEntity<String>）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ydsz_agent_token_usage (
    id                  VARCHAR(32)     PRIMARY KEY COMMENT '主键 ID（Snowflake）',
    tenant_id           VARCHAR(32)     NOT NULL DEFAULT '0' COMMENT '租户 ID（多租户隔离）',
    conversation_id     VARCHAR(64)     NOT NULL COMMENT '所属对话 ID（关联 ydsz_agent_conversation）',
    model_name          VARCHAR(64)     NOT NULL COMMENT '使用的模型标识',
    prompt_tokens       BIGINT          NOT NULL DEFAULT 0 COMMENT '提示词 Token 数',
    completion_tokens   BIGINT          NOT NULL DEFAULT 0 COMMENT '补全 Token 数',
    total_tokens        BIGINT          NOT NULL DEFAULT 0 COMMENT '总 Token 数（prompt + completion）',
    status              VARCHAR(32)     DEFAULT NULL COMMENT '状态标识',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除，1=已删除）',
    revision            INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    created_by          VARCHAR(64)     DEFAULT NULL COMMENT '创建人',
    updated_by          VARCHAR(64)     DEFAULT NULL COMMENT '最后更新人',

    -- 索引
    INDEX idx_conversation_created (conversation_id, created_at),
    INDEX idx_tenant_deleted (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Token 用量记录（LLM 调用 Token 消耗明细）';

-- ============================================================================
-- 初始化数据：默认系统 Prompt 模板（沿用 V1__prompt_template.sql）
-- ============================================================================
INSERT INTO ydsz_prompt_template (id, tenant_id, template_code, template_name, content, description, category, current_version, deleted)
VALUES ('100000000000000001', '0', 'DEFAULT_SYSTEM', '默认系统 Prompt',
        '你是 YDSZ 项目管理信息系统的智能助手。你可以帮助用户查询项目信息、分析项目进度、发起审批流程、发送消息通知等。请用中文回答。',
        '系统默认的通用助手 Prompt', 'system', 1, FALSE);

INSERT INTO ydsz_prompt_version (id, tenant_id, template_code, version, content, change_note)
VALUES ('100000000000000002', '0', 'DEFAULT_SYSTEM', 1,
        '你是 YDSZ 项目管理信息系统的智能助手。你可以帮助用户查询项目信息、分析项目进度、发起审批流程、发送消息通知等。请用中文回答。',
        '初始版本');

INSERT INTO ydsz_prompt_template (id, tenant_id, template_code, template_name, content, description, category, current_version, deleted)
VALUES ('100000000000000003', '0', 'REACT_SYSTEM', 'ReAct Agent Prompt',
        '你是 YDSZ 项目管理信息系统的智能助手。你可以使用工具来帮助用户完成任务。请根据用户需求决定是否使用工具。如果不需要工具，直接回答即可。',
        'ReAct 模式下的工具调用助手 Prompt', 'system', 1, FALSE);

INSERT INTO ydsz_prompt_version (id, tenant_id, template_code, version, content, change_note)
VALUES ('100000000000000004', '0', 'REACT_SYSTEM', 1,
        '你是 YDSZ 项目管理信息系统的智能助手。你可以使用工具来帮助用户完成任务。请根据用户需求决定是否使用工具。如果不需要工具，直接回答即可。',
        '初始版本');
