-- ============================================================================
-- 模块：ydsz-agent
-- 说明：异步任务持久化表 ydsz_agt_async_task
-- 日期：2026-09-17
-- @author ydsz-team
-- ============================================================================

CREATE TABLE IF NOT EXISTS ydsz_agt_async_task (
    id                       VARCHAR(32)              NOT NULL,
    task_type                VARCHAR(64)              NOT NULL,
    status                   VARCHAR(32)              NOT NULL DEFAULT 'PENDING',
    tenant_code              VARCHAR(64)              DEFAULT NULL,
    user_id                  VARCHAR(64)              DEFAULT NULL,
    input_payload            TEXT                     DEFAULT NULL,
    output_payload           TEXT                     DEFAULT NULL,
    error_message            VARCHAR(1024)            DEFAULT NULL,
    progress_percent         INTEGER                  NOT NULL DEFAULT 0,
    retry_count              INTEGER                  NOT NULL DEFAULT 0,
    max_retry                INTEGER                  NOT NULL DEFAULT 3,
    next_retry_at            TIMESTAMP                DEFAULT NULL,
    timeout_seconds          BIGINT                   DEFAULT NULL,
    worker_id                VARCHAR(128)             DEFAULT NULL,
    started_at               TIMESTAMP                DEFAULT NULL,
    completed_at             TIMESTAMP                DEFAULT NULL,
    expire_at                TIMESTAMP                DEFAULT NULL,
    is_deleted               BOOLEAN                  NOT NULL DEFAULT FALSE,
    revision                 INTEGER                  NOT NULL DEFAULT 0,
    created_at               TIMESTAMP                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by               VARCHAR(64)              DEFAULT NULL,
    updated_by               VARCHAR(64)              DEFAULT NULL,

    CONSTRAINT pk_ydsz_agt_async_task PRIMARY KEY (id)
);

COMMENT ON TABLE ydsz_agt_async_task IS '异步任务持久化表';
COMMENT ON COLUMN ydsz_agt_async_task.id IS '主键 ID（Snowflake）';
COMMENT ON COLUMN ydsz_agt_async_task.task_type IS '任务类型编码（REPORT_GENERATE/DOC_INGEST/BATCH_CHAT/CODE_EXECUTION 等）';
COMMENT ON COLUMN ydsz_agt_async_task.status IS '任务状态（PENDING/RUNNING/SUCCEEDED/FAILED/CANCELED/EXPIRED）';
COMMENT ON COLUMN ydsz_agt_async_task.tenant_code IS '租户编码（多租户隔离）';
COMMENT ON COLUMN ydsz_agt_async_task.user_id IS '触发用户 ID';
COMMENT ON COLUMN ydsz_agt_async_task.input_payload IS '任务输入参数（JSON 字符串）';
COMMENT ON COLUMN ydsz_agt_async_task.output_payload IS '任务执行结果（JSON 字符串，完成后非空）';
COMMENT ON COLUMN ydsz_agt_async_task.error_message IS '失败原因（状态为 FAILED 时非空）';
COMMENT ON COLUMN ydsz_agt_async_task.progress_percent IS '当前进度百分比（0-100）';
COMMENT ON COLUMN ydsz_agt_async_task.retry_count IS '已重试次数';
COMMENT ON COLUMN ydsz_agt_async_task.max_retry IS '最大重试次数';
COMMENT ON COLUMN ydsz_agt_async_task.next_retry_at IS '下次重试时间';
COMMENT ON COLUMN ydsz_agt_async_task.timeout_seconds IS '执行超时时间（秒）';
COMMENT ON COLUMN ydsz_agt_async_task.worker_id IS 'Worker 节点标识（执行此任务的服务实例）';
COMMENT ON COLUMN ydsz_agt_async_task.started_at IS '任务开始执行时间';
COMMENT ON COLUMN ydsz_agt_async_task.completed_at IS '任务完成时间';
COMMENT ON COLUMN ydsz_agt_async_task.expire_at IS '任务过期时间（超时未完成则自动释放）';
COMMENT ON COLUMN ydsz_agt_async_task.is_deleted IS '逻辑删除标识（TRUE=已删除，FALSE=未删除）';
COMMENT ON COLUMN ydsz_agt_async_task.revision IS '乐观锁版本号';
COMMENT ON COLUMN ydsz_agt_async_task.created_at IS '创建时间';
COMMENT ON COLUMN ydsz_agt_async_task.updated_at IS '最后更新时间';
COMMENT ON COLUMN ydsz_agt_async_task.created_by IS '创建人';
COMMENT ON COLUMN ydsz_agt_async_task.updated_by IS '最后更新人';

-- ============================================================================
-- 索引
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_ydsz_agt_async_task_status_type
    ON ydsz_agt_async_task (status, task_type);

CREATE INDEX IF NOT EXISTS idx_ydsz_agt_async_task_tenant_status
    ON ydsz_agt_async_task (tenant_code, status);

CREATE INDEX IF NOT EXISTS idx_ydsz_agt_async_task_expire_at
    ON ydsz_agt_async_task (expire_at);

CREATE INDEX IF NOT EXISTS idx_ydsz_agt_async_task_created_at
    ON ydsz_agt_async_task (created_at ASC);

-- ============================================================================
-- 自动更新 updated_at 触发器（PostgreSQL）
-- ============================================================================

CREATE OR REPLACE FUNCTION fn_ydsz_agt_async_task_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_ydsz_agt_async_task_updated_at ON ydsz_agt_async_task;
CREATE TRIGGER trg_ydsz_agt_async_task_updated_at
    BEFORE UPDATE ON ydsz_agt_async_task
    FOR EACH ROW
    EXECUTE FUNCTION fn_ydsz_agt_async_task_set_updated_at();
