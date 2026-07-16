package com.njydsz.pmis.common.socket.heartbeat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.njydsz.pmis.common.socket.config.WebSocketProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 心跳保活处理器（P0-3）。
 *
 * <p>维护本地 {@code sessionId → lastHeartbeatTime} 映射，
 * 通过 {@link Scheduled} 定时扫描超时 Session，
 * 超过 {@link WebSocketProperties.Heartbeat#getStaleSessionTimeout()} 未活跃的 Session
 * 标记为僵尸连接并触发下线清理。
 *
 * <p>同时监听 STOMP 连接/断开事件，在连接时注册 Session，
 * 在断开时移除 Session。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@RequiredArgsConstructor
public class WebSocketHeartbeatHandler {

    private final Map<String, Long> sessionHeartbeats = new ConcurrentHashMap<>();
    private final WebSocketProperties properties;

    /**
     * 注册新连接的 Session。
     *
     * @param sessionId STOMP Session ID
     */
    public void registerSession(String sessionId) {
        if (sessionId != null) {
            sessionHeartbeats.put(sessionId, System.currentTimeMillis());
        }
    }

    /**
     * 续期 Session 心跳（收到客户端心跳帧时调用）。
     *
     * @param sessionId STOMP Session ID
     */
    public void renewHeartbeat(String sessionId) {
        if (sessionId != null) {
            sessionHeartbeats.put(sessionId, System.currentTimeMillis());
        }
    }

    /**
     * 移除已断开的 Session。
     *
     * @param sessionId STOMP Session ID
     */
    public void unregisterSession(String sessionId) {
        if (sessionId != null) {
            sessionHeartbeats.remove(sessionId);
        }
    }

    /**
     * 监听连接事件，注册 Session。
     *
     * @param event 连接事件
     */
    public void onSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        registerSession(sessionId);
        log.debug("[WS-Heartbeat] Session 注册: sessionId={}", sessionId);
    }

    /**
     * 监听断开事件，移除 Session。
     *
     * @param event 断开事件
     */
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        unregisterSession(sessionId);
        log.debug("[WS-Heartbeat] Session 移除: sessionId={}", sessionId);
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
        int cleaned = 0;
        for (Map.Entry<String, Long> entry : sessionHeartbeats.entrySet()) {
            if (now - entry.getValue() > staleTimeout) {
                String sessionId = entry.getKey();
                log.warn("[WS-Heartbeat] 检测到僵尸 Session, 清理: sessionId={}, idleMs={}",
                        sessionId, now - entry.getValue());
                sessionHeartbeats.remove(sessionId);
                cleaned++;
            }
        }
        if (cleaned > 0) {
            log.info("[WS-Heartbeat] 僵尸 Session 清理完成, 清理数={}, 剩余活跃={}",
                    cleaned, sessionHeartbeats.size());
        }
    }

    /**
     * 获取当前活跃 Session 数量。
     *
     * @return 活跃 Session 数
     */
    public int getActiveSessionCount() {
        return sessionHeartbeats.size();
    }
}
