package com.njydsz.cronjob.server.core.stats;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.njydsz.common.lock.annotation.DistributedScheduled;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.cronjob.domain.repository.JobDailyStatsRepository;
import com.njydsz.cronjob.domain.vo.JobDailyStatsVO;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.leader.LeaderElector;

/**
 * 每日统计聚合器（P2-3 执行历史趋势可视化）。
 *
 * <p>仅当 {@code ydsz.cronjob.leader.enabled=true} 且当前节点是 Leader 时启用。 每天凌晨 1 点定时聚合昨天 {@code
 * ydsz_job_log} 的执行数据，写入 {@code ydsz_job_daily_stats}，供前端趋势图展示。
 *
 * <h3>设计要点</h3>
 *
 * <ul>
 *   <li><b>Leader 独占</b>：通过 {@link LeaderElector#isLeader(String)} 判定， 避免多实例重复聚合
 *   <li><b>幂等</b>：使用 PostgreSQL UPSERT（ON CONFLICT），重跑时覆盖旧值
 *   <li><b>容错</b>：单任务聚合异常不影响其他任务
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(LeaderElector.class)
public class DailyStatsAggregator {

  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  private final JobDailyStatsRepository jobDailyStatsRepository;
  private final LeaderElector leaderElector;
  private final CronjobProperties cronjobProperties;

  private String leaderRole;

  /**
   * 初始化每日统计聚合器：解析 Leader 角色并确认启用状态。
   *
   * <p>仅在 {@code ydsz.cronjob.leader.enabled=true} 时进入聚合启用分支； 否则仅记录 Leaderless 日志。实际聚合由 {@link
   * #aggregate()} 上的 {@code @DistributedScheduled} 保证全集群唯一执行，本方法不预分配资源（聚合写入走 PostgreSQL UPSERT
   * 幂等，无需初始化缓存）。
   */
  @PostConstruct
  public void init() {
    this.leaderRole = cronjobProperties.getLeader().getRole();
    if (cronjobProperties.getLeader().isEnabled()) {
      log.info("[DailyStatsAggregator] 初始化完成, role={}", leaderRole);
    } else {
      log.info("[DailyStatsAggregator] leader.enabled=false, 每日统计聚合不启用");
    }
  }

  /**
   * 每天凌晨 1 点聚合昨天的执行日志。
   *
   * <p>使用 cron 表达式 {@code 0 0 1 * * ?}（秒 分 时 日 月 周）。
   */
  @DistributedScheduled(lockKey = "cronjob:daily-stats")
  @Scheduled(cron = "0 0 1 * * ?")
  public void aggregate() {
    if (!cronjobProperties.getLeader().isEnabled()) {
      return;
    }
    if (!leaderElector.isLeader(leaderRole)) {
      return;
    }
    // 聚合昨天 [00:00, 24:00) 的数据
    LocalDate yesterday = LocalDate.now().minusDays(1);
    aggregateForDate(yesterday);
  }

  /**
   * 聚合指定日期的执行日志（包可见，便于单元测试与手动补数据）。
   *
   * @param statsDate 统计日期
   */
  void aggregateForDate(LocalDate statsDate) {
    LocalDateTime start = statsDate.atStartOfDay();
    LocalDateTime end = statsDate.plusDays(1).atStartOfDay();
    log.info(
        "[DailyStatsAggregator] 开始聚合每日统计: statsDate={} start={} end={}", statsDate, start, end);

    List<Map<String, Object>> rows;
    try {
      rows = jobDailyStatsRepository.aggregateDaily(start, end);
    } catch (Exception e) {
      log.error(
          "[DailyStatsAggregator] 聚合查询异常: statsDate={} reason={}", statsDate, e.getMessage(), e);
      return;
    }

    if (rows == null || rows.isEmpty()) {
      log.info("[DailyStatsAggregator] 昨天无执行记录, 跳过: statsDate={}", statsDate);
      return;
    }

    int success = 0;
    int failed = 0;
    for (Map<String, Object> row : rows) {
      try {
        JobDailyStatsVO stats = toStats(row, statsDate);
        jobDailyStatsRepository.upsert(stats);
        success++;
      } catch (Exception e) {
        failed++;
        log.error(
            "[DailyStatsAggregator] 单任务聚合写入异常: jobId={} statsDate={} reason={}",
            row.get("job_id"),
            statsDate,
            e.getMessage(),
            e);
      }
    }
    log.info(
        "[DailyStatsAggregator] 聚合完成: statsDate={} total={} success={} failed={}",
        statsDate,
        rows.size(),
        success,
        failed);
  }

  /**
   * 将聚合查询的 Map 行转换为 {@link JobDailyStatsVO}。
   *
   * @param row 聚合行
   * @param statsDate 统计日期
   * @return 统计 VO
   */
  private JobDailyStatsVO toStats(Map<String, Object> row, LocalDate statsDate) {
    JobDailyStatsVO stats = new JobDailyStatsVO();
    stats.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    stats.setJobId((String) row.get("job_id"));
    stats.setJobKey((String) row.get("job_key"));
    stats.setStatsDate(statsDate);
    stats.setFireCount(toLong(row.get("fire_count")));
    stats.setSuccessCount(toLong(row.get("success_count")));
    stats.setFailCount(toLong(row.get("fail_count")));
    stats.setTimeoutCount(toLong(row.get("timeout_count")));
    stats.setAvgDurationMs(toLongOrNull(row.get("avg_duration_ms")));
    stats.setMaxDurationMs(toLongOrNull(row.get("max_duration_ms")));
    stats.setMinDurationMs(toLongOrNull(row.get("min_duration_ms")));
    stats.setP95DurationMs(toLongOrNull(row.get("p95_duration_ms")));
    stats.setCreatedAt(LocalDateTime.now());
    return stats;
  }

  /** 安全将 Map 中的统计值转为 long（兼容 Number / String，null 返回 0）。 */
  private long toLong(Object value) {
    if (value == null) {
      return 0L;
    }
    if (value instanceof Number n) {
      return n.longValue();
    }
    try {
      return Long.parseLong(value.toString());
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  /**
   * 安全将 Map 中的耗时值转为 Long（兼容 Number / String，null 返回 null）。
   *
   * <p>耗时字段（avg/max/min/p95）在无执行记录时可能为 null，需保留 null 语义。
   */
  private Long toLongOrNull(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number n) {
      return n.longValue();
    }
    try {
      return Long.parseLong(value.toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
