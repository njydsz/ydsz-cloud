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
    ADD COLUMN IF NOT EXISTS data_scope VARCHAR(16)  NOT NULL DEFAULT 'SELF',
    ADD COLUMN IF NOT EXISTS custom_dept_ids TEXT,
    ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS mfa_type VARCHAR(16)    NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS last_pwd_change_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS pwd_change_count INT    NOT NULL DEFAULT 0;

-- 字段注释（V1.0.0_001 中误提前写入，迁移至此处与 ADD COLUMN 同步）
COMMENT ON COLUMN pmis_user_account.data_scope IS '数据权限范围: ALL 全部 / DEPT 本部门 / DEPT_AND_SUB 本部门及下级 / SELF 本人 / CUSTOM 自定义';
COMMENT ON COLUMN pmis_user_account.custom_dept_ids IS '自定义数据权限部门 ID 列表(逗号分隔,data_scope=CUSTOM 时生效)';
COMMENT ON COLUMN pmis_user_account.mfa_enabled IS '是否启用双因素认证';
COMMENT ON COLUMN pmis_user_account.mfa_type IS '双因素认证类型: NONE 未启用 / TOTP 基于时间的一次性密码 / SMS 短信验证码';
COMMENT ON COLUMN pmis_user_account.last_pwd_change_at IS '最近密码修改时间';
COMMENT ON COLUMN pmis_user_account.pwd_change_count IS '密码修改次数(用于强制定期改密)';

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
COMMENT ON TABLE  pmis_login_audit IS '登录审计日志表: 等保 2.0 要求,登录成功/失败全留存,支持溯源审计';
COMMENT ON COLUMN pmis_login_audit.username IS '登录用户名: 失败时也可记录,便于排查撞库';
COMMENT ON COLUMN pmis_login_audit.user_id IS '登录用户 ID: 成功时记录,失败可为 NULL';
COMMENT ON COLUMN pmis_login_audit.login_at IS '登录时间';
COMMENT ON COLUMN pmis_login_audit.login_ip IS '登录 IP: 用于异常登录检测';
COMMENT ON COLUMN pmis_login_audit.user_agent IS '浏览器 UA: 用于设备指纹';
COMMENT ON COLUMN pmis_login_audit.status IS '状态: SUCCESS 成功 / FAIL 失败 / LOCKED 锁定';
COMMENT ON COLUMN pmis_login_audit.fail_reason IS '失败原因: 密码错误/账号锁定/MFA 失败等';
COMMENT ON COLUMN pmis_login_audit.mfa_used IS '是否使用 MFA: true=已启用并使用';
COMMENT ON COLUMN pmis_login_audit.mfa_success IS 'MFA 是否通过: NULL=未使用,true=通过,false=失败';
COMMENT ON COLUMN pmis_login_audit.trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_login_audit.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_login_audit.deleted IS '逻辑删除: 0=未删除,1=已删除';

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
COMMENT ON TABLE  pmis_user_2fa IS '用户双因素认证表: 基于 TOTP（Time-based OTP）的双因素认证,使用 constant-time 比对防时序攻击';
COMMENT ON COLUMN pmis_user_2fa.user_id IS '用户 ID';
COMMENT ON COLUMN pmis_user_2fa.mfa_type IS 'MFA 类型: TOTP 时间型 / SMS 短信 / EMAIL 邮件';
COMMENT ON COLUMN pmis_user_2fa.secret IS 'TOTP 密钥: Base32 编码,扫描二维码';
COMMENT ON COLUMN pmis_user_2fa.binding_at IS '绑定时间';
COMMENT ON COLUMN pmis_user_2fa.last_used_at IS '最近使用时间';
COMMENT ON COLUMN pmis_user_2fa.backup_codes IS '备份码（密文）: 一次性,小写 hex 存储,已使用标记为 _used_<timestamp>';
COMMENT ON COLUMN pmis_user_2fa.enabled IS '是否启用: true=启用';
COMMENT ON COLUMN pmis_user_2fa.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_user_2fa.deleted IS '逻辑删除: 0=未删除,1=已删除';

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
COMMENT ON TABLE  pmis_data_export_audit IS '数据导出审计表: 合同/财务/薪酬等敏感数据导出全留存,@DataExportAudit 自动捕获';
COMMENT ON COLUMN pmis_data_export_audit.user_id IS '导出用户 ID';
COMMENT ON COLUMN pmis_data_export_audit.username IS '导出用户姓名（冗余）';
COMMENT ON COLUMN pmis_data_export_audit.export_module IS '导出模块: PROJECT/EXECUTION/FINANCE 等';
COMMENT ON COLUMN pmis_data_export_audit.export_action IS '导出动作: EXPORT 导出 / PRINT 打印 / DOWNLOAD 下载';
COMMENT ON COLUMN pmis_data_export_audit.biz_type IS '业务类型';
COMMENT ON COLUMN pmis_data_export_audit.row_count IS '导出行数: 自动检测 Collection/Number,作为审计基数';
COMMENT ON COLUMN pmis_data_export_audit.file_name IS '导出文件名';
COMMENT ON COLUMN pmis_data_export_audit.file_size IS '文件大小(字节)';
COMMENT ON COLUMN pmis_data_export_audit.export_format IS '导出格式: XLSX/CSV/PDF';
COMMENT ON COLUMN pmis_data_export_audit.query_summary IS '查询条件摘要: 用于审计导出范围';
COMMENT ON COLUMN pmis_data_export_audit.trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_data_export_audit.client_ip IS '客户端 IP';
COMMENT ON COLUMN pmis_data_export_audit.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_data_export_audit.exported_at IS '导出时间';
COMMENT ON COLUMN pmis_data_export_audit.deleted IS '逻辑删除: 0=未删除,1=已删除';

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
COMMENT ON TABLE  pmis_sensitive_operation IS '敏感操作二次确认记录表: @RequireReAuth 注解触发的二次认证,token 一次性消费,防重放';
COMMENT ON COLUMN pmis_sensitive_operation.user_id IS '操作用户 ID';
COMMENT ON COLUMN pmis_sensitive_operation.username IS '操作用户姓名（冗余）';
COMMENT ON COLUMN pmis_sensitive_operation.operation_code IS '操作编码: 例如 USER_DELETE / CONTRACT_REVERSE';
COMMENT ON COLUMN pmis_sensitive_operation.operation_name IS '操作名称';
COMMENT ON COLUMN pmis_sensitive_operation.biz_type IS '业务类型';
COMMENT ON COLUMN pmis_sensitive_operation.biz_id IS '业务对象 ID';
COMMENT ON COLUMN pmis_sensitive_operation.re_auth_method IS '二次认证方式: PASSWORD 密码 / MFA / SMS';
COMMENT ON COLUMN pmis_sensitive_operation.re_auth_token IS '二次认证 Token: Redis Key 一次性消费';
COMMENT ON COLUMN pmis_sensitive_operation.verified_at IS '验证时间';
COMMENT ON COLUMN pmis_sensitive_operation.expire_at IS 'Token 过期时间';
COMMENT ON COLUMN pmis_sensitive_operation.client_ip IS '客户端 IP';
COMMENT ON COLUMN pmis_sensitive_operation.trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_sensitive_operation.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_sensitive_operation.deleted IS '逻辑删除: 0=未删除,1=已删除';

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
COMMENT ON TABLE  pmis_user_session IS '用户活跃会话表: 单点登录/强制下线管理,SessionService 维护生命周期';
COMMENT ON COLUMN pmis_user_session.user_id IS '用户 ID';
COMMENT ON COLUMN pmis_user_session.session_id IS '会话 ID: 唯一';
COMMENT ON COLUMN pmis_user_session.token_jti IS 'JWT ID: 用于 token 失效';
COMMENT ON COLUMN pmis_user_session.login_at IS '登录时间';
COMMENT ON COLUMN pmis_user_session.last_active_at IS '最近活跃时间';
COMMENT ON COLUMN pmis_user_session.expire_at IS '过期时间';
COMMENT ON COLUMN pmis_user_session.client_ip IS '客户端 IP';
COMMENT ON COLUMN pmis_user_session.user_agent IS '浏览器 UA';
COMMENT ON COLUMN pmis_user_session.device_type IS '设备类型: WEB / IOS / ANDROID / DESKTOP';
COMMENT ON COLUMN pmis_user_session.status IS '会话状态: ACTIVE 活跃 / EXPIRED 过期 / KICKED 踢出';
COMMENT ON COLUMN pmis_user_session.logout_at IS '登出时间';
COMMENT ON COLUMN pmis_user_session.logout_reason IS '登出原因: USER_LOGOUT / ADMIN_KICK / EXPIRED';
COMMENT ON COLUMN pmis_user_session.trace_id IS '链路追踪 ID';
COMMENT ON COLUMN pmis_user_session.tenant_id IS '租户 ID';
COMMENT ON COLUMN pmis_user_session.deleted IS '逻辑删除: 0=未删除,1=已删除';

CREATE INDEX IF NOT EXISTS idx_user_session_user_status ON pmis_user_session (user_id, status);
CREATE INDEX IF NOT EXISTS idx_user_session_expire      ON pmis_user_session (expire_at);
