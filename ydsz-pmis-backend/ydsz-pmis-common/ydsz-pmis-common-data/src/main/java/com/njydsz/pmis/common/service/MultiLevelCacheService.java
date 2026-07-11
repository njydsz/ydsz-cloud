package com.njydsz.pmis.common.service;

import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 多级缓存服务 (L1 Caffeine + L2 Redis)
 *
 * <p>提供透明的多级缓存读写能力，业务层无需关心缓存层级。
 *
 * <h3>读取流程</h3>
 * <ol>
 *   <li>L1 (Caffeine) 命中 → 直接返回</li>
 *   <li>L1 未命中 → L2 (Redis) 命中 → 回填 L1 → 返回</li>
 *   <li>L2 未命中 → DB (loader) → 回填 L1 + L2 → 返回</li>
 * </ol>
 *
 * <h3>写入流程</h3>
 * <ol>
 *   <li>写入 L1 (Caffeine)</li>
 *   <li>写入 L2 (Redis)</li>
 * </ol>
 *
 * <h3>失效流程</h3>
 * <ol>
 *   <li>移除 L1 (Caffeine)</li>
 *   <li>移除 L2 (Redis)</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Service
public class MultiLevelCacheService {

    private final Cache<String, Object> localCache;
    private final RedisTemplate<String, Object> redisTemplate;

    public MultiLevelCacheService(
            Cache<String, Object> localCache,
            RedisTemplate<String, Object> redisTemplate
    ) {
        this.localCache = localCache;
        this.redisTemplate = redisTemplate;
    }

    /** Redis key 前缀 */
    private static final String REDIS_PREFIX = "pmis:mc:";

    /**
     * 读取缓存（L1 → L2 → DB loader）
     *
     * @param key    缓存 key
     * @param clazz  返回值类型
     * @param loader DB 加载函数（缓存未命中时调用）
     * @param ttl    Redis TTL
     * @return 缓存值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz, Supplier<T> loader, Duration ttl) {
        // L1: Caffeine
        Object l1Value = localCache.getIfPresent(key);
        if (l1Value != null) {
            return (T) l1Value;
        }

        // L2: Redis
        String redisKey = REDIS_PREFIX + key;
        Object l2Value = redisTemplate.opsForValue().get(redisKey);
        if (l2Value != null) {
            // 回填 L1
            localCache.put(key, l2Value);
            return (T) l2Value;
        }

        // DB
        T dbValue = loader.get();
        if (dbValue != null) {
            // 回填 L1 + L2
            localCache.put(key, dbValue);
            redisTemplate.opsForValue().set(redisKey, dbValue, ttl);
        }

        return dbValue;
    }

    /**
     * 读取缓存（使用默认 30 分钟 TTL）
     */
    public <T> T get(String key, Class<T> clazz, Supplier<T> loader) {
        return get(key, clazz, loader, Duration.ofMinutes(30));
    }

    /**
     * 写入缓存（L1 + L2）
     *
     * @param key   缓存 key
     * @param value 缓存值
     * @param ttl   Redis TTL
     */
    public void put(String key, Object value, Duration ttl) {
        if (value == null) return;
        localCache.put(key, value);
        redisTemplate.opsForValue().set(REDIS_PREFIX + key, value, ttl);
    }

    /**
     * 写入缓存（默认 30 分钟 TTL）
     */
    public void put(String key, Object value) {
        put(key, value, Duration.ofMinutes(30));
    }

    /**
     * 失效缓存（L1 + L2）
     *
     * @param key 缓存 key
     */
    public void evict(String key) {
        localCache.invalidate(key);
        redisTemplate.delete(REDIS_PREFIX + key);
    }

    /**
     * 批量失效缓存（L1 + L2）
     *
     * @param keys 缓存 key 列表
     */
    public void evictAll(String... keys) {
        for (String key : keys) {
            localCache.invalidate(key);
            redisTemplate.delete(REDIS_PREFIX + key);
        }
    }

    /**
     * 按 pattern 批量失效（仅 L2 Redis 支持 pattern, L1 需全量清理）
     *
     * @param pattern Redis key pattern (如 "dict:*")
     */
    public void evictByPattern(String pattern) {
        // L1: 全量清理（Caffeine 不支持 pattern 删除）
        localCache.invalidateAll();
        // L2: Redis pattern 删除
        java.util.Set<String> keys = redisTemplate.keys(REDIS_PREFIX + pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * 清空所有缓存（L1 + L2）
     * <p><b>危险操作</b>：仅用于调试或缓存重置
     */
    public void clearAll() {
        localCache.invalidateAll();
        java.util.Set<String> keys = redisTemplate.keys(REDIS_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
