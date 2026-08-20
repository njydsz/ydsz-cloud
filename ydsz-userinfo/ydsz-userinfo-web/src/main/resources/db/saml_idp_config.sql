-- =====================================================================
--  SAML 身份提供者配置表 ydsz_saml_idp_config
-- ---------------------------------------------------------------------
--  存储 SAML 2.0 Identity Provider 的元数据和证书（P2-1 多租户）
--
--  支持多个 IdP 注册，每个租户可配置独立的 SAML IdP（如企业微信 SAML、飞书 SAML、ADFS）
--
--  索引设计：
--    - uk_entity_id: Entity ID 唯一索引
--    - idx_status: 状态索引
-- =====================================================================

CREATE TABLE IF NOT EXISTS ydsz_saml_idp_config (
    id VARCHAR(64) PRIMARY KEY COMMENT '配置 ID（UUID）',
    name VARCHAR(64) NOT NULL COMMENT 'IdP 显示名称（如 "企业微信 SAML"、"飞书 SAML"）',
    entity_id VARCHAR(512) NOT NULL COMMENT 'IdP Entity ID（SAML 协议中 IdP 的唯一标识 URI）',
    sso_url VARCHAR(512) DEFAULT NULL COMMENT 'IdP SSO 端点 URL',
    certificate TEXT DEFAULT NULL COMMENT 'IdP 公钥证书（PEM 格式，用于验证 SAML Response 签名）',
    email_attribute VARCHAR(64) DEFAULT 'email' COMMENT '用户邮箱对应的 SAML Attribute 名称',
    display_name_attribute VARCHAR(64) DEFAULT 'displayName' COMMENT '用户显示名称对应的 SAML Attribute 名称',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    sort_order INT DEFAULT 100 COMMENT '排序权重（越小越靠前）',
    remark VARCHAR(256) DEFAULT NULL COMMENT '备注说明',

    -- 通用字段（ydsz-common-jdbc MpBaseEntity）
    tenant_id VARCHAR(64) DEFAULT NULL COMMENT '租户 ID',
    deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '逻辑删除标记',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by VARCHAR(64) DEFAULT NULL COMMENT '创建者用户 ID',
    revision INT DEFAULT 0 COMMENT '乐观锁版本号',

    -- 索引
    UNIQUE INDEX uk_entity_id (`entity_id`) COMMENT 'Entity ID 唯一索引',
    INDEX idx_status (`status`) COMMENT '状态索引',
    INDEX idx_tenant_deleted (`tenant_id`, `deleted`) COMMENT '租户+删除标记索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SAML 2.0 身份提供者配置表';
