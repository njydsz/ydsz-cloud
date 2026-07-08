package com.njydsz.pmis.message.realtime;

import com.njydsz.pmis.common.util.JsonUtils;
import com.njydsz.pmis.message.constant.MessageConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * P0-4: 离线消息补偿服务（Redis-based）。
 *
 * <p>用户离线时，将待推送消息缓存到 Redis List（{@code pmis:ws:offline:{userId}}），
 * FIFO 顺序保留最近 {@link MessageConstants#WS_OFFLINE_MAX_CACHE} 条；
 * 用户上线时一次性拉取并清空缓存，逐条推送。
 *
 * <p>缓存 TTL 默认 7 天（{@link MessageConstants#WS_OFFLINE_TTL_SECONDS}），
 * 超时未上线的消息自动过期清理。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfflineMessageService {

    private final StringRedisTemplate redisTemplate;

    /**
     * 缓存一条离线消息。
     *
     * <p>使用 LPUSH 入队（新消息在头部），LTRIM 保留最近 maxCache 条，
     * 防止离线过久导致内存膨胀。每次写入刷新 TTL。
     *
     * @param userId  用户 ID
     * @param type    消息类型标签（如 NOTIFICATION / ALERT）
     * @param payload 消息内容（任意可序列化对象）
     */
    public void cacheOffline(String userId, String type, Object payload) {
        if (userId == null) {
            return;
        }
        try {
            String key = MessageConstants.WS_OFFLINE_KEY_PREFIX + userId;
            Map<String, Object> envelope = Map.of(
                    "type", type == null ? "UNKNOWN" : type,
                    "payload", payload,
                    "timestamp", System.currentTimeMillis());
            String json = JsonUtils.toJson(envelope);
            redisTemplate.opsForList().leftPush(key, json);
            // 保留最近 maxCache 条（FIFO 淘汰）
            redisTemplate.opsForList().trim(key, 0, MessageConstants.WS_OFFLINE_MAX_CACHE - 1);
            redisTemplate.expire(key, Duration.ofSeconds(MessageConstants.WS_OFFLINE_TTL_SECONDS));
            log.debug("[WS-Offline] 缓存离线消息: userId={}, type={}", userId, type);
        } catch (Exception e) {
            log.warn("[WS-Offline] 缓存离线消息失败，降级忽略: userId={}, err={}", userId, e.getMessage());
        }
    }

    /**
     * 拉取并清空用户的所有离线消息（FIFO 顺序：最旧的消息在列表尾部，先推送）。
     *
     * <p>使用 LRANGE 取出全部消息后 DEL key，保证一次性消费。
     * 返回顺序为时间正序（最旧在前）。
     *
     * @param userId 用户 ID
     * @return 离线消息 JSON 列表（最旧在前），无则返回空列表
     */
    public List<String> drainOffline(String userId) {
        if (userId == null) {
            return List.of();
        }
        String key = MessageConstants.WS_OFFLINE_KEY_PREFIX + userId;
        List<String> raw = redisTemplate.opsForList().range(key, 0, -1);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        redisTemplate.delete(key);
        // LPUSH 入队导致顺序反转，反转为时间正序（最旧在前）
        List<String> result = new ArrayList<>(raw);
        java.util.Collections.reverse(result);
        log.info("[WS-Offline] 拉取离线消息: userId={}, count={}", userId, result.size());
        return result;
    }

    /**
     * 查询用户离线消息数量（不消费）。
     *
     * @param userId 用户 ID
     * @return 离线消息数量
     */
    public long countOffline(String userId) {
        if (userId == null) {
            return 0L;
        }
        String key = MessageConstants.WS_OFFLINE_KEY_PREFIX + userId;
        Long size = redisTemplate.opsForList().size(key);
        return size == null ? 0L : size;
    }
}
