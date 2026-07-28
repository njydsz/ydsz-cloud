package com.njydsz.cronjob.server.core.dispatch;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import com.njydsz.common.redis.service.RedisService;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.cronjob.domain.entity.log.JobLog;
import com.njydsz.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.cronjob.infra.mapper.log.JobLogMapper;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.LockKeyUtil;
import com.njydsz.cronjob.server.core.alert.AlertContext;
import com.njydsz.cronjob.server.core.alert.AlertTrigger;
import com.njydsz.cronjob.server.core.alert.AlertType;
import com.njydsz.cronjob.server.core.leader.LeaderElector;
import com.njydsz.cronjob.server.metrics.CronjobMetrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.lock.annotation.DistributedScheduled;

/**
 * 任务执行超时监控器（P2-4）。
 *
 * <p>仅当 {@code ydsz.cronjob.leader.enabled=true} 且当前节点是 Leader 时启用。
 * 定时（默认 10s）扫描 {@code ydsz_job_log} 中 {@code status='RUNNING'} 且
 * {@code start_time + job.timeout_ms < NOW()} 的日志，标记为 TIMEOUT 并主动释放分布式锁。
 *
 * <h3>执行流程</h3>
 * <ol>
 *   <li>检查 Leader 身份（非 Leader 直接返回，避免重复扫描）</li>
 *   <li>查询超时日志（JOIN ydsz_job 取 timeout_ms）</li>
 *   <li>对每条超时日志：
 *     <ul>
 *       <li>标记 log.status=TIMEOUT，填充 end_time / duration_ms / error_message</li>
 *       <li>更新 job.fail_count + 1，status=ERROR（不阻止下次扫描，由运维人工处理）</li>
 *       <li>释放对应的 Redis 任务锁（Lua 脚本安全释放）</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p><b>设计权衡</b>：
 * <ul>
 *   <li>仅当 timeout_ms 非空时才检测（null 表示不限超时）</li>
 *   <li>标记 TIMEOUT 后任务 status=ERROR，避免下次扫描重复处理</li>
 *   <li>不主动中断执行中的线程（Java 无法安全中断外部线程），
 *       仅通过释放锁让其他节点可以重新派发</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(LeaderElector.class)
public class TimeoutMonitor {

    private final JobMapper jobMapper;
    private final JobLogMapper jobLogMapper;
    private final LeaderElector leaderElector;
    private final RedisService redisService;
    private final CronjobProperties cronjobProperties;
    /** P5: 告警触发器（可选注入，未配置时不触发告警） */
    private final ObjectProvider<AlertTrigger> alertTriggerProvider;
    /** P6-2: Prometheus 指标收集器（可选注入，未配置时不记录指标） */
    private final ObjectProvider<CronjobMetrics> cronjobMetricsProvider;

    /** P0-1: Lua 脚本: 安全释放锁（仅当 value 匹配时才 delete），与 DefaultTaskDispatcher 一致 */
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = initReleaseScript();

    private static DefaultRedisScript<Long> initReleaseScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(LockKeyUtil.RELEASE_LOCK_SCRIPT);
        script.setResultType(Long.class);
        return script;
    }

    /** 单批最多扫描超时日志数 */
    private static final int BATCH_SIZE = 100;

    private String leaderRole;

    @PostConstruct
    public void init() {
        this.leaderRole = cronjobProperties.getLeader().getRole();
        if (cronjobProperties.getLeader().isEnabled()) {
            log.info("[TimeoutMonitor] 初始化完成, role={}", leaderRole);
        } else {
            log.info("[TimeoutMonitor] leader.enabled=false, 超时监控不启用");
        }
    }

    /**
     * 定时扫描超时日志（默认 10s 一次）。
     */
    @DistributedScheduled(lockKey = "cronjob:timeout-monitor")
    @Scheduled(fixedDelayString = "${ydsz.cronjob.timeout-monitor.interval-ms:10000}")
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
            log.error("[TimeoutMonitor] 扫描异常: role={} reason={}", leaderRole, e.getMessage(), e);
        }
    }

    /**
     * 执行一次超时扫描。
     */
    private void doScan() {
        LocalDateTime now = LocalDateTime.now();
        List<JobLog> timedOut = jobLogMapper.selectTimedOutLogs(now, BATCH_SIZE);
        if (timedOut.isEmpty()) {
            return;
        }
        log.warn("[TimeoutMonitor] 发现 {} 个超时任务: role={}", timedOut.size(), leaderRole);
        for (JobLog log0 : timedOut) {
            try {
                handleTimeout(log0, now);
            } catch (Exception e) {
                log.error("[TimeoutMonitor] 处理超时日志失败: logId={} jobKey={} reason={}",
                        log0.getId(), log0.getJobKey(), e.getMessage(), e);
            }
        }
    }

    /**
     * 处理单个超时日志（事务内）。
     */
    @Transactional(rollbackFor = Exception.class)
    protected void handleTimeout(JobLog log0, LocalDateTime now) {
        long durationMs = Duration.between(log0.getStartTime(), now).toMillis();
        String errorMsg = "Task timed out (start=" + log0.getStartTime()
                + ", detected=" + now + ", duration=" + durationMs + "ms)";
        // 标记日志为 TIMEOUT（CAS: 仅当 status 仍为 RUNNING 时才更新）
        int affected = jobLogMapper.markTimeout(log0.getId(), now, durationMs, errorMsg);
        if (affected == 0) {
            log.debug("[TimeoutMonitor] 日志已非 RUNNING 状态, 跳过: logId={}", log0.getId());
            return;
        }
        // P6-2: 记录超时指标
        CronjobMetrics metrics = cronjobMetricsProvider.getIfAvailable();
        if (metrics != null) {
            metrics.incJobTimeout(log0.getJobKey());
        }
        // P0-7: 释放任务锁（Lua 脚本安全释放，仅当 lockHolder 匹配时才 delete）
        // P0-11: 通过 LockKeyUtil 统一构造，支持分片任务锁释放
        String lockKey = LockKeyUtil.buildJobLockKey(log0.getJobKey(), log0.getShardIndex());
        String holder = log0.getLockHolder();
        if (holder != null && !holder.isBlank()) {
            try {
                Long released = redisService.execute(RELEASE_LOCK_SCRIPT,
                        Collections.singletonList(lockKey), holder);
                if (released != null && released > 0) {
                    log.info("[TimeoutMonitor] 安全释放超时任务锁成功: jobKey={} lockKey={} holder={}",
                            log0.getJobKey(), lockKey, holder);
                } else {
                    log.info("[TimeoutMonitor] 锁 holder 不匹配或已过期, 跳过释放: jobKey={} lockKey={} holder={}",
                            log0.getJobKey(), lockKey, holder);
                }
            } catch (Exception e) {
                log.warn("[TimeoutMonitor] 释放锁失败(将等待 TTL 自动过期): lockKey={} reason={}",
                        lockKey, e.getMessage());
            }
        } else {
            // 兜底: 日志无 lockHolder（历史数据或 MANUAL 触发未持锁），跳过释放
            log.debug("[TimeoutMonitor] 日志无 lockHolder, 跳过锁释放: logId={} jobKey={}",
                    log0.getId(), log0.getJobKey());
        }
        // 更新任务统计：失败次数 +1，status=ERROR
        try {
            jobMapper.updateStats(log0.getJobId(), log0.getStartTime(), null,
                    null, 0L, 1L, "ERROR");
            log.warn("[TimeoutMonitor] 任务已标记 ERROR: jobId={} jobKey={} logId={}",
                    log0.getJobId(), log0.getJobKey(), log0.getId());
        } catch (Exception e) {
            log.error("[TimeoutMonitor] 更新任务统计失败: jobId={} reason={}",
                    log0.getJobId(), e.getMessage());
        }
        // P5: 触发超时告警
        triggerTimeoutAlert(log0, durationMs);
    }

    /**
     * P5: 触发超时告警。
     */
    private void triggerTimeoutAlert(JobLog log0, long durationMs) {
        AlertTrigger alertTrigger = alertTriggerProvider.getIfAvailable();
        if (alertTrigger == null) {
            return;
        }
        try {
            AlertContext context = AlertContext.of(
                    AlertType.TIMEOUT,
                    log0.getJobId(),
                    log0.getJobKey(),
                    null,
                    log0.getId(),
                    String.valueOf(durationMs),
                    "Task timed out",
                    log0.getTraceId(),
                    null
            );
            alertTrigger.trigger(context);
        } catch (Exception e) {
            log.warn("[TimeoutMonitor] 触发超时告警失败(不影响主流程): logId={} reason={}",
                    log0.getId(), e.getMessage());
        }
    }
}
