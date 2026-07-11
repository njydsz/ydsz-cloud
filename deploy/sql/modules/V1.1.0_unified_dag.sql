-- ============================================================
-- PMIS V1.1.0 统一 DAG 引擎 — 数据表迁移
-- 将 cronjob 的 pmis_job_dag_* 和 agent 的 pmis_agent_dag_* 统一为 pmis_dag_*
-- ============================================================

-- ====================================================================
-- [001] 统一 DAG 定义表
-- ====================================================================
CREATE TABLE IF NOT EXISTS pmis_dag_definition (
    id                  VARCHAR(20)       PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    name                VARCHAR(128)      NOT NULL,
    description         VARCHAR(512),
    biz_type            VARCHAR(64),                            -- 业务类型（RISK_ASSESS / JOB_PIPELINE 等）
    engine_type         VARCHAR(16)       NOT NULL DEFAULT 'AGENT', -- 引擎类型: AGENT / CRONJOB
    version             VARCHAR(32)       NOT NULL DEFAULT '1.0.0',
    definition_json     TEXT              NOT NULL,              -- 完整 DAG 定义 JSON（含 nodes / edges / config）
    failure_strategy    VARCHAR(32)       NOT NULL DEFAULT 'ABORT', -- ABORT / CONTINUE / RETRY / SKIP_SUBSEQUENT
    max_retries         INTEGER           NOT NULL DEFAULT 3,
    default_timeout_ms  BIGINT            NOT NULL DEFAULT 0,
    enabled             SMALLINT          NOT NULL DEFAULT 1,
    -- 审计字段
    created_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT          NOT NULL DEFAULT 0,
    provider_trace_id   VARCHAR(64),
    lock_version        INTEGER           NOT NULL DEFAULT 0,
    -- 约束
    CONSTRAINT uk_pdd_tenant_name_engine UNIQUE (tenant_id, name, engine_type, deleted),
    CONSTRAINT ck_pdd_engine_type CHECK (engine_type IN ('AGENT', 'CRONJOB')),
    CONSTRAINT ck_pdd_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_pdd_deleted CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_dag_definition IS 'V1.1.0 统一 DAG 定义表 — 替代 pmis_job_dag / pmis_agent_dag_definition';

COMMENT ON COLUMN pmis_dag_definition.engine_type IS '引擎类型: AGENT（多智能体编排）/ CRONJOB（定时任务流水线）';

COMMENT ON COLUMN pmis_dag_definition.definition_json IS '完整 DAG 定义 JSON（节点列表、边、全局配置）';

CREATE INDEX IF NOT EXISTS idx_pdd_tenant_engine_enabled
    ON pmis_dag_definition (tenant_id, engine_type, enabled)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pdd_trace
    ON pmis_dag_definition (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- ====================================================================
-- [002] 统一 DAG 实例表
-- ====================================================================
CREATE TABLE IF NOT EXISTS pmis_dag_instance (
    id                  VARCHAR(20)       PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    definition_id       VARCHAR(20)       NOT NULL,
    definition_version  VARCHAR(32),
    engine_type         VARCHAR(16)       NOT NULL DEFAULT 'AGENT',
    instance_code       VARCHAR(64)       NOT NULL,
    status              VARCHAR(16)       NOT NULL DEFAULT 'PENDING', -- PENDING / RUNNING / SUCCESS / FAILED / CANCELLED
    triggered_by        VARCHAR(20),
    trigger_reason      VARCHAR(256),
    global_inputs       TEXT,                                    -- 全局输入 JSON
    global_outputs      TEXT,                                    -- 全局输出 JSON
    started_at          TIMESTAMPTZ,
    finished_at         TIMESTAMPTZ,
    total_cost_ms       BIGINT,
    error_message       TEXT,
    -- 审计字段
    created_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    created_at          TIMESTAMPTZ       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(20)       NOT NULL DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT          NOT NULL DEFAULT 0,
    provider_trace_id   VARCHAR(64),
    lock_version        INTEGER           NOT NULL DEFAULT 0,
    -- 约束
    CONSTRAINT ck_pdi_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_pdi_engine_type CHECK (engine_type IN ('AGENT', 'CRONJOB')),
    CONSTRAINT ck_pdi_deleted CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_dag_instance IS 'V1.1.0 统一 DAG 实例表 — 替代 pmis_job_dag_instance / pmis_agent_dag_instance';

CREATE INDEX IF NOT EXISTS idx_pdi_definition
    ON pmis_dag_instance (definition_id, deleted)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pdi_status
    ON pmis_dag_instance (tenant_id, engine_type, status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pdi_trace
    ON pmis_dag_instance (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- ====================================================================
-- [003] 统一 DAG 节点实例表
-- ====================================================================
CREATE TABLE IF NOT EXISTS pmis_dag_node_instance (
    id                  VARCHAR(20)       PRIMARY KEY DEFAULT left(replace(gen_random_uuid()::text,'-',''),20),
    tenant_id           VARCHAR(20)       NOT NULL DEFAULT '1',
    instance_id         VARCHAR(20)       NOT NULL,
    node_name           VARCHAR(64)       NOT NULL,
    node_type           VARCHAR(64),                            -- 节点类型（agentType / jobKey）
    status              VARCHAR(16)       NOT NULL DEFAULT 'PENDING', -- PENDING / RUNNING / SUCCESS / FAILED / SKIPPED
    node_inputs         TEXT,                                    -- 节点输入 JSON
    node_output         TEXT,                                    -- 节点输出 JSON
    error_message       TEXT,
    retry_count         INTEGER           NOT NULL DEFAULT 0,
    started_at          TIMESTAMPTZ,
    finished_at         TIMESTAMPTZ,
    cost_ms             BIGINT,
    -- 审计字段
    created_at          TIMESTAMPTZ       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT          NOT NULL DEFAULT 0,
    provider_trace_id   VARCHAR(64),
    -- 约束
    CONSTRAINT ck_pdni_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'SKIPPED')),
    CONSTRAINT ck_pdni_deleted CHECK (deleted IN (0, 1))
);

COMMENT ON TABLE pmis_dag_node_instance IS 'V1.1.0 统一 DAG 节点实例表 — 替代 pmis_job_dag_node_instance / pmis_agent_dag_node_instance';

CREATE INDEX IF NOT EXISTS idx_pdni_instance
    ON pmis_dag_node_instance (instance_id, deleted)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pdni_status
    ON pmis_dag_node_instance (instance_id, status)
    WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_pdni_trace
    ON pmis_dag_node_instance (provider_trace_id)
    WHERE provider_trace_id IS NOT NULL;

-- ====================================================================
-- [004] 标记旧表废弃
-- ====================================================================
COMMENT ON TABLE pmis_job_dag IS 'DEPRECATED V1.1.0: 已迁移到 pmis_dag_definition（engine_type=CRONJOB）';
COMMENT ON TABLE pmis_job_dag_version IS 'DEPRECATED V1.1.0: 版本管理已合并到 pmis_dag_definition.lock_version';
COMMENT ON TABLE pmis_job_dag_instance IS 'DEPRECATED V1.1.0: 已迁移到 pmis_dag_instance';
COMMENT ON TABLE pmis_job_dag_node_instance IS 'DEPRECATED V1.1.0: 已迁移到 pmis_dag_node_instance';

COMMENT ON TABLE pmis_agent_dag_definition IS 'DEPRECATED V1.1.0: 已迁移到 pmis_dag_definition（engine_type=AGENT）';
COMMENT ON TABLE pmis_agent_dag_instance IS 'DEPRECATED V1.1.0: 已迁移到 pmis_dag_instance';
COMMENT ON TABLE pmis_agent_dag_node_instance IS 'DEPRECATED V1.1.0: 已迁移到 pmis_dag_node_instance';
