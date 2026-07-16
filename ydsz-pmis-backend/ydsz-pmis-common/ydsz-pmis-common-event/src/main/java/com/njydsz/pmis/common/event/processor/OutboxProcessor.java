package com.njydsz.pmis.common.event.processor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.event.config.EventProperties;
import com.njydsz.pmis.common.event.gateway.EventPublishGateway;
import com.njydsz.pmis.common.event.model.OutboxMessage;
import com.njydsz.pmis.common.event.model.OutboxStatus;
import com.njydsz.pmis.common.event.repository.OutboxRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Outbox 后台轮询处理器
 *
 * <p>定时扫描 PENDING 状态的 Outbox 消息，通过 {@link EventPublishGateway} 投递到消息队列。
 * 投递成功标记为 SENT，失败则增加重试计数并指数退避。
 *
 * <p>核心增强：
 * <ul>
 *   <li>批量 claim：单条 SQL 原子批量抢占消息，避免 N+1 查询</li>
 *   <li>多线程投递：轮询和投递分离，MQ 慢时不阻塞轮询</li>
 *   <li>超时回收：定期回收卡在 PROCESSING 状态的消息</li>
 *   <li>Gauge 指标：队列深度按状态暴露到 Prometheus</li>
 *   <li>分离 Timer：批量投递和单条投递独立计时</li>
 *   <li>自动清理：定期清理已投递的历史消息</li>
 * </ul>
 *
 * <p>退避策略：baseDelay * 2^min(retryCount,30)，最大不超过 maxBackoffSeconds。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    /** 位移量上限，防止 1L << retryCount 整数溢出 */
    private static final int MAX_SHIFT = 30;

    private final OutboxRepository outboxRepository;
    private final EventPublishGateway publishGateway;
    private final EventProperties properties;
    private final ScheduledExecutorService scheduler;
    private final ThreadPoolExecutor publishExecutor;

    private final Counter publishSuccessCounter;
    private final Counter publishFailureCounter;
    private final Counter deadLetterCounter;
    private final Timer singlePublishTimer;
    private final Timer batchPublishTimer;

    /** 缓存的队列深度（每次轮询后更新，供 Gauge 读取） */
    private volatile Map<String, Long> cachedStatusCounts = Map.of();

    private volatile boolean running = false;

    /**
     * @param outboxRepository Outbox 仓储
     * @param publishGateway    投递网关
     * @param properties        事件配置属性
     * @param meterRegistry     Micrometer 指标注册器（可为 null）
     */
    public OutboxProcessor(OutboxRepository outboxRepository,
                           EventPublishGateway publishGateway,
                           EventProperties properties,
                           MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.publishGateway = publishGateway;
        this.properties = properties;

        // 调度线程（单线程，仅负责轮询和 claim）
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "outbox-scheduler");
            t.setDaemon(true);
            return t;
        });

        // 投递线程池（可配置线程数，负责实际 MQ 发送）
        int workerThreads = Math.max(1, properties.getWorkerThreads());
        this.publishExecutor = new ThreadPoolExecutor(
                workerThreads, workerThreads,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                r -> {
                    Thread t = new Thread(r, "outbox-worker");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        if (meterRegistry != null) {
            this.publishSuccessCounter = Counter.builder("pmis.outbox.publish.success")
                    .description("Outbox messages published successfully")
                    .register(meterRegistry);
            this.publishFailureCounter = Counter.builder("pmis.outbox.publish.failure")
                    .description("Outbox messages failed to publish")
                    .register(meterRegistry);
            this.deadLetterCounter = Counter.builder("pmis.outbox.dead_letter")
                    .description("Outbox messages moved to dead letter")
                    .register(meterRegistry);
            this.singlePublishTimer = Timer.builder("pmis.outbox.publish.single.duration")
                    .description("Outbox single message publish duration")
                    .publishPercentiles(0.5, 0.9, 0.99)
                    .publishPercentileHistogram()
                    .register(meterRegistry);
            this.batchPublishTimer = Timer.builder("pmis.outbox.publish.batch.duration")
                    .description("Outbox batch publish duration")
                    .publishPercentiles(0.5, 0.9, 0.99)
                    .publishPercentileHistogram()
                    .register(meterRegistry);

            // 队列深度 Gauge
            for (OutboxStatus status : OutboxStatus.values()) {
                Gauge.builder("pmis.outbox.queue.size", () -> getCachedCount(status))
                        .tag("status", status.name())
                        .description("Outbox queue depth by status")
                        .register(meterRegistry);
            }
        } else {
            this.publishSuccessCounter = null;
            this.publishFailureCounter = null;
            this.deadLetterCounter = null;
            this.singlePublishTimer = null;
            this.batchPublishTimer = null;
        }
    }

    private long getCachedCount(OutboxStatus status) {
        return cachedStatusCounts.getOrDefault(status.name(), 0L);
    }

    /**
     * 启动轮询
     */
    public void start() {
        if (running) {
            return;
        }
        running = true;

        long pollInterval = properties.getPollIntervalSeconds();

        // 主轮询任务
        scheduler.scheduleWithFixedDelay(this::processBatch,
                pollInterval, pollInterval, TimeUnit.SECONDS);

        // 超时回收任务（每 2 倍轮询间隔执行一次）
        int staleThreshold = properties.getStaleProcessingThresholdMinutes();
        scheduler.scheduleWithFixedDelay(() -> reclaimStaleMessages(staleThreshold),
                pollInterval * 2, pollInterval * 2, TimeUnit.SECONDS);

        // 自动清理任务
        if (properties.isAutoCleanup() && properties.getSentRetentionDays() > 0) {
            long cleanupInterval = properties.getCleanupIntervalHours();
            scheduler.scheduleWithFixedDelay(
                    () -> cleanupSentMessages(properties.getSentRetentionDays()),
                    cleanupInterval, cleanupInterval, TimeUnit.HOURS);
        }

        log.info("OutboxProcessor started: pollInterval={}s, batchSize={}, workerThreads={}, staleThreshold={}min",
                pollInterval, properties.getBatchSize(), properties.getWorkerThreads(), staleThreshold);
    }

    /**
     * 停止轮询
     */
    public void stop() {
        running = false;
        scheduler.shutdown();
        publishExecutor.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (!publishExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                publishExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            publishExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("OutboxProcessor stopped");
    }

    /**
     * 处理一批消息
     */
    void processBatch() {
        try {
            // 更新队列深度缓存（供 Gauge 读取）
            cachedStatusCounts = outboxRepository.countByStatus();

            List<OutboxMessage> messages = outboxRepository.findPending(properties.getBatchSize());
            if (messages.isEmpty()) {
                return;
            }
            log.debug("Processing {} pending outbox messages", messages.size());

            // 批量 claim（单条 SQL）
            List<String> ids = messages.stream().map(OutboxMessage::getId).toList();
            int claimedCount = outboxRepository.claimBatchForProcessing(ids);

            if (claimedCount == 0) {
                return;
            }

            if (claimedCount == messages.size()) {
                // 快速路径：全部 claim 成功
                dispatchPublish(messages);
            } else {
                // 部分被其他实例 claim，逐条 claim 失败的消息跳过
                for (OutboxMessage msg : messages) {
                    if (outboxRepository.claimForProcessing(msg.getId())) {
                        dispatchPublish(List.of(msg));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing outbox batch", e);
        }
    }

    /**
     * 分发投递任务到工作线程池
     *
     * <p>当 workerThreads=1 时直接在调度线程中执行（同步），避免线程切换开销。
     * 当 workerThreads>1 时提交到线程池异步执行。
     */
    private void dispatchPublish(List<OutboxMessage> messages) {
        Runnable task = messages.size() > 1
                ? () -> processBatchPublish(messages)
                : () -> processSingle(messages.get(0));
        if (properties.getWorkerThreads() <= 1) {
            task.run();
        } else {
            publishExecutor.execute(task);
        }
    }

    /**
     * 批量投递
     */
    private void processBatchPublish(List<OutboxMessage> messages) {
        long startNanos = System.nanoTime();
        try {
            List<Boolean> results = publishGateway.publishBatch(messages);
            long durationNanos = System.nanoTime() - startNanos;

            for (int i = 0; i < messages.size() && i < results.size(); i++) {
                OutboxMessage message = messages.get(i);
                if (results.get(i)) {
                    outboxRepository.markAsSent(message.getId());
                    incrementCounter(publishSuccessCounter);
                    log.debug("Outbox message sent: id={}, type={}", message.getId(), message.getEventType());
                } else {
                    handleFailure(message, "Gateway returned false in batch");
                }
            }
            recordTimer(batchPublishTimer, durationNanos);
        } catch (Exception e) {
            recordTimer(batchPublishTimer, System.nanoTime() - startNanos);
            // 批量投递失败，降级为逐条投递
            log.warn("Batch publish failed, falling back to single publish", e);
            for (OutboxMessage message : messages) {
                processSingle(message);
            }
        }
    }

    private void processSingle(OutboxMessage message) {
        long startNanos = System.nanoTime();
        try {
            boolean success = publishGateway.publish(message);
            recordTimer(singlePublishTimer, System.nanoTime() - startNanos);

            if (success) {
                outboxRepository.markAsSent(message.getId());
                incrementCounter(publishSuccessCounter);
                log.debug("Outbox message sent: id={}, type={}", message.getId(), message.getEventType());
            } else {
                handleFailure(message, "Gateway returned false");
            }
        } catch (Exception e) {
            recordTimer(singlePublishTimer, System.nanoTime() - startNanos);
            handleFailure(message, e.getMessage());
        }
    }

    private void handleFailure(OutboxMessage message, String errorMessage) {
        long backoff = calculateBackoff(message.getRetryCount());
        outboxRepository.markAsFailed(message.getId(), errorMessage, backoff);

        incrementCounter(publishFailureCounter);

        if (message.getRetryCount() + 1 >= message.getMaxRetries()) {
            incrementCounter(deadLetterCounter);
            log.warn("Outbox message moved to dead letter: id={}, retryCount={}, error={}",
                    message.getId(), message.getRetryCount() + 1, errorMessage);
        } else {
            log.warn("Outbox message publish failed, will retry: id={}, retryCount={}, backoff={}s, error={}",
                    message.getId(), message.getRetryCount() + 1, backoff, errorMessage);
        }
    }

    /**
     * 指数退避计算
     *
     * <p>使用 {@code Math.min(retryCount, MAX_SHIFT)} 防止位移溢出。
     *
     * @param retryCount 当前重试次数
     * @return 退避秒数
     */
    private long calculateBackoff(int retryCount) {
        int shift = Math.min(retryCount, MAX_SHIFT);
        long backoff = properties.getBaseBackoffSeconds() * (1L << shift);
        return Math.min(backoff, properties.getMaxBackoffSeconds());
    }

    /**
     * 回收超时的 PROCESSING 消息
     */
    private void reclaimStaleMessages(int thresholdMinutes) {
        try {
            outboxRepository.reclaimStaleProcessing(thresholdMinutes);
        } catch (Exception e) {
            log.error("Error reclaiming stale processing messages", e);
        }
    }

    /**
     * 清理已投递消息
     *
     * @param retentionDays 保留天数
     */
    public void cleanupSentMessages(int retentionDays) {
        Instant cutoff = Instant.now().minusSeconds(retentionDays * 86400L);
        int deleted = outboxRepository.deleteSentBefore(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} sent outbox messages older than {} days", deleted, retentionDays);
        }
    }

    private void incrementCounter(Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }

    private void recordTimer(Timer timer, long durationNanos) {
        if (timer != null) {
            timer.record(durationNanos, TimeUnit.NANOSECONDS);
        }
    }
}
