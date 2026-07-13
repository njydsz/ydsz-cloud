package com.njydsz.pmis.common.redis.ops;

import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

/**
 * Redis ZSet (Sorted Set) 操作组件 —— 封装有序集合操作。
 * <p>
 * 对标 ydsz-common ZSetOps，提供排行榜、延迟队列等场景支持。
 * </p>
 *
 * @author njydsz
 * @since 1.0.0
 */
@Component
public class ZSetOps {

    private final StringRedisTemplate redis;

    public ZSetOps(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 添加成员（带分数）。
     */
    public Boolean add(String key, String member, double score) {
        return redis.opsForZSet().add(key, member, score);
    }

    /**
     * 递增成员分数。
     */
    public Double incrementScore(String key, String member, double delta) {
        return redis.opsForZSet().incrementScore(key, member, delta);
    }

    /**
     * 获取成员分数。
     */
    public Double score(String key, String member) {
        return redis.opsForZSet().score(key, member);
    }

    /**
     * 获取排名（从 0 开始，升序）。
     */
    public Long rank(String key, String member) {
        return redis.opsForZSet().rank(key, member);
    }

    /**
     * 获取排名（从 0 开始，降序）。
     */
    public Long reverseRank(String key, String member) {
        return redis.opsForZSet().reverseRank(key, member);
    }

    /**
     * 按排名范围获取成员（升序）。
     */
    public Set<String> range(String key, long start, long end) {
        return redis.opsForZSet().range(key, start, end);
    }

    /**
     * 按排名范围获取成员（降序）。
     */
    public Set<String> reverseRange(String key, long start, long end) {
        return redis.opsForZSet().reverseRange(key, start, end);
    }

    /**
     * 按分数范围获取成员。
     */
    public Set<ZSetOperations.TypedTuple<String>> rangeByScoreWithScores(String key, double min, double max) {
        return redis.opsForZSet().rangeByScoreWithScores(key, min, max);
    }

    /**
     * 移除成员。
     */
    public Long remove(String key, String... members) {
        return redis.opsForZSet().remove(key, (Object[]) members);
    }

    /**
     * 获取集合大小。
     */
    public long size(String key) {
        Long size = redis.opsForZSet().size(key);
        return size != null ? size : 0;
    }
}
