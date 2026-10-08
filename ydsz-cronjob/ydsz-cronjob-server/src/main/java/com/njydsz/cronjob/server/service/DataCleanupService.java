package com.njydsz.cronjob.server.service;

/**
 * 数据归档 TTL 清理服务（P2-6）。
 *
 * <p>提供 Job 执行历史软删除、审计日志归档的 TTL 自动管理能力，兼具 dryRun 预览模式。
 *
 * <p>与 {@link com.njydsz.cronjob.server.core.cleaner.LogCleaner} 互补：
 *
 * <ul>
 *   <li>LogCleaner：物理删除 ydsz_job_log / ydsz_job_log_content 等日志表（硬删除）
 *   <li>DataCleanupService：软删除 ydsz_job_history + 归档 ydsz_job_audit_log（TTL 归档）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public interface DataCleanupService {

  /**
   * 清理过期 Job 执行历史（软删除）。
   *
   * <p>将 changed_at 早于 {@code retentionDays} 前的记录标记 is_deleted = true。
   *
   * @param retentionDays 保留天数（超出此天数的历史记录被软删除）
   * @param dryRun 仅预览不实际操作（true = 只返回将要删除的条数）
   * @return 实际软删除条数（dryRun 模式下返回预览条数）
   */
  int cleanupExpiredJobHistory(int retentionDays, boolean dryRun);

  /**
   * 归档过期审计日志。
   *
   * <p>当 {@code sys_audit_log_archive} 表存在时，将过期记录迁移至归档表后物理删除；
   * 归档表不存在时，退化为调用 {@link com.njydsz.common.audit.storage.JdbcAuditStorage#cleanupExpired(int)} 物理删除。
   *
   * @param retentionDays 保留天数（超出此天数的审计日志被归档/删除）
   * @param dryRun 仅预览不实际操作
   * @return 实际归档/删除条数（dryRun 模式下返回预览条数）
   */
  int archiveExpiredAuditLogs(int retentionDays, boolean dryRun);

  /**
   * 执行全量 TTL 清理（Job 历史 + 审计日志）。
   *
   * <p>使用配置中的默认保留天数，每天由 Leader 节点调度执行。
   *
   * @param dryRun 仅预览不实际操作
   * @return 合计处理条数（History 条数 + Audit 条数）
   */
  int runAllCleanup(boolean dryRun);
}
