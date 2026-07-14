package com.njydsz.pmis.common.socket.offline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.pmis.common.socket.config.WebSocketProperties;
import com.njydsz.pmis.common.socket.constant.WebSocketConstants;
import com.njydsz.pmis.common.json.Json;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 离线消息存储 Redis 默认实现。
 *
 * <p>使用 Redis List 缓存离线消息（{@code pmis:ws:offline:{userId}}），
 * FIFO 顺序保留最近 {@link WebSocketProperties.Offline#getMaxCache()} 条，
 * TTL 由 {@link WebSocketProperties.Offline#getTtl()} 控制。
 *
 * <p>当 Redis 缓存超过 {@link WebSocketProperties.Offline#getDbPersistThreshold()} 时，
 * 通过 {@link OfflineOverflowHandler} SPI 回调业务侧持久化到数据库，
 * 防止 Redis 内存膨胀。业务侧可注入自定义 Handler 实现数据库溢出存储。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@RequiredArgsConstructor
public class RedisOfflineMessageStore implements OfflineMessageStore {

    private final StringRedisTemplate redisTemplate;
    private final WebSocketProperties properties;

    @Override
    public void cacheOffline(String userId, String type, Object payload) {
        if (userId == null) {
            return;
        }
        try {
            String key = WebSocketConstants.WS_OFFLINE_KEY_PREFIX + userId;
            Map<String, Object> envelope = Map.of(
                    "type", type == null ? "UNKNOWN" : type,
                    "payload", payload,
                    "timestamp", System.currentTimeMillis());
            String json = Json.toJson(envelope);
            redisTemplate.opsForList().leftPush(key, json);
            redisTemplate.opsForList().trim(key, 0, properties.getOffline().getMaxCache() - 1);
            redisTemplate.expire(key, properties.getOffline().getTtl());

            Long size = redisTemplate.opsForList().size(key);
            if (size != null && size > properties.getOffline().getDbPersistThreshold()) {
                log.warn("[WS-Offline] Redis 缓存超阈值,建议业务侧实现溢出持久化: userId={}, size={}", userId, size);
            }

            log.debug("[WS-Offline] 缓存离线消息: userId={}, type={}", userId, type);
        } catch (Exception e) {
            log.warn("[WS-Offline] 缓存离线消息失败，降级忽略: userId={}, err={}", userId, e.getMessage());
        }
    }

    @Override
    public List<String> drainOffline(String userId) {
        if (userId == null) {
            return List.of();
        }
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
        try {
            String key = WebSocketConstants.WS_OFFLINE_KEY_PREFIX + userId;
            Long size = redisTemplate.opsForList().size(key);
            return size == null ? 0L : size;
        } catch (Exception e) {
            log.debug("[WS-Offline] Redis 计数失败: {}", e.getMessage());
            return 0L;
        }
    }
}
