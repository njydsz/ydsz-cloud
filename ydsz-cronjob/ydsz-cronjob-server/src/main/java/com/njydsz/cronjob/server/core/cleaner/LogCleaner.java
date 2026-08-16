package com.njydsz.cronjob.server.core.cleaner;

import java.time.LocalDateTime;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.njydsz.common.audit.storage.JdbcAuditStorage;
import com.njydsz.cronjob.infra.mapper.job.JobAlertLogMapper;
import com.njydsz.cronjob.infra.mapper.job.JobHistoryMapper;
import com.njydsz.cronjob.infra.mapper.job.JobTaskMapper;
import com.njydsz.cronjob.infra.mapper.log.JobLogContentMapper;
import com.njydsz.cronjob.infra.mapper.log.JobLogMapper;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.leader.LeaderElector;

/**
 * 日志归档清理器（P2-2）。
 *
 * <p>仅当 {@code ydsz.cronjob.leader.enabled=true} 且当前节点是 Leader 时启用。 每天凌晨 3 点定时清理超过保留天数的日志记录，释放磁盘空间。
 *
 * <h3>清理范围</h3>
 *
 * <ul>
 *   <li>ydsz_job_log：任务执行日志（含 is_slow 慢任务标记）
 *   <li>ydsz_job_log_content：任务日志内容（在线日志白屏化）
 *   <li>ydsz_job_alert_log：告警日志
 *   <li>ydsz_job_task：MapReduce 子任务记录
 *   <li>ydsz_job_history：任务配置历史版本
 *   <li>sys_audit_log：审计日志（v1.2.0 新增，由 ydsz-common-audit 模块提供清理能力）
 * </ul>
 *
 * <h3>设计要点</h3>
 *
 * <ul>
 *   <li><b>Leader 独占</b>：通过 {@link LeaderElector#isLeader(String)} 判定， 避免多实例重复清理
 *   <li><b>批量删除</b>：每批最多删除 {@code batchSize} 条（默认 1000）， 循环执行直至无过期数据，避免大事务锁表
 *   <li><b>容错隔离</b>：单表清理异常不影响其他表，每表独立 try-catch
 *   <li><b>硬删除</b>：使用 DELETE 物理删除，真正释放磁盘空间（非逻辑删除）
 *   <li><b>审计日志联动</b>：当容器中存在 {@link JdbcAuditStorage} Bean 时， 自动清理 sys_audit_log
 *       中超过保留天数的记录，生命周期与审计配置一致
 * </ul>
 *
 * <h3>对标</h3>
 *
 * <p>对标 XXL-Job 的日志清理机制（logCleanThresholdDays + 定时清理）， 提供可配置的日志生命周期管理能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnBean(LeaderElector.class)
public class LogCleaner {

  private final JobLogMapper jobLogMapper;
  private final JobLogContentMapper jobLogContentMapper;
  private final JobAlertLogMapper jobAlertLogMapper;
  private final JobTaskMapper jobTaskMapper;
  private final JobHistoryMapper jobHistoryMapper;
  private final LeaderElector leaderElector;
  private final CronjobProperties cronjobProperties;

  /**
   * 审计日志存储（可选 Bean）。
   *
   * <p>当项目引入 ydsz-common-audit 模块时，Spring 容器中存在 {@link JdbcAuditStorage}， 定时清理任务会自动联动清理
   * sys_audit_log 表；未引入时静默跳过。
   *
   * <p>使用 {@code @Autowired(required = false)} 保持对 audit 模块的零硬依赖， 符合云顶编码规范「公共能力优先使用 common
   * 模块，但不强制依赖」原则。
   */
  @Autowired(required = false)
  private JdbcAuditStorage jdbcAuditStorage;

  /** 单表清理最大循环次数（防止异常情况下无限循环） */
  private static final int MAX_BATCH_LOOPS = 100;

  private String leaderRole;

  /**
   * 构造日志清理器
   *
   * <p>使用构造器注入核心依赖（LeaderElector 相关），保证启动时依赖校验。 审计日志存储使用字段注入（required=false），适配无 common-audit 的场景。
   *
   * @param jobLogMapper 任务日志 Mapper
   * @param jobLogContentMapper 任务日志内容 Mapper
   * @param jobAlertLogMapper 告警日志 Mapper
   * @param jobTaskMapper 任务记录 Mapper
   * @param jobHistoryMapper 任务历史 Mapper
   * @param leaderElector 选举器
   * @param cronjobProperties 定时任务配置
   */
  public LogCleaner(
      JobLogMapper jobLogMapper,
      JobLogContentMapper jobLogContentMapper,
      JobAlertLogMapper jobAlertLogMapper,
      JobTaskMapper jobTaskMapper,
      JobHistoryMapper jobHistoryMapper,
      LeaderElector leaderElector,
      CronjobProperties cronjobProperties) {
    this.jobLogMapper = jobLogMapper;
    this.jobLogContentMapper = jobLogContentMapper;
    this.jobAlertLogMapper = jobAlertLogMapper;
    this.jobTaskMapper = jobTaskMapper;
    this.jobHistoryMapper = jobHistoryMapper;
    this.leaderElector = leaderElector;
    this.cronjobProperties = cronjobProperties;
  }

  /**
   * 初始化清理器：解析 Leader 角色名并输出启用状态。
   *
   * <p>由 {@code @PostConstruct} 在 Bean 创建后调用，缓存 leader 角色名， 供 {@link #clean()} 判定本节点是否执行凌晨清理任务。
   */
  @PostConstruct
  public void init() {
    this.leaderRole = cronjobProperties.getLeader().getRole();
    CronjobProperties.LogRetention cfg = cronjobProperties.getLogRetention();
    if (cronjobProperties.getLeader().isEnabled()) {
      log.info(
          "[LogCleaner] 初始化完成, role={} retentionDays={} batchSize={} auditClean={}",
          leaderRole,
          cfg.getRetentionDays(),
          cfg.getBatchSize(),
          jdbcAuditStorage != null);
    } else {
      log.info("[LogCleaner] leader.enabled=false, 日志清理不启用");
    }
  }

  /**
   * 定时清理过期日志（每天凌晨 3 点执行）。
   *
   * <p>使用 cron 表达式 {@code 0 0 3 * * ?}（秒 分 时 日 月 周）， 可通过配置 {@code ydsz.cronjob.log-retention.cron}
   * 覆盖。
   */
  @Scheduled(cron = "${ydsz.cronjob.log-retention.cron:0 0 3 * * ?}")
  public void clean() {
    if (!cronjobProperties.getLeader().isEnabled()) {
      return;
    }
    if (!leaderElector.isLeader(leaderRole)) {
      return;
    }

    CronjobProperties.LogRetention cfg = cronjobProperties.getLogRetention();
    LocalDateTime before = LocalDateTime.now().minusDays(cfg.getRetentionDays());
    int batchSize = cfg.getBatchSize();

    log.info(
        "[LogCleaner] 开始清理过期日志: before={} retentionDays={} batchSize={}",
        before,
        cfg.getRetentionDays(),
        batchSize);

    long totalCleaned = 0;
    // 每张表独立清理，单表异常不影响其他表
    totalCleaned += cleanTable("ydsz_job_log", before, batchSize, jobLogMapper::cleanExpiredLogs);
    totalCleaned +=
        cleanTable(
            "ydsz_job_log_content", before, batchSize, jobLogContentMapper::cleanExpiredLogs);
    totalCleaned +=
        cleanTable("ydsz_job_alert_log", before, batchSize, jobAlertLogMapper::cleanExpiredLogs);
    totalCleaned += cleanTable("ydsz_job_task", before, batchSize, jobTaskMapper::cleanExpiredLogs);
    totalCleaned +=
        cleanTable("ydsz_job_history", before, batchSize, jobHistoryMapper::cleanExpiredLogs);

    // 审计日志清理（sys_audit_log）— 联动 ydsz-common-audit 模块能力
    totalCleaned += cleanAuditLogs(cfg.getRetentionDays());

    log.info("[LogCleaner] 清理完成: totalCleaned={}", totalCleaned);
  }

  /**
   * 清理审计日志表（sys_audit_log）中的过期记录。
   *
   * <p>委托 {@link JdbcAuditStorage#cleanExpiredLogs(int)} 执行物理删除， 保留天数与 cronjob 日志保留策略保持一致（可通过
   * ydsz.audit.retentionDays 独立配置）。
   *
   * <p>当 {@link JdbcAuditStorage} 不存在于容器时（项目未引入 common-audit 模块），静默跳过。
   *
   * @param retentionDays 日志保留天数
   * @return 实际删除条数
   */
  private long cleanAuditLogs(int retentionDays) {
    if (jdbcAuditStorage == null) {
      return 0;
    }
    try {
      int deleted = jdbcAuditStorage.cleanExpiredLogs(retentionDays);
      if (deleted > 0) {
        log.info("[LogCleaner] 审计日志表 sys_audit_log 清理完成: deleted={}", deleted);
      }
      return deleted;
    } catch (Exception e) {
      log.error("[LogCleaner] 审计日志表 sys_audit_log 清理异常: reason={}", e.getMessage(), e);
      return 0;
    }
  }

  /**
   * 清理单张表的过期数据，循环批量删除直至无数据或达到最大循环次数。
   *
   * <p>容错设计：单表清理异常被捕获并记录日志，不影响后续表清理。
   *
   * @param tableName 表名（仅用于日志展示）
   * @param before 过期分界时间
   * @param batchSize 单批删除条数
   * @param cleaner 清理函数（返回实际删除条数）
   * @return 累计删除条数
   */
  private long cleanTable(
      String tableName, LocalDateTime before, int batchSize, CleanFunction cleaner) {
    long total = 0;
    try {
      int loops = 0;
      while (loops < MAX_BATCH_LOOPS) {
        int deleted = cleaner.clean(before, batchSize);
        total += deleted;
        loops++;
        if (deleted < batchSize) {
          // 删除条数不足 batchSize，说明已无过期数据
          break;
        }
      }
      if (total > 0) {
        log.info("[LogCleaner] 表 {} 清理完成: deleted={} loops={}", tableName, total, loops);
      }
    } catch (Exception e) {
      log.error("[LogCleaner] 表 {} 清理异常: reason={}", tableName, e.getMessage(), e);
    }
    return total;
  }

  /** 清理函数接口（用于方法引用传递 Mapper 的 cleanExpiredLogs 方法）。 */
  @FunctionalInterface
  interface CleanFunction {
    int clean(LocalDateTime before, int limit);
  }
}
