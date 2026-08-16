package com.njydsz.common.socket.heartbeat;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;

import com.njydsz.common.socket.config.WebSocketProperties;
import com.njydsz.common.socket.constant.WebSocketConstants;
import com.njydsz.common.socket.session.OnlineUserService;

import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 心跳保活处理器（P0-3）。
 *
 * <p>维护 {@code sessionId → lastHeartbeatTime} 映射。当 Redis 可用时，
 * 使用 Redis Sorted Set（{@code ydsz:ws:heartbeat:sessions}）在集群范围内维护心跳状态，
 * 避免单节点宕机导致心跳记录丢失；Redis 不可用时降级为本地 {@link ConcurrentHashMap}。
 *
 * <p>通过 {@link Scheduled} 定时扫描超时 Session，
 * 超过 {@link WebSocketProperties.Heartbeat#getStaleSessionTimeout()} 未活跃的 Session
 * 标记为僵尸连接并触发下线清理。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class WebSocketHeartbeatHandler {

    private final WebSocketProperties properties;
    private final OnlineUserService onlineUserService;
    private final StringRedisTemplate redisTemplate;
    private final Map<String, Long> localSessionHeartbeats = new ConcurrentHashMap<>();
    private final Map<String, String> sessionUserMap = new ConcurrentHashMap<>();

    /** 是否使用 Redis 维护心跳（true=Redis，false=本地 fallback） */
    private final boolean useRedis;

    public WebSocketHeartbeatHandler(
            WebSocketProperties properties,
            OnlineUserService onlineUserService,
            StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.onlineUserService = onlineUserService;
        this.redisTemplate = redisTemplate;
        this.useRedis = redisTemplate != null;
        log.info("[WS-Heartbeat] 初始化心跳处理器: backend={}", useRedis ? "Redis Sorted Set" : "Local ConcurrentHashMap");
    }

    /**
     * 注册新连接的 Session。
     *
     * @param sessionId STOMP Session ID
     * @param userId    用户 ID
     */
    public void registerSession(String sessionId, String userId) {
        if (sessionId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (useRedis) {
            String key = getHeartbeatKey();
            redisTemplate.opsForZSet().add(key, sessionId, now);
            if (userId != null) {
                sessionUserMap.put(sessionId, userId);
            }
        } else {
            localSessionHeartbeats.put(sessionId, now);
            if (userId != null) {
                sessionUserMap.put(sessionId, userId);
            }
        }
    }

    /**
     * 更新 Session 心跳时间戳（客户端心跳到达时调用）。
     *
     * @param sessionId STOMP Session ID
     */
    public void updateHeartbeat(String sessionId) {
        if (sessionId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (useRedis) {
            String key = getHeartbeatKey();
            redisTemplate.opsForZSet().add(key, sessionId, now);
        } else {
            localSessionHeartbeats.put(sessionId, now);
        }
    }

    /**
     * 移除已断开的 Session。
     *
     * @param sessionId STOMP Session ID
     */
    public void unregisterSession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        if (useRedis) {
            String key = getHeartbeatKey();
            redisTemplate.opsForZSet().remove(key, sessionId);
        } else {
            localSessionHeartbeats.remove(sessionId);
        }
        sessionUserMap.remove(sessionId);
    }

    /**
     * 定时扫描僵尸 Session（每 30 秒执行一次）。
     *
     * <p>超过 {@code staleSessionTimeout} 未收到心跳的 Session，
     * 调用 {@link OnlineUserService#markOffline(String, String)} 清理。
     */
    @Scheduled(fixedDelay = 30000)
    public void cleanStaleSessions() {
        long now = System.currentTimeMillis();
        long staleTimeout = properties.getHeartbeat().getStaleSessionTimeout();
        long cutoffTime = now - staleTimeout;
        int cleaned = 0;

        if (useRedis) {
            cleaned = cleanStaleSessionsFromRedis(cutoffTime);
        } else {
            cleaned = cleanStaleSessionsFromLocal(cutoffTime);
        }

        if (cleaned > 0) {
            log.info("[WS-Heartbeat] 僵尸 Session 清理完成, 清理数={}", cleaned);
        }
    }

    /**
     * 从 Redis Sorted Set 中清理僵尸 Session。
     *
     * @param cutoffTime 截止时间戳（毫秒）
     * @return 清理数量
     */
    private int cleanStaleSessionsFromRedis(long cutoffTime) {
        String key = getHeartbeatKey();
        int cleaned = 0;
        try {
            Set<ZSetOperations.TypedTuple<String>> staleEntries =
                    redisTemplate.opsForZSet().rangeByScoreWithScores(key, 0, cutoffTime);
            if (staleEntries == null || staleEntries.isEmpty()) {
                return 0;
            }
            for (ZSetOperations.TypedTuple<String> entry : staleEntries) {
                String sessionId = entry.getValue();
                if (sessionId == null) {
                    continue;
                }
                log.warn("[WS-Heartbeat] 检测到僵尸 Session, 清理: sessionId={}, score={}", sessionId, entry.getScore());
                String userId = sessionUserMap.remove(sessionId);
                redisTemplate.opsForZSet().remove(key, sessionId);
                if (userId != null && onlineUserService != null) {
                    onlineUserService.markOffline(userId, sessionId);
                }
                cleaned++;
            }
        } catch (Exception e) {
            log.warn("[WS-Heartbeat] Redis 清理僵尸 Session 异常: err={}", e.getMessage());
        }
        return cleaned;
    }

    /**
     * 从本地 ConcurrentHashMap 中清理僵尸 Session。
     *
     * @param cutoffTime 截止时间戳（毫秒）
     * @return 清理数量
     */
    private int cleanStaleSessionsFromLocal(long cutoffTime) {
        int cleaned = 0;
        var iterator = localSessionHeartbeats.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue() <= cutoffTime) {
                String sessionId = entry.getKey();
                log.warn("[WS-Heartbeat] 检测到僵尸 Session, 清理: sessionId={}, idleMs={}",
                        sessionId, cutoffTime - entry.getValue() + properties.getHeartbeat().getStaleSessionTimeout());
                String userId = sessionUserMap.remove(sessionId);
                iterator.remove();
                if (userId != null && onlineUserService != null) {
                    onlineUserService.markOffline(userId, sessionId);
                }
                cleaned++;
            }
        }
        return cleaned;
    }

    /**
     * 获取当前活跃 Session 数量。
     *
     * @return 活跃 Session 数
     */
    public int getActiveSessionCount() {
        if (useRedis) {
            try {
                String key = getHeartbeatKey();
                Long size = redisTemplate.opsForZSet().size(key);
                return size != null ? size.intValue() : 0;
            } catch (Exception e) {
                log.warn("[WS-Heartbeat] Redis 获取 Session 数量异常: err={}", e.getMessage());
                return 0;
            }
        }
        return localSessionHeartbeats.size();
    }

    private String getHeartbeatKey() {
        return WebSocketConstants.WS_HEARTBEAT_KEY;
    }

    /**
     * 是否使用 Redis 作为心跳后端。
     *
     * @return true 表示使用 Redis Sorted Set
     */
    public boolean isUsingRedis() {
        return useRedis;
    }
}
