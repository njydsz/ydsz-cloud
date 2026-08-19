-- Outbox 事件表 DDL（P0-2：事务性 Outbox 事件模式）
-- 用于保证业务操作与事件发布的原子性

CREATE TABLE IF NOT EXISTS ydsz_job_outbox (
    id              BIGSERIAL PRIMARY KEY,
    event_key       VARCHAR(255) NOT NULL UNIQUE,       -- 事件唯一键（幂等去重）
    event_type      VARCHAR(50) NOT NULL,               -- 事件类型枚举
    topic           VARCHAR(50) NOT NULL,               -- 投递目标主题（webhook / metrics / audit）
    payload         TEXT,                               -- 事件负载 JSON
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- 发布状态（PENDING / PUBLISHED / DEAD）
    retry_count     INT NOT NULL DEFAULT 0,             -- 已重试次数
    next_retry_time TIMESTAMP NOT NULL DEFAULT NOW(),   -- 下次重试时间
    create_time     TIMESTAMP NOT NULL DEFAULT NOW(),   -- 创建时间
    update_time     TIMESTAMP NOT NULL DEFAULT NOW()    -- 更新时间
);

-- 索引：加速待发布事件查询
CREATE INDEX IF NOT EXISTS idx_outbox_pending ON ydsz_job_outbox (status, next_retry_time, create_time);

-- 索引：加速历史数据清理
CREATE INDEX IF NOT EXISTS idx_outbox_published ON ydsz_job_outbox (status, update_time);

-- 索引：加速按 topic 查询
CREATE INDEX IF NOT EXISTS idx_outbox_topic ON ydsz_job_outbox (topic);

COMMENT ON TABLE ydsz_job_outbox IS 'Outbox 事件表：保证业务操作与事件发布的原子性';
COMMENT ON COLUMN ydsz_job_outbox.event_key IS '事件唯一键（幂等去重，如 jobKey + logId + eventType）';
COMMENT ON COLUMN ydsz_job_outbox.event_type IS '事件类型（JOB_STARTED/JOB_SUCCESS/JOB_FAILED/JOB_TIMEOUT/DAG_STARTED/DAG_COMPLETED）';
COMMENT ON COLUMN ydsz_job_outbox.topic IS '投递目标主题（webhook/metrics/audit）';
COMMENT ON COLUMN ydsz_job_outbox.status IS '发布状态（PENDING/PUBLISHED/DEAD）';
COMMENT ON COLUMN ydsz_job_outbox.retry_count IS '已重试次数';
COMMENT ON COLUMN ydsz_job_outbox.next_retry_time IS '下次重试时间（指数退避）';
