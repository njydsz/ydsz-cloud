-- =====================================================================
--  认证策略配置表 ydsz_auth_policy
-- ---------------------------------------------------------------------
--  存储租户级认证策略（P3-1 多租户认证域隔离增强）
--
--  支持不同租户独立配置：密码策略、MFA策略、验证码策略、允许的身份提供者等
--
--  索引设计：
--    - uk_tenant_id: 租户 ID 唯一索引
-- =====================================================================

CREATE TABLE IF NOT EXISTS ydsz_auth_policy (
    id VARCHAR(64) PRIMARY KEY COMMENT '策略 ID（UUID）',
    tenant_id VARCHAR(64) DEFAULT NULL COMMENT '租户 ID（NULL 表示全局默认策略）',
    name VARCHAR(64) NOT NULL COMMENT '策略名称',
    password_min_length INT DEFAULT 8 COMMENT '密码最小长度（≥ 6）',
    password_require_uppercase BOOLEAN DEFAULT TRUE COMMENT '密码必须包含大写字母',
    password_require_digit BOOLEAN DEFAULT TRUE COMMENT '密码必须包含数字',
    mfa_enabled BOOLEAN DEFAULT FALSE COMMENT '是否启用双因素认证',
    captcha_enabled BOOLEAN DEFAULT TRUE COMMENT '登录是否启用图形验证码',
    allowed_identity_providers VARCHAR(256) DEFAULT 'LOCAL' COMMENT '允许的身份提供者类型（逗号分隔：LOCAL/LDAP/SAML/OAUTH2）',
    max_sessions_per_user INT DEFAULT 3 COMMENT '每个用户最大会话数',
    session_timeout_seconds INT DEFAULT 7200 COMMENT '会话超时时间（秒）',
    remark VARCHAR(256) DEFAULT NULL COMMENT '备注说明',

    -- 通用字段（ydsz-common-jdbc MpBaseEntity）
    deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '逻辑删除标记',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(64) DEFAULT NULL COMMENT '创建者用户 ID',
    revision INT DEFAULT 0 COMMENT '乐观锁版本号',

    -- 索引
    UNIQUE INDEX uk_tenant_id (`tenant_id`) COMMENT '租户 ID 唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='认证策略配置表';

-- 插入全局默认策略
INSERT INTO ydsz_auth_policy (id, tenant_id, name, password_min_length, password_require_uppercase, password_require_digit, mfa_enabled, captcha_enabled, allowed_identity_providers, max_sessions_per_user, session_timeout_seconds, remark, deleted, revision)
VALUES ('default-policy-001', NULL, '全局默认认证策略', 8, TRUE, TRUE, FALSE, TRUE, 'LOCAL', 3, 7200, '系统全局默认策略，租户未配置时继承', FALSE, 0)
ON DUPLICATED KEY UPDATE name = name;
