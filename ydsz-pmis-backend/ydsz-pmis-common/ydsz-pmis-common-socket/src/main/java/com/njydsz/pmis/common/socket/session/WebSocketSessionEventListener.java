package com.njydsz.pmis.common.socket.session;

import java.util.List;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.njydsz.pmis.common.socket.constant.WebSocketConstants;
import com.njydsz.pmis.common.socket.offline.OfflineMessageStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 会话事件监听器。
 *
 * <p>监听 STOMP {@link SessionConnectedEvent} / {@link SessionDisconnectEvent}，
 * 维护用户在线状态并在上线时补偿离线消息：
 * <ul>
 *   <li>连接成功：标记用户上线，拉取并推送离线消息</li>
 *   <li>断开连接：标记用户下线（仅当最后一个 session 断开时真正离线）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@RequiredArgsConstructor
public class WebSocketSessionEventListener {

    private final OnlineUserService onlineUserService;
    private final OfflineMessageStore offlineMessageStore;
    private final SimpMessagingTemplate messagingTemplate;

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
        log.info("[WS-Session] 用户断开: userId={}, sessionId={}", userId, sessionId);
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
