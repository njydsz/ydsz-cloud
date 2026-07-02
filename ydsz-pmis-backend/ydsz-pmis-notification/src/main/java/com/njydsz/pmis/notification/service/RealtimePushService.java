package com.njydsz.pmis.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 实时推送服务。
 * <p>
 * 通过 SimpMessagingTemplate 向指定用户推送消息，支持通知、告警、驾驶舱数据刷新。
 * 推送失败不影响主业务流程（try-catch 降级）。
 * </p>
 *
 * @author pmis
 */
@Service
public class RealtimePushService {

    private static final Logger log = LoggerFactory.getLogger(RealtimePushService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimePushService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 向指定用户推送通知。
     *
     * @param userId      用户ID
     * @param type        消息类型 (NOTIFICATION/ALERT/DASHBOARD)
     * @param payload     消息内容
     */
    public void pushToUser(Long userId, String type, Object payload) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", type);
            message.put("data", payload);
            message.put("timestamp", System.currentTimeMillis());
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    "/queue/notifications",
                    message
            );
            log.debug("[WebSocket] 推送消息到用户 {}: type={}", userId, type);
        } catch (Exception e) {
            log.warn("[WebSocket] 推送消息失败，降级忽略: userId={}, type={}, error={}", userId, type, e.getMessage());
        }
    }

    /**
     * 向所有在线用户广播消息。
     *
     * @param type    消息类型
     * @param payload 消息内容
     */
    public void broadcast(String type, Object payload) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", type);
            message.put("data", payload);
            message.put("timestamp", System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/broadcast", (Object) message);
            log.debug("[WebSocket] 广播消息: type={}", type);
        } catch (Exception e) {
            log.warn("[WebSocket] 广播消息失败，降级忽略: type={}, error={}", type, e.getMessage());
        }
    }

    /**
     * 向指定主题推送消息（如驾驶舱数据刷新）。
     *
     * @param topic   主题路径
     * @param payload 消息内容
     */
    public void pushToTopic(String topic, Object payload) {
        try {
            messagingTemplate.convertAndSend("/topic/" + topic, payload);
            log.debug("[WebSocket] 推送主题消息: topic={}", topic);
        } catch (Exception e) {
            log.warn("[WebSocket] 推送主题消息失败，降级忽略: topic={}, error={}", topic, e.getMessage());
        }
    }
}
