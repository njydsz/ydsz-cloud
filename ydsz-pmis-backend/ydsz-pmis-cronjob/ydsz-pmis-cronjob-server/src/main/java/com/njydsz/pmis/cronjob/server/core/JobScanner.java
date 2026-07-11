package com.njydsz.pmis.cronjob.server.core.dispatch;

import com.njydsz.pmis.common.util.TraceIdUtil;
import com.njydsz.pmis.cronjob.server.config.CronjobProperties;
import com.njydsz.pmis.cronjob.server.core.leader.LeaderElector;
import com.njydsz.pmis.cronjob.server.core.leader.PartitionLeaderManager;
import com.njydsz.pmis.cronjob.domain.entity.job.JobDO;
import com.njydsz.pmis.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.pmis.cronjob.server.metrics.CronjobMetrics;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务扫描器（P1-7 Leader 模式专用）。
 *
 * <p>仅当 {@code pmis.cronjob.leader.enabled=true} 且当前节点是 Leader 时启用。
 * 定时（默认 5s）扫描 {@code pmis_job} 表中 {@code next_fire_time <= NOW()} 的任务，
 * 通过 {@code SELECT ... FOR UPDATE SKIP LOCKED} 抢占式行锁获取待派发任务，
 * 然后调用 {@link TaskDispatcher#dispatch(JobDO, String, String)} 派发到执行节点。
 *
 * <h3>执行流程</h3>
 * <ol>
 *   <li>检查 Leader 身份（非 Leader 节点直接返回，避免重复扫描）</li>
 *   <li>开启事务，调用 {@link JobMapper#selectDueJobs(LocalDateTime, int)} 抢占式扫描</li>
 *   <li>对每个任务 CAS 推进 {@code next_fire_time}（防止 Leader 切换时重复派发）</li>
 *   <li>提交事务后调用 {@link TaskDispatcher} 派发（避免长事务阻塞）</li>
 *   <li>派发结果（成功/失败/跳过）记录到日志</li>
 * </ol>
 *
 * <p><b>避免重复派发的设计</b>：
 * <ul>
 *   <li>DB 行锁：{@code FOR UPDATE SKIP LOCKED} 保证多个 Leader 候选节点互不冲突</li>
 *   <li>CAS 推进：{@code WHERE next_fire_time = #{oldNextFireTime}} 保证 Leader 切换时不重复</li>
 *   <li>Redis 任务锁：{@link TaskDispatcher} 内部的 {@code pmis:job:lock:*} 锁兜底</li>
 * </ul>
 *
 * <p><b>故障转移</b>：Leader 节点宕机后，lease 到期自动释放，其他节点竞选为新 Leader，
 * 新 Leader 扫描时会重新发现 {@code next_fire_time <= NOW()} 的任务并派发。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(LeaderElector.class)
public class JobScanner {

    private final JobMapper jobMapper;
    private final LeaderElector leaderElector;
    private final TaskDispatcher taskDispatcher;
    private final CronjobProperties cronjobProperties;
    /** P6-2: Prometheus 指标收集器（可选注入，未配置时不记录指标） */
    private final ObjectProvider<CronjobMetrics> cronjobMetricsProvider;
    /** P2-9: 分区 Leader 管理器（可选注入，仅分区调度启用时存在） */
    private final ObjectProvider<PartitionLeaderManager> partitionLeaderManagerProvider;
    /** P1-1: 自适应批量调度器（可选注入，启用时动态调整 batchSize） */
    private final ObjectProvider<com.njydsz.pmis.cronjob.server.core.scheduler.AdaptiveBatchScheduler> adaptiveBatchSchedulerProvider;

    /** 扫描执行中标志（避免上次扫描未完成时重叠触发） */
    private final AtomicBoolean scanning = new AtomicBoolean(false);

    /** Leader 角色（从配置读取，便于多套调度集群隔离） */
    private String leaderRole;

    /** P0-2: 并行派发线程池 */
    private ExecutorService dispatchPool;

    @PostConstruct
    public void init() {
        this.leaderRole = cronjobProperties.getLeader().getRole();
        if (cronjobProperties.getLeader().isEnabled()) {
            // P0-2: 初始化并行派发线程池
            if (cronjobProperties.getScanner().isParallelDispatchEnabled()) {
                int poolSize = cronjobProperties.getScanner().getParallelDispatchPoolSize();
                this.dispatchPool = Executors.newFixedThreadPool(poolSize, r -> {
                    Thread t = new Thread(r, "job-scanner-dispatch");
                    t.setDaemon(true);
                    return t;
                });
                log.info("[JobScanner] 初始化完成, role={} scanInterval={}ms batchSize={} parallelDispatch=true poolSize={}",
                        leaderRole, cronjobProperties.getScanner().getIntervalMs(),
                        cronjobProperties.getScanner().getBatchSize(), poolSize);
            } else {
                log.info("[JobScanner] 初始化完成, role={} scanInterval={}ms batchSize={} parallelDispatch=false",
                        leaderRole, cronjobProperties.getScanner().getIntervalMs(),
                        cronjobProperties.getScanner().getBatchSize());
            }
        } else {
            log.info("[JobScanner] leader.enabled=false, 扫描器不启用（Leaderless 模式）");
        }
    }

    /**
     * 定时扫描待触发任务。
     *
     * <p>使用 {@code fixedDelayString} 而非 {@code fixedRateString}，
     * 避免上次扫描耗时较长时任务堆积。
     */
    @Scheduled(fixedDelayString = "${pmis.cronjob.scanner.interval-ms:5000}")
    public void scan() {
        if (!cronjobProperties.getLeader().isEnabled()) {
            return;
        }
        if (!leaderElector.isLeader(leaderRole)) {
            return;
        }
        if (!scanning.compareAndSet(false, true)) {
            log.debug("[JobScanner] 上次扫描尚未完成, 跳过本次执行");
            return;
        }
        // P6-2: 更新扫描中状态指标
        CronjobMetrics metrics = cronjobMetricsProvider.getIfAvailable();
        if (metrics != null) {
            metrics.setScanning(true);
        }
        try {
            doScan();
        } catch (Exception e) {
            log.error("[JobScanner] 扫描异常: role={} reason={}", leaderRole, e.getMessage(), e);
        } finally {
            scanning.set(false);
            // P6-2: 更新扫描中状态指标
            if (metrics != null) {
                metrics.setScanning(false);
            }
        }
    }

    /**
     * 执行一次扫描（事务内抢占 + CAS 推进 + 事务外派发）。
     *
     * <p>P2-2: 在派发前先判定 Misfire：
     * <ul>
     *   <li>{@link MisfirePolicy#SKIP} 跳过本次错过的触发，仅推进 next_fire_time</li>
     *   <li>{@link MisfirePolicy#FIRE_NOW} 立即执行一次（默认）</li>
     *   <li>{@link MisfirePolicy#COALESCE} 执行一次，日志 triggerType 标记 MISFIRED</li>
     * </ul>
     *
     * <p>P6-1: 在派发前通过 {@link TraceIdUtil#getOrCreate()} 初始化 traceId 到 MDC，
     * 使 DefaultTaskDispatcher 写入 job_log.trace_id 时能取到非空值，
     * 实现"扫描 → 派发 → 执行 → 日志"全链路 traceId 串联。
     * 单个任务派发完成后立即清理 MDC，避免 traceId 串任务。
     */
    private void doScan() {
        LocalDateTime now = LocalDateTime.now();
        // P1-1: 支持自适应 batchSize（AdaptiveBatchScheduler 启用时动态调整）
        int batchSize = resolveBatchSize();
        List<JobDO> dueJobs = acquireDueJobs(now, batchSize);
        // P6-2: 更新上次扫描到的待触发任务数指标
        CronjobMetrics metrics = cronjobMetricsProvider.getIfAvailable();
        if (metrics != null) {
            metrics.setLastScanDueJobs(dueJobs.size());
        }
        if (dueJobs.isEmpty()) {
            return;
        }
        log.info("[JobScanner] 扫描到 {} 个待触发任务: role={}", dueJobs.size(), leaderRole);

        // P0-2: 并行派发模式
        if (cronjobProperties.getScanner().isParallelDispatchEnabled() && dispatchPool != null) {
            doParallelDispatch(dueJobs, now, metrics);
        } else {
            doSequentialDispatch(dueJobs, now, metrics);
        }
    }

    /**
     * P0-2: 并行派发待触发任务。
     *
     * <p>每个任务的 Misfire 判定 + CAS 推进 + dispatch 在独立线程中执行，
     * CAS 操作（WHERE next_fire_time = old）保证幂等，并行不会导致重复派发。
     * 使用 CountDownLatch 等待全部完成后返回，确保单次扫描内不遗漏。
     *
     * @param dueJobs 待触发任务列表
     * @param now     扫描时间
     * @param metrics 指标收集器（可空）
     */
    private void doParallelDispatch(List<JobDO> dueJobs, LocalDateTime now, CronjobMetrics metrics) {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger skipCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<CompletableFuture<Void>> futures = new ArrayList<>(dueJobs.size());
        for (JobDO job : dueJobs) {
            CompletableFuture<Void> f = CompletableFuture.runAsync(
                    () -> dispatchSingleJob(job, now, metrics, successCount, skipCount, failCount),
                    dispatchPool);
            futures.add(f);
        }
        // 等待全部完成，任一异常不影响其他任务
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        log.info("[JobScanner] 并行派发完成: total={} success={} skip={} fail={}",
                dueJobs.size(), successCount.get(), skipCount.get(), failCount.get());
    }

    /**
     * P0-2: 串行派发（兼容模式，parallelDispatchEnabled=false 时使用）。
     */
    private void doSequentialDispatch(List<JobDO> dueJobs, LocalDateTime now, CronjobMetrics metrics) {
        for (JobDO job : dueJobs) {
            dispatchSingleJob(job, now, metrics, null, null, null);
        }
    }

    /**
     * P0-2: 派发单个任务（Misfire 判定 + CAS 推进 + dispatch）。
     *
     * <p>提取公共逻辑，串行/并行模式共用。每个任务独立生成 traceId，
     * 异常不传播到外层，仅记录日志并递增计数器。
     */
    private void dispatchSingleJob(JobDO job, LocalDateTime now, CronjobMetrics metrics,
                                    AtomicInteger successCount, AtomicInteger skipCount,
                                    AtomicInteger failCount) {
        // P2-9: 分区调度过滤 — 非本节点分区的任务跳过
        PartitionLeaderManager partitionManager = partitionLeaderManagerProvider.getIfAvailable();
        if (partitionManager != null && !partitionManager.isMyPartition(job)) {
            log.debug("[JobScanner] 任务不属于本节点分区, 跳过: key={} partition={}",
                    job.getJobKey(), partitionManager.computePartition(job));
            if (skipCount != null) skipCount.incrementAndGet();
            return;
        }
        // P6-1: 为每个任务派发生成独立 traceId，保证任务间链路隔离
        TraceIdUtil.getOrCreate();
        try {
            // P2-2: Misfire 判定
            MisfirePolicy policy = MisfirePolicy.parse(job.getMisfirePolicy());
            boolean misfired = isMisfired(job, now);
            if (misfired && policy == MisfirePolicy.SKIP) {
                // 仅推进 next_fire_time，不派发
                LocalDateTime newNext = nextFireTime(job.getCronExpression());
                boolean advanced = advanceNextFireTime(job, job.getNextFireTime(), newNext, now);
                // P6-2: Misfire SKIP 计数
                if (metrics != null) {
                    metrics.incMisfire("SKIP");
                }
                log.info("[JobScanner] Misfire SKIP 跳过派发: key={} advanced={}",
                        job.getJobKey(), advanced);
                if (skipCount != null) skipCount.incrementAndGet();
                return;
            }
            // 计算新的 next_fire_time 并 CAS 推进
            LocalDateTime oldNext = job.getNextFireTime();
            LocalDateTime newNext = nextFireTime(job.getCronExpression());
            boolean advanced = advanceNextFireTime(job, oldNext, newNext, now);
            if (!advanced) {
                log.debug("[JobScanner] 任务 next_fire_time 已被其他节点推进, 跳过: key={}",
                        job.getJobKey());
                if (skipCount != null) skipCount.incrementAndGet();
                return;
            }
            // P2-2: 选择 triggerType
            String triggerType = DefaultTaskDispatcher.TRIGGER_CRON;
            if (misfired && policy == MisfirePolicy.COALESCE) {
                triggerType = DefaultTaskDispatcher.TRIGGER_MISFIRED;
                if (metrics != null) {
                    metrics.incMisfire("COALESCE");
                }
                log.info("[JobScanner] Misfire COALESCE 派发（日志标记 MISFIRED）: key={}",
                        job.getJobKey());
            } else if (misfired) {
                if (metrics != null) {
                    metrics.incMisfire("FIRE_NOW");
                }
                log.info("[JobScanner] Misfire FIRE_NOW 立即派发: key={}", job.getJobKey());
            }
            String logId = taskDispatcher.dispatch(job, null, triggerType);
            if (logId == null) {
                log.debug("[JobScanner] 任务异步派发或被跳过: key={} triggerType={}",
                        job.getJobKey(), triggerType);
            } else {
                log.info("[JobScanner] 任务派发成功: key={} logId={} triggerType={} traceId={}",
                        job.getJobKey(), logId, triggerType, TraceIdUtil.get());
            }
            if (successCount != null) successCount.incrementAndGet();
        } catch (Exception e) {
            log.error("[JobScanner] 任务派发失败: key={} reason={}",
                    job.getJobKey(), e.getMessage(), e);
            if (failCount != null) failCount.incrementAndGet();
        } finally {
            // P6-1: 清理 MDC，避免 traceId 串到下一个任务
            TraceIdUtil.clear();
        }
    }

    /**
     * 判定任务是否 Misfire。
     *
     * <p>当 {@code next_fire_time} 早于 {@code NOW() - misfireGraceMinutes} 时视为 Misfire。
     *
     * @param job 任务定义
     * @param now 当前时间
     * @return true 视为 Misfire
     */
    private boolean isMisfired(JobDO job, LocalDateTime now) {
        if (job.getNextFireTime() == null) {
            return false;
        }
        Duration grace = Duration.ofMinutes(cronjobProperties.getScanner().getMisfireGraceMinutes());
        LocalDateTime threshold = now.minus(grace);
        return job.getNextFireTime().isBefore(threshold);
    }

    /**
     * 抢占式扫描待触发任务（事务内）。
     */
    @Transactional(readOnly = true)
    protected List<JobDO> acquireDueJobs(LocalDateTime now, int batchSize) {
        return jobMapper.selectDueJobs(now, batchSize);
    }

    /**
     * CAS 推进 next_fire_time（事务内，防止重复派发）。
     */
    @Transactional(rollbackFor = Exception.class)
    protected boolean advanceNextFireTime(JobDO job, LocalDateTime oldNext,
                                          LocalDateTime newNext, LocalDateTime lastFire) {
        if (oldNext == null) {
            log.warn("[JobScanner] next_fire_time 为 null, 跳过 CAS: key={}", job.getJobKey());
            return false;
        }
        int affected = jobMapper.advanceNextFireTime(job.getId(), oldNext, newNext, lastFire);
        return affected > 0;
    }

    /**
     * 计算下次触发时间（基于 CronExpression，Asia/Shanghai 时区）。
     */
    private LocalDateTime nextFireTime(String cron) {
        try {
            Assert.hasText(cron, "cron 表达式不能为空");
            CronExpression expr = CronExpression.parse(cron);
            return expr.next(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("[JobScanner] 计算 nextFireTime 失败: cron={} err={}", cron, e.getMessage());
            return null;
        }
    }

    /**
     * 优雅下线：无需特殊处理，{@link LeaderElector#release(String)} 会释放 Leader 锁。
     */
    @PreDestroy
    public void shutdown() {
        log.info("[JobScanner] 关闭: role={}", leaderRole);
        // P0-2: 关闭并行派发线程池
        if (dispatchPool != null && !dispatchPool.isShutdown()) {
            dispatchPool.shutdown();
            log.info("[JobScanner] 并行派发线程池已关闭");
        }
    }

    /**
     * 暴露扫描中状态（仅供测试断言使用）。
     */
    boolean isScanning() {
        return scanning.get();
    }

    /**
     * 暴露 Leader 角色（仅供测试断言使用）。
     */
    String getLeaderRole() {
        return leaderRole;
    }

    /**
     * P1-1: 解析当前扫描的 batchSize。
     *
     * <p>当 AdaptiveBatchScheduler 启用时，返回自适应调整后的 batchSize；
     * 否则返回配置的固定 batchSize。
     *
     * @return 当前扫描使用的 batchSize
     */
    private int resolveBatchSize() {
        com.njydsz.pmis.cronjob.server.core.scheduler.AdaptiveBatchScheduler adaptive =
                adaptiveBatchSchedulerProvider.getIfAvailable();
        if (adaptive != null) {
            return adaptive.getCurrentBatchSize();
        }
        return cronjobProperties.getScanner().getBatchSize();
    }

    /**
     * 计算任务 Misfire 宽容窗口（仅供测试断言使用）。
     */
    Duration getMisfireGrace() {
        return Duration.ofMinutes(cronjobProperties.getScanner().getMisfireGraceMinutes());
    }
}
