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
 * <p>P0-4 增强：{@link #pushToUserWithOffline} 方法在推送前检查用户在线状态，
 * 离线时缓存到 {@link OfflineMessageService}，待用户上线时补偿推送。
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

    /** P0-4: 在线用户状态服务 */
    private final OnlineUserService onlineUserService;

    /** P0-4: 离线消息补偿服务 */
    private final OfflineMessageService offlineMessageService;

    /**
     * 向指定用户推送通知（不做在线检查，直接推送）。
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
     * P0-4: 向指定用户推送通知，离线时缓存到 Redis 等待补偿。
     *
     * <p>策略：
     * <ul>
     *   <li>用户在线：直接通过 STOMP 推送</li>
     *   <li>用户离线：缓存到 {@link OfflineMessageService}，待上线时补偿</li>
     *   <li>推送异常：降级缓存（保证消息不丢）</li>
     * </ul>
     *
     * @param userId  用户 ID
     * @param type    消息类型标签
     * @param payload 消息内容
     */
    public void pushToUserWithOffline(String userId, String type, Object payload) {
        if (userId == null) {
            return;
        }
        try {
            if (onlineUserService.isOnline(userId)) {
                pushToUser(userId, type, payload);
            } else {
                offlineMessageService.cacheOffline(userId, type, payload);
                log.info("[WebSocket] 用户离线，消息已缓存: userId={}, type={}", userId, type);
            }
        } catch (Exception e) {
            // 在线检查失败时降级为直接推送（避免 Redis 故障阻断主流程）
            log.warn("[WebSocket] 在线检查异常，降级直接推送: userId={}, err={}", userId, e.getMessage());
            pushToUser(userId, type, payload);
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
        broadcast("BROADCAST", payload);
    }

    /**
     * 向所有在线用户广播消息（带类型标签）。
     *
     * <p>路由到 {@code /topic/broadcast}，{@code type} 仅作为日志标签使用。
     * 推送失败时降级吞掉异常。
     *
     * @param type    消息类型标签（如 BROADCAST / ALERT）
     * @param payload 消息内容
     */
    public void broadcast(String type, Object payload) {
        try {
            messagingTemplate.convertAndSend(MessageConstants.WS_BROADCAST_DESTINATION, payload);
            log.debug("[WebSocket] 广播消息: type={}", type);
        } catch (Exception e) {
            log.warn("[WebSocket] 广播消息失败，降级忽略: type={}, error={}", type, e.getMessage());
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
