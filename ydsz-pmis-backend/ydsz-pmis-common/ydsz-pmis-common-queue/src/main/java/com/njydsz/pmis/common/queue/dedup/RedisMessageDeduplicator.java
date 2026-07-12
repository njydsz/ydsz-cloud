package com.njydsz.pmis.common.queue.dedup;

import com.njydsz.pmis.common.redis.service.RedisService;

/**
 * 基于 Redis 的消息去重器
 * <p>适用于分布式场景，可跨实例去重。
 * <p>使用 Redis 的 SETNX 命令实现原子性去重判断，配合 TTL 自动过期清理。
 *
 * @author ydsz-pmis-team
 * 
 * 
 * @since 1.0.0
 * @see MessageDeduplicator
 */
public class RedisMessageDeduplicator {

    private static final String KEY_PREFIX = "queue:dedup:";

    private final RedisService redisService;
    private final long ttlSeconds;

    /**
     * 构造函数
     *
     * @param redisService Redis 服务
     * @param ttlMillis    去重窗口时间（毫秒）
     */
    public RedisMessageDeduplicator(RedisService redisService, long ttlMillis) {
        if (redisService == null) {
            throw new IllegalArgumentException("redisService 不能为 null");
        }
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("去重窗口必须大于 0");
        }
        this.redisService = redisService;
        this.ttlSeconds = Math.max(1, ttlMillis / 1000);
    }

    /**
     * 检查消息是否为重复消息
     *
     * @param messageId 消息 ID
     * @return true 表示重复，false 表示未处理过
     */
    public boolean isDuplicate(String messageId) {
        if (messageId == null || messageId.isEmpty()) {
            return false;
        }
        String key = KEY_PREFIX + messageId;
        Boolean success = redisService.setIfAbsent(key, "1", ttlSeconds);
        return !Boolean.TRUE.equals(success);
    }
}
