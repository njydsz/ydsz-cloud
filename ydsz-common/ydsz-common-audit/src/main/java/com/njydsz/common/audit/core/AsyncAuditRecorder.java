package com.njydsz.common.audit.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import com.njydsz.common.audit.config.AuditProperties;
import com.njydsz.common.audit.domain.AuditLog;
import com.njydsz.common.util.concurrent.ExecutorUtils;

/**
 * 异步批量审计记录器
 *
 * <p>使用 LinkedBlockingQueue 作为缓冲队列，通过 ScheduledExecutorService 定时批量写入。
 * 相比同步写入，显著降低对主业务的性能影响。
 *
 * <p>特性：
 * <ul>
 *   <li>基于 LinkedBlockingQueue 的高性能缓冲队列（有界队列，支持背压控制）</li>
 *   <li>使用 ScheduledExecutorService 定时刷新，支持按阈值刷新和定时刷新双机制</li>
 *   <li>写入操作委托给 {@link AuditWriter}，支持 JDBC、消息队列等多种后端</li>
 *   <li>优雅停机时自动将队列剩余日志全部写入（通过 DisposableBean 接口）</li>
 *   <li>写入失败数据不丢失，降级到磁盘兜底</li>
 *   <li>队列满时支持三种拒绝策略：DISCARD_OLDEST（丢弃最旧）、DISCARD_NEWEST（丢弃最新）、CALLER_RUNS（调用者阻塞）</li>
 *   <li>提供队列使用率监控指标，支持背压感知</li>
 * </ul>
 *
 * <p><b>队列满拒绝策略：</b></p>
 * <ul>
 *   <li>DISCARD_OLDEST（默认）：弹出队列中最旧的日志，尝试放入新日志</li>
 *   <li>DISCARD_NEWEST：直接丢弃最新日志</li>
 *   <li>CALLER_RUNS：调用者线程阻塞等待队列有空位（带超时）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
public class AsyncAuditRecorder implements AuditRecorder, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(AsyncAuditRecorder.class);

    /** 优雅停机超时时间（秒） */
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;
    /** CALLER_RUNS 策略阻塞等待超时时间（毫秒） */
    private static final long DEFAULT_BLOCK_TIMEOUT_MS = 3000L;
    /** 告警日志节流间隔（毫秒），避免频繁刷日志 */
    private static final long WARN_LOG_THROTTLE_MS = 10_000L;

    /** 异步缓冲队列，有界队列支持背压控制 */
    private final BlockingQueue<AuditLog> queue;
    /** 审计写入器，负责实际的持久化操作 */
    private final AuditWriter auditWriter;
    /** 审计配置属性 */
    private final AuditProperties properties;
    /** 异步批量写入配置 */
    private final AuditProperties.AsyncProperties asyncProps;
    /** 定时调度线程池，用于周期性刷新队列 */
    private final ScheduledExecutorService scheduler;
    /** 记录器运行状态标志 */
    private final AtomicBoolean running = new AtomicBoolean(true);
    /** 磁盘兜底写入器 */
    private final AuditFallbackWriter fallbackWriter;
    /** 队列满告警计数 */
    private final AtomicLong queueFullWarnCount = new AtomicLong(0);
    /** 上一次告警日志时间戳（用于节流） */
    private volatile long lastWarnLogTime = 0;
    /** 防止并发刷新的锁对象 */
    private final Object flushLock = new Object();

    /**
     * 构造函数
     *
     * @param auditWriter 审计写入器
     * @param properties  审计配置属性
     */
    public AsyncAuditRecorder(AuditWriter auditWriter, AuditProperties properties) {
        this.auditWriter = Objects.requireNonNull(auditWriter, "AuditWriter must not be null");
        this.properties = Objects.requireNonNull(properties, "AuditProperties must not be null");
        this.asyncProps = properties.getAsync();
        this.queue = new LinkedBlockingQueue<>(asyncProps.getExecutorQueueCapacity());
        this.scheduler = ExecutorUtils.newScheduledThreadPool(1, "audit-scheduler");
        this.fallbackWriter = new AuditFallbackWriter();

        // 启动定时刷新任务
        scheduler.scheduleAtFixedRate(
                this::flushFromQueue,
                asyncProps.getBatchIntervalMillis(),
                asyncProps.getBatchIntervalMillis(),
                TimeUnit.MILLISECONDS
        );

        log.info("【异步审计记录器】启动成功, 队列容量={}, 批量阈值={}, 刷新间隔={}ms, 写入器={}",
                asyncProps.getExecutorQueueCapacity(), asyncProps.getBatchSize(),
                asyncProps.getBatchIntervalMillis(), auditWriter.getName());
    }

    @Override
    public void record(AuditLog auditLog) {
        recordAsync(auditLog);
    }

    @Override
    public void recordAsync(AuditLog auditLog) {
        if (auditLog == null) {
            log.warn("【异步审计记录器】审计日志为空, 跳过记录");
            return;
        }

        if (!running.get()) {
            log.warn("【异步审计记录器】记录器已停止, 尝试同步写入");
            try {
                auditWriter.write(auditLog);
            } catch (Exception e) {
                log.error("【异步审计记录器】同步写入失败", e);
            }
            return;
        }

        boolean offered = queue.offer(auditLog);
        if (!offered) {
            handleQueueFull(auditLog);
            return;
        }

        // 当队列大小达到批量阈值时，立即触发一次刷新
        if (queue.size() >= asyncProps.getBatchSize()) {
            triggerFlush();
        }
    }

    /**
     * 触发一次非阻塞刷新（如果当前没有在刷新）
     */
    private void triggerFlush() {
        try {
            scheduler.submit(this::flushFromQueue);
        } catch (RejectedExecutionException e) {
            log.debug("【异步审计记录器】刷新任务已被拒绝，可能调度器已关闭");
        }
    }

    /**
     * 处理队列已满的情况
     *
     * @param auditLog 待写入的审计日志
     */
    private void handleQueueFull(AuditLog auditLog) {
        String strategy = asyncProps.getAsyncRejectPolicy();
        long warnCount = queueFullWarnCount.incrementAndGet();
        logQueueFullWarn(auditLog, strategy, warnCount);

        if ("DISCARD_OLDEST".equalsIgnoreCase(strategy)) {
            queue.poll();
            boolean offered = queue.offer(auditLog);
            if (!offered) {
                log.error("【异步审计记录器】队列已满, 丢弃最旧日志后仍然无法入队, id={}", auditLog.getId());
            }
            return;
        }

        if ("DISCARD_NEWEST".equalsIgnoreCase(strategy)) {
            log.error("【异步审计记录器】队列已满({}), 最新审计日志将被丢弃, id={}",
                    asyncProps.getExecutorQueueCapacity(), auditLog.getId());
            return;
        }

        if ("CALLER_RUNS".equalsIgnoreCase(strategy)) {
            try {
                boolean success = queue.offer(auditLog, DEFAULT_BLOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (!success) {
                    log.error("【异步审计记录器】阻塞等待超时({}ms), 队列仍未空出位置, 日志将被丢弃, id={}",
                            DEFAULT_BLOCK_TIMEOUT_MS, auditLog.getId());
                    writeToFallback(auditLog);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("【异步审计记录器】阻塞等待被中断, 尝试磁盘兜底, id={}", auditLog.getId());
                writeToFallback(auditLog);
            }
            return;
        }

        log.error("【异步审计记录器】未知队列满策略: {}, 默认丢弃最新日志, id={}", strategy, auditLog.getId());
    }

    /**
     * 队列满告警日志（带节流，避免频繁刷日志）
     */
    private void logQueueFullWarn(AuditLog auditLog, String strategy, long warnCount) {
        long now = System.currentTimeMillis();
        long elapsed = now - lastWarnLogTime;
        if (elapsed >= WARN_LOG_THROTTLE_MS || warnCount <= 5) {
            lastWarnLogTime = now;
            double usageRatio = getQueueUsageRatio();
            log.error("【异步审计记录器】队列已满! 容量={}, 当前={}, 使用率={}%, 策略={}, 累计触发={}",
                    asyncProps.getExecutorQueueCapacity(), queue.size(),
                    String.format("%.1f", usageRatio * 100), strategy, warnCount);
        }
    }

    /**
     * 获取队列使用率（队列当前大小 / 容量比率）
     *
     * @return 使用率，范围 [0.0, 1.0]
     */
    public double getQueueUsageRatio() {
        int capacity = asyncProps.getExecutorQueueCapacity();
        if (capacity <= 0) {
            return 0.0;
        }
        return (double) queue.size() / capacity;
    }

    /**
     * 获取当前队列大小
     *
     * @return 队列中待写入的审计日志数量
     */
    public int getQueueSize() {
        return queue.size();
    }

    /**
     * 获取队列满告警累计次数
     *
     * @return 队列满告警累计触发次数
     */
    public long getQueueFullWarnCount() {
        return queueFullWarnCount.get();
    }

    /**
     * 将审计日志写入磁盘兜底（委托给 AuditFallbackWriter）
     *
     * @param auditLog 待写入的审计日志
     */
    private void writeToFallback(AuditLog auditLog) {
        fallbackWriter.writeToFallback(auditLog);
    }

    /**
     * 从磁盘恢复审计日志到队列（委托给 AuditFallbackWriter）
     */
    public void recoverFromFallback() {
        List<Path> fallbackFiles = fallbackWriter.listFallbackFiles();
        if (fallbackFiles.isEmpty()) {
            return;
        }

        log.info("【异步审计记录器】开始恢复磁盘兜底日志, 文件数={}", fallbackFiles.size());

        for (Path file : fallbackFiles) {
            List<AuditLog> logs = fallbackWriter.readFromFallbackFile(file);
            int recovered = 0;

            for (AuditLog auditLog : logs) {
                boolean offered = queue.offer(auditLog);
                if (offered) {
                    recovered++;
                } else {
                    log.warn("【异步审计记录器】恢复时队列已满, 停止恢复, file={}", file);
                    break;
                }
            }

            fallbackWriter.deleteFallbackFile(file);
            log.info("【异步审计记录器】磁盘兜底文件已恢复, file={}, recovered={}", file, recovered);
        }
    }

    @Override
    public void recordBatch(List<AuditLog> auditLogs) {
        if (auditLogs == null || auditLogs.isEmpty()) {
            return;
        }

        if (!running.get()) {
            log.warn("【异步审计记录器】记录器已停止, 尝试同步批量写入, count={}", auditLogs.size());
            try {
                auditWriter.writeBatch(auditLogs);
            } catch (Exception e) {
                log.error("【异步审计记录器】同步批量写入失败", e);
            }
            return;
        }

        for (AuditLog auditLog : auditLogs) {
            if (auditLog != null) {
                recordAsync(auditLog);
            }
        }
    }

    @Override
    public String getName() {
        return "AsyncAuditRecorder";
    }

    /**
     * 返回异步记录器的健康状态（含队列水位和丢弃计数）
     *
     * @return 健康信息
     */
    @Override
    public HealthInfo health() {
        double usageRatio = getQueueUsageRatio();
        long queueFullCount = getQueueFullWarnCount();

        HealthInfo info = HealthInfo.up()
                .withDetail("queueSize", getQueueSize())
                .withDetail("queueUsageRatio", String.format("%.1f%%", usageRatio * 100))
                .withDetail("queueFullCount", queueFullCount);

        if (usageRatio > QUEUE_USAGE_WARN_THRESHOLD) {
            return HealthInfo.down(info.getDetails())
                    .withDetail("error", "队列使用率超过80%，审计日志可能被丢弃");
        }
        if (queueFullCount > 0) {
            info.withDetail("warning", "累计丢弃审计日志: " + queueFullCount + " 条");
        }
        return info;
    }

    /**
     * 设置队列满时的兜底策略
     *
     * @param strategy DISCARD_OLDEST（丢弃最旧）| DISCARD_NEWEST（丢弃最新）| CALLER_RUNS（调用者阻塞）
     */
    public void setRejectPolicy(String strategy) {
        if (strategy == null || strategy.isEmpty()) {
            throw new IllegalArgumentException("策略不能为空");
        }
        String upper = strategy.toUpperCase();
        if (!"DISCARD_OLDEST".equals(upper) && !"DISCARD_NEWEST".equals(upper) && !"CALLER_RUNS".equals(upper)) {
            throw new IllegalArgumentException("策略必须为 DISCARD_OLDEST、DISCARD_NEWEST 或 CALLER_RUNS");
        }
        asyncProps.setRejectPolicy(upper);
        log.info("【异步审计记录器】队列满策略已设置为: {}", upper);
    }

    /**
     * 设置磁盘兜底路径
     *
     * @param path 磁盘文件路径
     */
    public void setDiskFallbackPath(String path) {
        fallbackWriter.setFallbackDir(path);
        log.info("【异步审计记录器】磁盘兜底路径已设置为: {}", path);
    }

    /**
     * 从队列中批量取出审计日志并写入。
     * 此方法由定时调度任务和队列满阈值触发调用。
     */
    private void flushFromQueue() {
        if (!running.get()) {
            return;
        }

        // 防止并发刷新
        synchronized (flushLock) {
            List<AuditLog> batch = new ArrayList<>(asyncProps.getBatchSize());
            queue.drainTo(batch, asyncProps.getBatchSize());

            if (batch.isEmpty()) {
                return;
            }

            flushBatch(batch);
        }
    }

    /**
     * 批量写入审计日志
     *
     * @param batch 待写入的审计日志列表
     */
    private void flushBatch(List<AuditLog> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }

        int total = batch.size();

        try {
            auditWriter.writeBatch(batch);
            log.debug("【异步审计记录器】批量写入成功, total={}", total);
        } catch (Exception e) {
            log.warn("【异步审计记录器】批量写入失败, count={}, 尝试磁盘兜底写入", total, e);
            writeBatchToFallback(batch);
        }
    }

    /**
     * 批量写入失败时，将失败的批次写入磁盘兜底文件；
     * 若磁盘兜底也已失效，则尝试将日志重新放回队列尾部，避免审计日志永久丢失。
     *
     * @param batch 写入失败的审计日志批次
     */
    private void writeBatchToFallback(List<AuditLog> batch) {
        fallbackWriter.writeBatchToFallback(batch);

        // 磁盘兜底已失效，尝试将日志重新放回队列尾部，避免数据永久丢失
        if (fallbackWriter.isDiskFallbackFailed()) {
            log.warn("【异步审计记录器】磁盘兜底已失效, 尝试将失败批次重新放回队列尾部, count={}", batch.size());
            for (AuditLog auditLog : batch) {
                boolean offered = queue.offer(auditLog);
                if (!offered) {
                    log.error("【异步审计记录器】磁盘兜底失效且队列已满, 审计日志将丢失, id={}", auditLog.getId());
                }
            }
        }
    }

    /**
     * 优雅停机
     * 停止接收新日志，等待队列中剩余日志全部写入
     */
    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            log.warn("【异步审计记录器】记录器已处于停止状态");
            return;
        }

        log.info("【异步审计记录器】开始优雅停机...");
        scheduler.shutdown();

        try {
            if (!scheduler.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("【异步审计记录器】优雅停机超时({}s), 强制退出", SHUTDOWN_TIMEOUT_SECONDS);
                scheduler.shutdownNow();
            } else {
                // 确保队列中剩余日志全部写入
                flushRemaining();
                log.info("【异步审计记录器】优雅停机完成, 队列剩余日志已全部写入");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
            log.error("【异步审计记录器】优雅停机被中断", e);
        }
    }

    /**
     * 写入队列中剩余的日志
     */
    private void flushRemaining() {
        List<AuditLog> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            log.info("【异步审计记录器】写入剩余 {} 条审计日志", remaining.size());
            flushBatch(remaining);
        }
    }

    /**
     * 实现 DisposableBean 接口，在 Spring 容器关闭时自动调用
     */
    @Override
    public void destroy() throws Exception {
        shutdown();
    }
}
