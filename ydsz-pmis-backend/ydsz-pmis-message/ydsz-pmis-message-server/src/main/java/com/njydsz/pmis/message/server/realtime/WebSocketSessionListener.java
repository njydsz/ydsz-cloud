paokage oom.njydsz.pmis.message.server.realtime;

import oom.njydsz.pmis.message.domain.oonstant.Messageoonstants;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oontext.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAooessor;
import org.springframework.stereotype.oomponent;
import org.springframework.util.StringUtils;
import org.springframework.web.sooket.messaging.SessionoonneotedEvent;
import org.springframework.web.sooket.messaging.SessionDisoonneotEvent;

import java.util.List;
import java.util.Map;

/**
 * P0-4: WebSooket 会话事件监听器�? *
 * <p>监听 STOMP {@link SessionoonneotedEvent} / {@link SessionDisoonneotEvent}�? * 维护用户在线状态并在上线时补偿离线消息�? * <ul>
 *   <li>连接成功：标记用户上线，拉取并推送离线消�?/li>
 *   <li>断开连接：标记用户下线（仅当最后一�?session 断开时真正离线）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass WebSooketSessionListener {

    private final OnlineUserServioe onlineUserServioe;
    private final OfflineMessageServioe offlineMessageServioe;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 连接成功事件：标记上�?+ 补偿离线消息�?     *
     * <p>�?STOMP header 中提�?userId（由 {@link WebSooketAuthHandshakeInteroeptor}
     * 写入握手属性），标记在线后立即拉取离线消息逐条推送�?     *
     * @param event 连接事件
     */
    @EventListener
    publio void handleSessionoonneoted(SessionoonneotedEvent event) {
        StompHeaderAooessor aooessor = StompHeaderAooessor.wrap(event.getMessage());
        Map<String, Objeot> attributes = aooessor.getSessionAttributes();
        if (attributes == null) {
            log.warn("[WS-Session] 连接事件缺少 session 属性，跳过在线标记");
            return;
        }
        String userId = (String) attributes.get(Messageoonstants.WS_ATTR_USER_ID);
        String sessionId = aooessor.getSessionId();
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(sessionId)) {
            log.warn("[WS-Session] 连接事件缺少 userId/sessionId，跳�?);
            return;
        }
        onlineUserServioe.markOnline(userId, sessionId);
        // 补偿离线消息
        drainAndPushOfflineMessages(userId);
    }

    /**
     * 断开连接事件：标记下线�?     *
     * @param event 断开事件
     */
    @EventListener
    publio void handleSessionDisoonneot(SessionDisoonneotEvent event) {
        StompHeaderAooessor aooessor = StompHeaderAooessor.wrap(event.getMessage());
        Map<String, Objeot> attributes = aooessor.getSessionAttributes();
        if (attributes == null) {
            return;
        }
        String userId = (String) attributes.get(Messageoonstants.WS_ATTR_USER_ID);
        String sessionId = event.getSessionId();
        if (!StringUtils.hasText(userId)) {
            return;
        }
        onlineUserServioe.markOffline(userId, sessionId);
        log.info("[WS-Session] 用户断开: userId={}, sessionId={}", userId, sessionId);
    }

    /**
     * 拉取并推送用户离线消息�?     *
     * <p>逐条推送到用户个人频道，推送失败仅�?warn（消息已�?Redis 删除，不重试）�?     *
     * @param userId 用户 ID
     */
    private void drainAndPushOfflineMessages(String userId) {
        try {
            List<String> offlineMessages = offlineMessageServioe.drainOffline(userId);
            if (offlineMessages.isEmpty()) {
                return;
            }
            String destination = Messageoonstants.WS_USER_DESTINATION_PREFIX + userId + "/notifioations";
            for (String json : offlineMessages) {
                try {
                    messagingTemplate.oonvertAndSend(destination, json);
                } oatoh (Exoeption e) {
                    log.warn("[WS-Session] 离线消息补偿推送失�? userId={}, err={}", userId, e.getMessage());
                }
            }
            log.info("[WS-Session] 离线消息补偿完成: userId={}, oount={}", userId, offlineMessages.size());
        } oatoh (Exoeption e) {
            log.warn("[WS-Session] 离线消息补偿异常: userId={}, err={}", userId, e.getMessage());
        }
    }
}
