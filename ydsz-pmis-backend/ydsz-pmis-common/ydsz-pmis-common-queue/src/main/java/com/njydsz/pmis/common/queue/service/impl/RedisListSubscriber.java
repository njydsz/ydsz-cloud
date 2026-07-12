package com.njydsz.pmis.common.queue.service.impl;

import com.njydsz.pmis.common.queue.config.QueueProperties;
import com.njydsz.pmis.common.queue.domain.QueueMessage;
import com.njydsz.pmis.common.queue.rate.ConsumerRateLimiter;
import com.njydsz.pmis.common.queue.recovery.ConsumerThreadGuard;
import com.njydsz.pmis.common.queue.service.IMessageHandler;
import com.njydsz.pmis.common.queue.service.IMessageSubscriber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 Redis List 的消息订阅者
 *
 * <p>使用 Redis BRPOPLPUSH 原子操作从主队列弹出消息并推入备份队列，
 * 处理完成后再从备份队列删除，确保消息处理失败时不会丢失。
 * 通过 {@link RedisTemplate} 复用 ydsz-pmis-common-redis 的连接。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
public class RedisListSubscriber implements IMessageSubscriber {

    private final RedisTemplate<String, Object> redisTemplate;
    private final String channel;
    private final String backupChannel;
    private final int blockTimeoutSeconds;

    /**
     * 备份队列最大长度限制，超过此值时触发清理和告警
     * 默认 10000 条
     */
    private final int maxBackupQueueSize;

    private final AtomicBoolean running;
    private final AtomicLong consumedCount;
    private final AtomicLong trimmedCount;
    private final AtomicReference<Throwable> lastError;
    private final ConsumerRateLimiter rateLimiter;
    private final ExecutorService consumerExecutor;

    private volatile Thread consumerThread;
    private volatile ConsumerThreadGuard threadGuard;

    public RedisListSubscriber(RedisTemplate<String, Object> redisTemplate, String channel,
                               int blockTimeoutSeconds, ExecutorService consumerExecutor) {
        this(redisTemplate, channel, blockTimeoutSeconds, 10000,
                new ConsumerRateLimiter(0), consumerExecutor);
    }

    public RedisListSubscriber(RedisTemplate<String, Object> redisTemplate, String channel,
                               int blockTimeoutSeconds, int maxBackupQueueSize, ExecutorService consumerExecutor) {
        this(redisTemplate, channel, blockTimeoutSeconds, maxBackupQueueSize,
                new ConsumerRateLimiter(0), consumerExecutor);
    }

    public RedisListSubscriber(RedisTemplate<String, Object> redisTemplate, String channel,
                               int blockTimeoutSeconds, int maxBackupQueueSize,
                               QueueProperties queueProperties, ExecutorService consumerExecutor) {
        this(redisTemplate, channel, blockTimeoutSeconds, maxBackupQueueSize,
                queueProperties != null ? queueProperties.createRateLimiter() : new ConsumerRateLimiter(0),
                consumerExecutor);
    }

    private RedisListSubscriber(RedisTemplate<String, Object> redisTemplate, String channel,
                                int blockTimeoutSeconds, int maxBackupQueueSize,
                                ConsumerRateLimiter rateLimiter, ExecutorService consumerExecutor) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("RedisTemplate 不能为空");
        }
        if (channel == null || channel.isEmpty()) {
            throw new IllegalArgumentException("通道名称不能为空");
        }
        if (maxBackupQueueSize <= 0) {
            throw new IllegalArgumentException("备份队列最大长度必须大于 0");
        }
        this.redisTemplate = redisTemplate;
        this.channel = channel;
        this.backupChannel = channel + ":backup";
        this.blockTimeoutSeconds = blockTimeoutSeconds;
        this.maxBackupQueueSize = maxBackupQueueSize;
        this.running = new AtomicBoolean(false);
        this.consumedCount = new AtomicLong(0);
        this.trimmedCount = new AtomicLong(0);
        this.lastError = new AtomicReference<>();
        this.rateLimiter = rateLimiter != null ? rateLimiter : new ConsumerRateLimiter(0);
        this.consumerExecutor = consumerExecutor;
    }

    @Override
    public String subscribe() {
        try {
            if (blockTimeoutSeconds > 0) {
                // 使用 BRPOPLPUSH 原子操作：从 channel 弹出消息并推入 backupChannel
                // 返回消息内容（不含 key 名）
                Object message = redisTemplate.execute(connection -> {
                    byte[] result = connection.listCommands()
                            .bRPopLPush(blockTimeoutSeconds, channel.getBytes(), backupChannel.getBytes());
                    return result;
                }, true);
                if (message != null) {
                    return new String((byte[]) message);
                }
                return null;
            } else {
                Object value = redisTemplate.opsForList().rightPop(channel);
                return value != null ? String.valueOf(value) : null;
            }
        } catch (Exception e) {
            lastError.set(e);
            log.error("[RedisList] 拉取消息异常，channel={}", channel, e);
            return null;
        }
    }

    @Override
    public String subscribeAsync(IMessageHandler handler) {
        if (!running.compareAndSet(false, true)) {
            log.warn("[RedisList] 订阅者已在运行中，channel={}", channel);
            return channel;
        }
        threadGuard = new ConsumerThreadGuard("redis-list-" + channel, 10, consumerExecutor);
        threadGuard.start(() -> consumeLoop(handler));
        log.info("[RedisList] 异步消费已启动（ConsumerThreadGuard守护），channel={}", channel);
        return channel;
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (threadGuard != null) {
            threadGuard.stop();
        }
        log.info("[RedisList] 收到停止信号，channel={}", channel);
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public Object getChannel() {
        return channel;
    }

    @Override
    public String getConsumerId() {
        return channel;
    }

    @Override
    public int getConsumedCount() {
        return (int) consumedCount.get();
    }

    @Override
    public Throwable getLastError() {
        return lastError.get();
    }

    /**
     * 获取备份队列当前长度
     *
     * @return 备份队列长度
     */
    public long getBackupQueueSize() {
        try {
            Long size = redisTemplate.opsForList().size(backupChannel);
            return size != null ? size : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 获取已清理的备份队列消息总数
     *
     * @return 清理数量
     */
    public long getTrimmedCount() {
        return trimmedCount.get();
    }

    private void consumeLoop(IMessageHandler handler) {
        try {
            while (running.get()) {
                try {
                    // 每次消费前检查备份队列长度
                    trimBackupQueueIfExceeded();

                    String message = subscribe();
                    if (message != null) {
                        rateLimiter.acquire();
                        processMessage(message, handler);
                    }
                } catch (Exception e) {
                    lastError.set(e);
                    log.error("[RedisList] 消费循环异常，channel={}", channel, e);
                    sleepQuietly(1000);
                }
            }
        } finally {
            running.set(false);
            log.info("[RedisList] 消费循环已退出，channel={}", channel);
        }
    }

    /**
     * 检查并清理备份队列（超过最大长度时从左侧移除最旧消息）
     */
    private void trimBackupQueueIfExceeded() {
        try {
            Long size = redisTemplate.opsForList().size(backupChannel);
            if (size != null && size > maxBackupQueueSize) {
                long trimCount = size - maxBackupQueueSize;
                redisTemplate.opsForList().trim(backupChannel, trimCount, -1);
                trimmedCount.addAndGet(trimCount);
                log.warn("[RedisList] 备份队列长度超限({}>{})，已清理 {} 条最旧消息，channel={}",
                        size, maxBackupQueueSize, trimCount, channel);
            }
        } catch (Exception e) {
            log.warn("[RedisList] 备份队列清理失败，channel={}", channel, e);
        }
    }

    private void processMessage(String message, IMessageHandler handler) {
        if (message == null) {
            return;
        }
        boolean ackSuccess = false;
        try {
            QueueMessage queueMessage = QueueMessage.fromPayload(message);
            if (queueMessage == null) {
                queueMessage = QueueMessage.of(message);
            }
            if (handler != null) {
                handler.onMessage(queueMessage);
            }
            ackSuccess = true;
            consumedCount.incrementAndGet();
            lastError.set(null);
            log.debug("[RedisList] 消息处理成功，channel={}, traceId={}", channel, queueMessage.getTraceId());
        } catch (Exception e) {
            lastError.set(e);
            log.error("[RedisList] 消息处理异常，channel={}，消息保留在备份队列等待恢复", channel, e);
        } finally {
            // 处理成功后从备份队列删除消息；处理失败则保留在备份队列
            if (ackSuccess) {
                try {
                    redisTemplate.opsForList().remove(backupChannel, 1, message);
                } catch (Exception e) {
                    log.warn("[RedisList] 从备份队列删除消息失败，channel={}", channel, e);
                }
            }
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
