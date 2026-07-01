-- =====================================================
-- PMIS 审计日志模块 DDL
-- 版本: V1.0.0_008
-- 描述: 操作日志持久化（pmis_log schema）
-- =====================================================

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

COMMENT ON TABLE pmis_operation_log IS '操作审计日志: 异步持久化用户关键操作(模块/动作/参数/响应/异常/IP/UA),等保 2.0 三级要求';
COMMENT ON COLUMN pmis_operation_log.id IS '主键 ID';
COMMENT ON COLUMN pmis_operation_log.module IS '操作模块(如 project/contract/finance)';
COMMENT ON COLUMN pmis_operation_log.action IS '操作动作(如 create/update/delete/approve)';
COMMENT ON COLUMN pmis_operation_log.biz_type IS '业务类型';
COMMENT ON COLUMN pmis_operation_log.biz_id IS '业务单据 ID';
COMMENT ON COLUMN pmis_operation_log.user_id IS '操作用户 ID';
COMMENT ON COLUMN pmis_operation_log.username IS '操作用户名(冗余,避免连表)';
COMMENT ON COLUMN pmis_operation_log.request_url IS '请求 URL';
COMMENT ON COLUMN pmis_operation_log.http_method IS 'HTTP 方法(GET/POST/PUT/DELETE)';
COMMENT ON COLUMN pmis_operation_log.method_signature IS 'Java 方法签名(如 ProjectController#create)';
COMMENT ON COLUMN pmis_operation_log.client_ip IS '客户端 IP';
COMMENT ON COLUMN pmis_operation_log.user_agent IS '浏览器/客户端 User-Agent';
COMMENT ON COLUMN pmis_operation_log.params_json IS '请求参数 JSON(敏感字段脱敏)';
COMMENT ON COLUMN pmis_operation_log.response_json IS '响应数据 JSON(失败时为空)';
COMMENT ON COLUMN pmis_operation_log.status IS '操作状态: SUCCESS 成功 / FAILED 失败';
COMMENT ON COLUMN pmis_operation_log.error_message IS '错误信息(失败时填充堆栈摘要)';
COMMENT ON COLUMN pmis_operation_log.cost_ms IS '接口耗时(毫秒)';
COMMENT ON COLUMN pmis_operation_log.trace_id IS '系统链路追踪 ID(SkyWalking/TLog)';
COMMENT ON COLUMN pmis_operation_log.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_operation_log.created_at IS '操作时间';

CREATE INDEX idx_pol_user ON pmis_operation_log(user_id, created_at DESC);
CREATE INDEX idx_pol_biz ON pmis_operation_log(biz_type, biz_id);
CREATE INDEX idx_pol_status ON pmis_operation_log(status);
CREATE INDEX idx_pol_trace ON pmis_operation_log(trace_id);
CREATE INDEX idx_pol_created ON pmis_operation_log(created_at DESC);
