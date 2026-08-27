-- ============================================================================
-- ydsz-cronjob 辅助功能表结构（Flyway 迁移脚本 P2-8）
-- 版本: 1.2.0
-- 描述: 创建 Webhook、告警、统计、事件存储、审计日志等辅助表
-- 依赖: V2__dag_tables.sql
-- 兼容: MySQL 8.0+
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 10. Webhook 配置表（ydsz_job_webhook）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ydsz_job_webhook` (
  `id` VARCHAR(32) NOT NULL COMMENT '主键（雪花ID）',
  `job_id` VARCHAR(32) NOT NULL COMMENT '任务ID',
  `webhook_url` VARCHAR(512) NOT NULL COMMENT 'Webhook URL',
  `webhook_type` VARCHAR(16) NOT NULL DEFAULT 'POST' COMMENT '类型: POST/EMAIL/SMS',
  `secret_key` VARCHAR(256) DEFAULT NULL COMMENT '签名密钥（HMAC-SHA256）',
  `trigger_events` VARCHAR(256) DEFAULT 'SUCCESS,FAILURE' COMMENT '触发事件: SUCCESS/FAILURE/TIMEOUT',
  `max_retry_count` INT DEFAULT 3 COMMENT '最大重试次数',
  `retry_interval_ms` BIGINT DEFAULT 5000 COMMENT '重试间隔（毫秒）',
  `timeout_ms` BIGINT DEFAULT 10000 COMMENT '超时时间（毫秒）',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `tenant_id` VARCHAR(32) NOT NULL DEFAULT '1' COMMENT '租户ID',
  `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_job_id` (`job_id`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务Webhook配置表';

-- ---------------------------------------------------------------------------
-- 11. Webhook 重试记录表（ydsz_job_webhook_retry）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ydsz_job_webhook_retry` (
  `id` VARCHAR(32) NOT NULL COMMENT '主键（雪花ID）',
  `webhook_id` VARCHAR(32) NOT NULL COMMENT 'Webhook配置ID',
  `log_id` VARCHAR(32) DEFAULT NULL COMMENT '关联执行日志ID',
  `request_body` TEXT DEFAULT NULL COMMENT '请求体',
  `response_body` TEXT DEFAULT NULL COMMENT '响应体',
  `response_code` INT DEFAULT NULL COMMENT '响应状态码',
  `retry_count` INT DEFAULT 0 COMMENT '已重试次数',
  `next_retry_time` DATETIME DEFAULT NULL COMMENT '下次重试时间',
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/SUCCESS/FAILED/ABANDONED',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `tenant_id` VARCHAR(32) NOT NULL DEFAULT '1' COMMENT '租户ID',
  `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_webhook_id` (`webhook_id`),
  KEY `idx_status` (`status`),
  KEY `idx_next_retry_time` (`next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Webhook重试记录表';

-- ---------------------------------------------------------------------------
-- 12. 告警规则表（ydsz_job_alert_rule）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ydsz_job_alert_rule` (
  `id` VARCHAR(32) NOT NULL COMMENT '主键（雪花ID）',
  `rule_name` VARCHAR(128) NOT NULL COMMENT '规则名称',
  `job_id` VARCHAR(32) DEFAULT NULL COMMENT '任务ID（为空则匹配全部任务）',
  `job_group` VARCHAR(64) DEFAULT NULL COMMENT '任务分组',
  `alert_type` VARCHAR(32) NOT NULL COMMENT '告警类型: FAILURE/TIMEOUT/SLOW/MISSING',
  `threshold` INT DEFAULT 1 COMMENT '触发阈值（次数）',
  `window_minutes` INT DEFAULT 5 COMMENT '时间窗口（分钟）',
  `channels` VARCHAR(256) DEFAULT 'WEBHOOK' COMMENT '通知渠道: WEBHOOK/EMAIL/SMS',
  `webhook_url` VARCHAR(512) DEFAULT NULL COMMENT '通知Webhook URL',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `tenant_id` VARCHAR(32) NOT NULL DEFAULT '1' COMMENT '租户ID',
  `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `revision` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_job_id` (`job_id`),
  KEY `idx_alert_type` (`alert_type`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警规则表';

-- ---------------------------------------------------------------------------
-- 13. 告警记录表（ydsz_job_alert_log）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ydsz_job_alert_log` (
  `id` VARCHAR(32) NOT NULL COMMENT '主键（雪花ID）',
  `rule_id` VARCHAR(32) NOT NULL COMMENT '告警规则ID',
  `job_id` VARCHAR(32) DEFAULT NULL COMMENT '任务ID',
  `job_key` VARCHAR(128) DEFAULT NULL COMMENT '任务KEY',
  `alert_type` VARCHAR(32) NOT NULL COMMENT '告警类型',
  `alert_content` TEXT DEFAULT NULL COMMENT '告警内容',
  `alert_level` VARCHAR(16) DEFAULT 'WARNING' COMMENT '告警级别: INFO/WARNING/CRITICAL',
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/SENT/ACKNOWLEDGED/RESOLVED',
  `notified_at` DATETIME DEFAULT NULL COMMENT '通知时间',
  `resolved_at` DATETIME DEFAULT NULL COMMENT '解决时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `tenant_id` VARCHAR(32) NOT NULL DEFAULT '1' COMMENT '租户ID',
  `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_rule_id` (`rule_id`),
  KEY `idx_job_id` (`job_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警记录表';

-- ---------------------------------------------------------------------------
-- 14. 任务统计表（ydsz_job_daily_stats）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ydsz_job_daily_stats` (
  `id` VARCHAR(32) NOT NULL COMMENT '主键（雪花ID）',
  `job_id` VARCHAR(32) NOT NULL COMMENT '任务ID',
  `stats_date` DATE NOT NULL COMMENT '统计日期',
  `total_count` INT DEFAULT 0 COMMENT '总执行次数',
  `success_count` INT DEFAULT 0 COMMENT '成功次数',
  `fail_count` INT DEFAULT 0 COMMENT '失败次数',
  `timeout_count` INT DEFAULT 0 COMMENT '超时次数',
  `avg_duration_ms` BIGINT DEFAULT 0 COMMENT '平均耗时（毫秒）',
  `max_duration_ms` BIGINT DEFAULT 0 COMMENT '最大耗时（毫秒）',
  `min_duration_ms` BIGINT DEFAULT 0 COMMENT '最小耗时（毫秒）',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `tenant_id` VARCHAR(32) NOT NULL DEFAULT '1' COMMENT '租户ID',
  `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_date` (`job_id`, `stats_date`),
  KEY `idx_stats_date` (`stats_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务每日统计表';

-- ---------------------------------------------------------------------------
-- 15. 事件存储表（ydsz_event_store）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ydsz_event_store` (
  `id` VARCHAR(32) NOT NULL COMMENT '主键（雪花ID）',
  `aggregate_type` VARCHAR(64) NOT NULL COMMENT '聚合根类型',
  `aggregate_id` VARCHAR(32) NOT NULL COMMENT '聚合根ID',
  `event_type` VARCHAR(64) NOT NULL COMMENT '事件类型',
  `event_data` MEDIUMTEXT NOT NULL COMMENT '事件数据JSON',
  `event_version` INT NOT NULL DEFAULT 1 COMMENT '事件版本号',
  `occurred_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  `tenant_id` VARCHAR(32) NOT NULL DEFAULT '1' COMMENT '租户ID',
  `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_aggregate` (`aggregate_type`, `aggregate_id`),
  KEY `idx_event_type` (`event_type`),
  KEY `idx_occurred_at` (`occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事件存储表（Event Sourcing）';

-- ---------------------------------------------------------------------------
-- 16. 审计日志表（sys_audit_log）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_audit_log` (
  `id` VARCHAR(32) NOT NULL COMMENT '主键（雪花ID）',
  `module` VARCHAR(64) NOT NULL COMMENT '功能模块',
  `operation` VARCHAR(64) NOT NULL COMMENT '操作类型',
  `target` VARCHAR(128) DEFAULT NULL COMMENT '操作对象',
  `content` TEXT DEFAULT NULL COMMENT '操作内容',
  `operator_id` VARCHAR(64) DEFAULT NULL COMMENT '操作人ID',
  `operator_name` VARCHAR(64) DEFAULT NULL COMMENT '操作人姓名',
  `operator_ip` VARCHAR(64) DEFAULT NULL COMMENT '操作人IP',
  `result` VARCHAR(16) DEFAULT 'SUCCESS' COMMENT '操作结果: SUCCESS/FAILURE',
  `error_msg` TEXT DEFAULT NULL COMMENT '错误信息',
  `duration_ms` BIGINT DEFAULT NULL COMMENT '操作耗时（毫秒）',
  `tenant_id` VARCHAR(32) NOT NULL DEFAULT '1' COMMENT '租户ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_module` (`module`),
  KEY `idx_operator` (`operator_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计日志表';

-- ---------------------------------------------------------------------------
-- 17. Outbox 事件表（ydsz_outbox_event）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ydsz_outbox_event` (
  `id` VARCHAR(32) NOT NULL COMMENT '主键（雪花ID）`,
  `aggregate_type` VARCHAR(64) NOT NULL COMMENT '聚合根类型',
  `aggregate_id` VARCHAR(32) NOT NULL COMMENT '聚合根ID',
  `event_type` VARCHAR(64) NOT NULL COMMENT '事件类型',
  `payload` MEDIUMTEXT NOT NULL COMMENT '事件载荷JSON',
  `published` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已发布',
  `published_at` DATETIME DEFAULT NULL COMMENT '发布时间',
  `retry_count` INT DEFAULT 0 COMMENT '重试次数',
  `error_message` TEXT DEFAULT NULL COMMENT '错误信息',
  `tenant_id` VARCHAR(32) NOT NULL DEFAULT '1' COMMENT '租户ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_published` (`published`),
  KEY `idx_aggregate` (`aggregate_type`, `aggregate_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Outbox事件表（事务消息）';

-- ---------------------------------------------------------------------------
-- 18. 租户配额表（sys_tenant_quota）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_tenant_quota` (
  `id` VARCHAR(32) NOT NULL COMMENT '主键（雪花ID）',
  `tenant_id` VARCHAR(32) NOT NULL COMMENT '租户ID',
  `max_jobs` INT DEFAULT 100 COMMENT '最大任务数',
  `max_concurrent` INT DEFAULT 16 COMMENT '最大并发数',
  `max_daily_executions` INT DEFAULT 10000 COMMENT '每日最大执行次数',
  `used_jobs` INT DEFAULT 0 COMMENT '已使用任务数',
  `used_daily_executions` INT DEFAULT 0 COMMENT '今日已执行次数',
  `reset_date` DATE DEFAULT NULL COMMENT '用量重置日期',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `revision` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户配额表';
