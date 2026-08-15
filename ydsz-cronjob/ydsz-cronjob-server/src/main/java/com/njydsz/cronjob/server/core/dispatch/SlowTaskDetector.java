package com.njydsz.cronjob.server.core.dispatch;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import com.njydsz.cronjob.domain.entity.job.Job;
import com.njydsz.cronjob.domain.entity.log.JobLog;
import com.njydsz.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.cronjob.infra.mapper.log.JobLogMapper;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.leader.LeaderElector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 慢任务诊断扫描器。
 *
 * <p>仅当 {@code ydsz.cronjob.leader.enabled=true} 且当前节点是 Leader 时启用。
 * 定时（默认 30s）扫描 {@code ydsz_job_log} 中已结束（SUCCESS/FAILED/TIMEOUT）
 * 且耗时超过 {@code ydsz_job.slow_threshold_ms} 的记录，标记 {@code is_slow=1}。
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li><b>幂等</b>：通过 {@code WHERE is_slow = 0} 过滤已标记的记录，
 *       并在 UPDATE 中二次检查 {@code is_slow = 0} 防止并发竞态</li>
 *   <li><b>时间窗口</b>：仅扫描最近 N 分钟（默认 60min）的日志，避免全表扫描</li>
 *   <li><b>批量查询</b>：通过 selectByIds 一次性获取所有相关 Job，避免 N+1 查询</li>
 *   <li><b>独立失败</b>：每条标记独立 try-catch，单条失败不影响其他</li>
 * </ul>
 *
 * <h3>与告警系统的关系</h3>
 * <p>本扫描器仅负责 <b>标记</b> 慢任务（is_slow=1），用于性能趋势分析。
 * 慢任务 <b>告警</b> 由 {@code DefaultTaskDispatcher.triggerAlerts()} 在执行完成时
 * 实时触发 {@link com.njydsz.cronjob.server.core.alert.AlertType#SLOW} 告警，
 * 二者关注点正交。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(LeaderElector.class)
public class SlowTaskDetector {

    private final JobMapper jobMapper;
    private final JobLogMapper jobLogMapper;
    private final LeaderElector leaderElector;
    private final CronjobProperties cronjobProperties;

    /** 单批最多扫描慢任务数 */
    private static final int BATCH_SIZE = 100;

    /** 扫描时间窗口（分钟）：仅扫描最近 60 分钟的日志 */
    private static final long LOOKBACK_MINUTES = 60L;

    private String leaderRole;

    /**
     * 初始化慢任务诊断器：解析 Leader 角色并确认启用状态。
     *
     * <p>仅在 {@code ydsz.cronjob.leader.enabled=true} 时进入扫描启用分支；
     * 否则仅记录 Leaderless 日志，不创建任何资源（扫描方法 {@link #scan()} 内部已通过
     * Leader 身份与 enabled 双重校验拦截，故本方法不负责实际扫描逻辑）。
     * 扫描的时间窗口 {@code LOOKBACK_MINUTES}（60min）固定，不依赖外部配置。
     */
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
    @Scheduled(fixedDelayString = "${ydsz.cronjob.slow-task-detector.interval-ms:30000}")
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
        List<JobLog> slowLogs = jobLogMapper.selectSlowLogs(since, BATCH_SIZE);
        if (slowLogs.isEmpty()) {
            return;
        }
        log.info("[SlowTaskDetector] 发现 {} 个待标记的慢任务: role={}", slowLogs.size(), leaderRole);

        // 批量查询 Job（避免 N+1 查询），仅取需要的 slowThresholdMs
        Set<String> jobIds = slowLogs.stream()
                .map(JobLog::getJobId)
                .collect(Collectors.toSet());
        Map<String, Job> jobMap = batchFetchJobs(jobIds);

        int marked = 0;
        int skipped = 0;
        for (JobLog log0 : slowLogs) {
            try {
                boolean done = markSlowLog(log0, jobMap);
                if (done) {
                    marked++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                log.error("[SlowTaskDetector] 标记慢任务失败: logId={} jobKey={} reason={}",
                        log0.getId(), log0.getJobKey(), e.getMessage(), e);
            }
        }
        log.info("[SlowTaskDetector] 扫描完成: role={} marked={} skipped={} total={}",
                leaderRole, marked, skipped, slowLogs.size());
    }

    /**
     * 批量查询 Job（容错：查询异常时返回空 Map，调用方逐条跳过）。
     */
    private Map<String, Job> batchFetchJobs(Set<String> jobIds) {
        if (jobIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<Job> jobs = jobMapper.selectByIds(jobIds);
            return jobs.stream()
                    .collect(Collectors.toMap(Job::getId, Function.identity(), (a, b) -> a));
        } catch (Exception e) {
            log.warn("[SlowTaskDetector] 批量查询 Job 失败, 本批将全部跳过: reason={}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 标记单条慢任务日志（is_slow=1 + 快照 slow_threshold_ms）。
     *
     * <p>幂等保证：SQL 中 {@code WHERE is_slow = 0} 确保不会重复标记。
     *
     * @param log0    任务执行日志
     * @param jobMap  任务定义映射（key=jobId）
     * @return true=已标记; false=已跳过（任务不存在/阈值无效/已标记）
     */
    boolean markSlowLog(JobLog log0, Map<String, Job> jobMap) {
        Job job = jobMap.get(log0.getJobId());
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
        // 标记 is_slow=1 并快照 slow_threshold_ms（幂等：WHERE is_slow = 0）
        int updated = jobLogMapper.markSlow(log0.getId(), slowThreshold);
        if (updated > 0) {
            log.info("[SlowTaskDetector] 标记慢任务: jobKey={} logId={} duration={}ms threshold={}ms",
                    log0.getJobKey(), log0.getId(), log0.getDurationMs(), slowThreshold);
            return true;
        }
        return false;
    }
}
