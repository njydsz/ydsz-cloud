package com.njydsz.pmis.common.notify.core;

import com.njydsz.pmis.common.notify.enums.NotifyChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 持久化重试队列（优先 Redis，自动降级内存）
 *
 * <p>内部优先使用 Redis；当 Redis 不可用时自动降级到内置内存队列，保障服务可用性。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
public class PersistentNotifyRetryQueue implements NotifyRetryQueue {

    private static final Logger log = LoggerFactory.getLogger(PersistentNotifyRetryQueue.class);

    private static final int DEFAULT_MAX_RETRIES = 5;
    private static final int DEFAULT_CAPACITY = 10000;
    private static final int DEFAULT_BATCH_SIZE = 100;

    private final NotifyRetryQueue primary;
    private final NotifyRetryQueue fallback;
    private volatile boolean redisAvailable;

    /**
     * 创建持久化重试队列
     *
     * @param redisTemplate     Redis 模板（为 null 时直接使用内存队列）
     * @param maxRetries        最大重试次数
     * @param capacity          内存队列容量（降级时使用）
     * @param batchSize         批量处理大小
     */
    public PersistentNotifyRetryQueue(StringRedisTemplate redisTemplate,
                                      int maxRetries, int capacity, int batchSize) {
        this(redisTemplate, maxRetries, capacity, batchSize, null);
    }

    /**
     * 创建持久化重试队列
     *
     * @param redisTemplate     Redis 模板（为 null 时直接使用内存队列）
     * @param maxRetries        最大重试次数
     * @param capacity          内存队列容量（降级时使用）
     * @param batchSize         批量处理大小
     * @param redisKeyPrefix    Redis Key 前缀
     */
    public PersistentNotifyRetryQueue(StringRedisTemplate redisTemplate,
                                      int maxRetries, int capacity, int batchSize,
                                      String redisKeyPrefix) {
        int mr = maxRetries > 0 ? maxRetries : DEFAULT_MAX_RETRIES;
        int cap = capacity > 0 ? capacity : DEFAULT_CAPACITY;
        int bs = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;

        this.fallback = new InMemoryFallbackRetryQueue(mr, cap, bs);

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
     */
    private NotifyRetryQueue delegate() {
        if (redisAvailable) {
            try {
                // 每次操作前轻量探测 Redis 可用性
                primary.getQueueSize();
                return primary;
            } catch (Exception e) {
                log.warn("[PersistentNotifyRetryQueue] Redis 运行时异常，降级到内存队列, error={}", e.getMessage());
                redisAvailable = false;
            }
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

        InMemoryFallbackRetryQueue(int maxRetries, int capacity, int batchSize) {
            this.maxRetries = maxRetries;
            this.capacity = capacity;
            this.batchSize = batchSize;
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
                log.error("[NotifyRetryQueue] 重试超过最大次数，标记永久失败, channel={}, receiver={}, totalRetries={}, lastError={}",
                        entry.channel.getName(), entry.receiver, entry.retryCount, entry.lastError);
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
