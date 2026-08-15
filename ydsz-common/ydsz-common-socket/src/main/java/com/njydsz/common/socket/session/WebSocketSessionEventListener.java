package com.njydsz.common.socket.session;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.njydsz.common.socket.audit.WebSocketAuditService;
import com.njydsz.common.socket.constant.WebSocketConstants;
import com.njydsz.common.socket.heartbeat.WebSocketHeartbeatHandler;
import com.njydsz.common.socket.lifecycle.WebSocketConnectionListener;
import com.njydsz.common.socket.monitor.SlowConnectionDetector;
import com.njydsz.common.socket.offline.OfflineMessageStore;

import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 会话事件监听器。
 *
 * <p>监听 STOMP {@link SessionConnectedEvent} / {@link SessionDisconnectEvent}，
 * 维护用户在线状态并在上线时补偿离线消息：
 * <ul>
 *   <li>连接成功：标记用户上线，拉取并推送离线消息，通知连接监听器</li>
 *   <li>断开连接：标记用户下线，审计断开事件，通知连接监听器</li>
 * </ul>
 *
 * <p>集成心跳注册/注销（P0-3）和连接生命周期钩子（P3-5）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class WebSocketSessionEventListener {

    private final OnlineUserService onlineUserService;
    private final OfflineMessageStore offlineMessageStore;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketHeartbeatHandler heartbeatHandler;
    private final WebSocketAuditService auditService;
    private final SlowConnectionDetector slowConnectionDetector;
    private final List<WebSocketConnectionListener> connectionListeners;

    /** 本节点活跃连接计数器（供 HealthIndicator 读取） */
    private final AtomicLong activeConnections = new AtomicLong(0);
    /** Session ID → 连接时间戳（用于计算连接时长） */
    private final Map<String, Long> connectTimes = new ConcurrentHashMap<>();

    public WebSocketSessionEventListener(
            OnlineUserService onlineUserService,
            OfflineMessageStore offlineMessageStore,
            SimpMessagingTemplate messagingTemplate,
            WebSocketHeartbeatHandler heartbeatHandler,
            WebSocketAuditService auditService,
            SlowConnectionDetector slowConnectionDetector,
            List<WebSocketConnectionListener> connectionListeners) {
        this.onlineUserService = onlineUserService;
        this.offlineMessageStore = offlineMessageStore;
        this.messagingTemplate = messagingTemplate;
        this.heartbeatHandler = heartbeatHandler;
        this.auditService = auditService;
        this.slowConnectionDetector = slowConnectionDetector;
        this.connectionListeners = connectionListeners != null ? connectionListeners : List.of();
    }

    /**
     * 连接成功事件：标记上线 + 补偿离线消息。
     *
     * @param event 连接事件
     */
    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes == null) {
            log.warn("[WS-Session] 连接事件缺少 session 属性，跳过在线标记");
            return;
        }
        String userId = (String) attributes.get(WebSocketConstants.WS_ATTR_USER_ID);
        String sessionId = accessor.getSessionId();
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(sessionId)) {
            log.warn("[WS-Session] 连接事件缺少 userId/sessionId，跳过");
            return;
        }
        onlineUserService.markOnline(userId, sessionId);
        activeConnections.incrementAndGet();
        connectTimes.put(sessionId, System.currentTimeMillis());
        if (heartbeatHandler != null) {
            heartbeatHandler.registerSession(sessionId, userId);
        }
        log.info("[WS-Session] 用户连接: userId={}, sessionId={}, localActive={}",
                userId, sessionId, activeConnections.get());
        notifyConnected(userId, sessionId);
        drainAndPushOfflineMessages(userId);
    }

    /**
     * 断开连接事件：标记下线。
     *
     * @param event 断开事件
     */
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes == null) {
            return;
        }
        String userId = (String) attributes.get(WebSocketConstants.WS_ATTR_USER_ID);
        String sessionId = event.getSessionId();
        if (!StringUtils.hasText(userId)) {
            return;
        }
        onlineUserService.markOffline(userId, sessionId);
        activeConnections.decrementAndGet();
        Long connectTime = connectTimes.remove(sessionId);
        if (heartbeatHandler != null) {
            heartbeatHandler.unregisterSession(sessionId);
        }
        if (auditService != null) {
            long duration = connectTime != null ? System.currentTimeMillis() - connectTime : 0;
            auditService.auditDisconnect(userId, sessionId, duration);
        }
        log.info("[WS-Session] 用户断开: userId={}, sessionId={}, localActive={}",
                userId, sessionId, activeConnections.get());
        if (slowConnectionDetector != null) {
            slowConnectionDetector.cleanup(sessionId);
        }
        notifyDisconnected(userId, sessionId);
    }

    /**
     * 获取本节点活跃连接数（供 HealthIndicator 使用）。
     *
     * @return 当前活跃连接数
     */
    public long getActiveConnections() {
        return activeConnections.get();
    }

    /**
     * 获取活跃连接计数器引用（供 HealthIndicator 直接引用）。
     *
     * @return AtomicLong 计数器实例
     */
    public AtomicLong getActiveConnectionsCounter() {
        return activeConnections;
    }

    /**
     * 通知所有注册的连接监听器：连接建立。
     */
    private void notifyConnected(String userId, String sessionId) {
        for (WebSocketConnectionListener listener : connectionListeners) {
            try {
                listener.onConnected(userId, sessionId);
            } catch (Exception e) {
                log.warn("[WS-Session] 连接监听器异常: listener={}, err={}",
                        listener.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    /**
     * 通知所有注册的连接监听器：连接断开。
     */
    private void notifyDisconnected(String userId, String sessionId) {
        for (WebSocketConnectionListener listener : connectionListeners) {
            try {
                listener.onDisconnected(userId, sessionId);
            } catch (Exception e) {
                log.warn("[WS-Session] 断开监听器异常: listener={}, err={}",
                        listener.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    /**
     * 拉取并推送用户离线消息。
     *
     * @param userId 用户 ID
     */
    private void drainAndPushOfflineMessages(String userId) {
        try {
            List<String> offlineMessages = offlineMessageStore.drainOffline(userId);
            if (offlineMessages.isEmpty()) {
                return;
            }
            String destination = WebSocketConstants.WS_USER_DESTINATION_PREFIX + userId + "/notifications";
            for (String json : offlineMessages) {
                try {
                    messagingTemplate.convertAndSend(destination, json);
                } catch (Exception e) {
                    log.warn("[WS-Session] 离线消息补偿推送失败: userId={}, err={}", userId, e.getMessage());
                }
            }
            log.info("[WS-Session] 离线消息补偿完成: userId={}, count={}", userId, offlineMessages.size());
        } catch (Exception e) {
            log.warn("[WS-Session] 离线消息补偿异常: userId={}, err={}", userId, e.getMessage());
        }
    }
}
