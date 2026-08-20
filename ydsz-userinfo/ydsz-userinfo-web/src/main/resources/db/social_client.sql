-- =====================================================================
--  社交平台客户端配置表 ydsz_social_client
-- ---------------------------------------------------------------------
--  存储社交平台 OAuth2 应用的客户端配置（P1-1 热更新支持）
--
--  配置优先级：数据库 > application.yml（DB 有值时覆盖 YAML）
--
--  索引设计：
--    - uk_platform: 平台标识唯一索引
--    - idx_status: 状态索引
-- =====================================================================

CREATE TABLE IF NOT EXISTS ydsz_social_client (
    id VARCHAR(64) PRIMARY KEY COMMENT '配置 ID（UUID）',
    platform VARCHAR(32) NOT NULL COMMENT '平台标识（GITHUB/DINGTALK/ENTERPRISE_WECHAT/FEISHU 等）',
    platform_name VARCHAR(64) DEFAULT NULL COMMENT '平台显示名称',
    app_id VARCHAR(128) NOT NULL COMMENT '应用 ID（平台分配的 appId）',
    app_secret VARCHAR(256) NOT NULL COMMENT '应用密钥（BCrypt 加密存储）',
    scope VARCHAR(256) DEFAULT NULL COMMENT 'OAuth2 授权范围（scope）',
    redirect_uri VARCHAR(512) DEFAULT NULL COMMENT '授权回调地址（redirectUri）',
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
    UNIQUE INDEX uk_platform (`platform`) COMMENT '平台标识唯一索引',
    INDEX idx_status (`status`) COMMENT '状态索引',
    INDEX idx_tenant_deleted (`tenant_id`, `deleted`) COMMENT '租户+删除标记索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社交平台客户端配置表';
