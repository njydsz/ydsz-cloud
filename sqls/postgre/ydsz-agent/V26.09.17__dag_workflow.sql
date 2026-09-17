-- ============================================================================
-- V26.09.17: DAG 工作流持久化表（支持可视化编辑器的保存/加载）
-- ============================================================================

CREATE TABLE IF NOT EXISTS ydsz_agt_dag_workflow (
    id              VARCHAR(32) PRIMARY KEY,
    workflow_code   VARCHAR(64) NOT NULL UNIQUE,
    workflow_name   VARCHAR(128) NOT NULL,
    description     TEXT,
    dsl_content     TEXT NOT NULL,
    layout_json     TEXT,
    category        VARCHAR(64),
    is_published    BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    created_by      VARCHAR(64),
    updated_by      VARCHAR(64),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_dag_wf_code ON ydsz_agt_dag_workflow(workflow_code);
CREATE INDEX IF NOT EXISTS idx_dag_wf_category ON ydsz_agt_dag_workflow(category);
CREATE INDEX IF NOT EXISTS idx_dag_wf_published ON ydsz_agt_dag_workflow(is_published);

-- 注释
COMMENT ON TABLE ydsz_agt_dag_workflow IS 'DAG 工作流（持久化 DSL 供可视化编辑器使用）';
COMMENT ON COLUMN ydsz_agt_dag_workflow.workflow_code IS '工作流业务唯一编码';
COMMENT ON COLUMN ydsz_agt_dag_workflow.dsl_content IS 'YAML DSL 编排脚本';
COMMENT ON COLUMN ydsz_agt_dag_workflow.layout_json IS '可视化布局 JSON（节点坐标等前端状态）';
COMMENT ON COLUMN ydsz_agt_dag_workflow.is_published IS '是否已发布（true 后不可直接编辑，需走版本管理）';
