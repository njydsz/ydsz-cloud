-- ============================================================================
-- ydsz-cronjob 初始数据库结构（Flyway 迁移脚本 P2-8）
-- 版本: 1.0.0
-- 描述: 创建定时任务调度引擎全部表结构
-- 兼容: MySQL 8.0+
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 任务主表（ydsz_job_main）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ydsz_job_main` (
  `id` VARCHAR(32) NOT NULL COMMENT '主键（雪花ID）',
  `job_name` VARCHAR(128) NOT NULL COMMENT '任务名称',
  `job_group` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '任务分组',
  `job_key` VARCHAR(128) NOT NULL COMMENT '任务唯一标识',
  `handler` VARCHAR(256) NOT NULL COMMENT '任务处理器Bean名称',
  `cron_expression` VARCHAR(64) DEFAULT NULL COMMENT 'Cron表达式',
  `schedule_type` VARCHAR(16) NOT NULL DEFAULT 'CRON' COMMENT '调度类型: CRON/FIXED_RATE/FIXED_DELAY/API',
  `fixed_rate_ms` BIGINT DEFAULT NULL COMMENT '固定频率间隔（毫秒）',
  `fixed_delay_ms` BIGINT DEFAULT NULL COMMENT '固定延迟间隔（毫秒）',
  `params_json` TEXT DEFAULT NULL COMMENT '任务参数JSON',
  `job_remark` VARCHAR(512) DEFAULT NULL COMMENT '任务备注',
  `next_fire_time` DATETIME DEFAULT NULL COMMENT '下次触发时间',
  `last_fire_time` DATETIME DEFAULT NULL COMMENT '上次触发时间',
  `fire_count` BIGINT NOT NULL DEFAULT 0 COMMENT '触发总次数',
  `success_count` BIGINT NOT NULL DEFAULT 0 COMMENT '成功次数',
  `fail_count` BIGINT NOT NULL DEFAULT 0 COMMENT '失败次数',
  `lock_ttl_ms` BIGINT DEFAULT 30000 COMMENT '分布式锁TTL（毫秒）',
  `timeout_ms` BIGINT DEFAULT 60000 COMMENT '超时时间（毫秒）',
  `sla_ms` BIGINT DEFAULT NULL COMMENT 'SLA达标阈值（毫秒）',
  `slow_threshold_ms` BIGINT DEFAULT NULL COMMENT '慢任务阈值（毫秒）',
  `misfire_policy` VARCHAR(16) DEFAULT 'DO_NOTHING' COMMENT 'Misfire策略',
  `shard_total` INT DEFAULT 1 COMMENT '分片总数',
  `job_type` VARCHAR(16) DEFAULT 'NORMAL' COMMENT '任务类型: NORMAL/DAG_NODE',
  `max_retries` INT DEFAULT 0 COMMENT '最大重试次数',
  `retry_interval_ms` BIGINT DEFAULT 1000 COMMENT '重试间隔（毫秒）',
  `retry_backoff` VARCHAR(16) DEFAULT 'FIXED' COMMENT '重试退避策略: FIXED/EXPONENTIAL',
  `block_strategy` VARCHAR(16) DEFAULT 'SERIAL_EXECUTION' COMMENT '阻塞策略',
  `consecutive_fail_count` INT DEFAULT 0 COMMENT '连续失败计数',
  `max_consecutive_fails` INT DEFAULT 5 COMMENT '最大连续失败次数（熔断阈值）',
  `auto_resume_after_minutes` INT DEFAULT 30 COMMENT '自动恢复时间（分钟）',
  `priority` INT DEFAULT 0 COMMENT '优先级（数值越大优先级越高）',
  `version` INT DEFAULT 0 COMMENT '版本号（乐观锁）',
  `timezone` VARCHAR(32) DEFAULT 'Asia/Shanghai' COMMENT '时区',
  `cluster` VARCHAR(64) DEFAULT NULL COMMENT '集群标识',
  `canary_ratio` INT DEFAULT 0 COMMENT '灰度比例（0-100）',
  `canary_handler` VARCHAR(256) DEFAULT NULL COMMENT '灰度处理器',
  `status` VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT '状态: NORMAL/PAUSED/AUTO_PAUSED/ERROR/STOPPED',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=未删除, 1=已删除',
  `tenant_id` VARCHAR(32) NOT NULL DEFAULT '1' COMMENT '租户ID',
  `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `revision` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_key` (`job_key`),
  KEY `idx_job_group` (`job_group`),
  KEY `idx_status` (`status`),
  KEY `idx_next_fire_time` (`next_fire_time`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务定义主表';

-- ---------------------------------------------------------------------------
-- 2. 任务执行日志表（ydsz_job_log）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ydsz_job_log` (
  `id` VARCHAR(32) NOT NULL COMMENT '主键（雪花ID）',
  `job_id` VARCHAR(32) NOT NULL COMMENT '任务ID',
  `job_key` VARCHAR(128) NOT NULL COMMENT '任务KEY',
  `start_time` DATETIME DEFAULT NULL COMMENT '开始时间',
  `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
  `duration_ms` BIGINT DEFAULT NULL COMMENT '耗时（毫秒）',
  `error_message` TEXT DEFAULT NULL COMMENT '错误信息',
  `params_json` TEXT DEFAULT NULL COMMENT '参数JSON',
  `result_json` TEXT DEFAULT NULL COMMENT '结果JSON',
  `trace_id` VARCHAR(64) DEFAULT NULL COMMENT '链路追踪ID',
  `trigger_type` VARCHAR(16) DEFAULT NULL COMMENT '触发类型: CRON/MANUAL/RETRY/MISFIRED',
  `lock_holder` VARCHAR(128) DEFAULT NULL COMMENT '持锁者标识',
  `exec_node_id` VARCHAR(128) DEFAULT NULL COMMENT '执行节点ID',
  `exec_thread_id` VARCHAR(64) DEFAULT NULL COMMENT '执行线程ID',
  `queue_time` DATETIME DEFAULT NULL COMMENT '入队时间',
  `dispatch_time` DATETIME DEFAULT NULL COMMENT '派发时间',
  `handler_init_time` DATETIME DEFAULT NULL COMMENT '处理器初始化时间',
  `handler_end_time` DATETIME DEFAULT NULL COMMENT '处理器结束时间',
  `status` VARCHAR(16) NOT NULL DEFAULT 'RUNNING' COMMENT '状态: RUNNING/SUCCESS/FAILED/TIMEOUT',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `tenant_id` VARCHAR(32) NOT NULL DEFAULT '1' COMMENT '租户ID',
  `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_job_id` (`job_id`),
  KEY `idx_job_key` (`job_key`),
  KEY `idx_start_time` (`start_time`),
  KEY `idx_status` (`status`),
  KEY `idx_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行日志表';

-- ---------------------------------------------------------------------------
-- 3. 任务节点注册表（ydsz_job_node）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ydsz_job_node` (
  `id` VARCHAR(32) NOT NULL COMMENT '主键（雪花ID）',
  `node_id` VARCHAR(128) NOT NULL COMMENT '节点唯一标识（hostname:port）',
  `node_name` VARCHAR(128) DEFAULT NULL COMMENT '节点名称',
  `node_ip` VARCHAR(64) DEFAULT NULL COMMENT '节点IP',
  `cluster` VARCHAR(64) DEFAULT NULL COMMENT '集群标识',
  `last_heartbeat` DATETIME DEFAULT NULL COMMENT '最后心跳时间',
  `status` VARCHAR(16) NOT NULL DEFAULT 'ONLINE' COMMENT '状态: ONLINE/OFFLINE/DRAINING',
  `concurrent_count` INT DEFAULT 0 COMMENT '当前并发数',
  `max_concurrent` INT DEFAULT 16 COMMENT '最大并发数',
  `version` VARCHAR(32) DEFAULT NULL COMMENT '应用版本',
  `metadata` TEXT DEFAULT NULL COMMENT '扩展元数据JSON',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `tenant_id` VARCHAR(32) NOT NULL DEFAULT '1' COMMENT '租户ID',
  `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `revision` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_node_id` (`node_id`),
  KEY `idx_cluster` (`cluster`),
  KEY `idx_status` (`status`),
  KEY `idx_last_heartbeat` (`last_heartbeat`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行节点注册表';

-- ---------------------------------------------------------------------------
-- 4. 任务分片表（ydsz_job_task）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ydsz_job_task` (
  `id` VARCHAR(32) NOT NULL COMMENT '主键（雪花ID）',
  `job_id` VARCHAR(32) NOT NULL COMMENT '任务ID',
  `shard_index` INT NOT NULL COMMENT '分片索引（0-based）',
  `shard_total` INT NOT NULL COMMENT '分片总数',
  `node_id` VARCHAR(128) DEFAULT NULL COMMENT '执行节点ID',
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/RUNNING/SUCCESS/FAILED',
  `start_time` DATETIME DEFAULT NULL COMMENT '开始时间',
  `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
  `result_json` TEXT DEFAULT NULL COMMENT '结果JSON',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `tenant_id` VARCHAR(32) NOT NULL DEFAULT '1' COMMENT '租户ID',
  `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_job_id` (`job_id`),
  KEY `idx_status` (`status`),
  KEY `idx_node_id` (`node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务分片执行表';
