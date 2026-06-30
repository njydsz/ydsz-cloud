-- =====================================================
-- PMIS 审计日志模块 DDL
-- 版本: V1.0.0_008
-- 描述: 操作日志持久化（pmis_log schema）
-- =====================================================

CREATE SCHEMA IF NOT EXISTS pmis_log;

DROP TABLE IF EXISTS pmis_operation_log;
CREATE TABLE pmis_operation_log (
    id                BIGSERIAL PRIMARY KEY,
    module            VARCHAR(64)  NOT NULL,
    action            VARCHAR(128) NOT NULL,
    biz_type          VARCHAR(64),
    biz_id            VARCHAR(64),
    user_id           BIGINT,
    username          VARCHAR(64),
    request_url       VARCHAR(512),
    http_method       VARCHAR(16),
    method_signature  VARCHAR(256),
    client_ip         VARCHAR(64),
    user_agent        VARCHAR(512),
    params_json       TEXT,
    response_json     TEXT,
    status            VARCHAR(16)  NOT NULL,
    error_message     TEXT,
    cost_ms           BIGINT,
    trace_id          VARCHAR(64),
    tenant_id         BIGINT       DEFAULT 1,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE pmis_operation_log IS '操作日志：用户关键操作审计追踪';

CREATE INDEX idx_pol_user ON pmis_operation_log(user_id, created_at DESC);
CREATE INDEX idx_pol_biz ON pmis_operation_log(biz_type, biz_id);
CREATE INDEX idx_pol_status ON pmis_operation_log(status);
CREATE INDEX idx_pol_trace ON pmis_operation_log(trace_id);
CREATE INDEX idx_pol_created ON pmis_operation_log(created_at DESC);
