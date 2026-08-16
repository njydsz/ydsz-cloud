package com.njydsz.common.socket.session;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.njydsz.common.socket.constant.WebSocketConstants;

/**
 * 在线用户状态服务（Redis-based）。
 *
 * <p>使用 Redis Hash 跟踪用户在线状态：key = {@code ydsz:ws:online:{userId}}，
 * field = sessionId，value = 时间戳。支持同一用户多端登录（多个 sessionId 共存）。
 *
 * <p>判定在线策略：用户至少有一个活跃 session 即视为在线；断开时移除对应 sessionId，
 * 当 Hash 为空时判定离线。
 *
 * <p>每个 session 记录设置 TTL（默认 1h，由心跳续期），防止异常断开导致僵尸 session。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class OnlineUserService {

    private final StringRedisTemplate redisTemplate;
    private final long sessionTtlSeconds;

    /**
     * 标记用户上线：在 Hash 中记录 sessionId。
     *
     * @param userId    用户 ID
     * @param sessionId WebSocket session ID
     */
    public void markOnline(String userId, String sessionId) {
        if (redisTemplate == null) return;
        if (userId == null || sessionId == null) {
            return;
        }
        String key = WebSocketConstants.WS_ONLINE_KEY_PREFIX + userId;
        redisTemplate.opsForHash().put(key, sessionId, String.valueOf(System.currentTimeMillis()));
        redisTemplate.expire(key, Duration.ofSeconds(sessionTtlSeconds));
        log.debug("[WS-Online] 用户上线: userId={}, sessionId={}", userId, sessionId);
    }

    /**
     * 标记用户下线：从 Hash 中移除 sessionId；Hash 为空时删除 key。
     *
     * @param userId    用户 ID
     * @param sessionId WebSocket session ID
     */
    public void markOffline(String userId, String sessionId) {
        if (redisTemplate == null) return;
        if (userId == null || sessionId == null) {
            return;
        }
        String key = WebSocketConstants.WS_ONLINE_KEY_PREFIX + userId;
        Long remaining = redisTemplate.opsForHash().delete(key, sessionId);
        if (remaining != null && remaining == 0) {
            redisTemplate.delete(key);
        }
        log.debug("[WS-Online] 用户下线: userId={}, sessionId={}", userId, sessionId);
    }

    /**
     * 判断用户是否在线（至少有一个活跃 session）。
     *
     * @param userId 用户 ID
     * @return true 表示在线
     */
    public boolean isOnline(String userId) {
        if (redisTemplate == null) return false;
        if (userId == null) {
            return false;
        }
        String key = WebSocketConstants.WS_ONLINE_KEY_PREFIX + userId;
        Long size = redisTemplate.opsForHash().size(key);
        return size != null && size > 0;
    }

    /**
     * 获取用户当前活跃 session 数量（用于多端登录判断）。
     *
     * @param userId 用户 ID
     * @return session 数量
     */
    public long getSessionCount(String userId) {
        if (redisTemplate == null) return 0L;
        if (userId == null) {
            return 0L;
        }
        String key = WebSocketConstants.WS_ONLINE_KEY_PREFIX + userId;
        Long size = redisTemplate.opsForHash().size(key);
        return size == null ? 0L : size;
    }

    /**
     * 续期 session（心跳保活时调用，防止 TTL 过期）。
     *
     * @param userId    用户 ID
     * @param sessionId WebSocket session ID
     */
    public void renewSession(String userId, String sessionId) {
        if (redisTemplate == null) return;
        if (userId == null || sessionId == null) {
            return;
        }
        String key = WebSocketConstants.WS_ONLINE_KEY_PREFIX + userId;
        Boolean exists = redisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(exists)) {
            redisTemplate.opsForHash().put(key, sessionId, String.valueOf(System.currentTimeMillis()));
            redisTemplate.expire(key, Duration.ofSeconds(sessionTtlSeconds));
        }
    }
}
