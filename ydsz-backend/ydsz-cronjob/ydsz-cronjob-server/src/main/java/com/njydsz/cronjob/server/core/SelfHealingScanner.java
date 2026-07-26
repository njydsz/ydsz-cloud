package com.njydsz.cronjob.server.core.healing;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import com.njydsz.common.redis.service.RedisService;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.cronjob.domain.entity.job.JobDO;
import com.njydsz.cronjob.domain.entity.log.JobLogDO;
import com.njydsz.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.cronjob.infra.mapper.log.JobLogMapper;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.LockKeyUtil;
import com.njydsz.cronjob.server.core.alert.AlertContext;
import com.njydsz.cronjob.server.core.alert.AlertTrigger;
import com.njydsz.cronjob.server.core.alert.AlertType;
import com.njydsz.cronjob.server.core.dispatch.DefaultTaskDispatcher;
import com.njydsz.cronjob.server.core.dispatch.TaskDispatcher;
import com.njydsz.cronjob.server.core.leader.LeaderElector;
import com.njydsz.cronjob.server.metrics.CronjobMetrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 自愈扫描器（P3-2）。
 *
 * <p>定时检测异常状态的任务并自动修复，无需人工介入：
 * <ul>
 *   <li><b>卡死任务</b>：RUNNING 状态超过阈值无更新 → 标记 FAILED 并重新派发</li>
 *   <li><b>孤儿任务</b>：执行节点下线但日志仍 RUNNING → 清理并转移到其他节点</li>
 *   <li><b>自动暂停恢复</b>：AUTO_PAUSED 状态到达恢复时间 → 恢复为 NORMAL</li>
 *   <li><b>连续失败降级</b>：连续失败次数超过限制 → 触发降级通知</li>
 * </ul>
 *
 * <h3>修复策略</h3>
 * <ol>
 *   <li>检测到卡死任务 → 释放分布式锁</li>
 *   <li>标记日志为 FAILED（CAS 更新，仅 RUNNING 状态可改）</li>
 *   <li>更新任务 fail_count + 1</li>
 *   <li>若任务仍为 NORMAL 且自动派发开启 → 以 triggerType=FAILOVER 重新派发</li>
 *   <li>若重试次数超限 → 标记任务 status=AUTO_PAUSED，触发告警</li>
 * </ol>
 *
 * <p>仅在 {@code ydsz.cronjob.self-healing.enabled=true} 时启用。
 * 仅 Leader 节点执行扫描，避免多节点重复修复。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(LeaderElector.class)
@ConditionalOnProperty(name = "ydsz.cronjob.self-healing.enabled", havingValue = "true")
public class SelfHealingScanner {

    private final JobMapper jobMapper;
    private final JobLogMapper jobLogMapper;
    private final LeaderElector leaderElector;
    private final CronjobProperties cronjobProperties;
    private final RedisService redisService;
    /** 告警触发器（可选注入） */
    private final ObjectProvider<AlertTrigger> alertTriggerProvider;
    /** 任务派发器（可选注入，用于重新派发） */
    private final ObjectProvider<TaskDispatcher> taskDispatcherProvider;
    /** Prometheus 指标（可选注入） */
    private final ObjectProvider<CronjobMetrics> cronjobMetricsProvider;

    /** Lua 脚本: 安全释放锁 */
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = initReleaseScript();

    /** 自愈重试计数 Redis key 前缀 */
    private static final String HEAL_RETRY_PREFIX = "ydsz:job:heal:retry:";

    private String leaderRole;

    private static DefaultRedisScript<Long> initReleaseScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end");
        script.setResultType(Long.class);
        return script;
    }

    @PostConstruct
    public void init() {
        this.leaderRole = cronjobProperties.getLeader().getRole();
        log.info("[SelfHealing] 初始化完成, role={} scanInterval={}s stuckThreshold={}s maxHealPerScan={}",
                leaderRole, cronjobProperties.getSelfHealing().getScanIntervalSeconds(),
                cronjobProperties.getSelfHealing().getStuckThresholdSeconds(),
                cronjobProperties.getSelfHealing().getMaxHealPerScan());
    }

    /**
     * 定时扫描异常任务。
     */
    @Scheduled(fixedDelayString = "${ydsz.cronjob.self-healing.scan-interval-ms:60000}")
    public void scan() {
        if (!cronjobProperties.getLeader().isEnabled()) {
            return;
        }
        if (!leaderElector.isLeader(leaderRole)) {
            return;
        }
        try {
            healStuckTasks();
            healAutoPausedTasks();
        } catch (Exception e) {
            log.error("[SelfHealing] 扫描异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 检测并修复卡死任务。
     *
     * <p>RUNNING 状态超过阈值未更新视为卡死（可能因 JVM 崩溃、线程死锁、网络中断导致）。
     */
    private void healStuckTasks() {
        CronjobProperties.SelfHealing config = cronjobProperties.getSelfHealing();
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(config.getStuckThresholdSeconds());

        // 查询卡死的 RUNNING 日志
        List<JobLogDO> stuckLogs = jobLogMapper.selectList(
                new LambdaQueryWrapper<JobLogDO>()
                        .eq(JobLogDO::getStatus, "RUNNING")
                        .lt(JobLogDO::getStartTime, threshold)
                        .last("LIMIT " + config.getMaxHealPerScan()));

        if (stuckLogs.isEmpty()) {
            return;
        }

        log.warn("[SelfHealing] 发现 {} 个卡死任务, 开始修复", stuckLogs.size());
        int healed = 0;
        int failed = 0;
        for (JobLogDO stuckLog : stuckLogs) {
            try {
                healSingleStuckTask(stuckLog);
                healed++;
            } catch (Exception e) {
                failed++;
                log.error("[SelfHealing] 修复卡死任务失败: logId={} jobKey={} reason={}",
                        stuckLog.getId(), stuckLog.getJobKey(), e.getMessage(), e);
            }
        }
        log.info("[SelfHealing] 卡死任务修复完成: total={} healed={} failed={}",
                stuckLogs.size(), healed, failed);
    }

    /**
     * 修复单个卡死任务。
     */
    @Transactional(rollbackFor = Exception.class)
    protected void healSingleStuckTask(JobLogDO stuckLog) {
        LocalDateTime now = LocalDateTime.now();
        long durationMs = Duration.between(stuckLog.getStartTime(), now).toMillis();
        String errorMsg = "Self-healing: task stuck (start=" + stuckLog.getStartTime()
                + ", detected=" + now + ", duration=" + durationMs + "ms)";

        // 1. CAS 标记日志为 FAILED
        int affected = jobLogMapper.markTimeout(stuckLog.getId(), now, durationMs, errorMsg);
        if (affected == 0) {
            log.debug("[SelfHealing] 日志已非 RUNNING 状态, 跳过: logId={}", stuckLog.getId());
            return;
        }

        // 2. 释放分布式锁（P0-7: 传入 shardIndex 支持分片任务）
        releaseJobLock(stuckLog.getJobKey(), stuckLog.getShardIndex(), stuckLog.getLockHolder());

        // 3. 更新任务统计
        try {
            jobMapper.updateStats(stuckLog.getJobId(), stuckLog.getStartTime(), null,
                    null, 0L, 1L, "ERROR");
        } catch (Exception e) {
            log.warn("[SelfHealing] 更新任务统计失败: jobId={} reason={}", stuckLog.getJobId(), e.getMessage());
        }

        // 4. 记录指标
        CronjobMetrics metrics = cronjobMetricsProvider.getIfAvailable();
        if (metrics != null) {
            metrics.incJobTimeout(stuckLog.getJobKey());
        }

        // 5. 判断是否重新派发
        CronjobProperties.SelfHealing config = cronjobProperties.getSelfHealing();
        if (config.isAutoRedispatch()) {
            tryRedispatch(stuckLog, config);
        }

        // 6. 触发告警
        triggerHealAlert(stuckLog, durationMs);
    }

    /**
     * 尝试重新派发修复后的任务。
     */
    private void tryRedispatch(JobLogDO stuckLog, CronjobProperties.SelfHealing config) {
        String retryKey = HEAL_RETRY_PREFIX + stuckLog.getJobKey();
        try {
            Long retryCount = redisService.incr(retryKey, 1);
            if (retryCount == null) {
                retryCount = 1L;
            }
            // 设置 1 小过期
            if (retryCount == 1) {
                redisService.expire(retryKey, Duration.ofHours(1));
            }

            if (retryCount > config.getMaxRedispatchRetries()) {
                log.warn("[SelfHealing] 任务重试次数超限, 标记 AUTO_PAUSED: jobKey={} retries={}",
                        stuckLog.getJobKey(), retryCount);
                // 标记任务为 AUTO_PAUSED
                jobMapper.markAutoPaused(stuckLog.getJobId());
                return;
            }

            // 查询任务定义，确认仍为 NORMAL 状态
            JobDO job = jobMapper.selectById(stuckLog.getJobId());
            if (job == null || !"NORMAL".equals(job.getStatus())) {
                log.debug("[SelfHealing] 任务非 NORMAL 状态, 跳过重派: jobKey={} status={}",
                        stuckLog.getJobKey(), job != null ? job.getStatus() : "null");
                return;
            }

            // 重新派发
            TaskDispatcher dispatcher = taskDispatcherProvider.getIfAvailable();
            if (dispatcher != null) {
                String logId = dispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_FAILOVER);
                log.info("[SelfHealing] 任务重新派发成功: jobKey={} retries={} newLogId={}",
                        stuckLog.getJobKey(), retryCount, logId);
            }
        } catch (Exception e) {
            log.warn("[SelfHealing] 重新派发失败: jobKey={} reason={}", stuckLog.getJobKey(), e.getMessage());
        }
    }

    /**
     * 修复 AUTO_PAUSED 状态的任务（到达恢复时间后自动恢复）。
     */
    private void healAutoPausedTasks() {
        // 查询 AUTO_PAUSED 状态且 lastFireTime 超过 1 小时的任务（给足够冷却时间）
        LocalDateTime threshold = LocalDateTime.now().minusHours(1);
        List<JobDO> autoPausedJobs = jobMapper.selectList(
                new LambdaQueryWrapper<JobDO>()
                        .eq(JobDO::getStatus, "AUTO_PAUSED")
                        .lt(JobDO::getLastFireTime, threshold)
                        .last("LIMIT " + cronjobProperties.getSelfHealing().getMaxHealPerScan()));

        if (autoPausedJobs.isEmpty()) {
            return;
        }

        log.info("[SelfHealing] 发现 {} 个 AUTO_PAUSED 任务待恢复", autoPausedJobs.size());
        for (JobDO job : autoPausedJobs) {
            try {
                // 清除重试计数
                redisService.delete(HEAL_RETRY_PREFIX + job.getJobKey());
                // 恢复为 NORMAL
                jobMapper.resumeAutoPaused(job.getId());
                log.info("[SelfHealing] 任务已自动恢复: jobKey={}", job.getJobKey());
            } catch (Exception e) {
                log.warn("[SelfHealing] 恢复任务失败: jobKey={} reason={}", job.getJobKey(), e.getMessage());
            }
        }
    }

    /**
     * 安全释放任务锁（P0-7: 支持分片任务锁）。
     *
     * @param jobKey 任务 key
     * @param shardIndex 分片索引（null 或负数表示非分片任务）
     * @param lockHolder 锁持有者标识
     */
    private void releaseJobLock(String jobKey, Integer shardIndex, String lockHolder) {
        if (lockHolder == null || lockHolder.isBlank()) {
            return;
        }
        try {
            // P0-11: 通过 LockKeyUtil 统一构造，支持分片任务锁释放
            String lockKey = LockKeyUtil.buildJobLockKey(jobKey, shardIndex);
            Long released = redisService.execute(RELEASE_LOCK_SCRIPT,
                    Collections.singletonList(lockKey), lockHolder);
            if (released != null && released > 0) {
                log.info("[SelfHealing] 释放卡死任务锁成功: jobKey={} shardIndex={} lockKey={}",
                        jobKey, shardIndex, lockKey);
            }
        } catch (Exception e) {
            log.warn("[SelfHealing] 释放锁失败: jobKey={} reason={}", jobKey, e.getMessage());
        }
    }

    /**
     * 触发自愈告警。
     */
    private void triggerHealAlert(JobLogDO stuckLog, long durationMs) {
        AlertTrigger trigger = alertTriggerProvider.getIfAvailable();
        if (trigger == null) {
            return;
        }
        try {
            AlertContext context = AlertContext.of(
                    AlertType.TIMEOUT,
                    stuckLog.getJobId(),
                    stuckLog.getJobKey(),
                    null,
                    stuckLog.getId(),
                    String.valueOf(durationMs),
                    "Self-healing: task stuck and auto-recovered",
                    stuckLog.getTraceId(),
                    null
            );
            trigger.trigger(context);
        } catch (Exception e) {
            log.warn("[SelfHealing] 触发告警失败(不影响主流程): logId={} reason={}",
                    stuckLog.getId(), e.getMessage());
        }
    }
}
