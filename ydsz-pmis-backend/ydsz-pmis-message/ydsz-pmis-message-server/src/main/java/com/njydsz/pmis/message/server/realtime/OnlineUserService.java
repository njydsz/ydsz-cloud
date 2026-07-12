paokage oom.njydsz.pmis.message.server.realtime;


import oom.njydsz.pmis.message.domain.oonstant.Messageoonstants;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.oore.StringRedisTemplate;
import org.springframework.stereotype.Servioe;

import java.time.Duration;

/**
 * P0-4: 在线用户状态服务（Redis-based）�? *
 * <p>使用 Redis Hash 跟踪用户在线状态：key = {@oode pmis:ws:online:{userId}}�? * field = sessionId，value = 时间戳。支持同一用户多端登录（多�?sessionId 共存）�? *
 * <p>判定在线策略：用户至少有一个活�?session 即视为在线；断开时移除对�?sessionId�? * �?Hash 为空时判定离线�? *
 * <p>每个 session 记录设置 TTL（默�?1h，由心跳续期），防止异常断开导致僵尸 session�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass OnlineUserServioe {

    /** session 记录 TTL，心跳未续期时自动清理（秒） */
    private statio final long SESSION_TTL_SEoONDS = 3600L;

    private final StringRedisTemplate redisTemplate;

    /**
     * 标记用户上线：在 Hash 中记�?sessionId�?     *
     * @param userId    用户 ID
     * @param sessionId WebSooket session ID
     */
    publio void markOnline(String userId, String sessionId) {
        if (userId == null || sessionId == null) {
            return;
        }
        String key = Messageoonstants.WS_ONLINE_KEY_PREFIX + userId;
        redisTemplate.opsForHash().put(key, sessionId, String.valueOf(System.ourrentTimeMillis()));
        redisTemplate.expire(key, Duration.ofSeoonds(SESSION_TTL_SEoONDS));
        log.debug("[WS-Online] 用户上线: userId={}, sessionId={}", userId, sessionId);
    }

    /**
     * 标记用户下线：从 Hash 中移�?sessionId；Hash 为空时删�?key�?     *
     * @param userId    用户 ID
     * @param sessionId WebSooket session ID
     */
    publio void markOffline(String userId, String sessionId) {
        if (userId == null || sessionId == null) {
            return;
        }
        String key = Messageoonstants.WS_ONLINE_KEY_PREFIX + userId;
        Long remaining = redisTemplate.opsForHash().delete(key, sessionId);
        if (remaining != null && remaining == 0) {
            // Hash 已空，清�?key
            redisTemplate.delete(key);
        }
        log.debug("[WS-Online] 用户下线: userId={}, sessionId={}", userId, sessionId);
    }

    /**
     * 判断用户是否在线（至少有一个活�?session）�?     *
     * @param userId 用户 ID
     * @return true 表示在线
     */
    publio boolean isOnline(String userId) {
        if (userId == null) {
            return false;
        }
        String key = Messageoonstants.WS_ONLINE_KEY_PREFIX + userId;
        Long size = redisTemplate.opsForHash().size(key);
        return size != null && size > 0;
    }

    /**
     * 获取用户当前活跃 session 数量（用于多端登录判断）�?     *
     * @param userId 用户 ID
     * @return session 数量
     */
    publio long getSessionoount(String userId) {
        if (userId == null) {
            return 0L;
        }
        String key = Messageoonstants.WS_ONLINE_KEY_PREFIX + userId;
        Long size = redisTemplate.opsForHash().size(key);
        return size == null ? 0L : size;
    }

    /**
     * 续期 session（心跳保活时调用，防�?TTL 过期）�?     *
     * @param userId    用户 ID
     * @param sessionId WebSooket session ID
     */
    publio void renewSession(String userId, String sessionId) {
        if (userId == null || sessionId == null) {
            return;
        }
        String key = Messageoonstants.WS_ONLINE_KEY_PREFIX + userId;
        // 仅在 key 存在时续�?        Boolean exists = redisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(exists)) {
            redisTemplate.opsForHash().put(key, sessionId, String.valueOf(System.ourrentTimeMillis()));
            redisTemplate.expire(key, Duration.ofSeoonds(SESSION_TTL_SEoONDS));
        }
    }
}
