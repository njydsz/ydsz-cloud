package com.remisoft.common.notify.core;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.remisoft.common.notify.enums.NotifyChannel;

/**
 * 持久化重试队列（优先 Redis，自动降级内存）。
 *
 * <p>内部优先使用 Redis List 存储待重试消息；当 Redis 不可用时自动降级到
 * 内置 {@link ArrayBlockingQueue} 内存队列，保障服务可用性。
 * 超过最大重试次数的消息自动移入死信队列。
 *
 * <h3>重试流程</h3>
 * <ol>
 *   <li>消息发送失败后调用 {@code enqueue} 加入重试队列</li>
 *   <li>后台定时线程以固定间隔 {@code poll} 取出消息重新发送</li>
 *   <li>发送成功则移除；失败则重试次数 +1 后重新入队</li>
 *   <li>重试次数超过 {@code maxRetries} 后移入死信队列</li>
 * </ol>
 *
 * <h3>Redis 降级探测</h3>
 * <p>Redis 操作异常后，进入降级模式使用内存队列。定时（30s）探测 Redis 恢复，
 * 恢复后将内存队列中的消息刷回 Redis。
 *
 * @author remi-team
 * @since 1.0.0
 * @see NotifyRetryQueue
 * @see InMemoryDeadLetterHandler
 */
public class PersistentNotifyRetryQueue implements NotifyRetryQueue {

    private static final Logger log = LoggerFactory.getLogger(PersistentNotifyRetryQueue.class);

    private static final int DEFAULT_MAX_RETRIES = 5;
    private static final int DEFAULT_CAPACITY = 10000;
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final long PROBE_INTERVAL_MS = 30_000L;

    private final NotifyRetryQueue primary;
    private final NotifyRetryQueue fallback;
    private final DeadLetterHandler deadLetterHandler;
    private volatile boolean redisAvailable;
    private volatile long lastProbeTime = 0;

    /**
     * 创建持久化重试队列（带死信处理器）
     *
     * @param redisTemplate     Redis 模板（为 null 时直接使用内存队列）
     * @param maxRetries        最大重试次数
     * @param capacity          内存队列容量（降级时使用）
     * @param batchSize         批量处理大小
     * @param redisKeyPrefix    Redis Key 前缀
     * @param deadLetterHandler 死信处理器（P0-2，可为 null）
     */
    public PersistentNotifyRetryQueue(StringRedisTemplate redisTemplate,
                                      int maxRetries, int capacity, int batchSize,
                                      String redisKeyPrefix,
                                      DeadLetterHandler deadLetterHandler) {
        int mr = maxRetries > 0 ? maxRetries : DEFAULT_MAX_RETRIES;
        int cap = capacity > 0 ? capacity : DEFAULT_CAPACITY;
        int bs = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
        this.deadLetterHandler = deadLetterHandler != null ? deadLetterHandler : new InMemoryDeadLetterHandler();

        this.fallback = new InMemoryFallbackRetryQueue(mr, cap, bs, this.deadLetterHandler);

        if (redisTemplate != null) {
            this.primary = new RedisNotifyRetryQueue(redisTemplate, mr, bs, redisKeyPrefix);
            this.redisAvailable = testRedisConnection(redisTemplate);
        } else {
            this.primary = fallback;
            this.redisAvailable = false;
            log.info("[PersistentNotifyRetryQueue] Redis 不可用，使用内存队列（降级）");
        }
    }

    /**
     * 探测 Redis 连接是否可用
     */
    private boolean testRedisConnection(StringRedisTemplate redisTemplate) {
        try {
            var connectionFactory = redisTemplate.getConnectionFactory();
            if (connectionFactory == null) {
                log.warn("[PersistentNotifyRetryQueue] Redis ConnectionFactory 为 null，使用内存队列（降级）");
                return false;
            }
            connectionFactory.getConnection().ping();
            log.info("[PersistentNotifyRetryQueue] Redis 连接正常，使用持久化队列");
            return true;
        } catch (Exception e) {
            log.warn("[PersistentNotifyRetryQueue] Redis 连接测试失败，使用内存队列（降级）, error={}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取当前实际使用的队列实例
     *
     * <p>P2-7: 优化为周期性探测（每 30 秒），避免每次操作都发起 Redis 往返。
     */
    private NotifyRetryQueue delegate() {
        if (redisAvailable) {
            long now = System.currentTimeMillis();
            if (now - lastProbeTime > PROBE_INTERVAL_MS) {
                lastProbeTime = now;
                try {
                    primary.getQueueSize();
                } catch (Exception e) {
                    log.warn("[PersistentNotifyRetryQueue] Redis 探测失败，降级到内存队列, error={}", e.getMessage());
                    redisAvailable = false;
                    return fallback;
                }
            }
            return primary;
        }
        return fallback;
    }

    @Override
    public void offer(NotifyChannel channel, String receiver, String title,
                      String content, String lastError) {
        delegate().offer(channel, receiver, title, content, lastError);
    }

    @Override
    public void retry(NotifyService notifyService) {
        delegate().retry(notifyService);
    }

    @Override
    public int retryBatch(NotifyService notifyService, int maxBatchSize) {
        return delegate().retryBatch(notifyService, maxBatchSize);
    }

    @Override
    public int retryBatch(NotifyService notifyService) {
        return delegate().retryBatch(notifyService);
    }

    @Override
    public int getQueueSize() {
        return delegate().getQueueSize();
    }

    @Override
    public int getQueuedCount() {
        return delegate().getQueuedCount();
    }

    @Override
    public int getPermanentFailCount() {
        return delegate().getPermanentFailCount();
    }

    @Override
    public int getDroppedCount() {
        return delegate().getDroppedCount();
    }

    @Override
    public int getCapacity() {
        return delegate().getCapacity();
    }

    @Override
    public int getBatchSize() {
        return delegate().getBatchSize();
    }

    /**
     * 当前是否使用 Redis 持久化队列
     */
    public boolean isRedisAvailable() {
        return redisAvailable;
    }

    /**
     * 获取死信处理器（P0-2）
     */
    public DeadLetterHandler getDeadLetterHandler() {
        return deadLetterHandler;
    }

    /**
     * 内置内存降级队列实现（不对外暴露，仅用于 PersistentNotifyRetryQueue 内部降级）
     */
    private static class InMemoryFallbackRetryQueue implements NotifyRetryQueue {

        private static final long BASE_BACKOFF_MS = 1000;

        private final ArrayBlockingQueue<RetryEntry> queue;
        private final int maxRetries;
        private final int capacity;
        private final AtomicInteger queuedCount = new AtomicInteger(0);
        private final AtomicInteger permanentFailCount = new AtomicInteger(0);
        private final AtomicInteger droppedCount = new AtomicInteger(0);
        private final int batchSize;
        private final DeadLetterHandler deadLetterHandler;

        InMemoryFallbackRetryQueue(int maxRetries, int capacity, int batchSize,
                                    DeadLetterHandler deadLetterHandler) {
            this.maxRetries = maxRetries;
            this.capacity = capacity;
            this.batchSize = batchSize;
            this.deadLetterHandler = deadLetterHandler;
            this.queue = new ArrayBlockingQueue<>(capacity);
        }

        @Override
        public void offer(NotifyChannel channel, String receiver, String title,
                          String content, String lastError) {
            RetryEntry entry = new RetryEntry(channel, receiver, title, content);
            entry.lastError = lastError;
            entry.retryCount = 1;
            entry.nextRetryTime = System.currentTimeMillis();
            if (!queue.offer(entry)) {
                droppedCount.incrementAndGet();
                log.warn("[NotifyRetryQueue] 队列已满，丢弃消息, channel={}, receiver={}",
                        channel.getName(), receiver);
                return;
            }
            queuedCount.incrementAndGet();
            log.warn("[NotifyRetryQueue] 加入重试队列, channel={}, receiver={}, retryCount={}",
                    channel.getName(), receiver, entry.retryCount);
        }

        @Override
        public void retry(NotifyService notifyService) {
            RetryEntry entry = queue.poll();
            if (entry == null) {
                return;
            }

            if (entry.nextRetryTime > System.currentTimeMillis()) {
                if (!queue.offer(entry)) {
                    droppedCount.incrementAndGet();
                    log.warn("[NotifyRetryQueue] 队列已满，丢弃延迟重试消息, channel={}, receiver={}",
                            entry.channel.getName(), entry.receiver);
                }
                return;
            }

            try {
                NotifySendResult result = notifyService.send(entry.channel, entry.receiver,
                        entry.title, entry.content);
                if (result.isSuccess()) {
                    log.info("[NotifyRetryQueue] 重试成功, channel={}, receiver={}",
                            entry.channel.getName(), entry.receiver);
                    return;
                }
                handleRetryFailure(entry, result.getErrorMessage());
            } catch (Exception e) {
                handleRetryFailure(entry, e.getMessage());
            }
        }

        @Override
        public int retryBatch(NotifyService notifyService, int maxBatchSize) {
            int limit = maxBatchSize > 0 ? maxBatchSize : batchSize;
            int processed = 0;
            long now = System.currentTimeMillis();

            while (processed < limit) {
                RetryEntry entry = queue.poll();
                if (entry == null) {
                    break;
                }

                if (entry.nextRetryTime > now) {
                    if (!queue.offer(entry)) {
                        droppedCount.incrementAndGet();
                        log.warn("[NotifyRetryQueue] 队列已满，丢弃延迟重试消息, channel={}, receiver={}",
                                entry.channel.getName(), entry.receiver);
                    }
                    continue;
                }

                try {
                    NotifySendResult result = notifyService.send(entry.channel, entry.receiver,
                            entry.title, entry.content);
                    if (result.isSuccess()) {
                        log.info("[NotifyRetryQueue] 批量重试成功, channel={}, receiver={}",
                                entry.channel.getName(), entry.receiver);
                    } else {
                        handleRetryFailure(entry, result.getErrorMessage());
                    }
                } catch (Exception e) {
                    handleRetryFailure(entry, e.getMessage());
                }

                processed++;
            }

            if (processed > 0) {
                log.debug("[NotifyRetryQueue] 批量重试完成, 处理消息数={}", processed);
            }
            return processed;
        }

        @Override
        public int retryBatch(NotifyService notifyService) {
            return retryBatch(notifyService, batchSize);
        }

        private void handleRetryFailure(RetryEntry entry, String error) {
            entry.retryCount++;
            entry.lastError = error;

            if (entry.retryCount > maxRetries) {
                permanentFailCount.incrementAndGet();
                log.error("[NotifyRetryQueue] 重试超过最大次数，移入死信队列, channel={}, receiver={}, totalRetries={}, lastError={}",
                        entry.channel.getName(), entry.receiver, entry.retryCount, entry.lastError);
                // P0-2：移入死信队列
                if (deadLetterHandler != null) {
                    deadLetterHandler.moveToDeadLetter(entry.channel, entry.receiver, entry.title,
                            entry.content, entry.retryCount, entry.lastError);
                }
                return;
            }

            long backoffMs = BASE_BACKOFF_MS * (1L << (entry.retryCount - 1));
            entry.nextRetryTime = System.currentTimeMillis() + backoffMs;

            if (!queue.offer(entry)) {
                droppedCount.incrementAndGet();
                log.warn("[NotifyRetryQueue] 队列已满，丢弃重试消息, channel={}, receiver={}",
                        entry.channel.getName(), entry.receiver);
                return;
            }
            log.warn("[NotifyRetryQueue] 重试失败，重新入队 ({}/{}), channel={}, receiver={}, nextRetryIn={}ms",
                    entry.retryCount, maxRetries, entry.channel.getName(), entry.receiver, backoffMs);
        }

        @Override
        public int getQueueSize() {
            return queue.size();
        }

        @Override
        public int getQueuedCount() {
            return queuedCount.get();
        }

        @Override
        public int getPermanentFailCount() {
            return permanentFailCount.get();
        }

        @Override
        public int getDroppedCount() {
            return droppedCount.get();
        }

        @Override
        public int getCapacity() {
            return capacity;
        }

        @Override
        public int getBatchSize() {
            return batchSize;
        }

        /**
         * 内存重试队列中的单条待重试消息记录。
         *
         * <p>持有通知要素（渠道/接收人/标题/内容）与重试进度（当前重试次数、
         * 最近一次错误、下次重试时间）；下次重试时间由指数退避算法在失败时计算。
         *
         * @author remi-team
         * @since 1.0.0
         */
        private static class RetryEntry {
            private final NotifyChannel channel;
            private final String receiver;
            private final String title;
            private final String content;
            private int retryCount;
            private String lastError;
            private long nextRetryTime;

            RetryEntry(NotifyChannel channel, String receiver, String title, String content) {
                this.channel = channel;
                this.receiver = receiver;
                this.title = title;
                this.content = content;
            }
        }
    }
}
