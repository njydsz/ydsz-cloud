package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * P2-2: 日志归档清理配置。
 *
 * <p>控制 LogCleaner 的清理行为：
 *
 * <ul>
 *   <li>{@link #retentionDays} 日志保留天数（超过此天数的日志将被清理，默认 30 天）
 *   <li>{@link #batchSize} 单批删除条数（避免大事务锁表，默认 1000 条/批）
 * </ul>
 *
 * <p>清理范围：ydsz_job_log / ydsz_job_log_content / ydsz_job_alert_log / ydsz_job_task，每天凌晨 3 点由 Leader 节点执行。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class LogRetentionConfig {

  /** 默认retentionDays值（可被配置文件覆盖） */
  private static final int DEFAULT_RETENTION_DAYS = 30;

  /** 默认batchSize值（可被配置文件覆盖） */
  private static final int DEFAULT_BATCH_SIZE = 1000;

  /** 日志保留天数（超过此天数的日志将被硬删除，默认 30 天） */
  private int retentionDays = DEFAULT_RETENTION_DAYS;

  /** 单批删除条数（避免大事务锁表，默认 1000 条/批） */
  private int batchSize = DEFAULT_BATCH_SIZE;

  /** 定时清理 cron 表达式（默认每天凌晨 3 点：0 0 3 * * ?） */
  private String cron = "0 0 3 * * ?";
}
