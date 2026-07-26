-- ====================================================================
-- ydsz-agent 模块数据库表（V1.0.0）
-- 物理 Mapper 路径：ydsz-agent/ydsz-agent-infra/.../mapper/
-- 任何 schema 调整请直接编辑本文件，禁止新增增量脚本
-- ====================================================================

-- Agent 定义表
CREATE TABLE IF NOT EXISTS ydsz_agent_definition (
    id              VARCHAR(64)   PRIMARY KEY DEFAULT gen_random_uuid()::text,
    agent_code      VARCHAR(128)  NOT NULL,
    agent_name      VARCHAR(256)  NOT NULL,
    agent_type      VARCHAR(32)   NOT NULL DEFAULT 'CHAT',
    description     TEXT,
    system_prompt   TEXT,
    model_config    JSONB,
    tool_names      JSONB,
    temperature     NUMERIC(3,2)  DEFAULT 0.70,
    max_tokens      INTEGER       DEFAULT 2048,
    status          VARCHAR(16)   DEFAULT 'ACTIVE',
    deleted         BOOLEAN       DEFAULT FALSE,
    tenant_id       VARCHAR(64)   DEFAULT '1',
    created_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at      TIMESTAMPTZ   DEFAULT NOW(),
    updated_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at      TIMESTAMPTZ   DEFAULT NOW()
);
COMMENT ON TABLE ydsz_agent_definition IS 'Agent 定义表';
COMMENT ON COLUMN ydsz_agent_definition.agent_type IS 'CHAT / REACT / RAG / PLAN_EXECUTE / ROUTER';
COMMENT ON COLUMN ydsz_agent_definition.deleted IS '逻辑删除标记：FALSE=正常，TRUE=已删除';
CREATE INDEX IF NOT EXISTS idx_agent_def_tenant_code ON ydsz_agent_definition(tenant_id, agent_code) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_agent_def_status ON ydsz_agent_definition(status) WHERE deleted = FALSE;

-- 对话会话表
CREATE TABLE IF NOT EXISTS ydsz_agent_conversation (
    id              VARCHAR(64)   PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id         VARCHAR(64),
    agent_id        VARCHAR(64),
    title           VARCHAR(256)  DEFAULT '新对话',
    status          VARCHAR(16)   DEFAULT 'ACTIVE',
    total_tokens    INTEGER       DEFAULT 0,
    message_count   INTEGER       DEFAULT 0,
    deleted         BOOLEAN       DEFAULT FALSE,
    tenant_id       VARCHAR(64)   DEFAULT '1',
    created_at      TIMESTAMPTZ   DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   DEFAULT NOW()
);
COMMENT ON TABLE ydsz_agent_conversation IS '对话会话表';
COMMENT ON COLUMN ydsz_agent_conversation.deleted IS '逻辑删除标记：FALSE=正常，TRUE=已删除';
CREATE INDEX IF NOT EXISTS idx_agent_conv_user ON ydsz_agent_conversation(user_id, created_at DESC) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_agent_conv_agent ON ydsz_agent_conversation(agent_id, created_at DESC) WHERE deleted = FALSE;

-- 对话消息表
CREATE TABLE IF NOT EXISTS ydsz_agent_message (
    id                  VARCHAR(64)   PRIMARY KEY DEFAULT gen_random_uuid()::text,
    conversation_id     VARCHAR(64)   NOT NULL,
    role                VARCHAR(16)   NOT NULL,
    content             TEXT,
    tool_calls          JSONB,
    tool_call_id        VARCHAR(64),
    prompt_tokens       INTEGER       DEFAULT 0,
    completion_tokens   INTEGER       DEFAULT 0,
    total_tokens        INTEGER       DEFAULT 0,
    tenant_id           VARCHAR(64)   DEFAULT '1',
    created_at          TIMESTAMPTZ   DEFAULT NOW()
);
COMMENT ON TABLE ydsz_agent_message IS '对话消息表';
CREATE INDEX IF NOT EXISTS idx_agent_msg_conv ON ydsz_agent_message(conversation_id, created_at);

-- LLM 模型配置表
CREATE TABLE IF NOT EXISTS ydsz_agent_model_config (
    id              VARCHAR(64)   PRIMARY KEY DEFAULT gen_random_uuid()::text,
    model_id        VARCHAR(128)  NOT NULL,
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
    deleted         BOOLEAN       DEFAULT FALSE,
    tenant_id       VARCHAR(64)   DEFAULT '1',
    created_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at      TIMESTAMPTZ   DEFAULT NOW(),
    updated_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at      TIMESTAMPTZ   DEFAULT NOW()
);
COMMENT ON TABLE ydsz_agent_model_config IS 'LLM 模型配置表';
COMMENT ON COLUMN ydsz_agent_model_config.api_key IS 'Jasypt ENC() 加密存储，应用层解密，禁止明文';
COMMENT ON COLUMN ydsz_agent_model_config.deleted IS '逻辑删除标记：FALSE=正常，TRUE=已删除';
CREATE INDEX IF NOT EXISTS idx_agent_model_tenant_id ON ydsz_agent_model_config(tenant_id, model_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_agent_model_provider ON ydsz_agent_model_config(provider, status) WHERE deleted = FALSE;

-- Token 用量记录表
CREATE TABLE IF NOT EXISTS ydsz_agent_token_usage (
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
COMMENT ON TABLE ydsz_agent_token_usage IS 'Token 用量记录表';
CREATE INDEX IF NOT EXISTS idx_agent_token_conv ON ydsz_agent_token_usage(conversation_id, created_at);
CREATE INDEX IF NOT EXISTS idx_agent_token_model ON ydsz_agent_token_usage(model, created_at);
CREATE INDEX IF NOT EXISTS idx_agent_token_tenant ON ydsz_agent_token_usage(tenant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_agent_token_user ON ydsz_agent_token_usage(user_id, created_at);

-- Agent 执行链路表
CREATE TABLE IF NOT EXISTS ydsz_agent_execution_trace (
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
    tenant_id       VARCHAR(64)   DEFAULT '1',
    created_at      TIMESTAMPTZ   DEFAULT NOW()
);
COMMENT ON TABLE ydsz_agent_execution_trace IS 'Agent 执行链路表';
CREATE INDEX IF NOT EXISTS idx_agent_trace_conv ON ydsz_agent_execution_trace(conversation_id, step_index);
CREATE INDEX IF NOT EXISTS idx_agent_trace_agent ON ydsz_agent_execution_trace(agent_id, created_at DESC);

-- Prompt 模板表
CREATE TABLE IF NOT EXISTS ydsz_agent_prompt_template (
    id              VARCHAR(64)   PRIMARY KEY DEFAULT gen_random_uuid()::text,
    template_code   VARCHAR(128)  NOT NULL,
    template_name   VARCHAR(256)  NOT NULL,
    content         TEXT          NOT NULL,
    version         VARCHAR(32)   DEFAULT '1.0',
    description     TEXT,
    variables       JSONB,
    status          VARCHAR(16)   DEFAULT 'ACTIVE',
    deleted         BOOLEAN       DEFAULT FALSE,
    tenant_id       VARCHAR(64)   DEFAULT '1',
    created_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at      TIMESTAMPTZ   DEFAULT NOW(),
    updated_by      VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at      TIMESTAMPTZ   DEFAULT NOW(),
    UNIQUE(template_code, version)
);
COMMENT ON TABLE ydsz_agent_prompt_template IS 'Prompt 模板表';
COMMENT ON COLUMN ydsz_agent_prompt_template.deleted IS '逻辑删除标记：FALSE=正常，TRUE=已删除';

-- 工具定义表
CREATE TABLE IF NOT EXISTS ydsz_agent_tool_def (
    id                  VARCHAR(64)   PRIMARY KEY DEFAULT gen_random_uuid()::text,
    tool_name           VARCHAR(128)  NOT NULL,
    description         TEXT,
    parameters_schema   JSONB,
    implementation_class VARCHAR(512),
    category            VARCHAR(64),
    status              VARCHAR(16)   DEFAULT 'ACTIVE',
    deleted             BOOLEAN       DEFAULT FALSE,
    tenant_id           VARCHAR(64)   DEFAULT '1',
    created_by          VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    created_at          TIMESTAMPTZ   DEFAULT NOW(),
    updated_by          VARCHAR(64) DEFAULT 'SYSTEM' NOT NULL,
    updated_at          TIMESTAMPTZ   DEFAULT NOW(),
    UNIQUE(tool_name)
);
COMMENT ON TABLE ydsz_agent_tool_def IS '工具定义表';
COMMENT ON COLUMN ydsz_agent_tool_def.deleted IS '逻辑删除标记：FALSE=正常，TRUE=已删除';
CREATE INDEX IF NOT EXISTS idx_agent_tool_category ON ydsz_agent_tool_def(category, status) WHERE deleted = FALSE;

-- pgvector 扩展（RAG 向量存储）
CREATE EXTENSION IF NOT EXISTS vector;

-- 文档分块向量表（RAG 知识库核心表）
CREATE TABLE IF NOT EXISTS ydsz_agent_document_chunk (
    id              VARCHAR(64)   PRIMARY KEY DEFAULT gen_random_uuid()::text,
    document_id     VARCHAR(64)   NOT NULL,
    content         TEXT          NOT NULL,
    embedding       vector(1536),
    chunk_index     INTEGER       DEFAULT 0,
    token_count     INTEGER       DEFAULT 0,
    document_title  VARCHAR(256),
    source          VARCHAR(128),
    metadata        JSONB,
    deleted         BOOLEAN       DEFAULT FALSE,
    tenant_id       VARCHAR(64)   DEFAULT '1',
    created_at      TIMESTAMPTZ   DEFAULT NOW()
);
COMMENT ON TABLE ydsz_agent_document_chunk IS '文档分块向量表（RAG 知识库）';
COMMENT ON COLUMN ydsz_agent_document_chunk.deleted IS '逻辑删除标记：FALSE=正常，TRUE=已删除';

-- IVFFlat 向量索引（近似最近邻搜索）
CREATE INDEX IF NOT EXISTS idx_chunk_embedding
    ON ydsz_agent_document_chunk USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

-- 文档 ID 索引（按文档删除/查询）
CREATE INDEX IF NOT EXISTS idx_chunk_doc
    ON ydsz_agent_document_chunk(document_id) WHERE deleted = FALSE;

-- 来源类型索引（按来源过滤）
CREATE INDEX IF NOT EXISTS idx_chunk_source
    ON ydsz_agent_document_chunk(source) WHERE deleted = FALSE;

-- 租户索引（按租户隔离查询）
CREATE INDEX IF NOT EXISTS idx_chunk_tenant
    ON ydsz_agent_document_chunk(tenant_id, created_at DESC) WHERE deleted = FALSE;
