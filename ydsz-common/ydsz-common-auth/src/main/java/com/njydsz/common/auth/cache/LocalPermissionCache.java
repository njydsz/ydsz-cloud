package com.njydsz.common.auth.cache;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;
import com.njydsz.common.cache.listener.RemovalCause;

/**
 * 本地权限缓存兜底实现。
 *
 * <p>当 Redis 不可用时，提供本地缓存作为降级方案。
 * 使用 ydsz-common-cache 实现，支持 5 分钟过期。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
public class LocalPermissionCache<V> {

    private static final Logger log = LoggerFactory.getLogger(LocalPermissionCache.class);

    private static final long DEFAULT_EXPIRE_MINUTES = 5;

    private final Cache<String, V> cache;

    private volatile boolean redisAvailable = true;

    /**
     * 构建本地权限缓存。
     *
     * @param cacheName 缓存名称
     */
    public LocalPermissionCache(String cacheName) {
        this(cacheName, DEFAULT_EXPIRE_MINUTES);
    }

    /**
     * 构建本地权限缓存。
     *
     * @param cacheName      缓存名称
     * @param expireMinutes  过期时间（分钟）
     */
    public LocalPermissionCache(String cacheName, long expireMinutes) {
        this.cache = YdszCache.<String, V>newBuilder()
                .type(CacheType.STRIPED)
                .expireAfterWrite(expireMinutes, TimeUnit.MINUTES)
                .removalListener((String key, V value, RemovalCause cause) -> {
                    if (log.isDebugEnabled()) {
                        log.debug("本地权限缓存淘汰: cache={}, key={}, cause={}", cacheName, key, cause);
                    }
                })
                .build();

        log.info("本地权限缓存已初始化: cache={}, expire={}分钟", cacheName, expireMinutes);
    }

    /**
     * 获取缓存值。
     *
     * @param key 缓存键
     * @return 缓存值，不存在返回 null
     */
    public V get(String key) {
        return cache.getIfPresent(key);
    }

    /**
     * 存入缓存。
     *
     * @param key   缓存键
     * @param value 缓存值
     */
    public void put(String key, V value) {
        cache.put(key, value);
    }

    /**
     * 移除缓存。
     *
     * @param key 缓存键
     */
    public void remove(String key) {
        cache.invalidate(key);
    }

    /**
     * 清空所有缓存。
     */
    public void clear() {
        cache.invalidateAll();
    }

    /**
     * 获取缓存大小。
     *
     * @return 缓存条目数
     */
    public long size() {
        return cache.estimatedSize();
    }

    /**
     * 标记 Redis 是否可用。
     *
     * @param available Redis 是否可用
     */
    public void setRedisAvailable(boolean available) {
        if (this.redisAvailable != available) {
            this.redisAvailable = available;
            if (!available) {
                log.warn("Redis 不可用，已降级到本地缓存兜底");
            } else {
                log.info("Redis 已恢复，继续使用 Redis 缓存");
            }
        }
    }

    /**
     * 判断 Redis 是否可用。
     *
     * @return Redis 是否可用
     */
    public boolean isRedisAvailable() {
        return redisAvailable;
    }
}
