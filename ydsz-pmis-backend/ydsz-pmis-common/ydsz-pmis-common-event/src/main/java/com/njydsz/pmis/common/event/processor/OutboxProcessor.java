package com.njydsz.pmis.common.event.processor;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.event.config.EventProperties;
import com.njydsz.pmis.common.event.gateway.EventPublishGateway;
import com.njydsz.pmis.common.event.model.OutboxMessage;
import com.njydsz.pmis.common.event.repository.OutboxRepository;

import io.micrometer.core.instrument.Counter;
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
 *   <li>原子 claim 机制：多实例部署时通过 DB UPDATE 原子抢占消息，避免重复投递</li>
 *   <li>超时回收：定期回收卡在 PROCESSING 状态的消息（实例宕机恢复）</li>
 *   <li>批量投递：利用 MQ 批量发送能力提升吞吐量</li>
 *   <li>Timer 指标：P50/P90/P99 投递耗时监控</li>
 *   <li>自动清理：定期清理已投递的历史消息</li>
 * </ul>
 *
 * <p>退避策略：baseDelay * 2^retryCount，最大不超过 maxBackoffSeconds。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private final OutboxRepository outboxRepository;
    private final EventPublishGateway publishGateway;
    private final EventProperties properties;
    private final ScheduledExecutorService scheduler;

    private final Counter publishSuccessCounter;
    private final Counter publishFailureCounter;
    private final Counter deadLetterCounter;
    private final Timer publishTimer;

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
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "outbox-processor");
            t.setDaemon(true);
            return t;
        });

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
            this.publishTimer = Timer.builder("pmis.outbox.publish.duration")
                    .description("Outbox message publish duration")
                    .publishPercentiles(0.5, 0.9, 0.99)
                    .publishPercentileHistogram()
                    .register(meterRegistry);
        } else {
            this.publishSuccessCounter = null;
            this.publishFailureCounter = null;
            this.deadLetterCounter = null;
            this.publishTimer = null;
        }
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

        log.info("OutboxProcessor started: pollInterval={}s, batchSize={}, staleThreshold={}min",
                pollInterval, properties.getBatchSize(), staleThreshold);
    }

    /**
     * 停止轮询
     */
    public void stop() {
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("OutboxProcessor stopped");
    }

    /**
     * 处理一批消息
     */
    void processBatch() {
        try {
            List<OutboxMessage> messages = outboxRepository.findPending(properties.getBatchSize());
            if (messages.isEmpty()) {
                return;
            }
            log.debug("Processing {} pending outbox messages", messages.size());

            // 原子 claim 每条消息，仅处理 claim 成功的
            List<OutboxMessage> claimed = messages.stream()
                    .filter(msg -> {
                        boolean claimedFlag = outboxRepository.claimForProcessing(msg.getId());
                        if (!claimedFlag) {
                            log.debug("Message already claimed by another instance: id={}", msg.getId());
                        }
                        return claimedFlag;
                    })
                    .toList();

            if (claimed.isEmpty()) {
                return;
            }

            // 尝试批量投递
            if (claimed.size() > 1) {
                processBatchPublish(claimed);
            } else {
                processSingle(claimed.get(0));
            }
        } catch (Exception e) {
            log.error("Error processing outbox batch", e);
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
            recordTimer(publishTimer, durationNanos);
        } catch (Exception e) {
            long durationNanos = System.nanoTime() - startNanos;
            recordTimer(publishTimer, durationNanos);
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
            long durationNanos = System.nanoTime() - startNanos;
            recordTimer(publishTimer, durationNanos);

            if (success) {
                outboxRepository.markAsSent(message.getId());
                incrementCounter(publishSuccessCounter);
                log.debug("Outbox message sent: id={}, type={}", message.getId(), message.getEventType());
            } else {
                handleFailure(message, "Gateway returned false");
            }
        } catch (Exception e) {
            long durationNanos = System.nanoTime() - startNanos;
            recordTimer(publishTimer, durationNanos);
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
     * @param retryCount 当前重试次数
     * @return 退避秒数
     */
    private long calculateBackoff(int retryCount) {
        long backoff = properties.getBaseBackoffSeconds() * (1L << retryCount);
        return Math.min(backoff, properties.getMaxBackoffSeconds());
    }

    /**
     * 回收超时的 PROCESSING 消息
     *
     * @param thresholdMinutes 超时阈值（分钟）
     */
    private void reclaimStaleMessages(int thresholdMinutes) {
        try {
            outboxRepository.reclaimStaleProcessing(thresholdMinutes);
        } catch (Exception e) {
            log.error("Error reclaiming stale processing messages", e);
        }
    }

    /**
     * 清理已投递消息（定期维护调用）
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
