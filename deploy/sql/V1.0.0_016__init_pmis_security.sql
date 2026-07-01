-- ============================================================
-- V1.0.0_016  权限安全体系  脚本
-- ============================================================
-- 说明：批次 13 权限安全体系
-- 1) 数据权限：pmis_user_account 增加 data_scope / custom_dept_ids 字段
-- 2) 登录审计：pmis_login_audit
-- 3) 双因素认证：pmis_user_2fa
-- 4) 数据导出审计：pmis_data_export_audit
-- 5) 敏感操作复核：pmis_sensitive_operation
-- 6) 会话管理：pmis_user_session
-- ============================================================

-- ----------------------------
-- 1) 增强用户账号表
-- ----------------------------
ALTER TABLE pmis_user_account
    ADD COLUMN IF NOT EXISTS data_scope VARCHAR(16)  NOT NULL DEFAULT 'SELF'
    ADD COLUMN IF NOT EXISTS custom_dept_ids TEXT
    ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN     NOT NULL DEFAULT FALSE
    ADD COLUMN IF NOT EXISTS mfa_type VARCHAR(16)    NOT NULL DEFAULT 'NONE'
    ADD COLUMN IF NOT EXISTS last_pwd_change_at TIMESTAMP
    ADD COLUMN IF NOT EXISTS pwd_change_count INT    NOT NULL DEFAULT 0;

-- ----------------------------
-- 2) 登录审计
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_login_audit (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(64)   NOT NULL,
    user_id         BIGINT,
    login_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    login_ip        VARCHAR(64),
    user_agent      VARCHAR(512),
    status          VARCHAR(16)   NOT NULL,
    fail_reason     VARCHAR(64),
    mfa_used        BOOLEAN       NOT NULL DEFAULT FALSE,
    mfa_success     BOOLEAN,
    trace_id        VARCHAR(64),
    tenant_id       BIGINT        NOT NULL DEFAULT 1,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT      NOT NULL DEFAULT 0
);
COMMENT ON TABLE pmis_login_audit IS '登录审计日志（等保2.0要求：登录成功/失败全留存）';

CREATE INDEX IF NOT EXISTS idx_login_audit_user_at ON pmis_login_audit (username, login_at DESC);
CREATE INDEX IF NOT EXISTS idx_login_audit_ip_at   ON pmis_login_audit (login_ip, login_at DESC);
CREATE INDEX IF NOT EXISTS idx_login_audit_status  ON pmis_login_audit (status, login_at DESC);

-- ----------------------------
-- 3) 双因素认证
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_user_2fa (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL,
    mfa_type        VARCHAR(16)   NOT NULL DEFAULT 'TOTP',
    secret          VARCHAR(128)  NOT NULL,
    binding_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at    TIMESTAMP,
    backup_codes    TEXT,
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    tenant_id       BIGINT        NOT NULL DEFAULT 1,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT      NOT NULL DEFAULT 0,
    UNIQUE (user_id, mfa_type, deleted)
);
COMMENT ON TABLE pmis_user_2fa IS '用户双因素认证（基于 TOTP）';

-- ----------------------------
-- 4) 数据导出审计
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_data_export_audit (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL,
    username        VARCHAR(64)   NOT NULL,
    export_module   VARCHAR(64)   NOT NULL,
    export_action   VARCHAR(64)   NOT NULL,
    biz_type        VARCHAR(32),
    row_count       INT           NOT NULL DEFAULT 0,
    file_name       VARCHAR(256),
    file_size       BIGINT,
    export_format   VARCHAR(16),
    query_summary   TEXT,
    trace_id        VARCHAR(64),
    client_ip       VARCHAR(64),
    tenant_id       BIGINT        NOT NULL DEFAULT 1,
    exported_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT      NOT NULL DEFAULT 0
);
COMMENT ON TABLE pmis_data_export_audit IS '数据导出审计（合同/财务/薪酬等敏感数据导出全留存）';

CREATE INDEX IF NOT EXISTS idx_export_audit_user_at  ON pmis_data_export_audit (user_id, exported_at DESC);
CREATE INDEX IF NOT EXISTS idx_export_audit_module   ON pmis_data_export_audit (export_module, exported_at DESC);

-- ----------------------------
-- 5) 敏感操作二次确认
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_sensitive_operation (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL,
    username        VARCHAR(64)   NOT NULL,
    operation_code  VARCHAR(64)   NOT NULL,
    operation_name  VARCHAR(128)  NOT NULL,
    biz_type        VARCHAR(32),
    biz_id          VARCHAR(64),
    re_auth_method  VARCHAR(16)   NOT NULL,
    re_auth_token   VARCHAR(256),
    verified_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_at       TIMESTAMP     NOT NULL,
    client_ip       VARCHAR(64),
    trace_id        VARCHAR(64),
    tenant_id       BIGINT        NOT NULL DEFAULT 1,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT      NOT NULL DEFAULT 0
);
COMMENT ON TABLE pmis_sensitive_operation IS '敏感操作二次确认记录';

CREATE INDEX IF NOT EXISTS idx_sensitive_op_user_at ON pmis_sensitive_operation (user_id, verified_at DESC);
CREATE INDEX IF NOT EXISTS idx_sensitive_op_code    ON pmis_sensitive_operation (operation_code, verified_at DESC);

-- ----------------------------
-- 6) 用户会话（单点登录/强制下线）
-- ----------------------------
CREATE TABLE IF NOT EXISTS pmis_user_session (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL,
    session_id      VARCHAR(64)   NOT NULL,
    token_jti       VARCHAR(64),
    login_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_at       TIMESTAMP     NOT NULL,
    client_ip       VARCHAR(64),
    user_agent      VARCHAR(512),
    device_type     VARCHAR(32),
    status          VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    logout_at       TIMESTAMP,
    logout_reason   VARCHAR(64),
    trace_id        VARCHAR(64),
    tenant_id       BIGINT        NOT NULL DEFAULT 1,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT      NOT NULL DEFAULT 0,
    UNIQUE (session_id)
);
COMMENT ON TABLE pmis_user_session IS '用户活跃会话';

CREATE INDEX IF NOT EXISTS idx_user_session_user_status ON pmis_user_session (user_id, status);
CREATE INDEX IF NOT EXISTS idx_user_session_expire      ON pmis_user_session (expire_at);
