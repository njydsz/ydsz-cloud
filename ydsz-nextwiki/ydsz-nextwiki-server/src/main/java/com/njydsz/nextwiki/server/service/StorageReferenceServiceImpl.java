package com.njydsz.nextwiki.server.service;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.njydsz.nextwiki.domain.service.StorageReferenceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于 Redis INCR/DECR 的存储引用计数服务实现。
 * <p>
 * Key 格式：{@code wiki:ref:{storageKey}}，TTL 30 天防孤儿 key 堆积。
 * <ul>
 *   <li>首次 INCR 时设置 TTL（仅 key 不存在时）</li>
 *   <li>DECR 归零时主动删除 key 并返回 0，调用方可安全物理删除</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageReferenceServiceImpl implements StorageReferenceService {

    /** Redis key 前缀 */
    private static final String KEY_PREFIX = "wiki:ref:";

    /** key 生存时间（30 天），防止孤儿 key 永久堆积 */
    private static final long TTL_DAYS = 30L;

    private final StringRedisTemplate redisTemplate;

    @Override
    public long increment(String storageKey) {
        String key = buildKey(storageKey);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, TTL_DAYS, TimeUnit.DAYS);
        }
        log.info("[StorageReference] 引用++ : storageKey={}, count={}", storageKey, count);
        return count != null ? count : 0L;
    }

    @Override
    public long decrement(String storageKey) {
        String key = buildKey(storageKey);
        Long count = redisTemplate.opsForValue().decrement(key);
        if (count != null && count <= 0L) {
            redisTemplate.delete(key);
            log.info("[StorageReference] 引用归零，已清除 key: storageKey={}", storageKey);
            return 0L;
        }
        log.info("[StorageReference] 引用-- : storageKey={}, count={}", storageKey, count);
        return count != null ? count : 0L;
    }

    @Override
    public long getCount(String storageKey) {
        String key = buildKey(storageKey);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("[StorageReference] 计数值异常，返回 0: storageKey={}, value={}", storageKey, value);
            return 0L;
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 构建 Redis key
     */
    private String buildKey(String storageKey) {
        return KEY_PREFIX + storageKey;
    }
}
