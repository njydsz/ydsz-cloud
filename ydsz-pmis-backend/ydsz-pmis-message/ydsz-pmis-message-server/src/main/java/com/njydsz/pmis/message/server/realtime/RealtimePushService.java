package com.njydsz.pmis.message.server.realtime;

import com.njydsz.pmis.common.util.json.JsonUtils;
import com.njydsz.pmis.message.domain.constant.MessageConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * 实时推送服务（基于 STOMP over WebSocket + Redis Pub/Sub 集群广播）。
 *
 * <p>P0-1 增强：多实例部署下，推送指令先通过 {@link WebSocketClusterPublisher} 发布到 Redis Channel，
 * 所有实例订阅后各自推送到本地 WebSocket session，实现跨节点广播。Redis 异常时降级为本地直接推送。
 *
 * <p>P0-4 增强：{@link #pushToUserWithOffline} 方法在推送前检查用户在线状态，
 * 离线时缓存到 {@link OfflineMessageService}，待用户上线时补偿推送。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimePushService {

    /** STOMP 消息模板（本地推送降级用） */
    private final SimpMessagingTemplate messagingTemplate;

    /** P0-1: 集群广播发布者 */
    private final WebSocketClusterPublisher clusterPublisher;

    /** P0-4: 在线用户状态服务 */
    private final OnlineUserService onlineUserService;

    /** P0-4: 离线消息补偿服务 */
    private final OfflineMessageService offlineMessageService;

    /**
     * 向指定用户推送通知（集群广播）。
     *
     * <p>通过 Redis Pub/Sub 发布到所有实例，各实例推送到本地 WebSocket session。
     * Redis 异常时降级为本地直接推送。
     *
     * @param userId  用户 ID
     * @param type    消息类型（NOTIFICATION/ALERT/DASHBOARD 等）
     * @param payload 消息内容
     */
    public void pushToUser(String userId, String type, Object payload) {
        if (userId == null) {
            return;
        }
        String payloadJson = serializePayload(payload);
        WebSocketClusterMessage msg = WebSocketClusterMessage.forUser(userId, type, payloadJson);
        if (!clusterPublisher.publish(msg)) {
            // Redis 发布失败，降级本地推送
            localPushToUser(userId, payloadJson);
        }
    }

    /**
     * P0-4: 向指定用户推送通知，离线时缓存到 Redis 等待补偿。
     *
     * <p>策略：
     * <ul>
     *   <li>用户在线：通过集群广播推送</li>
     *   <li>用户离线：缓存到 {@link OfflineMessageService}，待上线时补偿</li>
     *   <li>在线检查异常：降级为直接推送（保证消息不丢）</li>
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
            log.warn("[WebSocket] 在线检查异常，降级直接推送: userId={}, err={}", userId, e.getMessage());
            pushToUser(userId, type, payload);
        }
    }

    /**
     * 向所有在线用户广播消息（集群广播）。
     *
     * @param payload 消息内容
     */
    public void broadcast(Object payload) {
        broadcast("BROADCAST", payload);
    }

    /**
     * 向所有在线用户广播消息（带类型标签，集群广播）。
     *
     * @param type    消息类型标签（如 BROADCAST / ALERT）
     * @param payload 消息内容
     */
    public void broadcast(String type, Object payload) {
        String payloadJson = serializePayload(payload);
        WebSocketClusterMessage msg = WebSocketClusterMessage.forBroadcast(type, payloadJson);
        if (!clusterPublisher.publish(msg)) {
            // Redis 发布失败，降级本地广播
            localBroadcast(payloadJson);
        }
    }

    /**
     * 向指定主题推送消息（如驾驶舱数据刷新，集群广播）。
     *
     * @param topic   主题路径
     * @param payload 消息内容
     */
    public void pushToTopic(String topic, Object payload) {
        String payloadJson = serializePayload(payload);
        WebSocketClusterMessage msg = WebSocketClusterMessage.forTopic(topic, payloadJson);
        if (!clusterPublisher.publish(msg)) {
            // Redis 发布失败，降级本地推送
            localPushToTopic(topic, payloadJson);
        }
    }

    // ==================== 本地降级推送 ====================

    /**
     * 本地直接推送到用户（Redis 不可用时的降级）。
     */
    private void localPushToUser(String userId, String payloadJson) {
        try {
            String destination = MessageConstants.WS_USER_DESTINATION_PREFIX + userId + "/notifications";
            messagingTemplate.convertAndSend(destination, payloadJson);
            log.debug("[WebSocket] 本地降级推送: userId={}", userId);
        } catch (Exception e) {
            log.warn("[WebSocket] 本地降级推送失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 本地广播（Redis 不可用时的降级）。
     */
    private void localBroadcast(String payloadJson) {
        try {
            messagingTemplate.convertAndSend(MessageConstants.WS_BROADCAST_DESTINATION, payloadJson);
        } catch (Exception e) {
            log.warn("[WebSocket] 本地降级广播失败: error={}", e.getMessage());
        }
    }

    /**
     * 本地主题推送（Redis 不可用时的降级）。
     */
    private void localPushToTopic(String topic, String payloadJson) {
        try {
            messagingTemplate.convertAndSend(
                    MessageConstants.WS_TOPIC_DESTINATION_PREFIX + topic, payloadJson);
        } catch (Exception e) {
            log.warn("[WebSocket] 本地降级主题推送失败: topic={}, error={}", topic, e.getMessage());
        }
    }

    /**
     * 序列化 payload 为 JSON 字符串。
     *
     * @param payload 消息内容
     * @return JSON 字符串；序列化失败返回 toString 结果
     */
    private String serializePayload(Object payload) {
        if (payload == null) {
            return "{}";
        }
        if (payload instanceof String s) {
            return s;
        }
        try {
            return JsonUtils.toJson(payload);
        } catch (Exception e) {
            log.warn("[WebSocket] payload 序列化失败,降级 toString: {}", e.getMessage());
            return String.valueOf(payload);
        }
    }
}
