-- ====================================================================
-- ydsz-pmis-agent 模块数据库表
-- 任何 schema 调整请直接编辑 deploy/sql/V1.0.0.sql，禁止新增增量脚本
-- ====================================================================

-- Agent 定义表
CREATE TABLE IF NOT EXISTS pmis_agent_definition (
    id              VARCHAR(64)   PRIMARY KEY DEFAULT gen_random_uuid()::text,
    agent_code      VARCHAR(128)  NOT NULL UNIQUE,
    agent_name      VARCHAR(256)  NOT NULL,
    agent_type      VARCHAR(32)   NOT NULL DEFAULT 'CHAT',
    description     TEXT,
    system_prompt   TEXT,
    model_config    JSONB,
    tool_names      JSONB,
    temperature     NUMERIC(3,2)  DEFAULT 0.70,
    max_tokens      INTEGER       DEFAULT 2048,
    status          VARCHAR(16)   DEFAULT 'ACTIVE',
    tenant_id       VARCHAR(64)   DEFAULT '1',
    created_by      VARCHAR(64),
    created_at      TIMESTAMPTZ   DEFAULT NOW(),
    updated_by      VARCHAR(64),
    updated_at      TIMESTAMPTZ   DEFAULT NOW()
);
COMMENT ON TABLE pmis_agent_definition IS 'Agent 定义表';
COMMENT ON COLUMN pmis_agent_definition.agent_type IS 'CHAT / REACT / PLAN_EXECUTE / ROUTER';

-- 对话会话表
CREATE TABLE IF NOT EXISTS pmis_agent_conversation (
    id              VARCHAR(64)   PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id         VARCHAR(64),
    agent_id        VARCHAR(64),
    title           VARCHAR(256)  DEFAULT '新对话',
    status          VARCHAR(16)   DEFAULT 'ACTIVE',
    total_tokens    INTEGER       DEFAULT 0,
    message_count   INTEGER       DEFAULT 0,
    tenant_id       VARCHAR(64)   DEFAULT '1',
    created_at      TIMESTAMPTZ   DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   DEFAULT NOW()
);
COMMENT ON TABLE pmis_agent_conversation IS '对话会话表';
CREATE INDEX IF NOT EXISTS idx_agent_conv_user ON pmis_agent_conversation(user_id, created_at DESC);

-- 对话消息表
CREATE TABLE IF NOT EXISTS pmis_agent_message (
    id                  VARCHAR(64)   PRIMARY KEY DEFAULT gen_random_uuid()::text,
    conversation_id     VARCHAR(64)   NOT NULL,
    role                VARCHAR(16)   NOT NULL,
    content             TEXT,
    tool_calls          JSONB,
    tool_call_id        VARCHAR(64),
    prompt_tokens       INTEGER       DEFAULT 0,
    completion_tokens   INTEGER       DEFAULT 0,
    total_tokens        INTEGER       DEFAULT 0,
    created_at          TIMESTAMPTZ   DEFAULT NOW()
);
COMMENT ON TABLE pmis_agent_message IS '对话消息表';
CREATE INDEX IF NOT EXISTS idx_agent_msg_conv ON pmis_agent_message(conversation_id, created_at);

-- LLM 模型配置表
CREATE TABLE IF NOT EXISTS pmis_agent_model_config (
    id              VARCHAR(64)   PRIMARY KEY DEFAULT gen_random_uuid()::text,
    model_id        VARCHAR(128)  NOT NULL UNIQUE,
    provider        VARCHAR(64)   NOT NULL,
    model_name      VARCHAR(128)  NOT NULL,
    api_key         TEXT,
    base_url        VARCHAR(512),
    temperature     NUMERIC(3,2)  DEFAULT 0.70,
    max_tokens      INTEGER       DEFAULT 2048,
    top_p           NUMERIC(3,2)  DEFAULT 1.00,
    timeout_seconds INTEGER       DEFAULT 60,
    is_default      BOOLEAN       DEFAULT FALSE,
    status          VARCHAR(16)   DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ   DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   DEFAULT NOW()
);
COMMENT ON TABLE pmis_agent_model_config IS 'LLM 模型配置表';

-- Token 用量记录表
CREATE TABLE IF NOT EXISTS pmis_agent_token_usage (
    id                  VARCHAR(64)   PRIMARY KEY DEFAULT gen_random_uuid()::text,
    conversation_id     VARCHAR(64),
    agent_id            VARCHAR(64),
    model               VARCHAR(128),
    provider            VARCHAR(64),
    prompt_tokens       INTEGER       NOT NULL DEFAULT 0,
    completion_tokens   INTEGER       NOT NULL DEFAULT 0,
    total_tokens        INTEGER       NOT NULL DEFAULT 0,
    user_id             VARCHAR(64),
    tenant_id           VARCHAR(64)   DEFAULT '1',
    created_at          TIMESTAMPTZ   DEFAULT NOW()
);
COMMENT ON TABLE pmis_agent_token_usage IS 'Token 用量记录表';
CREATE INDEX IF NOT EXISTS idx_agent_token_conv ON pmis_agent_token_usage(conversation_id, created_at);
CREATE INDEX IF NOT EXISTS idx_agent_token_model ON pmis_agent_token_usage(model, created_at);

-- Agent 执行链路表
CREATE TABLE IF NOT EXISTS pmis_agent_execution_trace (
    id              VARCHAR(64)   PRIMARY KEY DEFAULT gen_random_uuid()::text,
    conversation_id VARCHAR(64)   NOT NULL,
    agent_id        VARCHAR(64),
    step_index      INTEGER       NOT NULL DEFAULT 0,
    step_type       VARCHAR(32)   NOT NULL,
    step_content    TEXT,
    step_input      JSONB,
    step_output     JSONB,
    duration_ms     BIGINT,
    status          VARCHAR(16)   DEFAULT 'SUCCESS',
    created_at      TIMESTAMPTZ   DEFAULT NOW()
);
COMMENT ON TABLE pmis_agent_execution_trace IS 'Agent 执行链路表';
CREATE INDEX IF NOT EXISTS idx_agent_trace_conv ON pmis_agent_execution_trace(conversation_id, step_index);

-- Prompt 模板表
CREATE TABLE IF NOT EXISTS pmis_agent_prompt_template (
    id              VARCHAR(64)   PRIMARY KEY DEFAULT gen_random_uuid()::text,
    template_code   VARCHAR(128)  NOT NULL,
    template_name   VARCHAR(256)  NOT NULL,
    content         TEXT          NOT NULL,
    version         VARCHAR(32)   DEFAULT '1.0',
    description     TEXT,
    variables       JSONB,
    status          VARCHAR(16)   DEFAULT 'ACTIVE',
    created_by      VARCHAR(64),
    created_at      TIMESTAMPTZ   DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   DEFAULT NOW(),
    UNIQUE(template_code, version)
);
COMMENT ON TABLE pmis_agent_prompt_template IS 'Prompt 模板表';

-- 工具定义表
CREATE TABLE IF NOT EXISTS pmis_agent_tool_def (
    id                  VARCHAR(64)   PRIMARY KEY DEFAULT gen_random_uuid()::text,
    tool_name           VARCHAR(128)  NOT NULL UNIQUE,
    description         TEXT,
    parameters_schema   JSONB,
    implementation_class VARCHAR(512),
    category            VARCHAR(64),
    status              VARCHAR(16)   DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ   DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   DEFAULT NOW()
);
COMMENT ON TABLE pmis_agent_tool_def IS '工具定义表';
