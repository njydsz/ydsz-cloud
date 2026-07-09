-- P1-8: DAG 工作流版本管理表
-- 存储 DAG 定义的每次变更快照，支持版本历史查看、差异对比和回滚

CREATE TABLE IF NOT EXISTS pmis_job_dag_version (
    id              VARCHAR(32)   NOT NULL,
    dag_id          VARCHAR(32)   NOT NULL,
    dag_key         VARCHAR(128)  NOT NULL,
    version         INTEGER       NOT NULL,
    dag_definition  TEXT          NOT NULL,
    dag_name        VARCHAR(255),
    trigger_type    VARCHAR(32),
    cron_expression VARCHAR(128),
    fail_strategy   VARCHAR(64),
    remark          VARCHAR(512),
    changed_by      VARCHAR(64),

    -- BaseDO 公共字段
    created_by      VARCHAR(64),
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(64),
    updated_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT      DEFAULT 0,
    tenant_id       VARCHAR(64),

    PRIMARY KEY (id)
);

-- 版本查询索引（按 dag_id 查版本历史）
CREATE INDEX IF NOT EXISTS idx_dag_version_dag_id ON pmis_job_dag_version (dag_id, version DESC);

-- 按 dag_id + version 精确查询索引
CREATE UNIQUE INDEX IF NOT EXISTS uk_dag_version_dag_id_version ON pmis_job_dag_version (dag_id, version);

COMMENT ON TABLE pmis_job_dag_version IS 'DAG 工作流版本历史表（P1-8 版本管理）';
COMMENT ON COLUMN pmis_job_dag_version.dag_id IS 'DAG ID（关联 pmis_job_dag.id）';
COMMENT ON COLUMN pmis_job_dag_version.version IS '版本号（从 1 递增）';
COMMENT ON COLUMN pmis_job_dag_version.dag_definition IS 'DAG 定义 JSON 快照';
COMMENT ON COLUMN pmis_job_dag_version.remark IS '版本备注（如"新增节点A"、"修改条件分支"）';
COMMENT ON COLUMN pmis_job_dag_version.changed_by IS '变更操作人';
