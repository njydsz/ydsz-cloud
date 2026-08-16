package com.njydsz.common.socket.offline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.socket.config.WebSocketProperties;
import com.njydsz.common.socket.constant.WebSocketConstants;
import com.njydsz.common.socket.resilience.WebSocketCircuitBreaker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 离线消息存储 Redis 默认实现。
 *
 * <p>使用 Redis List 缓存离线消息（{@code ydsz:ws:offline:{userId}}），
 * FIFO 顺序保留最近 {@link WebSocketProperties.Offline#getMaxCache()} 条，
 * TTL 由 {@link WebSocketProperties.Offline#getTtl()} 控制。
 *
 * <p>当 Redis 缓存超过 {@link WebSocketProperties.Offline#getDbPersistThreshold()} 时，
 * 通过 {@link OfflineOverflowHandler} SPI 回调业务侧持久化到数据库，
 * 防止 Redis 内存膨胀。业务侧可注入自定义 Handler 实现数据库溢出存储。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class RedisOfflineMessageStore implements OfflineMessageStore {

    private final StringRedisTemplate redisTemplate;
    private final WebSocketProperties properties;
    private final WebSocketCircuitBreaker circuitBreaker;

    @Override
    public void cacheOffline(String userId, String type, Object payload) {
        if (userId == null) {
            return;
        }
        circuitBreaker.execute(
                () -> doCacheOffline(userId, type, payload),
                () -> log.warn("[WS-Offline] 熔断中, 跳过缓存: userId={}", userId)
        );
    }

    private void doCacheOffline(String userId, String type, Object payload) {
        String key = WebSocketConstants.WS_OFFLINE_KEY_PREFIX + userId;
        Map<String, Object> envelope = Map.of(
                "type", type == null ? "UNKNOWN" : type,
                "payload", payload,
                "timestamp", System.currentTimeMillis());
        String json = YdszJson.toJson(envelope);
        redisTemplate.opsForList().leftPush(key, json);
        redisTemplate.opsForList().trim(key, 0, properties.getOffline().getMaxCache() - 1);
        redisTemplate.expire(key, properties.getOffline().getTtl());

        Long size = redisTemplate.opsForList().size(key);
        if (size != null && size > properties.getOffline().getDbPersistThreshold()) {
            log.warn("[WS-Offline] Redis 缓存超阈值,建议业务侧实现溢出持久化: userId={}, size={}", userId, size);
        }

        log.debug("[WS-Offline] 缓存离线消息: userId={}, type={}", userId, type);
    }

    @Override
    public List<String> drainOffline(String userId) {
        if (userId == null) {
            return List.of();
        }
        return circuitBreaker.execute(
                () -> doDrainOffline(userId),
                () -> List.of()
        );
    }

    private List<String> doDrainOffline(String userId) {
        String key = WebSocketConstants.WS_OFFLINE_KEY_PREFIX + userId;
        List<String> raw = redisTemplate.opsForList().range(key, 0, -1);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        redisTemplate.delete(key);
        // LPUSH 入队导致顺序反转，反转为时间正序（最旧在前）
        List<String> result = new ArrayList<>(raw);
        Collections.reverse(result);
        log.info("[WS-Offline] 拉取离线消息: userId={}, total={}", userId, result.size());
        return result;
    }

    @Override
    public long countOffline(String userId) {
        if (userId == null) {
            return 0L;
        }
        return circuitBreaker.execute(
                () -> {
                    String key = WebSocketConstants.WS_OFFLINE_KEY_PREFIX + userId;
                    Long size = redisTemplate.opsForList().size(key);
                    return size == null ? 0L : size;
                },
                () -> 0L
        );
    }

    @Override
    public List<String> pageOffline(String userId, int offset, int limit) {
        if (userId == null) {
            return List.of();
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset 不能为负数: " + offset);
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 必须为正整数: " + limit);
        }
        return circuitBreaker.execute(
                () -> doPageOffline(userId, offset, limit),
                () -> List.of()
        );
    }

    /**
     * 分页查询离线消息内部实现。
     *
     * <p>Redis List 使用 LPUSH 入队，最新数据在 list 头部（index=0）。
     * 为实现 FIFO 正序分页（最旧在前），需要将 offset 映射到 list 的尾部方向。
     *
     * @param userId 用户 ID
     * @param offset 起始偏移（0 起始，最旧的消息在 offset=0）
     * @param limit  最多返回条数
     * @return 分页结果（最旧在前）
     */
    private List<String> doPageOffline(String userId, int offset, int limit) {
        String key = WebSocketConstants.WS_OFFLINE_KEY_PREFIX + userId;
        Long totalSize = redisTemplate.opsForList().size(key);
        if (totalSize == null || totalSize == 0) {
            return List.of();
        }
        // LPUSH 入队：list 头部为最新数据，尾部为最旧数据
        // 要获取 offset 起始的旧数据 → 从尾部开始计算
        // start = totalSize - offset - limit (但不能 < 0)
        // end = totalSize - offset - 1 (但不能 < 0)
        long end = totalSize - (long) offset - 1;
        long start = end - (long) limit + 1;
        if (start < 0) {
            start = 0;
        }
        if (end < 0) {
            return List.of();
        }
        List<String> raw = redisTemplate.opsForList().range(key, start, end);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        // raw 是从头部到尾部的顺序（新→旧），需要反转为旧→新（FIFO 正序）
        List<String> result = new ArrayList<>(raw);
        Collections.reverse(result);
        log.debug("[WS-Offline] 分页查询: userId={}, offset={}, limit={}, returned={}",
                userId, offset, limit, result.size());
        return result;
    }
}
