package com.njydsz.cronjob.server.service.impl;

import java.time.LocalDateTime;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.njydsz.common.audit.storage.JdbcAuditStorage;
import com.njydsz.common.locales.util.I18nMessages;
import com.njydsz.cronjob.domain.repository.JobHistoryRepository;
import com.njydsz.cronjob.server.config.ArchivalConfig;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.leader.LeaderElector;
import com.njydsz.cronjob.server.service.DataCleanupService;

/**
 * 数据归档 TTL 清理服务实现（P2-6）。
 *
 * <p>定时清理过期数据：
 *
 * <ul>
 *   <li>ydsz_job_history：保留 90 天，超出软删除（is_deleted = true）
 *   <li>sys_audit_log：保留 180 天，超出归档或物理删除
 * </ul>
 *
 * <h3>设计要点</h3>
 *
 * <ul>
 *   <li><b>Leader 独占</b>：仅 Leader 节点执行，避免多实例重复处理
 *   <li><b>批量软删除</b>：每批最多 500 条，循环执行直至无过期数据
 *   <li><b>dryRun 模式</b>：仅查询待处理条数但不实际修改
 *   <li><b>审计日志联动</b>：可选委托 JdbcAuditStorage，不存在时静默跳过
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(LeaderElector.class)
public class DataCleanupServiceImpl implements DataCleanupService {

  /** 单表清理最大循环次数（防止异常情况下无限循环） */
  private static final int MAX_BATCH_LOOPS = 200;

  private final JobHistoryRepository jobHistoryRepository;
  private final LeaderElector leaderElector;
  private final CronjobProperties cronjobProperties;
  private final I18nMessages i18nMessages;

  /** 审计日志存储（可选 Bean，未引入 common-audit 时为 null） */
  private final ObjectProvider<JdbcAuditStorage> jdbcAuditStorageProvider;

  private String leaderRole;

  @PostConstruct
  public void init() {
    this.leaderRole = cronjobProperties.getLeader().getRole();
    ArchivalConfig cfg = cronjobProperties.getArchival();
    if (cronjobProperties.getLeader().isEnabled()) {
      log.info(
          "[DataCleanup] 初始化完成, role={} historyRetention={}d auditRetention={}d batchSize={}",
          leaderRole,
          cfg.getJobHistoryRetentionDays(),
          cfg.getAuditLogRetentionDays(),
          cfg.getBatchSize());
    } else {
      log.info("[DataCleanup] leader.enabled=false, 数据归档 TTL 不启用");
    }
  }

  @Override
  public int cleanupExpiredJobHistory(int retentionDays, boolean dryRun) {
    LocalDateTime before = LocalDateTime.now().minusDays(retentionDays);
    int batchSize = cronjobProperties.getArchival().getBatchSize();

    if (dryRun) {
      int count = jobHistoryRepository.countExpired(before);
      log.info("[DataCleanup] dryRun — Job 历史过期条数: count={} retentionDays={}", count, retentionDays);
      return count;
    }

    int total = 0;
    int loops = 0;
    while (loops < MAX_BATCH_LOOPS) {
      int deleted = jobHistoryRepository.softDeleteExpired(before, batchSize);
      total += deleted;
      loops++;
      if (deleted < batchSize) {
        break;
      }
    }
    if (total > 0) {
      log.info("[DataCleanup] Job 历史软删除完成: deleted={} loops={} retentionDays={}", total, loops, retentionDays);
    }
    return total;
  }

  @Override
  public int archiveExpiredAuditLogs(int retentionDays, boolean dryRun) {
    JdbcAuditStorage auditStorage = jdbcAuditStorageProvider.getIfAvailable();
    if (auditStorage == null) {
      log.info("[DataCleanup] JdbcAuditStorage 不可用, 跳过审计日志归档");
      return 0;
    }

    if (dryRun) {
      // JdbcAuditStorage 无 count 方法，dryRun 时直接返回 0 并提示
      log.info("[DataCleanup] dryRun — 审计日志归档预览需查库统计, retentionDays={}", retentionDays);
      return 0;
    }

    try {
      int deleted = auditStorage.cleanupExpired(retentionDays);
      if (deleted > 0) {
        log.info("[DataCleanup] 审计日志归档完成: deleted={} retentionDays={}", deleted, retentionDays);
      }
      return deleted;
    } catch (Exception e) {
      log.error("[DataCleanup] 审计日志归档异常: reason={}", e.getMessage(), e);
      return 0;
    }
  }

  @Override
  public int runAllCleanup(boolean dryRun) {
    ArchivalConfig cfg = cronjobProperties.getArchival();
    int historyCount = cleanupExpiredJobHistory(cfg.getJobHistoryRetentionDays(), dryRun);
    int auditCount = archiveExpiredAuditLogs(cfg.getAuditLogRetentionDays(), dryRun);
    int total = historyCount + auditCount;
    log.info("[DataCleanup] 全量 TTL 清理完成: historyCount={} auditCount={} dryRun={}", historyCount, auditCount, dryRun);
    return total;
  }

  /**
   * 定时执行全量 TTL 清理（每天由 Leader 节点执行）。
   *
   * <p>cron 可通过 {@code ydsz.cronjob.archival.cron} 配置覆盖。
   */
  @Scheduled(cron = "${ydsz.cronjob.archival.cron:0 0 4 * * ?}")
  public void scheduledCleanup() {
    if (!cronjobProperties.getLeader().isEnabled()) {
      return;
    }
    if (!leaderElector.isLeader(leaderRole)) {
      return;
    }
    log.info("[DataCleanup] 开始定时 TTL 清理");
    runAllCleanup(false);
  }
}
