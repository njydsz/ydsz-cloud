package com.njydsz.pmis.common.redis.ops;

import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis Set 操作组件 —— 封装集合操作。
 * <p>
 * 对标 ydsz-common SetOps，提供类型安全的 Set 操作。
 * </p>
 *
 * @author njydsz
 * @since 1.0.0
 */
@Component
public class SetOps {

    private final StringRedisTemplate redis;

    public SetOps(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 添加成员。
     */
    public Long add(String key, String... members) {
        return redis.opsForSet().add(key, members);
    }

    /**
     * 移除成员。
     */
    public Long remove(String key, String... members) {
        return redis.opsForSet().remove(key, (Object[]) members);
    }

    /**
     * 判断是否是成员。
     */
    public boolean isMember(String key, String member) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(key, member));
    }

    /**
     * 获取所有成员。
     */
    public Set<String> members(String key) {
        return redis.opsForSet().members(key);
    }

    /**
     * 获取集合大小。
     */
    public long size(String key) {
        Long size = redis.opsForSet().size(key);
        return size != null ? size : 0;
    }

    /**
     * 随机弹出成员。
     */
    public String pop(String key) {
        return redis.opsForSet().pop(key);
    }

    /**
     * 求交集。
     */
    public Set<String> intersect(String key1, String key2) {
        return redis.opsForSet().intersect(key1, key2);
    }

    /**
     * 求并集。
     */
    public Set<String> union(String key1, String key2) {
        return redis.opsForSet().union(key1, key2);
    }

    /**
     * 求差集。
     */
    public Set<String> difference(String key1, String key2) {
        return redis.opsForSet().difference(key1, key2);
    }
}
