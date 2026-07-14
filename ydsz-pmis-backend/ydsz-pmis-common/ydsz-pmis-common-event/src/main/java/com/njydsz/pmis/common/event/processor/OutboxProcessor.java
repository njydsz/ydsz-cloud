package com.njydsz.pmis.common.event.processor;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.event.gateway.EventPublishGateway;
import com.njydsz.pmis.common.event.model.OutboxMessage;
import com.njydsz.pmis.common.event.repository.OutboxRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Outbox 后台轮询处理器
 *
 * <p>定时扫描 PENDING 状态的 Outbox 消息，通过 {@link EventPublishGateway} 投递到消息队列。
 * 投递成功标记为 SENT，失败则增加重试计数并指数退避。
 *
 * <p>退避策略：baseDelay * 2^retryCount，最大不超过 maxBackoffSeconds。
 *
 * @author Marvin Lee
 * @since 1.0.0
 */
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private final OutboxRepository outboxRepository;
    private final EventPublishGateway publishGateway;
    private final ScheduledExecutorService scheduler;

    private final long pollIntervalSeconds;
    private final int batchSize;
    private final long baseBackoffSeconds;
    private final long maxBackoffSeconds;

    private final Counter publishSuccessCounter;
    private final Counter publishFailureCounter;
    private final Counter deadLetterCounter;

    private volatile boolean running = false;

    /**
     * @param outboxRepository    Outbox 仓储
     * @param publishGateway       投递网关
     * @param pollIntervalSeconds  轮询间隔（秒）
     * @param batchSize            每批最大条数
     * @param baseBackoffSeconds   基础退避秒数
     * @param maxBackoffSeconds    最大退避秒数
     * @param meterRegistry        Micrometer 指标注册器（可为 null）
     */
    public OutboxProcessor(OutboxRepository outboxRepository,
                           EventPublishGateway publishGateway,
                           long pollIntervalSeconds,
                           int batchSize,
                           long baseBackoffSeconds,
                           long maxBackoffSeconds,
                           MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.publishGateway = publishGateway;
        this.pollIntervalSeconds = pollIntervalSeconds;
        this.batchSize = batchSize;
        this.baseBackoffSeconds = baseBackoffSeconds;
        this.maxBackoffSeconds = maxBackoffSeconds;
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
        } else {
            this.publishSuccessCounter = null;
            this.publishFailureCounter = null;
            this.deadLetterCounter = null;
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
        scheduler.scheduleWithFixedDelay(this::processBatch,
                pollIntervalSeconds, pollIntervalSeconds, TimeUnit.SECONDS);
        log.info("OutboxProcessor started: pollInterval={}s, batchSize={}", pollIntervalSeconds, batchSize);
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
            List<OutboxMessage> messages = outboxRepository.findPending(batchSize);
            if (messages.isEmpty()) {
                return;
            }
            log.debug("Processing {} pending outbox messages", messages.size());

            for (OutboxMessage message : messages) {
                processSingle(message);
            }
        } catch (Exception e) {
            log.error("Error processing outbox batch", e);
        }
    }

    private void processSingle(OutboxMessage message) {
        try {
            boolean success = publishGateway.publish(message);
            if (success) {
                outboxRepository.markAsSent(message.getId());
                if (publishSuccessCounter != null) {
                    publishSuccessCounter.increment();
                }
                log.debug("Outbox message sent: id={}, type={}", message.getId(), message.getEventType());
            } else {
                handleFailure(message, "Gateway returned false");
            }
        } catch (Exception e) {
            handleFailure(message, e.getMessage());
        }
    }

    private void handleFailure(OutboxMessage message, String errorMessage) {
        long backoff = calculateBackoff(message.getRetryCount());
        outboxRepository.markAsFailed(message.getId(), errorMessage, backoff);

        if (publishFailureCounter != null) {
            publishFailureCounter.increment();
        }

        if (message.getRetryCount() + 1 >= message.getMaxRetries()) {
            if (deadLetterCounter != null) {
                deadLetterCounter.increment();
            }
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
        long backoff = baseBackoffSeconds * (1L << retryCount);
        return Math.min(backoff, maxBackoffSeconds);
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
}
