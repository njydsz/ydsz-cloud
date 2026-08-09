package com.njydsz.cronjob.server.core.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.util.id.TracerUtils;
import com.njydsz.cronjob.domain.entity.job.Job;
import com.njydsz.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.dispatch.DefaultTaskDispatcher;
import com.njydsz.cronjob.server.core.dispatch.TaskDispatcher;
import com.njydsz.cronjob.server.core.leader.LeaderElector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 精准调度管理器（P0-2 时间轮预加载）。
 *
 * <p>通过预加载窗口将即将到期的 CRON 任务提前加载到 {@link ScheduledExecutorService}，
 * 在任务的精确 {@code next_fire_time} 时刻派发，将调度精度从 ±5s（扫描间隔）提升到 ±0.1s。
 *
 * <h3>工作流程</h3>
 * <ol>
 *   <li>快速扫描线程每 {@code fastScanIntervalMs}（默认 1s）执行一次</li>
 *   <li>查询 {@code next_fire_time <= NOW() + preLoadWindowSeconds} 的 CRON 任务</li>
 *   <li>对每个任务 CAS 推进 {@code next_fire_time}（防止重复加载）</li>
 *   <li>计算延迟时间 {@code delay = next_fire_time - NOW()}，调度到 ScheduledExecutorService</li>
 *   <li>到点后执行 {@link TaskDispatcher#dispatch}，triggerType=CRON</li>
 * </ol>
 *
 * <h3>与 JobScanner 的关系</h3>
 * <ul>
 *   <li>启用精准调度后，JobScanner 仍然作为兜底机制（5s 间隔扫描过期任务）</li>
 *   <li>精准调度器处理窗口内的任务（精度 ±0.1s）</li>
 *   <li>JobScanner 处理窗口外的任务（如 Leader 切换后遗留的过期任务）</li>
 * </ul>
 *
 * <h3>容错设计</h3>
 * <ul>
 *   <li>Leader 切换时，已调度但未执行的任务会被新 Leader 的 JobScanner 兜底处理</li>
 *   <li>CAS 推进 {@code next_fire_time} 防止多 Leader 候选节点重复加载</li>
 *   <li>Redis 分布式锁兜底防止重复执行</li>
 * </ul>
 *
 * <p>仅在 {@code ydsz.cronjob.precise-scheduling.enabled=true} 时启用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(LeaderElector.class)
@ConditionalOnProperty(name = "ydsz.cronjob.precise-scheduling.enabled", havingValue = "true")
public class PreciseSchedulingManager {

    private final JobMapper jobMapper;
    private final TaskDispatcher taskDispatcher;
    private final LeaderElector leaderElector;
    private final CronjobProperties cronjobProperties;

    /** 精准调度线程池 */
    private ScheduledExecutorService preciseScheduler;

    /** 快速扫描线程池 */
    private ScheduledExecutorService fastScanner;

    /** 已预加载的任务: jobId -> ScheduledFuture（用于取消和去重） */
    private final Map<String, ScheduledFuture<?>> preLoadedTasks = new ConcurrentHashMap<>();

    /** Leader 角色 */
    private String leaderRole;

    /**
     * 初始化精准调度：创建调度/扫描线程池并启动快速扫描循环。
     *
     * <p>两个线程池均为本类自建并自管理生命周期（非 common-thread）：
     * <ul>
     *   <li>{@code preciseScheduler}：{@code ScheduledThreadPool}，到点执行精准派发任务；</li>
     *   <li>{@code fastScanner}：单线程 {@code ScheduledExecutor}，按 {@code fastScanIntervalMs}
     *       周期预加载窗口内即将到期的任务、CAS 推进后提交到 preciseScheduler。</li>
     * </ul>
     * 仅当 {@code ydsz.cronjob.precise-scheduling.enabled=true} 时注册。
     * 注意：扫描线程启动后不会立即抢占，需在 {@link #fastScan()} 内通过 Leader 身份校验才真正生效。
     */
    @PostConstruct
    public void init() {
        this.leaderRole = cronjobProperties.getLeader().getRole();
        CronjobProperties.PreciseScheduling config = cronjobProperties.getPreciseScheduling();
        this.preciseScheduler = Executors.newScheduledThreadPool(
                config.getPoolSize(), buildThreadFactory("ydsz-precise-dispatch"));
        this.fastScanner = Executors.newSingleThreadScheduledExecutor(
                buildThreadFactory("ydsz-precise-scan"));
        // 启动快速扫描线程
        fastScanner.scheduleWithFixedDelay(
                this::fastScan,
                config.getFastScanIntervalMs(),
                config.getFastScanIntervalMs(),
                TimeUnit.MILLISECONDS);
        log.info("[PreciseScheduling] 初始化完成, scanInterval={}ms preLoadWindow={}s poolSize={}",
                config.getFastScanIntervalMs(), config.getPreLoadWindowSeconds(), config.getPoolSize());
    }

    /**
     * 容器销毁钩子：取消已调度任务并优雅关闭自建线程池。
     *
     * <p>关闭顺序：先取消 {@code preLoadedTasks} 中所有未执行的 {@link ScheduledFuture}
     * （{@code cancel(false)} 不中断正在运行的派发），清空映射；再分别优雅关闭
     * preciseScheduler 与 fastScanner（先 {@code shutdown()} + 最多等待 10s，
     * 超时则 {@code shutdownNow()} 强制终止）。
     * 被取消而未能派发的任务由 JobScanner 在兜底扫描中重新发现并派发，不会永久丢失。
     */
    @PreDestroy
    public void shutdown() {
        log.info("[PreciseScheduling] 关闭中, 已加载任务数={}", preLoadedTasks.size());
        preLoadedTasks.values().forEach(f -> {
            try {
                f.cancel(false);
            } catch (Exception ignored) {
                // 忽略取消异常
            }
        });
        preLoadedTasks.clear();
        shutdownExecutor(preciseScheduler, "preciseScheduler");
        shutdownExecutor(fastScanner, "fastScanner");
        log.info("[PreciseScheduling] 已关闭");
    }

    /**
     * 快速扫描并预加载即将到期的任务。
     *
     * <p>每 {@code fastScanIntervalMs} 执行一次，查询窗口内到期的 CRON 任务，
     * CAS 推进 next_fire_time 后调度到精准调度线程池。
     */
    private void fastScan() {
        if (!leaderElector.isLeader(leaderRole)) {
            return;
        }
        try {
            CronjobProperties.PreciseScheduling config = cronjobProperties.getPreciseScheduling();
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime windowEnd = now.plusSeconds(config.getPreLoadWindowSeconds());

            // 查询窗口内到期的 CRON 任务
            List<Job> dueJobs = acquireJobsInWindow(now, windowEnd,
                    cronjobProperties.getScanner().getBatchSize());
            if (dueJobs.isEmpty()) {
                return;
            }
            log.debug("[PreciseScheduling] 扫描到 {} 个即将到期任务", dueJobs.size());

            for (Job job : dueJobs) {
                // 去重：已加载的任务不重复加载
                if (preLoadedTasks.containsKey(job.getId())) {
                    continue;
                }
                // CAS 推进 next_fire_time
                LocalDateTime oldNext = job.getNextFireTime();
                LocalDateTime newNext = nextFireTime(job.getCronExpression());
                boolean advanced = advanceNextFireTime(job, oldNext, newNext, now);
                if (!advanced) {
                    continue;
                }
                // 计算延迟并调度
                long delayMs = Duration.between(now, oldNext).toMillis();
                if (delayMs < 0) {
                    // 已过期，立即派发
                    delayMs = 0;
                }
                scheduleDispatch(job, delayMs);
            }
        } catch (Exception e) {
            log.error("[PreciseScheduling] 快速扫描异常: reason={}", e.getMessage(), e);
        }
    }

    /**
     * 调度任务在精确时间派发。
     *
     * @param job     任务定义
     * @param delayMs 延迟毫秒数
     */
    private void scheduleDispatch(Job job, long delayMs) {
        Runnable task = () -> {
            try {
                preLoadedTasks.remove(job.getId());
                TracerUtils.getOrCreateTraceId();
                String logId = taskDispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_CRON);
                log.info("[PreciseScheduling] 精准派发: key={} logId={} delayMs={} traceId={}",
                        job.getJobKey(), logId, delayMs, TracerUtils.getTraceId());
            } catch (Exception e) {
                log.error("[PreciseScheduling] 精准派发异常: key={} reason={}",
                        job.getJobKey(), e.getMessage(), e);
            } finally {
                TracerUtils.clear();
            }
        };
        ScheduledFuture<?> future = preciseScheduler.schedule(task, delayMs, TimeUnit.MILLISECONDS);
        preLoadedTasks.put(job.getId(), future);
        log.debug("[PreciseScheduling] 预加载任务: key={} nextFireTime={} delayMs={}",
                job.getJobKey(), job.getNextFireTime(), delayMs);
    }

    /**
     * 查询窗口内到期的 CRON 任务（事务内抢占）。
     */
    @Transactional(readOnly = true)
    protected List<Job> acquireJobsInWindow(LocalDateTime now, LocalDateTime windowEnd, int limit) {
        return jobMapper.selectDueJobsInWindow(now, windowEnd, limit);
    }

    /**
     * CAS 推进 next_fire_time。
     */
    @Transactional(rollbackFor = Exception.class)
    protected boolean advanceNextFireTime(Job job, LocalDateTime oldNext,
                                          LocalDateTime newNext, LocalDateTime lastFire) {
        if (oldNext == null) {
            return false;
        }
        int affected = jobMapper.advanceNextFireTime(job.getId(), oldNext, newNext, lastFire);
        return affected > 0;
    }

    /**
     * 计算下次触发时间。
     */
    private LocalDateTime nextFireTime(String cron) {
        try {
            CronExpression expr = CronExpression.parse(cron);
            return expr.next(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("[PreciseScheduling] 计算 nextFireTime 失败: cron={} err={}", cron, e.getMessage());
            return null;
        }
    }

    /**
     * 构造守护线程工厂。
     */
    private ThreadFactory buildThreadFactory(String prefix) {
        return r -> {
            Thread t = new Thread(r, prefix + "-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        };
    }

    /**
     * 优雅关闭线程池。
     */
    private void shutdownExecutor(ScheduledExecutorService executor, String name) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        log.info("[PreciseScheduling] {} 已关闭", name);
    }
}
