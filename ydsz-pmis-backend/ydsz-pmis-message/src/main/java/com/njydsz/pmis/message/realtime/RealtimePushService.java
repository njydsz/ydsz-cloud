package com.njydsz.pmis.message.realtime;

import com.njydsz.pmis.message.constant.MessageConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * 实时推送服务（基于 STOMP over WebSocket）。
 *
 * <p>通过 {@link SimpMessagingTemplate} 向用户 / 广播 / 主题推送消息。
 * 推送失败不影响主业务流程（try-catch 降级，仅 warn 日志）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimePushService {

    /** STOMP 消息模板 */
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 向指定用户推送通知。
     *
     * <p>路由到 {@code /topic/user/{userId}/notifications}，前端订阅该目的地接收消息。
     * 推送失败时降级吞掉异常，仅输出 warn 日志。
     *
     * @param userId  用户 ID
     * @param type    消息类型（NOTIFICATION/ALERT/DASHBOARD 等）
     * @param payload 消息内容
     */
    public void pushToUser(String userId, String type, Object payload) {
        try {
            String destination = MessageConstants.WS_USER_DESTINATION_PREFIX + userId + "/notifications";
            messagingTemplate.convertAndSend(destination, payload);
            log.debug("[WebSocket] 推送消息到用户 {}: type={}", userId, type);
        } catch (Exception e) {
            log.warn("[WebSocket] 推送消息失败，降级忽略: userId={}, type={}, error={}",
                    userId, type, e.getMessage());
        }
    }

    /**
     * 向所有在线用户广播消息。
     *
     * <p>路由到 {@code /topic/broadcast}。推送失败时降级吞掉异常。
     *
     * @param payload 消息内容
     */
    public void broadcast(Object payload) {
        try {
            messagingTemplate.convertAndSend(MessageConstants.WS_BROADCAST_DESTINATION, payload);
            log.debug("[WebSocket] 广播消息");
        } catch (Exception e) {
            log.warn("[WebSocket] 广播消息失败，降级忽略: error={}", e.getMessage());
        }
    }

    /**
     * 向指定主题推送消息（如驾驶舱数据刷新）。
     *
     * <p>路由到 {@code /topic/{topic}}。推送失败时降级吞掉异常。
     *
     * @param topic   主题路径
     * @param payload 消息内容
     */
    public void pushToTopic(String topic, Object payload) {
        try {
            messagingTemplate.convertAndSend(
                    MessageConstants.WS_TOPIC_DESTINATION_PREFIX + topic, payload);
            log.debug("[WebSocket] 推送主题消息: topic={}", topic);
        } catch (Exception e) {
            log.warn("[WebSocket] 推送主题消息失败，降级忽略: topic={}, error={}", topic, e.getMessage());
        }
    }
}
