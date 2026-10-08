package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * P2-6: 数据归档 TTL 配置。
 *
 * <p>控制 {@link com.njydsz.cronjob.server.service.DataCleanupService} 的归档清理行为：
 *
 * <ul>
 *   <li>{@link #jobHistoryRetentionDays} Job 执行历史保留天数（超出后软删除，默认 90 天）
 *   <li>{@link #auditLogRetentionDays} 审计日志保留天数（超出后归档或软删除，默认 180 天）
 *   <li>{@link #batchSize} 单批处理条数（避免大事务锁表，默认 500 条/批）
 * </ul>
 *
 * <p>清理任务每天由 Leader 节点执行一次。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
public class ArchivalConfig {

  /** Job 执行历史默认保留天数 */
  private static final int DEFAULT_JOB_HISTORY_RETENTION_DAYS = 90;

  /** 审计日志默认保留天数 */
  private static final int DEFAULT_AUDIT_LOG_RETENTION_DAYS = 180;

  /** 默认批处理条数 */
  private static final int DEFAULT_BATCH_SIZE = 500;

  /** Job 执行历史保留天数（超出后标记 is_deleted） */
  private int jobHistoryRetentionDays = DEFAULT_JOB_HISTORY_RETENTION_DAYS;

  /** 审计日志保留天数（超出后归档或软删除） */
  private int auditLogRetentionDays = DEFAULT_AUDIT_LOG_RETENTION_DAYS;

  /** 单批处理条数（避免大事务锁表） */
  private int batchSize = DEFAULT_BATCH_SIZE;

  /** 定时归档 cron 表达式（默认每天凌晨 4 点：0 0 4 * * ?） */
  private String cron = "0 0 4 * * ?";
}
