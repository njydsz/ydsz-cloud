package com.njydsz.pmis.message.server.realtime;

import com.njydsz.pmis.message.domain.constant.MessageConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;
import java.util.Map;

/**
 * P0-4: WebSocket 会话事件监听器�? *
 * <p>监听 STOMP {@link SessionConnectedEvent} / {@link SessionDisconnectEvent}�? * 维护用户在线状态并在上线时补偿离线消息�? * <ul>
 *   <li>连接成功：标记用户上线，拉取并推送离线消�?/li>
 *   <li>断开连接：标记用户下线（仅当最后一�?session 断开时真正离线）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketSessionListener {

    private final OnlineUserService onlineUserService;
    private final OfflineMessageService offlineMessageService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 连接成功事件：标记上�?+ 补偿离线消息�?     *
     * <p>�?STOMP header 中提�?userId（由 {@link WebSocketAuthHandshakeInterceptor}
     * 写入握手属性），标记在线后立即拉取离线消息逐条推送�?     *
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
        String userId = (String) attributes.get(MessageConstants.WS_ATTR_USER_ID);
        String sessionId = accessor.getSessionId();
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(sessionId)) {
            log.warn("[WS-Session] 连接事件缺少 userId/sessionId，跳�?);
            return;
        }
        onlineUserService.markOnline(userId, sessionId);
        // 补偿离线消息
        drainAndPushOfflineMessages(userId);
    }

    /**
     * 断开连接事件：标记下线�?     *
     * @param event 断开事件
     */
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes == null) {
            return;
        }
        String userId = (String) attributes.get(MessageConstants.WS_ATTR_USER_ID);
        String sessionId = event.getSessionId();
        if (!StringUtils.hasText(userId)) {
            return;
        }
        onlineUserService.markOffline(userId, sessionId);
        log.info("[WS-Session] 用户断开: userId={}, sessionId={}", userId, sessionId);
    }

    /**
     * 拉取并推送用户离线消息�?     *
     * <p>逐条推送到用户个人频道，推送失败仅�?warn（消息已�?Redis 删除，不重试）�?     *
     * @param userId 用户 ID
     */
    private void drainAndPushOfflineMessages(String userId) {
        try {
            List<String> offlineMessages = offlineMessageService.drainOffline(userId);
            if (offlineMessages.isEmpty()) {
                return;
            }
            String destination = MessageConstants.WS_USER_DESTINATION_PREFIX + userId + "/notifications";
            for (String json : offlineMessages) {
                try {
                    messagingTemplate.convertAndSend(destination, json);
                } catch (Exception e) {
                    log.warn("[WS-Session] 离线消息补偿推送失�? userId={}, err={}", userId, e.getMessage());
                }
            }
            log.info("[WS-Session] 离线消息补偿完成: userId={}, count={}", userId, offlineMessages.size());
        } catch (Exception e) {
            log.warn("[WS-Session] 离线消息补偿异常: userId={}, err={}", userId, e.getMessage());
        }
    }
}
