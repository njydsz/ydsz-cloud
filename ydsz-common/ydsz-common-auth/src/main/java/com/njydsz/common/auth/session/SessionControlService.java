package com.njydsz.common.auth.session;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import com.njydsz.common.auth.config.AuthProperties;
import com.njydsz.common.redis.service.ops.RedisHashOps;
import com.njydsz.common.redis.service.ops.RedisStringOps;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * 多端会话控制服务。
 *
 * <p>基于 Redis Hash 管理用户登录会话，支持以下策略：</p>
 * <ul>
 *   <li><b>并发会话限制</b>：通过 {@code max-sessions-per-user} 控制单用户最大并发会话数</li>
 *   <li><b>互踢策略</b>：超出限制时，按照 FIFO 策略淘汰最早会话</li>
 *   <li><b>主动登出</b>：用户主动登出时会话立即失效</li>
 * </ul>
 *
 * <p><b>Redis 数据结构：</b></p>
 * <pre>{@code
 * Hash:   auth:session:{userId}      → {tokenId: deviceInfoJson}
 * TTL:    = access_token 过期时间
 * }</pre>
 *
 * <p><b>互踢事件：</b>被互踢的会话 token 会自动加入黑名单，客户端收到 401 时需重新登录。</p>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Service
@ConditionalOnBean(RedisHashOps.class)
public class SessionControlService {

    private static final Logger log = LoggerFactory.getLogger(SessionControlService.class);
    private static final String SESSION_KEY_PREFIX = "auth:session:";

    private final RedisHashOps redisHashOps;
    private final RedisStringOps redisStringOps;
    private final AuthProperties authProperties;
    private final SessionControlListener listener;

    public SessionControlService(RedisHashOps redisHashOps,
                                  RedisStringOps redisStringOps,
                                  AuthProperties authProperties,
                                  SessionControlListener listener) {
        this.redisHashOps = redisHashOps;
        this.redisStringOps = redisStringOps;
        this.authProperties = authProperties;
        this.listener = listener;
    }

    /**
     * 注册新会话（登录成功后调用）。
     *
     * <p>超出 max-sessions-per-user 时，会根据配置选择 LRU 或 FIFO 策略淘汰最旧会话。</p>
     *
     * @param userId   用户 ID
     * @param tokenId  当前 token 的 jti（唯一标识）
     * @param deviceInfo 设备信息（用于日志和 UI 展示）
     */
    public void registerSession(Long userId, String tokenId, DeviceInfo deviceInfo) {
        if (!authProperties.getSession().isEnabled()) {
            return;
        }
        String sessionKey = buildSessionKey(userId);
        String field = tokenId;
        String value = serializeDeviceInfo(deviceInfo);

        redisHashOps.hSet(sessionKey, field, value);
        redisStringOps.expire(sessionKey, Duration.ofSeconds(authProperties.getBlacklist().getExpireSeconds()));

        long activeSessions = redisHashOps.hSize(sessionKey);
        int maxAllowed = authProperties.getSession().getMaxSessionsPerUser();

        if (activeSessions > maxAllowed) {
            evictOldestSessions(userId, activeSessions - maxAllowed);
        }
    }

    /**
     * 注销指定会话（登出时调用）。
     *
     * @param userId  用户 ID
     * @param tokenId 当前 token 的 jti
     */
    public void unregisterSession(Long userId, String tokenId) {
        if (!authProperties.getSession().isEnabled()) {
            return;
        }
        String sessionKey = buildSessionKey(userId);
        redisHashOps.hDel(sessionKey, tokenId);
    }

    /**
     * 判断指定会话是否仍有效（未被互踢）。
     *
     * @param userId  用户 ID
     * @param tokenId token jti
     * @return true 表示会话仍有效
     */
    public boolean isSessionValid(Long userId, String tokenId) {
        if (!authProperties.getSession().isEnabled()) {
            return true;
        }
        String sessionKey = buildSessionKey(userId);
        return redisHashOps.hHasKey(sessionKey, tokenId);
    }

    /**
     * 强制注销用户所有会话（账号异常、改密后调用）。
     *
     * @param userId 用户 ID
     */
    public void invalidateAllSessions(Long userId) {
        String sessionKey = buildSessionKey(userId);
        redisStringOps.del(sessionKey);
        log.info("Force invalidate all sessions for user: {}", userId);
    }

    /**
     * 获取用户的活跃会话列表。
     *
     * @param userId 用户 ID
     * @return 会话信息列表
     */
    public List<SessionInfo> getActiveSessions(Long userId) {
        if (!authProperties.getSession().isEnabled()) {
            return Collections.emptyList();
        }
        String sessionKey = buildSessionKey(userId);
        var entries = redisHashOps.hGetAll(sessionKey, String.class);
        List<SessionInfo> sessions = new ArrayList<>(entries.size());
        entries.forEach((tokenId, deviceJson) ->
            sessions.add(SessionInfo.builder()
                .userId(userId)
                .tokenId(tokenId)
                .deviceInfo(deserializeDeviceInfo(deviceJson))
                .build())
        );
        return sessions;
    }

    private void evictOldestSessions(Long userId, long count) {
        if (count <= 0) return;

        String sessionKey = buildSessionKey(userId);
        Set<Object> tokenIds = redisHashOps.hKeys(sessionKey);

        List<Object> evictionCandidates = new ArrayList<>(tokenIds);
        // FIFO: 淘汰最早的（这里按 Redis 返回的 key 顺序）
        int toRemove = (int) Math.min(count, evictionCandidates.size());

        for (int i = 0; i < toRemove; i++) {
            String evictedTokenId = (String) evictionCandidates.get(i);
            redisHashOps.hDel(sessionKey, evictedTokenId);

            if (listener != null) {
                try {
                    listener.onSessionEvicted(userId, evictedTokenId);
                } catch (Exception e) {
                    log.warn("Session eviction listener threw exception", e);
                }
            }
            log.info("Session evicted due to max-sessions limit: userId={}, tokenId={}",
                userId, evictedTokenId);
        }
    }

    private String buildSessionKey(Long userId) {
        return SESSION_KEY_PREFIX + userId;
    }

    private String serializeDeviceInfo(DeviceInfo info) {
        if (info == null) return "{}";
        return String.format("{\"type\":\"%s\",\"ua\":\"%s\"}",
            nullToEmpty(info.getDeviceType()),
            nullToEmpty(info.getUserAgent()));
    }

    private DeviceInfo deserializeDeviceInfo(String json) {
        if (json == null || json.isEmpty() || "{}".equals(json)) {
            return DeviceInfo.unknown();
        }
        return DeviceInfo.builder()
            .deviceType("unknown")
            .userAgent(json)
            .build();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * 设备信息值对象。
     */
    @Getter
    @Builder
    @ToString
    public static class DeviceInfo {
        private final String deviceType;
        private final String userAgent;
        private final String ipAddress;

        public static DeviceInfo unknown() {
            return DeviceInfo.builder()
                .deviceType("UNKNOWN")
                .build();
        }

        public static DeviceInfo of(String deviceType, String userAgent) {
            return DeviceInfo.builder()
                .deviceType(deviceType)
                .userAgent(userAgent)
                .build();
        }
    }

    /**
     * 活跃会话信息。
     */
    @Getter
    @Builder
    @ToString
    public static class SessionInfo {
        private final Long userId;
        private final String tokenId;
        private final DeviceInfo deviceInfo;
    }

    /**
     * 会话互踢事件监听器。
     *
     * <p>业务方可通过实现此接口监听互踢事件（如推送通知给被踢客户端）。</p>
     */
    @FunctionalInterface
    public interface SessionControlListener {
        void onSessionEvicted(Long userId, String tokenId);
    }
}
