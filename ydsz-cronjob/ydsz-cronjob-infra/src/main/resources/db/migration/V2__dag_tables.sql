-- ============================================================================
-- ydsz-cronjob DAG 工作流表结构（Flyway 迁移脚本 P2-8）
-- 版本: 1.1.0
-- 描述: 创建 DAG 工作流相关表结构
-- 依赖: V1__initial_schema.sql
-- 兼容: MySQL 8.0+
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 5. DAG 工作流定义表（ydsz_job_dag）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ydsz_job_dag` (
  `id` VARCHAR(32) NOT NULL COMMENT '主键（雪花ID）',
  `dag_key` VARCHAR(128) NOT NULL COMMENT 'DAG唯一标识',
  `dag_name` VARCHAR(128) NOT NULL COMMENT 'DAG名称',
  `dag_definition` MEDIUMTEXT NOT NULL COMMENT 'DAG定义JSON（nodes+edges+坐标）',
  `dag_status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/ENABLED/DISABLED',
  `trigger_type` VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT '触发类型: MANUAL/CRON',
  `cron_expression` VARCHAR(64) DEFAULT NULL COMMENT 'Cron表达式（CRON触发时必填）',
  `max_concurrent_instances` INT DEFAULT 1 COMMENT '最大并发实例数',
  `fail_strategy` VARCHAR(16) DEFAULT 'FAIL_FAST' COMMENT '失败策略: FAIL_FAST/CONTINUE_ON_FAIL',
  `last_fire_time` DATETIME DEFAULT NULL COMMENT '上次触发时间',
  `next_fire_time` DATETIME DEFAULT NULL COMMENT '下次触发时间',
  `fire_count` BIGINT DEFAULT 0 COMMENT '触发总次数',
  `success_count` BIGINT DEFAULT 0 COMMENT '成功次数',
  `fail_count` BIGINT DEFAULT 0 COMMENT '失败次数',
  `dag_remark` VARCHAR(512) DEFAULT NULL COMMENT '备注',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `tenant_id` VARCHAR(32) NOT NULL DEFAULT '1' COMMENT '租户ID',
  `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `revision` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dag_key` (`dag_key`),
  KEY `idx_dag_status` (`dag_status`),
  KEY `idx_next_fire_time` (`next_fire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DAG工作流定义表';

-- ---------------------------------------------------------------------------
-- 6. DAG 实例表（ydsz_job_dag_instance）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ydsz_job_dag_instance` (
  `id` VARCHAR(32) NOT NULL COMMENT '主键（雪花ID）',
  `dag_id` VARCHAR(32) NOT NULL COMMENT 'DAG定义ID',
  `dag_key` VARCHAR(128) NOT NULL COMMENT 'DAG KEY',
  `instance_status` VARCHAR(16) NOT NULL DEFAULT 'RUNNING' COMMENT '状态: RUNNING/SUCCESS/FAILED/ABORTED',
  `trigger_type` VARCHAR(16) DEFAULT NULL COMMENT '触发类型',
  `start_time` DATETIME DEFAULT NULL COMMENT '开始时间',
  `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
  `duration_ms` BIGINT DEFAULT NULL COMMENT '耗时（毫秒）',
  `params_json` TEXT DEFAULT NULL COMMENT '参数JSON',
  `error_message` TEXT DEFAULT NULL COMMENT '错误信息',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `tenant_id` VARCHAR(32) NOT NULL DEFAULT '1' COMMENT '租户ID',
  `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_dag_id` (`dag_id`),
  KEY `idx_instance_status` (`instance_status`),
  KEY `idx_start_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DAG工作流实例表';

-- ---------------------------------------------------------------------------
-- 7. DAG 节点实例表（ydsz_job_dag_node_instance）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ydsz_job_dag_node_instance` (
  `id` VARCHAR(32) NOT NULL COMMENT '主键（雪花ID）',
  `dag_instance_id` VARCHAR(32) NOT NULL COMMENT 'DAG实例ID',
  `job_key` VARCHAR(128) NOT NULL COMMENT '任务KEY',
  `node_status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/RUNNING/SUCCESS/FAILED/SKIPPED/WAITING_APPROVAL/APPROVAL_REJECTED',
  `start_time` DATETIME DEFAULT NULL COMMENT '开始时间',
  `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
  `duration_ms` BIGINT DEFAULT NULL COMMENT '耗时（毫秒）',
  `error_message` TEXT DEFAULT NULL COMMENT '错误信息',
  `retry_count` INT DEFAULT 0 COMMENT '重试次数',
  `log_id` VARCHAR(32) DEFAULT NULL COMMENT '关联执行日志ID',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `tenant_id` VARCHAR(32) NOT NULL DEFAULT '1' COMMENT '租户ID',
  `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_dag_instance_id` (`dag_instance_id`),
  KEY `idx_job_key` (`job_key`),
  KEY `idx_node_status` (`node_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DAG节点实例表';

-- ---------------------------------------------------------------------------
-- 8. DAG 版本表（ydsz_job_dag_version）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ydsz_job_dag_version` (
  `id` VARCHAR(32) NOT NULL COMMENT '主键（雪花ID）',
  `dag_id` VARCHAR(32) NOT NULL COMMENT 'DAG定义ID',
  `version` INT NOT NULL COMMENT '版本号',
  `dag_definition` MEDIUMTEXT NOT NULL COMMENT 'DAG定义JSON',
  `change_log` VARCHAR(512) DEFAULT NULL COMMENT '变更说明',
  `published` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已发布',
  `published_at` DATETIME DEFAULT NULL COMMENT '发布时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `tenant_id` VARCHAR(32) NOT NULL DEFAULT '1' COMMENT '租户ID',
  `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dag_version` (`dag_id`, `version`),
  KEY `idx_published` (`published`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DAG版本管理表';

-- ---------------------------------------------------------------------------
-- 9. DAG 上下文表（ydsz_job_dag_context）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ydsz_job_dag_context` (
  `id` VARCHAR(32) NOT NULL COMMENT '主键（雪花ID）',
  `dag_instance_id` VARCHAR(32) NOT NULL COMMENT 'DAG实例ID',
  `context_data` MEDIUMTEXT DEFAULT NULL COMMENT '上下文数据JSON',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `tenant_id` VARCHAR(32) NOT NULL DEFAULT '1' COMMENT '租户ID',
  `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dag_instance` (`dag_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DAG实例上下文表';
