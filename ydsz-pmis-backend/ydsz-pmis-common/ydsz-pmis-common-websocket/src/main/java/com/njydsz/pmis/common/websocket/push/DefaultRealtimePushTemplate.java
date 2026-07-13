package com.njydsz.pmis.common.websocket.push;

import com.njydsz.pmis.common.util.json.JsonUtils;
import com.njydsz.pmis.common.websocket.cluster.WebSocketClusterMessage;
import com.njydsz.pmis.common.websocket.cluster.WebSocketClusterPublisher;
import com.njydsz.pmis.common.websocket.constant.WebSocketConstants;
import com.njydsz.pmis.common.websocket.metric.WebSocketMetrics;
import com.njydsz.pmis.common.websocket.offline.OfflineMessageStore;
import com.njydsz.pmis.common.websocket.session.OnlineUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * 默认实时推送模板实现（STOMP + Redis Pub/Sub 集群广播 + 降级 + 离线补偿）。
 *
 * <p>推送流程：
 * <ol>
 *   <li>序列化 payload 为 JSON</li>
 *   <li>通过 {@link WebSocketClusterPublisher} 发布到 Redis Channel（集群广播）</li>
 *   <li>Redis 发布失败时降级为本地直接推送</li>
 *   <li>{@link #pushToUserWithOffline} 方法额外检查在线状态，离线时缓存到 {@link OfflineMessageStore}</li>
 * </ol>
 *
 * <p>推送成功/失败通过 {@link WebSocketMetrics} 记录 Micrometer 指标。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultRealtimePushTemplate implements RealtimePushTemplate {

    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketClusterPublisher clusterPublisher;
    private final OnlineUserService onlineUserService;
    private final OfflineMessageStore offlineMessageStore;
    private final WebSocketMetrics webSocketMetrics;

    @Override
    public void pushToUser(String userId, String type, Object payload) {
        if (userId == null) {
            return;
        }
        String payloadJson = serializePayload(payload);
        WebSocketClusterMessage msg = WebSocketClusterMessage.forUser(userId, type, payloadJson);
        if (!clusterPublisher.publish(msg)) {
            localPushToUser(userId, payloadJson);
        }
        webSocketMetrics.recordPush("USER", true);
    }

    @Override
    public void pushToUserWithOffline(String userId, String type, Object payload) {
        if (userId == null) {
            return;
        }
        try {
            if (onlineUserService.isOnline(userId)) {
                pushToUser(userId, type, payload);
            } else {
                offlineMessageStore.cacheOffline(userId, type, payload);
                log.info("[WebSocket] 用户离线，消息已缓存: userId={}, type={}", userId, type);
            }
        } catch (Exception e) {
            log.warn("[WebSocket] 在线检查异常，降级直接推送: userId={}, err={}", userId, e.getMessage());
            pushToUser(userId, type, payload);
        }
    }

    @Override
    public void broadcast(Object payload) {
        broadcast("BROADCAST", payload);
    }

    @Override
    public void broadcast(String type, Object payload) {
        String payloadJson = serializePayload(payload);
        WebSocketClusterMessage msg = WebSocketClusterMessage.forBroadcast(type, payloadJson);
        if (!clusterPublisher.publish(msg)) {
            localBroadcast(payloadJson);
        }
        webSocketMetrics.recordPush("BROADCAST", true);
    }

    @Override
    public void pushToTopic(String topic, Object payload) {
        String payloadJson = serializePayload(payload);
        WebSocketClusterMessage msg = WebSocketClusterMessage.forTopic(topic, payloadJson);
        if (!clusterPublisher.publish(msg)) {
            localPushToTopic(topic, payloadJson);
        }
        webSocketMetrics.recordPush("TOPIC", true);
    }

    // ==================== 本地降级推送 ====================

    private void localPushToUser(String userId, String payloadJson) {
        try {
            String destination = WebSocketConstants.WS_USER_DESTINATION_PREFIX + userId + "/notifications";
            messagingTemplate.convertAndSend(destination, payloadJson);
            log.debug("[WebSocket] 本地降级推送: userId={}", userId);
        } catch (Exception e) {
            log.warn("[WebSocket] 本地降级推送失败: userId={}, error={}", userId, e.getMessage());
            webSocketMetrics.recordPush("USER", false);
        }
    }

    private void localBroadcast(String payloadJson) {
        try {
            messagingTemplate.convertAndSend(WebSocketConstants.WS_BROADCAST_DESTINATION, payloadJson);
        } catch (Exception e) {
            log.warn("[WebSocket] 本地降级广播失败: error={}", e.getMessage());
            webSocketMetrics.recordPush("BROADCAST", false);
        }
    }

    private void localPushToTopic(String topic, String payloadJson) {
        try {
            messagingTemplate.convertAndSend(
                    WebSocketConstants.WS_TOPIC_DESTINATION_PREFIX + topic, payloadJson);
        } catch (Exception e) {
            log.warn("[WebSocket] 本地降级主题推送失败: topic={}, error={}", topic, e.getMessage());
            webSocketMetrics.recordPush("TOPIC", false);
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
