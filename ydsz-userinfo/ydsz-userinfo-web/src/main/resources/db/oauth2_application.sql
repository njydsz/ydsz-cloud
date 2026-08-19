-- =====================================================================
--  OAuth2 应用注册表 ydsz_oauth2_application
-- ---------------------------------------------------------------------
--  存储 OAuth2 客户端应用注册信息，由 OAuth2ApplicationService 写入
--
--  索引设计：
--    - uk_client_id: clientId 唯一索引
--    - idx_status: 状态索引
-- =====================================================================

CREATE TABLE IF NOT EXISTS ydsz_oauth2_application (
    id VARCHAR(64) PRIMARY KEY COMMENT '应用 ID（UUID）',
    client_id VARCHAR(128) NOT NULL COMMENT '客户端 ID（唯一标识）',
    client_name VARCHAR(256) NOT NULL COMMENT '应用名称',
    client_secret VARCHAR(256) NOT NULL COMMENT '客户端密钥（BCrypt 加密存储）',
    client_type VARCHAR(16) NOT NULL COMMENT '客户端类型：CONFIDENTIAL/PUBLIC',
    redirect_uris JSON NOT NULL COMMENT '授权回调地址白名单（JSON 数组）',
    allowed_scopes JSON DEFAULT NULL COMMENT '允许申请的权限范围（JSON 数组）',
    allowed_audiences JSON DEFAULT NULL COMMENT '允许的受众（JSON 数组）',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '应用状态：ENABLED/DISABLED',
    description VARCHAR(512) DEFAULT NULL COMMENT '应用描述',
    icon_url VARCHAR(512) DEFAULT NULL COMMENT '应用图标 URL',
    created_by VARCHAR(64) DEFAULT NULL COMMENT '创建者用户 ID',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    -- 通用字段（ydsz-common-jdbc MpBaseEntity）
    tenant_id VARCHAR(64) DEFAULT NULL COMMENT '租户 ID',
    deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '逻辑删除标记',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    revision INT DEFAULT 0 COMMENT '乐观锁版本号',

    -- 索引
    UNIQUE INDEX uk_client_id (`client_id`) COMMENT 'clientId 唯一索引',
    INDEX idx_status (`status`) COMMENT '状态索引',
    INDEX idx_tenant_deleted (`tenant_id`, `deleted`) COMMENT '租户+删除标记索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2 应用注册表';
