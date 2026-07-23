package com.njydsz.common.notify.core;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.njydsz.common.notify.enums.NotifyChannel;
import com.njydsz.common.json.YdszJson;

/**
 * 基于 Redis 的持久化重试队列实现
 *
 * <p>支持多实例部署时重试数据共享，服务重启后数据不丢失。
 * 使用 ZSET 按重试时间排序，Hash 存储消息详情。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class RedisNotifyRetryQueue implements NotifyRetryQueue {

    private static final Logger log = LoggerFactory.getLogger(RedisNotifyRetryQueue.class);

    private static final int DEFAULT_MAX_RETRIES = 5;
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final long BASE_BACKOFF_MS = 1000;

    /** ZSET 键：按 nextRetryTime 排序 */
    private final String zsetKey;
    /** Hash 键前缀：存储消息详情 */
    private final String hashKeyPrefix;
    /** 计数器键前缀 */
    private final String counterQueued;
    private final String counterPermanent;
    private final String counterDropped;

    /** 消息 TTL（30 天） */
    private static final long MSG_TTL_SECONDS = 30 * 24 * 3600;

    /** Lua 脚本：原子地将消息加入重试队列（ZADD + HSET + INCR） */
    private static final String LUA_OFFER_SCRIPT =
            "redis.call('SET', KEYS[1], ARGV[1])\n" +
            "redis.call('EXPIRE', KEYS[1], ARGV[3])\n" +
            "redis.call('ZADD', KEYS[2], ARGV[2], ARGV[4])\n" +
            "redis.call('INCR', KEYS[3])\n" +
            "return 1";

    /** Lua 脚本：原子地重新入队（ZADD + SET） */
    private static final String LUA_REQUEUE_SCRIPT =
            "redis.call('SET', KEYS[1], ARGV[1])\n" +
            "redis.call('EXPIRE', KEYS[1], ARGV[3])\n" +
            "redis.call('ZADD', KEYS[2], ARGV[2], ARGV[4])\n" +
            "return 1";

    private final StringRedisTemplate stringRedisTemplate;
    private final int maxRetries;
    private final int batchSize;

    /** 本地计数器缓存（避免每次都查 Redis） */
    private final AtomicInteger queuedCount = new AtomicInteger(0);
    private final AtomicInteger permanentFailCount = new AtomicInteger(0);
    private final AtomicInteger droppedCount = new AtomicInteger(0);

    public RedisNotifyRetryQueue(StringRedisTemplate stringRedisTemplate) {
        this(stringRedisTemplate, DEFAULT_MAX_RETRIES, DEFAULT_BATCH_SIZE, null);
    }

    public RedisNotifyRetryQueue(StringRedisTemplate stringRedisTemplate,
                                 int maxRetries, int batchSize) {
        this(stringRedisTemplate, maxRetries, batchSize, null);
    }

    public RedisNotifyRetryQueue(StringRedisTemplate stringRedisTemplate,
                                 int maxRetries, int batchSize, String redisKeyPrefix) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.maxRetries = maxRetries > 0 ? maxRetries : DEFAULT_MAX_RETRIES;
        this.batchSize = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
        String prefix = (redisKeyPrefix != null && !redisKeyPrefix.isEmpty()) ? redisKeyPrefix : "notify:retry:";
        this.zsetKey = prefix + "zset";
        this.hashKeyPrefix = prefix + "msg:";
        this.counterQueued = prefix + "count:queued";
        this.counterPermanent = prefix + "count:permanent";
        this.counterDropped = prefix + "count:dropped";
    }

    @Override
    public void offer(NotifyChannel channel, String receiver, String title,
                      String content, String lastError) {
        try {
            RetryMessage msg = new RetryMessage(
                    channel, receiver, title, content, 1, lastError,
                    System.currentTimeMillis());
            String msgJson = YdszJson.toJson(msg);
            String msgKey = hashKeyPrefix + msg.id;

            // 使用 Lua 脚本保证 ZADD + SET + INCR 原子性
            Long result = stringRedisTemplate.execute(
                    new DefaultRedisScript<>(LUA_OFFER_SCRIPT, Long.class),
                    Arrays.asList(msgKey, zsetKey, counterQueued),
                    msgJson, String.valueOf(msg.nextRetryTime), String.valueOf(MSG_TTL_SECONDS), msg.id
            );

            if (result != null && result == 1) {
                queuedCount.incrementAndGet();
            }

            log.warn("[NotifyRetryQueue] 加入 Redis 重试队列, id={}, channel={}, receiver={}, retryCount={}",
                    msg.id, channel.getName(), receiver, msg.retryCount);
        } catch (Exception e) {
            droppedCount.incrementAndGet();
            log.error("[NotifyRetryQueue] Redis offer 失败, channel={}, receiver={}, error={}",
                    channel.getName(), receiver, e.getMessage());
        }
    }

    @Override
    public void retry(NotifyService notifyService) {
        retryBatch(notifyService, 1);
    }

    @Override
    public int retryBatch(NotifyService notifyService, int maxBatchSize) {
        int limit = maxBatchSize > 0 ? maxBatchSize : batchSize;
        int processed = 0;
        long now = System.currentTimeMillis();

        try {
            // 取出 score <= now 的条目（已到重试时间的）
            Set<String> ids = stringRedisTemplate.opsForZSet()
                    .rangeByScore(zsetKey, 0, now, 0, limit);
            if (ids == null || ids.isEmpty()) {
                return 0;
            }

            for (String id : ids) {
                // 原子性从 ZSET 中移除
                Long removed = stringRedisTemplate.opsForZSet().remove(zsetKey, id);
                if (removed == null || removed <= 0) {
                    continue;
                }

                String msgKey = hashKeyPrefix + id;
                String msgJson = stringRedisTemplate.opsForValue().get(msgKey);
                if (msgJson == null) {
                    continue;
                }

                RetryMessage msg = YdszJson.toObject(msgJson, RetryMessage.class);
                if (msg == null) {
                    stringRedisTemplate.delete(msgKey);
                    continue;
                }

                try {
                    NotifySendResult result = notifyService.send(msg.channel, msg.receiver,
                            msg.title, msg.content);
                    if (result.isSuccess()) {
                        log.info("[NotifyRetryQueue] Redis 重试成功, id={}, channel={}, receiver={}",
                                id, msg.channel.getName(), msg.receiver);
                        stringRedisTemplate.delete(msgKey);
                    } else {
                        handleRetryFailure(id, msg, result.getErrorMessage());
                    }
                } catch (Exception e) {
                    handleRetryFailure(id, msg, e.getMessage());
                }

                processed++;
            }
        } catch (Exception e) {
            log.error("[NotifyRetryQueue] Redis retryBatch 失败, error={}", e.getMessage());
        }

        if (processed > 0) {
            log.debug("[NotifyRetryQueue] Redis 批量重试完成, 处理消息数={}", processed);
        }
        return processed;
    }

    @Override
    public int retryBatch(NotifyService notifyService) {
        return retryBatch(notifyService, batchSize);
    }

    /**
     * 处理重试失败，更新消息状态并重新入队
     */
    private void handleRetryFailure(String id, RetryMessage msg, String error) {
        msg.retryCount++;
        msg.lastError = error;

        if (msg.retryCount > maxRetries) {
            permanentFailCount.incrementAndGet();
            stringRedisTemplate.opsForValue().increment(counterPermanent);
            log.error("[NotifyRetryQueue] 重试超过最大次数，标记永久失败, id={}, channel={}, receiver={}, totalRetries={}",
                    id, msg.channel.getName(), msg.receiver, msg.retryCount);
            stringRedisTemplate.delete(hashKeyPrefix + id);
            return;
        }

        long backoffMs = BASE_BACKOFF_MS * (1L << (msg.retryCount - 1));
        msg.nextRetryTime = System.currentTimeMillis() + backoffMs;

        try {
            String msgJson = YdszJson.toJson(msg);
            // 使用 Lua 脚本保证 SET + ZADD 原子性
            stringRedisTemplate.execute(
                    new DefaultRedisScript<>(LUA_REQUEUE_SCRIPT, Long.class),
                    Arrays.asList(hashKeyPrefix + id, zsetKey),
                    msgJson, String.valueOf(msg.nextRetryTime), String.valueOf(MSG_TTL_SECONDS), id
            );

            log.warn("[NotifyRetryQueue] 重试失败，重新入队 ({}/{}), id={}, channel={}, receiver={}, nextRetryIn={}ms",
                    msg.retryCount, maxRetries, id, msg.channel.getName(), msg.receiver, backoffMs);
        } catch (Exception e) {
            log.error("[NotifyRetryQueue] 重新入队失败, id={}, error={}", id, e.getMessage());
        }
    }

    @Override
    public int getQueueSize() {
        try {
            Long size = stringRedisTemplate.opsForZSet().zCard(zsetKey);
            return size != null ? size.intValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public int getQueuedCount() {
        try {
            String val = stringRedisTemplate.opsForValue().get(counterQueued);
            if (val != null) {
                return Integer.parseInt(val);
            }
        } catch (Exception e) {
            log.warn("[NotifyRetryQueue] 读取 Redis 入队计数失败，降级返回本地计数 | error={}", e.getMessage());
        }
        return queuedCount.get();
    }

    @Override
    public int getPermanentFailCount() {
        try {
            String val = stringRedisTemplate.opsForValue().get(counterPermanent);
            if (val != null) {
                return Integer.parseInt(val);
            }
        } catch (Exception e) {
            log.warn("[NotifyRetryQueue] 读取 Redis 永久失败计数失败，降级返回本地计数 | error={}", e.getMessage());
        }
        return permanentFailCount.get();
    }

    @Override
    public int getDroppedCount() {
        try {
            String val = stringRedisTemplate.opsForValue().get(counterDropped);
            if (val != null) {
                return Integer.parseInt(val);
            }
        } catch (Exception e) {
            log.warn("[NotifyRetryQueue] 读取 Redis 丢弃计数失败，降级返回本地计数 | error={}", e.getMessage());
        }
        return droppedCount.get();
    }

    @Override
    public int getCapacity() {
        return Integer.MAX_VALUE; // Redis 队列容量理论上无限
    }

    @Override
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * 重试消息数据（序列化到 Redis）
     */
    private static class RetryMessage {
        /** 唯一消息 ID */
        String id;
        /** 通知渠道 */
        NotifyChannel channel;
        /** 接收者 */
        String receiver;
        /** 消息标题 */
        String title;
        /** 消息内容 */
        String content;
        /** 已重试次数 */
        int retryCount;
        /** 最后错误信息 */
        String lastError;
        /** 下次重试时间（毫秒时间戳） */
        long nextRetryTime;

        RetryMessage() {
        }

        RetryMessage(NotifyChannel channel, String receiver, String title, String content,
                     int retryCount, String lastError, long nextRetryTime) {
            this.id = UUID.randomUUID().toString().replace("-", "");
            this.channel = channel;
            this.receiver = receiver;
            this.title = title;
            this.content = content;
            this.retryCount = retryCount;
            this.lastError = lastError;
            this.nextRetryTime = nextRetryTime;
        }
    }
}
