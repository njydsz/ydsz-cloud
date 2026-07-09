-- =============================================================================
-- V1.3.0 数据库迁移: 添加链路追踪相关表与索引
-- -----------------------------------------------------------------------------
-- 变更内容:
--   1. 添加 api_access_log 表 (API 访问日志, 配合 SkyWalking trace_id)
--   2. 添加 flyway_schema_history 索引优化
--   3. 为核心业务表添加 trace_id 字段 (可选, 用于关联日志)
-- =============================================================================

-- API 访问日志表 (配合 SkyWalking 链路追踪使用)
CREATE TABLE IF NOT EXISTS api_access_log (
    id              BIGSERIAL       PRIMARY KEY,
    trace_id        VARCHAR(64)     NOT NULL,
    service_name    VARCHAR(100)    NOT NULL,
    method          VARCHAR(10)     NOT NULL,
    uri             VARCHAR(500)    NOT NULL,
    status_code     INTEGER,
    duration_ms     BIGINT,
    user_id         BIGINT,
    client_ip       VARCHAR(50),
    user_agent      VARCHAR(500),
    request_body    TEXT,
    response_body   TEXT,
    error_message   TEXT,
    created_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

-- 索引: 按 trace_id 查询 (链路追踪关联)
CREATE INDEX IF NOT EXISTS idx_api_access_log_trace_id
    ON api_access_log (trace_id);

-- 索引: 按时间范围查询 (日志清理与统计)
CREATE INDEX IF NOT EXISTS idx_api_access_log_created_at
    ON api_access_log (created_at);

-- 索引: 按服务名查询
CREATE INDEX IF NOT EXISTS idx_api_access_log_service
    ON api_access_log (service_name);

-- 索引: 按 URI 查询 (API 慢请求分析)
CREATE INDEX IF NOT EXISTS idx_api_access_log_uri
    ON api_access_log (uri);

-- 评论: 此表数据量增长较快, 建议配合定时任务定期清理 (保留 30 天)
-- 清理脚本: deploy/scripts/cleanup-api-logs.sh
