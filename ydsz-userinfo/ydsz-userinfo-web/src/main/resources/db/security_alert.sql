-- =====================================================================
--  安全告警表 ydsz_security_alert
-- ---------------------------------------------------------------------
--  存储安全告警事件记录，由 SecurityAlertService 写入，由管理员通过
--  SecurityAlertController API 查询和处理
--
--  索引设计：
--    - idx_status_risk: 状态+风险等级复合索引（待处理告警查询）
--    - idx_type_time: 告警类型+创建时间复合索引（告警去重统计）
--    - idx_user_id: 用户 ID 索引（按用户查询告警历史）
--    - idx_source_ip: 来源 IP 索引（IP 维度告警统计）
-- =====================================================================

CREATE TABLE IF NOT EXISTS ydsz_security_alert (
    id VARCHAR(64) PRIMARY KEY COMMENT '告警 ID（UUID）',
    alert_type VARCHAR(32) NOT NULL COMMENT '告警类型：ACCOUNT_LOCKED/ACCOUNT_BANNED/MFA_FAILED/BRUTE_FORCE/ANOMALOUS_LOGIN/PASSWORD_SPRAY',
    risk_level VARCHAR(16) NOT NULL COMMENT '风险等级：LOW/MEDIUM/HIGH/CRITICAL',
    user_id VARCHAR(64) DEFAULT NULL COMMENT '关联用户 ID',
    username VARCHAR(128) DEFAULT NULL COMMENT '关联用户名',
    source_ip VARCHAR(64) DEFAULT NULL COMMENT '来源 IP',
    title VARCHAR(256) NOT NULL COMMENT '告警标题',
    content TEXT COMMENT '告警内容',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '告警状态：PENDING/ACKNOWLEDGED/RESOLVED/IGNORED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    handled_at TIMESTAMP DEFAULT NULL COMMENT '处理时间',
    handler_note VARCHAR(512) DEFAULT NULL COMMENT '处理备注',

    -- 通用字段（ydsz-common-jdbc MpBaseEntity）
    tenant_id VARCHAR(64) DEFAULT NULL COMMENT '租户 ID',
    deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '逻辑删除标记',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    revision INT DEFAULT 0 COMMENT '乐观锁版本号',

    -- 索引
    INDEX idx_status_risk (`status`, `risk_level`) COMMENT '状态+风险等级复合索引',
    INDEX idx_type_time (`alert_type`, `created_at`) COMMENT '告警类型+创建时间复合索引',
    INDEX idx_user_id (`user_id`) COMMENT '用户 ID 索引',
    INDEX idx_source_ip (`source_ip`) COMMENT '来源 IP 索引',
    INDEX idx_tenant_deleted (`tenant_id`, `deleted`) COMMENT '租户+删除标记索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='安全告警表';
