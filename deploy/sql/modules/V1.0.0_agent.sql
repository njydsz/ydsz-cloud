-- ====================================================================
-- AI Agent (Agent/Orch/Knowledge/Tool/HitL)
-- Module: agent | Version: V1.0.0 | Target: PostgreSQL 18
-- Generated from deploy/sql/V1.0.0.sql
-- ====================================================================


-- =====================================================
-- 6. AI 智能体预测/推荐结果表 pmis_agent_prediction
-- =====================================================
-- P1-6: 宸插簾寮?鏃犻渶 DROP), 鏍囪淇濈暀浠ヨ褰曞巻鍙?DROP TABLE IF EXISTS pmis_agent_prediction; -- 已废弃
CREATE TABLE IF NOT EXISTS pmis_agent_prediction(
    id                  VARCHAR(20) PRIMARY KEY,
    task_code           VARCHAR(64)  NOT NULL,
    agent_type          VARCHAR(32)  NOT NULL,                  -- RISK_WARNING/RESOURCE_RECOMMEND/PROFIT_FORECAST/WIN_RATE_PREDICT/TIMESHEET_ANOMALY
    biz_type            VARCHAR(32),                            -- PROJECT/OPPORTUNITY/TIMESHEET/STAFF
    biz_id              VARCHAR(20),
    biz_ref             VARCHAR(256),
    input_snapshot      TEXT,                                   -- 输入数据 JSON
    output_result       TEXT,                                   -- 输出数据 JSON
    alert_level         VARCHAR(16)  NOT NULL DEFAULT 'NORMAL', -- INFO/YELLOW/RED/NORMAL/RECOMMEND
    score               NUMERIC(7,2) NOT NULL DEFAULT 0,        -- 0-100
    confidence          NUMERIC(4,2) NOT NULL DEFAULT 0,        -- 0-1
    suggestion          TEXT,
    matched_rules       VARCHAR(2048) NOT NULL DEFAULT '[]',   -- 命中规则 JSON
    cost_ms             BIGINT       NOT NULL DEFAULT 0,
    model_version       VARCHAR(32)  NOT NULL DEFAULT 'v1.0.0',
    status              VARCHAR(32)  NOT NULL DEFAULT 'PENDING',  -- PENDING/RUNNING/SUCCESS/FAILED
    error_msg           TEXT,
    caller_id           VARCHAR(20),
    caller_name         VARCHAR(64),
    source              VARCHAR(32)  NOT NULL DEFAULT 'MANUAL', -- MANUAL/SCHEDULED/EVENT
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    provider_trace_id   VARCHAR(64)  NOT NULL DEFAULT '',
    created_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_pap_code              UNIQUE (task_code, deleted),
    CONSTRAINT ck_pap_agent_type        CHECK (agent_type IN ('RISK_WARNING','RESOURCE_RECOMMEND','PROFIT_FORECAST','WIN_RATE_PREDICT','TIMESHEET_ANOMALY')),
    CONSTRAINT ck_pap_biz_type          CHECK (biz_type IS NULL OR biz_type IN ('PROJECT','OPPORTUNITY','TIMESHEET','STAFF')),
    CONSTRAINT ck_pap_alert_level       CHECK (alert_level IN ('INFO','YELLOW','RED','NORMAL','RECOMMEND')),
    CONSTRAINT ck_pap_status_enum       CHECK (status IN ('PENDING','RUNNING','SUCCESS','FAILED')),
    CONSTRAINT ck_pap_source            CHECK (source IN ('MANUAL','SCHEDULED','EVENT')),
    CONSTRAINT ck_pap_score_range       CHECK (score >= 0 AND score <= 100),
    CONSTRAINT ck_pap_confidence_range  CHECK (confidence >= 0 AND confidence <= 1),
    CONSTRAINT ck_pap_cost_ms_nonneg    CHECK (cost_ms >= 0),
    CONSTRAINT ck_pap_deleted_enum      CHECK (deleted IN (0, 1))
);
COMMENT ON TABLE  pmis_agent_prediction IS 'AI 智能体预测/推荐结果表: 5 类智能体（风险预警/资源推荐/利润预测/赢率预测/工时异常）的输出持久化';
COMMENT ON COLUMN pmis_agent_prediction.task_code IS '任务编码: 业务唯一,如 AGT-2026-001';
COMMENT ON COLUMN pmis_agent_prediction.agent_type IS '智能体类型: RISK_WARNING 风险预警 / RESOURCE_RECOMMEND 资源推荐 / PROFIT_FORECAST 利润预测 / WIN_RATE_PREDICT 赢率预测 / TIMESHEET_ANOMALY 工时异常';
COMMENT ON COLUMN pmis_agent_prediction.biz_type IS '业务类型: PROJECT 项目 / OPPORTUNITY 商机 / TIMESHEET 工时 / STAFF 员工';
COMMENT ON COLUMN pmis_agent_prediction.biz_id IS '业务对象 ID';
COMMENT ON COLUMN pmis_agent_prediction.biz_ref IS '业务对象引用: 例如项目编号';
COMMENT ON COLUMN pmis_agent_prediction.input_snapshot IS '输入数据快照 JSON: 智能体推理时的完整输入';
COMMENT ON COLUMN pmis_agent_prediction.output_result IS '输出结果 JSON: 智能体的推理结果';
COMMENT ON COLUMN pmis_agent_prediction.alert_level IS '预警等级: INFO 提示 / YELLOW 黄色 / RED 红色 / NORMAL 正常 / RECOMMEND 推荐';
COMMENT ON COLUMN pmis_agent_prediction.score IS '评分: 0-100 分,具体含义由 agent_type 决定';
COMMENT ON COLUMN pmis_agent_prediction.confidence IS '置信度: 0-1,例如 0.85=85% 置信';
COMMENT ON COLUMN pmis_agent_prediction.suggestion IS '建议: 智能体给出的处置建议';
COMMENT ON COLUMN pmis_agent_prediction.matched_rules IS '命中规则 JSON 数组: 命中的规则编码列表';
COMMENT ON COLUMN pmis_agent_prediction.cost_ms IS '推理耗时(毫秒): 用于性能监控';
COMMENT ON COLUMN pmis_agent_prediction.model_version IS '模型版本: 默认 v1.0.0';
COMMENT ON COLUMN pmis_agent_prediction.status IS '执行状态: PENDING 待执行 / RUNNING 执行中 / SUCCESS 成功 / FAILED 失败';
COMMENT ON COLUMN pmis_agent_prediction.error_msg IS '错误信息: 执行失败时的错误堆栈';
COMMENT ON COLUMN pmis_agent_prediction.caller_id IS '调用方 ID';
COMMENT ON COLUMN pmis_agent_prediction.caller_name IS '调用方姓名（冗余）';
COMMENT ON COLUMN pmis_agent_prediction.source IS '调用来源: MANUAL 手动 / SCHEDULED 定时 / EVENT 事件触发';
COMMENT ON COLUMN pmis_agent_prediction.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_agent_prediction.provider_trace_id IS '链路追踪 ID: 端到端 trace';
COMMENT ON COLUMN pmis_agent_prediction.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- =====================================================
-- 6.1 Agent Prompt 模板表 pmis_agent_prompt_template（P2-2）
--    支持 ${var} 变量替换 / 版本管理 / 激活排他
-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_agent_prompt_template(
    id              VARCHAR(20)      PRIMARY KEY,
    template_code   VARCHAR(128)  NOT NULL,                  -- 模板编码（业务唯一）
    template_name   VARCHAR(256)  NOT NULL,                  -- 模板名称（展示用）
    agent_type      VARCHAR(32)   NOT NULL DEFAULT 'COMMON', -- FLOW_GENERATOR/RISK_WARNING/COMMON
    prompt_role     VARCHAR(32)   NOT NULL DEFAULT 'SYSTEM', -- SYSTEM/USER/REACT_FORMAT
    content         TEXT         NOT NULL,                  -- 模板内容，支持 ${var} 占位符
    version         VARCHAR(32)   NOT NULL DEFAULT '1.0.0',  -- 语义版本
    is_active       BOOLEAN      NOT NULL DEFAULT false,     -- 是否当前生效（同 code 仅一条为 true）
    description     VARCHAR(512),
    tenant_id       VARCHAR(20)       NOT NULL DEFAULT '1',
    created_by      VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT ck_papt_role_enum     CHECK (prompt_role IN ('SYSTEM', 'USER', 'REACT_FORMAT')),
    CONSTRAINT ck_papt_deleted_enum  CHECK (deleted IN (0, 1))
);
COMMENT ON TABLE  pmis_agent_prompt_template IS 'Agent Prompt 模板表: 支持 ${var} 变量替换 / 版本管理 / 激活排他';
COMMENT ON COLUMN pmis_agent_prompt_template.template_code IS '模板编码: REACT_FORMAT_INSTRUCTION / FLOW_GENERATOR_SYSTEM / FLOW_GENERATOR_USER 等';
COMMENT ON COLUMN pmis_agent_prompt_template.agent_type IS 'Agent 类型: FLOW_GENERATOR / RISK_WARNING / COMMON（通用）';
COMMENT ON COLUMN pmis_agent_prompt_template.prompt_role IS 'Prompt 角色: SYSTEM 系统提示 / USER 用户提示 / REACT_FORMAT ReAct 格式说明';
COMMENT ON COLUMN pmis_agent_prompt_template.content IS '模板内容: 支持 ${var} 和 ${a.b.c} 嵌套占位符';
COMMENT ON COLUMN pmis_agent_prompt_template.version IS '语义版本: 同 code 可有多版本，仅一条 is_active=true 生效';
COMMENT ON COLUMN pmis_agent_prompt_template.is_active IS '是否生效: true=生效，同一 template_code 仅允许一条为 true';

-- 种子数据：3 条内置默认模板（与 BuiltInPromptTemplates 保持一致）
INSERT INTO pmis_agent_prompt_template
    (id, template_code, template_name, agent_type, prompt_role, content, version, is_active, description, tenant_id)
VALUES
    ('1', 'REACT_FORMAT_INSTRUCTION', 'ReAct 推理循环输出格式说明', 'COMMON', 'REACT_FORMAT',
     E'你正在参与 ReAct 推理循环（Thought → Action → Observation）。\n每一步你必须输出以下 JSON 结构（不要使用 markdown 代码块包裹）：\n{\n  "thought": "对当前步骤的思考（为何选择此 Action）",\n  "action": "工具名 或 final_answer",\n  "parameters": { "参数名": "参数值" },\n  "finalAnswer": null\n}\n\n规则：\n1. 若需要调用工具获取信息，action 填写工具名，parameters 填写工具参数，finalAnswer 必须为 null。\n2. 若已得到最终答案，action 必须填写 "final_answer"，parameters 必须为 null，finalAnswer 填写最终答案。\n3. 你可以最多思考 5 步，请合理规划工具调用顺序。\n4. 工具执行结果会以 "[步骤 N 观察]" 的形式追加在用户问题之后。',
     '1.0.0', true, 'ReAct 推理循环通用格式说明，所有 ReAct Agent 共享', '1'),
    ('2', 'FLOW_GENERATOR_SYSTEM', '流程生成 Agent 系统提示词', 'FLOW_GENERATOR', 'SYSTEM',
     E'你是一名资深的工作流（BPMN 2.0）建模专家。请根据用户提供的自然语言流程描述，\n生成一段符合 BPMN 2.0 规范的 XML 流程定义。\n\n要求：\n1. 根元素必须为 <bpmn:definitions>，并声明 bpmn / bpmndi / dc / di 命名空间；\n   targetNamespace 使用 "http://njydsz.com/pmis/flow"。\n2. 流程必须包含：开始节点（startEvent）、至少一个审批节点（userTask）、结束节点（endEvent）。\n3. 当描述中存在条件分支（如"3天以上需经理审批"）时，使用 exclusiveGateway（排他网关）\n   配合 sequenceFlow 的 conditionExpression 表达分支。\n4. 节点之间使用 <bpmn:sequenceFlow> 连接，sourceRef / targetRef 引用节点 id。\n5. 为每个节点设置语义化 id 与中文 name。\n\n工作流程建议：\n- 先生成 BPMN XML，调用 bpmn_validate 工具校验结构完整性\n- 校验通过后，在 final_answer 中输出完整的 BPMN XML（纯 XML 文本，不要 JSON 包裹）\n- 在 final_answer 步骤的 thought 中用一句话描述流程特点',
     '1.0.0', true, 'FlowGeneratorAgent 系统提示词，约束 LLM 输出 BPMN 2.0 XML', '1'),
    ('3', 'FLOW_GENERATOR_USER', '流程生成 Agent 用户提示词模板', 'FLOW_GENERATOR', 'USER',
     E'请根据以下描述生成 BPMN 2.0 流程定义 XML：\n\n${description}',
     '1.0.0', true, 'FlowGeneratorAgent 用户提示词模板，支持 ${description} 变量', '1')
ON CONFLICT DO NOTHING;

-- =====================================================
-- 6.2 Agent 全链路 Tracing 表 pmis_agent_trace（P2-3）
--    每个 Agent 执行的关键节点（AGENT_START/LLM_CALL/TOOL_CALL/STEP/AGENT_END）
--    都落一行 span，按 trace_id 串联完整链路，便于查询/审计/性能分析。
-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_agent_trace(
    id                VARCHAR(20)   PRIMARY KEY,
    trace_id          VARCHAR(64)   NOT NULL,                  -- 链路 ID（与 AgentContext.traceId / Brave traceId 对齐）
    span_id           VARCHAR(20)   NOT NULL,                  -- 本 span ID（雪花算法）
    parent_span_id    VARCHAR(20),                             -- 父 span ID（树形结构，AGENT_START 为根）
    agent_type        VARCHAR(32)  NOT NULL,                  -- RISK_WARNING/RESOURCE_RECOMMEND/PROFIT_FORECAST/WIN_RATE_PREDICT/TIMESHEET_ANOMALY/FLOW_GENERATOR
    biz_type          VARCHAR(32),                             -- PROJECT/OPPORTUNITY/TIMESHEET/STAFF
    biz_id            VARCHAR(20),
    biz_ref           VARCHAR(256),
    span_name         VARCHAR(64)   NOT NULL,                  -- AGENT_START/STEP_START/LLM_THOUGHT/LLM_ACTION/TOOL_OBSERVATION/FINAL_ANSWER/STEP_END/AGENT_END/AGENT_ERROR
    step_index        SMALLINT      NOT NULL DEFAULT 0,        -- ReAct 步骤序号（1-based，非 ReAct 节点为 0）
    status            VARCHAR(16)   NOT NULL DEFAULT 'SUCCESS', -- SUCCESS/FAILED
    input_data        TEXT,                                    -- 输入数据 JSON
    output_data       TEXT,                                    -- 输出数据 JSON
    error_msg         TEXT,                                    -- 错误信息（status=FAILED 时填）
    cost_ms           BIGINT       NOT NULL DEFAULT 0,         -- 本 span 耗时（毫秒）
    provider_trace_id VARCHAR(64)   NOT NULL DEFAULT '',       -- 第三方大模型 provider trace ID
    tenant_id         VARCHAR(20)   NOT NULL DEFAULT '1',
    created_by        VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_pat_span_status   CHECK (status IN ('SUCCESS','FAILED')),
    CONSTRAINT ck_pat_step_nonneg   CHECK (step_index >= 0),
    CONSTRAINT ck_pat_cost_nonneg   CHECK (cost_ms >= 0),
    CONSTRAINT ck_pat_deleted_enum  CHECK (deleted IN (0, 1))
);
COMMENT ON TABLE  pmis_agent_trace IS 'Agent 全链路 Tracing 表: 记录每个 Agent 执行的关键节点 span，按 trace_id 串联完整链路';
COMMENT ON COLUMN pmis_agent_trace.trace_id IS '链路 ID: 与 AgentContext.traceId / Brave traceId 对齐，便于跨模块查询';
COMMENT ON COLUMN pmis_agent_trace.span_id IS 'Span ID: 本 span 唯一标识（雪花算法）';
COMMENT ON COLUMN pmis_agent_trace.parent_span_id IS '父 Span ID: 树形结构，AGENT_START 为根 span（null）';
COMMENT ON COLUMN pmis_agent_trace.agent_type IS 'Agent 类型: RISK_WARNING/RESOURCE_RECOMMEND/PROFIT_FORECAST/WIN_RATE_PREDICT/TIMESHEET_ANOMALY/FLOW_GENERATOR';
COMMENT ON COLUMN pmis_agent_trace.biz_type IS '业务类型: PROJECT/OPPORTUNITY/TIMESHEET/STAFF';
COMMENT ON COLUMN pmis_agent_trace.biz_id IS '业务对象 ID';
COMMENT ON COLUMN pmis_agent_trace.biz_ref IS '业务对象引用';
COMMENT ON COLUMN pmis_agent_trace.span_name IS 'Span 名称: AGENT_START/STEP_START/LLM_THOUGHT/LLM_ACTION/TOOL_OBSERVATION/FINAL_ANSWER/STEP_END/AGENT_END/AGENT_ERROR';
COMMENT ON COLUMN pmis_agent_trace.step_index IS 'ReAct 步骤序号: 1-based，非 ReAct 节点（如 AGENT_START/AGENT_END）为 0';
COMMENT ON COLUMN pmis_agent_trace.status IS 'Span 状态: SUCCESS 成功 / FAILED 失败';
COMMENT ON COLUMN pmis_agent_trace.input_data IS '输入数据 JSON: 本 span 的输入快照';
COMMENT ON COLUMN pmis_agent_trace.output_data IS '输出数据 JSON: 本 span 的输出快照';
COMMENT ON COLUMN pmis_agent_trace.error_msg IS '错误信息: status=FAILED 时填异常 message';
COMMENT ON COLUMN pmis_agent_trace.cost_ms IS 'Span 耗时(毫秒)';
COMMENT ON COLUMN pmis_agent_trace.provider_trace_id IS '第三方大模型 provider trace ID: 用于与 LLM 厂商账单核对';
COMMENT ON COLUMN pmis_agent_trace.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_agent_trace.deleted IS '逻辑删除: 0=未删除,1=已删除';

-- =====================================================
-- 6.3 Agent 租户级 Token 限额与计费表（P2-4）
--    pmis_agent_token_quota   : 租户月度配额（一行 = 一个租户一个月）
--    pmis_agent_token_usage_log: 每次大模型调用的 token 使用明细
-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_agent_token_quota(
    id                  VARCHAR(20)   PRIMARY KEY,
    tenant_id           VARCHAR(20)   NOT NULL,                  -- 租户 ID
    quota_month         VARCHAR(6)    NOT NULL,                  -- 配额月份 YYYYMM（如 202607）
    total_quota         BIGINT        NOT NULL DEFAULT 1000000,  -- 月度配额上限（token 数）
    used_tokens         BIGINT        NOT NULL DEFAULT 0,        -- 已使用 token 数
    status              VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE', -- ACTIVE/RUNOUT/RESET
    reset_at            TIMESTAMPTZ,                             -- 上次重置时间
    created_by          VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_patq_tenant_month   UNIQUE (tenant_id, quota_month, deleted),
    CONSTRAINT ck_patq_status_enum    CHECK (status IN ('ACTIVE','RUNOUT','RESET')),
    CONSTRAINT ck_patq_quota_nonneg   CHECK (total_quota >= 0),
    CONSTRAINT ck_patq_used_nonneg    CHECK (used_tokens >= 0),
    CONSTRAINT ck_patq_deleted_enum   CHECK (deleted IN (0, 1))
);
COMMENT ON TABLE  pmis_agent_token_quota IS 'Agent 租户级 Token 配额表: 按月统计每个租户的 LLM token 消耗';
COMMENT ON COLUMN pmis_agent_token_quota.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_agent_token_quota.quota_month IS '配额月份 YYYYMM: 202607 表示 2026 年 7 月';
COMMENT ON COLUMN pmis_agent_token_quota.total_quota IS '月度配额上限: token 数，默认 100 万';
COMMENT ON COLUMN pmis_agent_token_quota.used_tokens IS '已使用 token 数: 累计值，重置时归零';
COMMENT ON COLUMN pmis_agent_token_quota.status IS '配额状态: ACTIVE 正常 / RUNOUT 已耗尽 / RESET 已重置';
COMMENT ON COLUMN pmis_agent_token_quota.reset_at IS '上次重置时间: 手动或月初自动重置时记录';

CREATE TABLE IF NOT EXISTS pmis_agent_token_usage_log(
    id                  VARCHAR(20)   PRIMARY KEY,
    tenant_id           VARCHAR(20)   NOT NULL,                  -- 租户 ID
    trace_id            VARCHAR(64)   NOT NULL,                  -- 链路 ID（与 pmis_agent_trace 对齐）
    agent_type          VARCHAR(32),                             -- Agent 类型
    provider            VARCHAR(64)   NOT NULL,                  -- LLM Provider 名称
    model               VARCHAR(64),                             -- 模型名称（如 gpt-4o / qwen-max）
    biz_ref             VARCHAR(256),                            -- 业务引用
    prompt_tokens       INTEGER       NOT NULL DEFAULT 0,       -- 输入 token 数
    completion_tokens   INTEGER       NOT NULL DEFAULT 0,        -- 输出 token 数
    total_tokens        INTEGER       NOT NULL DEFAULT 0,        -- 总 token 数
    cost_ms             BIGINT        NOT NULL DEFAULT 0,        -- 调用耗时（毫秒）
    caller_id           VARCHAR(20),                             -- 调用人 ID
    caller_name         VARCHAR(64),                             -- 调用人姓名
    created_by          VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_patul_tokens_nonneg CHECK (prompt_tokens >= 0 AND completion_tokens >= 0 AND total_tokens >= 0),
    CONSTRAINT ck_patul_cost_nonneg   CHECK (cost_ms >= 0),
    CONSTRAINT ck_patul_deleted_enum  CHECK (deleted IN (0, 1))
);
COMMENT ON TABLE  pmis_agent_token_usage_log IS 'Agent Token 使用明细表: 每次大模型调用的 token 消耗记录';
COMMENT ON COLUMN pmis_agent_token_usage_log.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_agent_token_usage_log.trace_id IS '链路 ID: 与 pmis_agent_trace.trace_id 对齐';
COMMENT ON COLUMN pmis_agent_token_usage_log.provider IS 'LLM Provider 名称: mock/spring-ai-openai/dashscope/qianfan';
COMMENT ON COLUMN pmis_agent_token_usage_log.prompt_tokens IS '输入 token 数: 通过 TokenCounter 估算';
COMMENT ON COLUMN pmis_agent_token_usage_log.completion_tokens IS '输出 token 数: 通过 TokenCounter 估算';
COMMENT ON COLUMN pmis_agent_token_usage_log.total_tokens IS '总 token 数: prompt + completion';

CREATE TABLE IF NOT EXISTS pmis_agent_knowledge_base(
    id              VARCHAR(20)   NOT NULL,
    tenant_id       VARCHAR(20)   NOT NULL DEFAULT '1',
    name            VARCHAR(128)  NOT NULL,
    description     VARCHAR(512),
    status          VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    doc_count       INT           NOT NULL DEFAULT 0,
    chunk_count     INT           NOT NULL DEFAULT 0,
    embedding_model VARCHAR(64)   NOT NULL DEFAULT 'mock',
    embedding_dim   INT           NOT NULL DEFAULT 1536,
    -- 审计字段
    created_by      VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_pakb_tenant_name UNIQUE (tenant_id, name, deleted)
);
COMMENT ON TABLE  pmis_agent_knowledge_base IS 'Agent RAG 知识库表: 按租户隔离的知识库元数据';
COMMENT ON COLUMN pmis_agent_knowledge_base.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_agent_knowledge_base.name IS '知识库名称: 同租户下唯一';
COMMENT ON COLUMN pmis_agent_knowledge_base.status IS '状态: ACTIVE 可用 / ARCHIVED 归档';
COMMENT ON COLUMN pmis_agent_knowledge_base.doc_count IS '文档数量: 冗余字段，文档增删时同步更新';
COMMENT ON COLUMN pmis_agent_knowledge_base.chunk_count IS '分块数量: 冗余字段，入库/删除时同步更新';
COMMENT ON COLUMN pmis_agent_knowledge_base.embedding_model IS 'Embedding 模型: mock/dashscope/qianfan/openai';
COMMENT ON COLUMN pmis_agent_knowledge_base.embedding_dim IS '向量维度: 与 embedding_model 对齐（mock=8, dashscope=1536, openai=1536）';

CREATE TABLE IF NOT EXISTS pmis_agent_document(
    id                  VARCHAR(20)   NOT NULL,
    tenant_id           VARCHAR(20)   NOT NULL DEFAULT '1',
    knowledge_base_id   VARCHAR(20)   NOT NULL,
    name                VARCHAR(256)  NOT NULL,
    source_type         VARCHAR(16)   NOT NULL DEFAULT 'TEXT',
    source_uri          VARCHAR(1024),
    content             TEXT,
    chunk_count         INT           NOT NULL DEFAULT 0,
    total_tokens        INT           NOT NULL DEFAULT 0,
    status              VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    -- 审计字段
    created_by          VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_pad_knowledge_base
        FOREIGN KEY (knowledge_base_id)
        REFERENCES pmis_agent_knowledge_base(id)
        ON DELETE CASCADE
);
COMMENT ON TABLE  pmis_agent_document IS 'Agent RAG 文档表: 知识库中的文档元数据与原始内容';
COMMENT ON COLUMN pmis_agent_document.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_agent_document.knowledge_base_id IS '所属知识库 ID';
COMMENT ON COLUMN pmis_agent_document.name IS '文档名称';
COMMENT ON COLUMN pmis_agent_document.source_type IS '来源类型: TEXT/MARKDOWN/URL/PDF/DOCX';
COMMENT ON COLUMN pmis_agent_document.source_uri IS '来源 URI: URL 或文件路径';
COMMENT ON COLUMN pmis_agent_document.content IS '原始内容: 纯文本（PDF/DOCX 需先提取文本）';
COMMENT ON COLUMN pmis_agent_document.chunk_count IS '分块数量: 入库时统计';
COMMENT ON COLUMN pmis_agent_document.total_tokens IS '文档总 token 数: 入库时估算';
COMMENT ON COLUMN pmis_agent_document.status IS '状态: PENDING 待处理 / INGESTED 已入库 / FAILED 入库失败';

CREATE TABLE IF NOT EXISTS pmis_agent_document_chunk(
    id                  VARCHAR(20)   NOT NULL,
    tenant_id           VARCHAR(20)   NOT NULL DEFAULT '1',
    knowledge_base_id   VARCHAR(20)   NOT NULL,
    document_id         VARCHAR(20)   NOT NULL,
    chunk_index         INT           NOT NULL,
    content             TEXT          NOT NULL,
    -- pgvector 向量字段；维度由知识库 embedding_dim 决定
    -- 默认 1536 维（DashScope/OpenAI text-embedding 标准），mock 用 8 维
    embedding           vector(1536),
    token_count         INT           NOT NULL DEFAULT 0,
    -- 审计字段
    created_by          VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_padc_document
        FOREIGN KEY (document_id)
        REFERENCES pmis_agent_document(id)
        ON DELETE CASCADE,
    CONSTRAINT uk_padc_doc_chunk UNIQUE (document_id, chunk_index)
);
COMMENT ON TABLE  pmis_agent_document_chunk IS 'Agent RAG 文档分块表: 向量检索核心表（pgvector）';
COMMENT ON COLUMN pmis_agent_document_chunk.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_agent_document_chunk.knowledge_base_id IS '所属知识库 ID';
COMMENT ON COLUMN pmis_agent_document_chunk.document_id IS '所属文档 ID';
COMMENT ON COLUMN pmis_agent_document_chunk.chunk_index IS '分块序号: 同文档内从 0 开始递增';
COMMENT ON COLUMN pmis_agent_document_chunk.content IS '分块文本内容';
COMMENT ON COLUMN pmis_agent_document_chunk.embedding IS '向量: pgvector 类型，由 EmbeddingProvider 生成';
COMMENT ON COLUMN pmis_agent_document_chunk.token_count IS '分块 token 数: 入库时估算';

-- =====================================================
-- P3-2: DAG 编排引擎（pmis_agent_dag_*）
--   pmis_agent_dag_definition    : DAG 定义（节点列表以 JSON 存储）
--   pmis_agent_dag_instance      : DAG 执行实例（整体状态/汇总）
--   pmis_agent_dag_node_instance : 节点执行实例（明细/输出/错误）
-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_agent_dag_definition(
    id                 VARCHAR(20)   NOT NULL,
    tenant_id          VARCHAR(20)   NOT NULL DEFAULT '1',
    name               VARCHAR(200)  NOT NULL,
    description        VARCHAR(1000),
    biz_type           VARCHAR(100),
    version            VARCHAR(50),
    definition_json    TEXT          NOT NULL,
    failure_strategy   VARCHAR(20)   NOT NULL DEFAULT 'ABORT',
    max_retries        INT           NOT NULL DEFAULT 3,
    default_timeout_ms BIGINT        NOT NULL DEFAULT 0,
    enabled            SMALLINT      NOT NULL DEFAULT 1,
    created_by         VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_dag_def_tenant_name UNIQUE (tenant_id, name, deleted)
);

COMMENT ON TABLE  pmis_agent_dag_definition IS 'Agent DAG 定义表: 持久化多智能体编排流程';
COMMENT ON COLUMN pmis_agent_dag_definition.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_agent_dag_definition.name IS 'DAG 名称: 同租户下唯一';
COMMENT ON COLUMN pmis_agent_dag_definition.description IS 'DAG 描述';
COMMENT ON COLUMN pmis_agent_dag_definition.biz_type IS '业务类型: RISK_ASSESS/BUDGET_APPROVE 等';
COMMENT ON COLUMN pmis_agent_dag_definition.version IS '版本号: 语义化版本 1.0.0';
COMMENT ON COLUMN pmis_agent_dag_definition.definition_json IS 'DAG 定义 JSON: 节点列表+全局配置，反序列化为 DagDefinition';
COMMENT ON COLUMN pmis_agent_dag_definition.failure_strategy IS '默认失败策略: CONTINUE/ABORT/RETRY';
COMMENT ON COLUMN pmis_agent_dag_definition.max_retries IS '默认最大重试次数: RETRY 策略生效';
COMMENT ON COLUMN pmis_agent_dag_definition.default_timeout_ms IS '默认节点超时(毫秒): 0=不超时';
COMMENT ON COLUMN pmis_agent_dag_definition.enabled IS '是否启用: 1=启用 0=禁用';

CREATE TABLE IF NOT EXISTS pmis_agent_dag_instance(
    id                 VARCHAR(20)   NOT NULL,
    tenant_id          VARCHAR(20)   NOT NULL DEFAULT '1',
    dag_definition_id  VARCHAR(20)   NOT NULL,
    dag_name           VARCHAR(200),
    biz_type           VARCHAR(100),
    biz_ref            VARCHAR(200),
    status             VARCHAR(20)   NOT NULL DEFAULT 'CREATED',
    global_inputs_json TEXT,
    node_outputs_json  TEXT,
    total_cost_ms      BIGINT,
    success_count      INT           NOT NULL DEFAULT 0,
    failed_count       INT           NOT NULL DEFAULT 0,
    skipped_count      INT           NOT NULL DEFAULT 0,
    total_nodes        INT           NOT NULL DEFAULT 0,
    note               VARCHAR(1000),
    created_by         VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_dag_inst_def FOREIGN KEY (dag_definition_id)
        REFERENCES pmis_agent_dag_definition(id),
    CONSTRAINT ck_dag_inst_status CHECK (status IN ('CREATED','RUNNING','SUCCESS','FAILED','CANCELLED','TIMEOUT'))
);

COMMENT ON TABLE  pmis_agent_dag_instance IS 'Agent DAG 执行实例表: 记录每次执行的整体状态';
COMMENT ON COLUMN pmis_agent_dag_instance.status IS '实例状态: CREATED/RUNNING/SUCCESS/FAILED/CANCELLED/TIMEOUT';
COMMENT ON COLUMN pmis_agent_dag_instance.global_inputs_json IS '全局输入参数 JSON';
COMMENT ON COLUMN pmis_agent_dag_instance.node_outputs_json IS '节点输出汇总 JSON';
COMMENT ON COLUMN pmis_agent_dag_instance.total_cost_ms IS '总耗时(毫秒)';
COMMENT ON COLUMN pmis_agent_dag_instance.note IS '备注: 如中止原因';

CREATE TABLE IF NOT EXISTS pmis_agent_dag_node_instance(
    id                 VARCHAR(20)   NOT NULL,
    tenant_id          VARCHAR(20)   NOT NULL DEFAULT '1',
    dag_instance_id    VARCHAR(20)   NOT NULL,
    node_name          VARCHAR(200)  NOT NULL,
    agent_type         VARCHAR(100),
    status             VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    output_json        TEXT,
    error_message      VARCHAR(2000),
    retry_count        INT           NOT NULL DEFAULT 0,
    start_time         TIMESTAMP,
    end_time           TIMESTAMP,
    created_by         VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_dag_node_inst FOREIGN KEY (dag_instance_id)
        REFERENCES pmis_agent_dag_instance(id),
    CONSTRAINT ck_dag_node_status CHECK (status IN ('PENDING','RUNNING','SUCCESS','FAILED','SKIPPED'))
);

COMMENT ON TABLE  pmis_agent_dag_node_instance IS 'Agent DAG 节点实例表: 每个节点一次执行的明细';
COMMENT ON COLUMN pmis_agent_dag_node_instance.dag_instance_id IS '所属 DAG 实例 ID';
COMMENT ON COLUMN pmis_agent_dag_node_instance.node_name IS '节点名: DAG 内唯一';
COMMENT ON COLUMN pmis_agent_dag_node_instance.agent_type IS '关联 Agent 类型: 为空表示空节点';
COMMENT ON COLUMN pmis_agent_dag_node_instance.status IS '节点状态: PENDING/RUNNING/SUCCESS/FAILED/SKIPPED';
COMMENT ON COLUMN pmis_agent_dag_node_instance.output_json IS '节点输出 JSON';
COMMENT ON COLUMN pmis_agent_dag_node_instance.error_message IS '错误消息';
COMMENT ON COLUMN pmis_agent_dag_node_instance.retry_count IS '已重试次数';

-- =====================================================
-- 6.5 Agent HITL 人工审批请求表 pmis_agent_hitl_approval（P3-4）
--     当 ReAct 循环遇到需人工审批的工具时创建审批请求并暂停，
--     审批通过后通过 snapshot_json 恢复循环。
-- =====================================================
CREATE TABLE IF NOT EXISTS pmis_agent_hitl_approval(
    id               VARCHAR(20)   PRIMARY KEY,
    tenant_id        VARCHAR(20)   NOT NULL DEFAULT '1',
    trace_id        VARCHAR(64)   NOT NULL DEFAULT '',
    agent_type      VARCHAR(32)   NOT NULL,
    biz_type        VARCHAR(32),
    biz_id          VARCHAR(20),
    biz_ref         VARCHAR(256),
    tool_name       VARCHAR(128)   NOT NULL,
    parameters_json TEXT,
    description     VARCHAR(512),
    status          VARCHAR(16)   NOT NULL DEFAULT 'PENDING',  -- PENDING/APPROVED/REJECTED/TIMEOUT/CANCELLED
    snapshot_json   TEXT,                                     -- ReAct 循环快照 JSON（用于恢复）
    requester_id    VARCHAR(20),
    requester_name  VARCHAR(64),
    approver_id     VARCHAR(20),
    approver_name   VARCHAR(64),
    approver_comment TEXT,
    timeout_at      TIMESTAMPTZ,
    resolved_at     TIMESTAMPTZ,
    created_by      VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(20)   NOT NULL DEFAULT 'SYSTEM',
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_hitla_status   CHECK (status IN ('PENDING','APPROVED','REJECTED','TIMEOUT','CANCELLED')),
    CONSTRAINT ck_hitla_deleted  CHECK (deleted IN (0, 1))
);
COMMENT ON TABLE  pmis_agent_hitl_approval IS 'Agent HITL 人工审批请求表: ReAct 循环暂停后的审批请求';
COMMENT ON COLUMN pmis_agent_hitl_approval.trace_id IS '链路追踪 ID: 与 AgentContext.traceId 对齐';
COMMENT ON COLUMN pmis_agent_hitl_approval.agent_type IS 'Agent 类型';
COMMENT ON COLUMN pmis_agent_hitl_approval.tool_name IS '需审批的工具名';
COMMENT ON COLUMN pmis_agent_hitl_approval.parameters_json IS '工具参数 JSON: 审批人查看将要执行的操作';
COMMENT ON COLUMN pmis_agent_hitl_approval.status IS '审批状态: PENDING/APPROVED/REJECTED/TIMEOUT/CANCELLED';
COMMENT ON COLUMN pmis_agent_hitl_approval.snapshot_json IS 'ReAct 循环快照 JSON: 审批后用于恢复执行';
COMMENT ON COLUMN pmis_agent_hitl_approval.requester_id IS '请求人 ID: 触发 Agent 的用户';
COMMENT ON COLUMN pmis_agent_hitl_approval.approver_id IS '审批人 ID';
COMMENT ON COLUMN pmis_agent_hitl_approval.approver_comment IS '审批意见: 批准/拒绝理由';
COMMENT ON COLUMN pmis_agent_hitl_approval.timeout_at IS '审批超时时间: 超过此时间自动标记 TIMEOUT';
COMMENT ON COLUMN pmis_agent_hitl_approval.resolved_at IS '审批结果时间: 批准/拒绝/超时/取消的时间';
