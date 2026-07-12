package com.njydsz.pmis.common.redis.ops;

import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Redis Hash 操作组件 —— 封装哈希表操作。
 * <p>
 * 对标 ydsz-common HashOps，提供类型安全的 Hash 操作。
 * </p>
 *
 * @author njydsz
 * @since 1.0.0
 */
@Component
public class HashOps {

    private final StringRedisTemplate redis;

    public HashOps(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 设置哈希字段。
     */
    public void put(String key, String field, String value) {
        redis.opsForHash().put(key, field, value);
    }

    /**
     * 批量设置哈希字段。
     */
    public void putAll(String key, Map<String, String> entries) {
        HashOperations<String, String, String> hashOps = redis.opsForHash();
        hashOps.putAll(key, entries);
    }

    /**
     * 获取哈希字段值。
     */
    public String get(String key, String field) {
        Object value = redis.opsForHash().get(key, field);
        return value != null ? value.toString() : null;
    }

    /**
     * 批量获取哈希字段值。
     */
    public List<String> multiGet(String key, Collection<String> fields) {
        HashOperations<String, String, String> hashOps = redis.opsForHash();
        return hashOps.multiGet(key, fields);
    }

    /**
     * 删除哈希字段。
     */
    public Long delete(String key, String... fields) {
        return redis.opsForHash().delete(key, (Object[]) fields);
    }

    /**
     * 检查哈希字段是否存在。
     */
    public boolean hasKey(String key, String field) {
        return Boolean.TRUE.equals(redis.opsForHash().hasKey(key, field));
    }

    /**
     * 获取整个哈希表。
     */
    public Map<String, String> entries(String key) {
        HashOperations<String, String, String> hashOps = redis.opsForHash();
        return hashOps.entries(key);
    }

    /**
     * 哈希字段递增。
     */
    public Long increment(String key, String field, long delta) {
        return redis.opsForHash().increment(key, field, delta);
    }

    /**
     * 获取哈希表大小。
     */
    public long size(String key) {
        Long size = redis.opsForHash().size(key);
        return size != null ? size : 0;
    }
}
