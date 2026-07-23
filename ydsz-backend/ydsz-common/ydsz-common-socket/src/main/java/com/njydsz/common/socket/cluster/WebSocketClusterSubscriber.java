package com.njydsz.common.socket.cluster;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.socket.compress.MessageCompressor;
import com.njydsz.common.socket.constant.WebSocketConstants;
import com.njydsz.common.socket.trace.WebSocketTraceContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 集群广播订阅者（Redis Pub/Sub -> 本地 STOMP 推送）。
 *
 * <p>订阅 Redis Channel，收到消息后根据推送类型将消息推送到本地 JVM 的 WebSocket session：
 * <ul>
 *   <li>{@code USER}：推送到 {@code /topic/user/{userId}/notifications}</li>
 *   <li>{@code BROADCAST}：推送到 {@code /topic/broadcast}</li>
 *   <li>{@code TOPIC}：推送到 {@code /topic/{topic}}</li>
 * </ul>
 *
 * <p>收到消息后从 {@link WebSocketClusterMessage#getTraceId()} 恢复 MDC traceId（P1-1），
 * 如果消息被压缩则解压（P2-3）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class WebSocketClusterSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageCompressor messageCompressor;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (message == null || message.getBody() == null) {
            return;
        }
        String body = new String(message.getBody());
        WebSocketClusterMessage clusterMsg;
        try {
            clusterMsg = YdszJson.toObject(body, WebSocketClusterMessage.class);
        } catch (Exception e) {
            log.warn("[WS-Cluster] 消息解析失败,跳过: err={}", e.getMessage());
            return;
        }
        if (clusterMsg == null) {
            return;
        }
        WebSocketTraceContext.runWithTrace(clusterMsg.getTraceId(), () -> {
            try {
                dispatchToLocal(clusterMsg);
            } catch (Exception e) {
                log.warn("[WS-Cluster] 本地推送失败: type={} err={}",
                        clusterMsg.getPushType(), e.getMessage());
            }
        });
    }

    /**
     * 将集群消息推送到本地 WebSocket session。
     *
     * @param msg 集群推送消息
     */
    private void dispatchToLocal(WebSocketClusterMessage msg) {
        String pushType = msg.getPushType();
        String payloadJson = msg.getPayloadJson();
        if (messageCompressor != null) {
            payloadJson = messageCompressor.decompressIfNeeded(payloadJson);
        }
        if ("USER".equals(pushType) && msg.getUserId() != null) {
            String destination = WebSocketConstants.WS_USER_DESTINATION_PREFIX
                    + msg.getUserId() + "/notifications";
            messagingTemplate.convertAndSend(destination, payloadJson);
        } else if ("BROADCAST".equals(pushType)) {
            messagingTemplate.convertAndSend(
                    WebSocketConstants.WS_BROADCAST_DESTINATION, payloadJson);
        } else if ("TOPIC".equals(pushType) && msg.getTopic() != null) {
            messagingTemplate.convertAndSend(
                    WebSocketConstants.WS_TOPIC_DESTINATION_PREFIX + msg.getTopic(),
                    payloadJson);
        } else {
            log.warn("[WS-Cluster] 未知推送类型或参数缺失: type={} userId={} topic={}",
                    pushType, msg.getUserId(), msg.getTopic());
        }
    }
}
