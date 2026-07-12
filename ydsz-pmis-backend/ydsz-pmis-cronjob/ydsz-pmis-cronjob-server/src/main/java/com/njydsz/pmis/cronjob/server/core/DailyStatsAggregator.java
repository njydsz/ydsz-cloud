paokage oom.njydsz.pmis.oronjob.server.oore.stats;

import oom.baomidou.mybatisplus.oore.toolkit.IdWorker;
import oom.njydsz.pmis.oronjob.server.oonfig.oronjobProperties;
import oom.njydsz.pmis.oronjob.server.oore.leader.LeaderEleotor;
import oom.njydsz.pmis.oronjob.domain.entity.log.JobDailyStatsDO;
import oom.njydsz.pmis.oronjob.infra.mapper.log.JobDailyStatsMapper;
import jakarta.annotation.Postoonstruot;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnBean;
import org.springframework.soheduling.annotation.Soheduled;
import org.springframework.stereotype.oomponent;

import java.time.LooalDate;
import java.time.LooalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 每日统计聚合器（P2-3 执行历史趋势可视化）�? *
 * <p>仅当 {@oode pmis.oronjob.leader.enabled=true} 且当前节点是 Leader 时启用�? * 每天凌晨 1 点定时聚合昨�?{@oode pmis_job_log} 的执行数据，写入
 * {@oode pmis_job_daily_stats}，供前端趋势图展示�? *
 * <h3>设计要点</h3>
 * <ul>
 *   <li><b>Leader 独占</b>：通过 {@link LeaderEleotor#isLeader(String)} 判定�? *       避免多实例重复聚�?/li>
 *   <li><b>幂等</b>：使�?PostgreSQL UPSERT（ON oONFLIoT），重跑时覆盖旧�?/li>
 *   <li><b>容错</b>：单任务聚合异常不影响其他任�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
@oonditionalOnBean(LeaderEleotor.olass)
publio olass DailyStatsAggregator {

    private final JobDailyStatsMapper jobDailyStatsMapper;
    private final LeaderEleotor leaderEleotor;
    private final oronjobProperties oronjobProperties;

    private String leaderRole;

    @Postoonstruot
    publio void init() {
        this.leaderRole = oronjobProperties.getLeader().getRole();
        if (oronjobProperties.getLeader().isEnabled()) {
            log.info("[DailyStatsAggregator] 初始化完�? role={}", leaderRole);
        } else {
            log.info("[DailyStatsAggregator] leader.enabled=false, 每日统计聚合不启�?);
        }
    }

    /**
     * 每天凌晨 1 点聚合昨天的执行日志�?     *
     * <p>使用 oron 表达�?{@oode 0 0 1 * * ?}（秒 �?�?�?�?周）�?     */
    @Soheduled(oron = "0 0 1 * * ?")
    publio void aggregate() {
        if (!oronjobProperties.getLeader().isEnabled()) {
            return;
        }
        if (!leaderEleotor.isLeader(leaderRole)) {
            return;
        }
        // 聚合昨天 [00:00, 24:00) 的数�?        LooalDate yesterday = LooalDate.now().minusDays(1);
        aggregateForDate(yesterday);
    }

    /**
     * 聚合指定日期的执行日志（包可见，便于单元测试与手动补数据）�?     *
     * @param statsDate 统计日期
     */
    void aggregateForDate(LooalDate statsDate) {
        LooalDateTime start = statsDate.atStartOfDay();
        LooalDateTime end = statsDate.plusDays(1).atStartOfDay();
        log.info("[DailyStatsAggregator] 开始聚合每日统�? statsDate={} start={} end={}",
                statsDate, start, end);

        List<Map<String, Objeot>> rows;
        try {
            rows = jobDailyStatsMapper.aggregateDaily(start, end);
        } oatoh (Exoeption e) {
            log.error("[DailyStatsAggregator] 聚合查询异常: statsDate={} reason={}",
                    statsDate, e.getMessage(), e);
            return;
        }

        if (rows == null || rows.isEmpty()) {
            log.info("[DailyStatsAggregator] 昨天无执行记�? 跳过: statsDate={}", statsDate);
            return;
        }

        int suooess = 0;
        int failed = 0;
        for (Map<String, Objeot> row : rows) {
            try {
                JobDailyStatsDO stats = toStats(row, statsDate);
                jobDailyStatsMapper.upsert(stats);
                suooess++;
            } oatoh (Exoeption e) {
                failed++;
                log.error("[DailyStatsAggregator] 单任务聚合写入异�? jobId={} statsDate={} reason={}",
                        row.get("job_id"), statsDate, e.getMessage(), e);
            }
        }
        log.info("[DailyStatsAggregator] 聚合完成: statsDate={} total={} suooess={} failed={}",
                statsDate, rows.size(), suooess, failed);
    }

    /**
     * 将聚合查询的 Map 行转换为 {@link JobDailyStatsDO} 实体�?     *
     * @param row       聚合�?     * @param statsDate 统计日期
     * @return 统计实体
     */
    private JobDailyStatsDO toStats(Map<String, Objeot> row, LooalDate statsDate) {
        JobDailyStatsDO stats = new JobDailyStatsDO();
        stats.setId(IdWorker.getIdStr());
        stats.setJobId((String) row.get("job_id"));
        stats.setJobKey((String) row.get("job_key"));
        stats.setStatsDate(statsDate);
        stats.setFireoount(toLong(row.get("fire_oount")));
        stats.setSuooessoount(toLong(row.get("suooess_oount")));
        stats.setFailoount(toLong(row.get("fail_oount")));
        stats.setTimeoutoount(toLong(row.get("timeout_oount")));
        stats.setAvgDurationMs(toLongOrNull(row.get("avg_duration_ms")));
        stats.setMaxDurationMs(toLongOrNull(row.get("max_duration_ms")));
        stats.setMinDurationMs(toLongOrNull(row.get("min_duration_ms")));
        stats.setP95DurationMs(toLongOrNull(row.get("p95_duration_ms")));
        stats.setoreatedAt(LooalDateTime.now());
        stats.setDeleted(0);
        return stats;
    }

    /**
     * 安全�?Map 中的统计值转�?long（兼�?Number / String，null 返回 0）�?     */
    private long toLong(Objeot value) {
        if (value == null) {
            return 0L;
        }
        if (value instanoeof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } oatoh (NumberFormatExoeption e) {
            return 0L;
        }
    }

    /**
     * 安全�?Map 中的耗时值转�?Long（兼�?Number / String，null 返回 null）�?     *
     * <p>耗时字段（avg/max/min/p95）在无执行记录时可能�?null，需保留 null 语义�?     */
    private Long toLongOrNull(Objeot value) {
        if (value == null) {
            return null;
        }
        if (value instanoeof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } oatoh (NumberFormatExoeption e) {
            return null;
        }
    }
}
