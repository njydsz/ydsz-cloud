package com.njydsz.pmis.common.redis.ops;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis List 操作组件
 *
 * <p>封装常用的 List 操作，支持队列/栈模式。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Component
public class ListOps {

    private final StringRedisTemplate redis;

    public ListOps(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 左推入队列（LPUSH）
     */
    public long leftPush(String key, String... values) {
        return redis.opsForList().leftPushAll(key, values);
    }

    /**
     * 右推入队列（RPUSH）
     */
    public long rightPush(String key, String... values) {
        return redis.opsForList().rightPushAll(key, values);
    }

    /**
     * 左弹出（LPOP）
     */
    public String leftPop(String key) {
        return redis.opsForList().leftPop(key);
    }

    /**
     * 左弹出（阻塞，BLPOP）
     */
    public String leftPop(String key, Duration timeout) {
        return redis.opsForList().leftPop(key, timeout);
    }

    /**
     * 右弹出（RPOP）
     */
    public String rightPop(String key) {
        return redis.opsForList().rightPop(key);
    }

    /**
     * 右弹出（阻塞，BRPOP）
     */
    public String rightPop(String key, Duration timeout) {
        return redis.opsForList().rightPop(key, timeout);
    }

    /**
     * 获取列表范围
     */
    public List<String> range(String key, long start, long end) {
        return redis.opsForList().range(key, start, end);
    }

    /**
     * 列表长度
     */
    public long size(String key) {
        Long size = redis.opsForList().size(key);
        return size != null ? size : 0;
    }

    /**
     * 修剪列表（保留 start~end 范围）
     */
    public void trim(String key, long start, long end) {
        redis.opsForList().trim(key, start, end);
    }

    /**
     * 通过索引获取元素
     */
    public String index(String key, long index) {
        return redis.opsForList().index(key, index);
    }

    /**
     * 通过索引设置元素
     */
    public void set(String key, long index, String value) {
        redis.opsForList().set(key, index, value);
    }

    /**
     * 移除等于 value 的元素
     */
    public long remove(String key, long count, String value) {
        Long removed = redis.opsForList().remove(key, count, value);
        return removed != null ? removed : 0;
    }
}
