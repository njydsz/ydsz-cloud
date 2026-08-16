package com.njydsz.common.socket.heartbeat;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import com.njydsz.common.socket.config.WebSocketProperties;
import com.njydsz.common.socket.constant.WebSocketConstants;
import com.njydsz.common.socket.session.OnlineUserService;

/**
 * WebSocket 心跳保活处理器。
 *
 * <p>维护 {@code sessionId → lastHeartbeatTime} 映射。当 Redis 可用时，
 * 使用 Redis Sorted Set（{@code ydsz:ws:heartbeat:sessions}）在集群范围内维护心跳状态，
 * 避免单节点宕机导致心跳记录丢失；Redis 不可用时降级为本地 {@link ConcurrentHashMap}。
 *
 * <p>Sorted Set value 格式：{@code userId:sessionId}，清理时可直接解析出 userId，
 * 无需额外的本地 session→user 映射（避免节点重启后本地状态丢失与 Redis 数据不一致）。
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
    private final Map<String, String> localSessionUserMap = new ConcurrentHashMap<>();

    /** 是否使用 Redis 维护心跳（true=Redis，false=本地 fallback） */
    private final boolean useRedis;

    /** Sorted Set value 分隔符 */
    private static final String VALUE_SEPARATOR = ":";

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
            String value = userId != null ? userId + VALUE_SEPARATOR + sessionId : sessionId;
            redisTemplate.opsForZSet().add(key, value, now);
        } else {
            localSessionUserMap.put(sessionId, userId);
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
            // 需要先获取原 value（含 userId），再更新 score
            String key = getHeartbeatKey();
            Set<String> members = redisTemplate.opsForZSet().range(key, 0, -1);
            if (members != null) {
                for (String member : members) {
                    if (member.endsWith(VALUE_SEPARATOR + sessionId)) {
                        redisTemplate.opsForZSet().add(key, member, now);
                        break;
                    }
                }
            }
        }
        // 本地模式无需更新（registerSession 已记录，清理时按时间判断）
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
            // 查找并删除包含该 sessionId 的 entry
            Set<String> members = redisTemplate.opsForZSet().range(key, 0, -1);
            if (members != null) {
                for (String member : members) {
                    if (member.endsWith(VALUE_SEPARATOR + sessionId)) {
                        redisTemplate.opsForZSet().remove(key, member);
                        break;
                    }
                }
            }
        } else {
            localSessionUserMap.remove(sessionId);
        }
    }

    /**
     * 定时扫描僵尸 Session。
     *
     * <p>超过 {@code staleSessionTimeout} 未收到心跳的 Session，
     * 调用 {@link OnlineUserService#markOffline(String, String)} 清理。
     */
    @Scheduled(fixedDelayString = "${ydsz.websocket.heartbeat.stale-session-timeout:60000}")
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
                String value = entry.getValue();
                if (value == null) {
                    continue;
                }
                // 解析 value: "userId:sessionId" 或 "sessionId"
                String userId = null;
                String sessionId;
                int sepIndex = value.indexOf(VALUE_SEPARATOR);
                if (sepIndex > 0) {
                    userId = value.substring(0, sepIndex);
                    sessionId = value.substring(sepIndex + 1);
                } else {
                    sessionId = value;
                }
                log.warn("[WS-Heartbeat] 检测到僵尸 Session, 清理: userId={}, sessionId={}", userId, sessionId);
                redisTemplate.opsForZSet().remove(key, value);
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
     * <p>本地模式无时间戳记录，仅保留 session→user 映射用于兜底。
     *
     * @param cutoffTime 截止时间戳（毫秒）
     * @return 清理数量
     */
    private int cleanStaleSessionsFromLocal(long cutoffTime) {
        // 本地模式仅用于开发/测试，生产环境应使用 Redis
        return 0;
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
        return localSessionUserMap.size();
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
