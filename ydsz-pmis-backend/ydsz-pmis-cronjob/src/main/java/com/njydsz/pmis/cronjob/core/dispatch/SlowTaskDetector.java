package com.njydsz.pmis.cronjob.core.dispatch;

import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.core.leader.LeaderElector;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.entity.JobLogDO;
import com.njydsz.pmis.cronjob.entity.JobSlowLogDO;
import com.njydsz.pmis.cronjob.mapper.JobLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import com.njydsz.pmis.cronjob.mapper.JobSlowLogMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 慢任务诊断扫描器（P6-3）。
 *
 * <p>仅当 {@code pmis.cronjob.leader.enabled=true} 且当前节点是 Leader 时启用。
 * 定时（默认 30s）扫描 {@code pmis_job_log} 中已结束（SUCCESS/FAILED/TIMEOUT）
 * 且耗时超过 {@code pmis_job.slow_threshold_ms} 的记录，写入 {@code pmis_job_slow_log}。
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li><b>幂等</b>：通过 LEFT JOIN pmis_job_slow_log 过滤已记录的 log_id，
 *       并在写入前二次检查 countByLogId 防止并发场景下的竞态</li>
 *   <li><b>时间窗口</b>：仅扫描最近 N 分钟（默认 60min）的日志，避免全表扫描；
 *       默认扫描窗口足以覆盖上次扫描失败的场景</li>
 *   <li><b>批量查询</b>：通过 selectBatchIds 一次性获取所有相关 JobDO，
 *       避免 N+1 查询</li>
 *   <li><b>独立失败</b>：每条 slow_log 写入独立 try-catch，单条失败不影响其他</li>
 *   <li><b>解耦</b>：不依赖 DefaultTaskDispatcher 内联调用，
 *       可补偿主流程中漏记的慢任务（如实例崩溃、DB 抖动）</li>
 * </ul>
 *
 * <h3>与告警系统的关系</h3>
 * <p>本扫描器仅负责 <b>记录</b> 慢任务到 slow_log 表，用于性能趋势分析。
 * 慢任务 <b>告警</b> 由 {@code DefaultTaskDispatcher.triggerAlerts()} 在执行完成时
 * 实时触发 {@link com.njydsz.pmis.cronjob.core.alert.AlertType#SLOW} 告警，
 * 二者关注点正交。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(LeaderElector.class)
public class SlowTaskDetector {

    private final JobMapper jobMapper;
    private final JobLogMapper jobLogMapper;
    private final JobSlowLogMapper jobSlowLogMapper;
    private final LeaderElector leaderElector;
    private final CronjobProperties cronjobProperties;

    /** 单批最多扫描慢任务数 */
    private static final int BATCH_SIZE = 100;

    /** 扫描时间窗口（分钟）：仅扫描最近 60 分钟的日志 */
    private static final long LOOKBACK_MINUTES = 60L;

    private String leaderRole;

    @PostConstruct
    public void init() {
        this.leaderRole = cronjobProperties.getLeader().getRole();
        if (cronjobProperties.getLeader().isEnabled()) {
            log.info("[SlowTaskDetector] 初始化完成, role={} lookbackMinutes={}",
                    leaderRole, LOOKBACK_MINUTES);
        } else {
            log.info("[SlowTaskDetector] leader.enabled=false, 慢任务诊断不启用");
        }
    }

    /**
     * 定时扫描慢任务日志（默认 30s 一次）。
     *
     * <p>使用 {@code fixedDelayString} 而非 {@code fixedRateString}，
     * 避免上次扫描耗时较长时任务堆积。
     */
    @Scheduled(fixedDelayString = "${pmis.cronjob.slow-task-detector.interval-ms:30000}")
    public void scan() {
        if (!cronjobProperties.getLeader().isEnabled()) {
            return;
        }
        if (!leaderElector.isLeader(leaderRole)) {
            return;
        }
        try {
            doScan();
        } catch (Exception e) {
            log.error("[SlowTaskDetector] 扫描异常: role={} reason={}", leaderRole, e.getMessage(), e);
        }
    }

    /**
     * 执行一次慢任务扫描。
     */
    private void doScan() {
        LocalDateTime since = LocalDateTime.now().minusMinutes(LOOKBACK_MINUTES);
        List<JobLogDO> slowLogs = jobLogMapper.selectSlowLogs(since, BATCH_SIZE);
        if (slowLogs.isEmpty()) {
            return;
        }
        log.info("[SlowTaskDetector] 发现 {} 个待记录的慢任务: role={}", slowLogs.size(), leaderRole);

        // 批量查询 JobDO（避免 N+1 查询），仅取需要的 slowThresholdMs / tenantId
        Set<String> jobIds = slowLogs.stream()
                .map(JobLogDO::getJobId)
                .collect(Collectors.toSet());
        Map<String, JobDO> jobMap = batchFetchJobs(jobIds);

        int recorded = 0;
        int skipped = 0;
        for (JobLogDO log0 : slowLogs) {
            try {
                boolean done = recordSlowLog(log0, jobMap);
                if (done) {
                    recorded++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                log.error("[SlowTaskDetector] 记录慢任务失败: logId={} jobKey={} reason={}",
                        log0.getId(), log0.getJobKey(), e.getMessage(), e);
            }
        }
        log.info("[SlowTaskDetector] 扫描完成: role={} recorded={} skipped={} total={}",
                leaderRole, recorded, skipped, slowLogs.size());
    }

    /**
     * 批量查询 JobDO（容错：查询异常时返回空 Map，调用方逐条跳过）。
     */
    private Map<String, JobDO> batchFetchJobs(Set<String> jobIds) {
        if (jobIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<JobDO> jobs = jobMapper.selectBatchIds(jobIds);
            return jobs.stream()
                    .collect(Collectors.toMap(JobDO::getId, Function.identity(), (a, b) -> a));
        } catch (Exception e) {
            log.warn("[SlowTaskDetector] 批量查询 JobDO 失败, 本批将全部跳过: reason={}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 记录单条慢任务日志。
     *
     * <p>幂等保证：
     * <ol>
     *   <li>SQL 已通过 LEFT JOIN slow_log 过滤已记录的 log_id（主路径）</li>
     *   <li>写入前二次检查 countByLogId（防止并发场景下 LEFT JOIN 与 INSERT 之间的竞态）</li>
     * </ol>
     *
     * @param log0    任务执行日志
     * @param jobMap  任务定义映射（key=jobId）
     * @return true=已记录;false=已跳过（任务不存在/阈值无效/已记录）
     */
    boolean recordSlowLog(JobLogDO log0, Map<String, JobDO> jobMap) {
        JobDO job = jobMap.get(log0.getJobId());
        if (job == null) {
            log.debug("[SlowTaskDetector] 任务已被删除, 跳过: jobId={} logId={}",
                    log0.getJobId(), log0.getId());
            return false;
        }
        Long slowThreshold = job.getSlowThresholdMs();
        if (slowThreshold == null || slowThreshold <= 0) {
            // 阈值已被清空或无效（任务可能被修改过）
            return false;
        }
        // 二次幂等检查（防竞态）
        if (jobSlowLogMapper.countByLogId(log0.getId()) > 0) {
            return false;
        }
        JobSlowLogDO slowLog = new JobSlowLogDO();
        slowLog.setJobId(log0.getJobId());
        slowLog.setJobKey(log0.getJobKey());
        slowLog.setLogId(log0.getId());
        slowLog.setDurationMs(log0.getDurationMs());
        slowLog.setSlowThresholdMs(slowThreshold);
        slowLog.setParamsJson(log0.getParamsJson());
        slowLog.setErrorMessage(log0.getErrorMessage());
        slowLog.setTraceId(log0.getTraceId());
        slowLog.setTenantId(job.getTenantId());
        jobSlowLogMapper.insert(slowLog);
        log.info("[SlowTaskDetector] 记录慢任务: jobKey={} logId={} duration={}ms threshold={}ms",
                log0.getJobKey(), log0.getId(), log0.getDurationMs(), slowThreshold);
        return true;
    }
}
