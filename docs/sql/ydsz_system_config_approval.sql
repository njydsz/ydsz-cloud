-- =============================================================================
-- 云顶云平台 - 配置变更审批单表 DDL
-- 表名：ydsz_system_config_approval
-- 说明：记录配置/字典/变量变更的审批流（CREATE/UPDATE/DELETE）
-- 版本：26.09.08
-- =============================================================================

CREATE TABLE IF NOT EXISTS `ydsz_system_config_approval`
(
    `id`               VARCHAR(32)  NOT NULL COMMENT '审批单唯一 ID',
    `resource_type`    VARCHAR(16)  NOT NULL COMMENT '资源类型（CONFIG / DICT / VARIABLE）',
    `resource_key`     VARCHAR(128) NOT NULL COMMENT '资源唯一标识（如配置键、字典编码、变量键）',
    `resource_group`   VARCHAR(64)  DEFAULT NULL COMMENT '资源分组（仅 CONFIG 类型有值）',
    `change_type`      VARCHAR(16)  NOT NULL COMMENT '变更操作类型（CREATE / UPDATE / DELETE）',
    `before_json`      TEXT         DEFAULT NULL COMMENT '变更前的 JSON 值（CREATE 时为空）',
    `after_json`       TEXT         DEFAULT NULL COMMENT '变更后的 JSON 值（DELETE 时为空）',
    `status`           VARCHAR(16)  NOT NULL COMMENT '审批状态（PENDING / APPROVED / REJECTED / WITHDRAWN）',
    `submitter_id`     VARCHAR(64)  NOT NULL COMMENT '发起人 ID',
    `reason`           VARCHAR(500) DEFAULT NULL COMMENT '变更原因',
    `rejection_reason` VARCHAR(500) DEFAULT NULL COMMENT '拒绝原因',
    `submitted_at`     DATETIME     NOT NULL COMMENT '发起时间',
    `closed_at`        DATETIME     DEFAULT NULL COMMENT '审批单关闭时间',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status_submitted_at` (`status`, `submitted_at`),
    KEY `idx_submitter_id` (`submitter_id`),
    KEY `idx_resource` (`resource_type`, `resource_key`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='配置变更审批单';

-- =============================================================================
-- 初始化数据（无需初始化数据）
-- =============================================================================
