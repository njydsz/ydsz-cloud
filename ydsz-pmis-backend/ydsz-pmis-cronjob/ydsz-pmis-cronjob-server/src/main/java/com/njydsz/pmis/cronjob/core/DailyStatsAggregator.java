package com.njydsz.pmis.cronjob.server.core.stats;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.njydsz.pmis.cronjob.web.config.CronjobProperties;
import com.njydsz.pmis.cronjob.server.core.leader.LeaderElector;
import com.njydsz.pmis.cronjob.domain.entity.log.JobDailyStatsDO;
import com.njydsz.pmis.cronjob.infra.mapper.log.JobDailyStatsMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 每日统计聚合器（P2-3 执行历史趋势可视化）。
 *
 * <p>仅当 {@code pmis.cronjob.leader.enabled=true} 且当前节点是 Leader 时启用。
 * 每天凌晨 1 点定时聚合昨天 {@code pmis_job_log} 的执行数据，写入
 * {@code pmis_job_daily_stats}，供前端趋势图展示。
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li><b>Leader 独占</b>：通过 {@link LeaderElector#isLeader(String)} 判定，
 *       避免多实例重复聚合</li>
 *   <li><b>幂等</b>：使用 PostgreSQL UPSERT（ON CONFLICT），重跑时覆盖旧值</li>
 *   <li><b>容错</b>：单任务聚合异常不影响其他任务</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(LeaderElector.class)
public class DailyStatsAggregator {

    private final JobDailyStatsMapper jobDailyStatsMapper;
    private final LeaderElector leaderElector;
    private final CronjobProperties cronjobProperties;

    private String leaderRole;

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
        log.info("[DailyStatsAggregator] 开始聚合每日统计: statsDate={} start={} end={}",
                statsDate, start, end);

        List<Map<String, Object>> rows;
        try {
            rows = jobDailyStatsMapper.aggregateDaily(start, end);
        } catch (Exception e) {
            log.error("[DailyStatsAggregator] 聚合查询异常: statsDate={} reason={}",
                    statsDate, e.getMessage(), e);
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
                JobDailyStatsDO stats = toStats(row, statsDate);
                jobDailyStatsMapper.upsert(stats);
                success++;
            } catch (Exception e) {
                failed++;
                log.error("[DailyStatsAggregator] 单任务聚合写入异常: jobId={} statsDate={} reason={}",
                        row.get("job_id"), statsDate, e.getMessage(), e);
            }
        }
        log.info("[DailyStatsAggregator] 聚合完成: statsDate={} total={} success={} failed={}",
                statsDate, rows.size(), success, failed);
    }

    /**
     * 将聚合查询的 Map 行转换为 {@link JobDailyStatsDO} 实体。
     *
     * @param row       聚合行
     * @param statsDate 统计日期
     * @return 统计实体
     */
    private JobDailyStatsDO toStats(Map<String, Object> row, LocalDate statsDate) {
        JobDailyStatsDO stats = new JobDailyStatsDO();
        stats.setId(IdWorker.getIdStr());
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
        stats.setDeleted(0);
        return stats;
    }

    /**
     * 安全将 Map 中的统计值转为 long（兼容 Number / String，null 返回 0）。
     */
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
