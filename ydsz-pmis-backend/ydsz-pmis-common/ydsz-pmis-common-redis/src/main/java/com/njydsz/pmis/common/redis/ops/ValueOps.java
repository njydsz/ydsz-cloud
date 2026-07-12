package com.njydsz.pmis.common.redis.ops;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redis String 操作组件 —— 封装最常用的 KV 操作。
 * <p>
 * 对标 remi-comm ValueOps，提供类型安全的 String 操作封装，
 * 统一序列化策略和异常处理。
 * </p>
 *
 * @author njydsz
 * @since 1.0.0
 */
@Component
public class ValueOps {

    private final StringRedisTemplate redis;

    public ValueOps(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 设置键值。
     */
    public void set(String key, String value) {
        redis.opsForValue().set(key, value);
    }

    /**
     * 设置键值并指定过期时间。
     */
    public void set(String key, String value, Duration timeout) {
        redis.opsForValue().set(key, value, timeout);
    }

    /**
     * 设置键值（如果不存在），类似 SETNX。
     */
    public boolean setIfAbsent(String key, String value, Duration timeout) {
        Boolean result = redis.opsForValue().setIfAbsent(key, value, timeout);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 获取值。
     */
    public String get(String key) {
        return redis.opsForValue().get(key);
    }

    /**
     * 获取并设置新值。
     */
    public String getAndSet(String key, String newValue) {
        return redis.opsForValue().getAndSet(key, newValue);
    }

    /**
     * 删除键。
     */
    public boolean delete(String key) {
        return Boolean.TRUE.equals(redis.delete(key));
    }

    /**
     * 递增。
     */
    public Long increment(String key, long delta) {
        return redis.opsForValue().increment(key, delta);
    }

    /**
     * 递增 1。
     */
    public Long increment(String key) {
        return redis.opsForValue().increment(key);
    }

    /**
     * 检查键是否存在。
     */
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redis.hasKey(key));
    }

    /**
     * 设置过期时间。
     */
    public boolean expire(String key, Duration timeout) {
        return Boolean.TRUE.equals(redis.expire(key, timeout.toMillis(), TimeUnit.MILLISECONDS));
    }

    /**
     * 获取剩余过期时间（秒）。
     */
    public long getExpire(String key) {
        Long ttl = redis.getExpire(key);
        return ttl != null ? ttl : -1;
    }
}
