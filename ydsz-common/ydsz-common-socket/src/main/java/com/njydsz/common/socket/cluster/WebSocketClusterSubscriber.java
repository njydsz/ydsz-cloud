package com.njydsz.common.socket.cluster;

import java.util.Collections;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.socket.constant.WebSocketConstants;
import com.njydsz.common.socket.session.LocalSessionRegistry;
import com.njydsz.common.socket.trace.WebSocketTraceContext;

/**
 * WebSocket 集群广播订阅者（Redis Pub/Sub -> 本地 STOMP 推送）。
 *
 * <p>订阅 Redis Channel，收到消息后根据推送类型将消息推送到本地 JVM 的 WebSocket session：
 *
 * <ul>
 *   <li>{@code USER}：推送到 {@code /topic/user/{userId}/notifications}
 *   <li>{@code BROADCAST}：推送到 {@code /topic/broadcast}
 *   <li>{@code TOPIC}：推送到 {@code /topic/{topic}}
 * </ul>
 *
 * <p>收到消息后从 {@link WebSocketClusterMessage#getTraceId()} 恢复 MDC traceId。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class WebSocketClusterSubscriber implements MessageListener {

  private final SimpMessagingTemplate messagingTemplate;
  private final LocalSessionRegistry sessionRegistry;

  /**
   * 当前节点灰度标签（ARCH-006），由配置 {@code ydsz.websocket.cluster.nodeTags} 注入。
   *
   * <p>与消息中的 {@code tags} 取交集：无交集时跳过本节点下发，实现灰度节点子集推送。
   */
  @Value("${ydsz.websocket.cluster.nodeTags:}")
  private String nodeTagsConfig;

  /** 构造集群订阅者。 */
  public WebSocketClusterSubscriber(
      SimpMessagingTemplate messagingTemplate, LocalSessionRegistry sessionRegistry) {
    this.messagingTemplate = messagingTemplate;
    this.sessionRegistry = sessionRegistry;
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    if (message == null || message.getBody() == null) {
      return;
    }
    String body = new String(message.getBody());
    WebSocketClusterMessage clusterMsg;
    try {
      clusterMsg = YdszJson.fromJson(body, WebSocketClusterMessage.class);
    } catch (Exception e) {
      log.warn("[WS-Cluster] 消息解析失败,跳过: err={}", e.getMessage());
      return;
    }
    if (clusterMsg == null) {
      return;
    }
    // ARCH-006: 协议版本兼容性检查
    if (!clusterMsg.isCompatibleWithCurrent()) {
      log.warn(
          "[WS-Cluster] 协议版本不兼容, 跳过: incoming={}, current={}",
          clusterMsg.getProtocolVersion(),
          WebSocketClusterMessage.CURRENT_PROTOCOL_VERSION);
      return;
    }
    // ARCH-006: 灰度标签过滤
    if (!clusterMsg.matchesNodeTags(parseNodeTags())) {
      log.debug(
          "[WS-Cluster] 灰度标签不匹配, 跳过: msgTags={}, nodeTags={}",
          clusterMsg.getTags(),
          nodeTagsConfig);
      return;
    }
    WebSocketTraceContext.runWithTrace(
        clusterMsg.getTraceId(),
        () -> {
          try {
            dispatchToLocal(clusterMsg);
          } catch (Exception e) {
            log.warn(
                "[WS-Cluster] 本地推送失败: type={} err={}", clusterMsg.getPushType(), e.getMessage());
          }
        });
  }

  /**
   * 解析当前节点灰度标签字符串（逗号分隔）为 List。
   *
   * @return 标签列表，未配置时返回空列表
   */
  private List<String> parseNodeTags() {
    if (nodeTagsConfig == null || nodeTagsConfig.isEmpty()) {
      return Collections.emptyList();
    }
    return List.of(nodeTagsConfig.split(","));
  }

  /**
   * 将集群消息推送到本地 WebSocket session。
   *
   * @param msg 集群推送消息
   */
  private void dispatchToLocal(WebSocketClusterMessage msg) {
    String pushType = msg.getPushType();
    String payloadJson = msg.getPayloadJson();
    if ("USER".equals(pushType) && msg.getUserId() != null) {
      String destination =
          WebSocketConstants.WS_USER_DESTINATION_PREFIX + msg.getUserId() + "/notifications";
      messagingTemplate.convertAndSend(destination, payloadJson);
    } else if ("BROADCAST".equals(pushType)) {
      messagingTemplate.convertAndSend(WebSocketConstants.WS_BROADCAST_DESTINATION, payloadJson);
    } else if ("TOPIC".equals(pushType) && msg.getTopic() != null) {
      messagingTemplate.convertAndSend(
          WebSocketConstants.WS_TOPIC_DESTINATION_PREFIX + msg.getTopic(), payloadJson);
    } else if ("KICK".equals(pushType) && msg.getUserId() != null) {
      handleKickLocally(msg);
    } else {
      log.warn(
          "[WS-Cluster] 未知推送类型或参数缺失: type={} userId={} topic={}",
          pushType,
          msg.getUserId(),
          msg.getTopic());
    }
  }

  /**
   * P2-8: 处理集群 KICK 消息，踢出指定用户在本节点的所有 Session。
   *
   * @param msg 集群踢出消息
   */
  private void handleKickLocally(WebSocketClusterMessage msg) {
    String userId = msg.getUserId();
    List<String> sessionIds = sessionRegistry.getSessionIds(userId);
    if (sessionIds.isEmpty()) {
      return;
    }
    for (String sessionId : sessionIds) {
      WebSocketSession session = sessionRegistry.getSession(sessionId);
      if (session != null && session.isOpen()) {
        try {
          session.close(new CloseStatus(4001, "KICK:" + msg.getKickReason()));
          log.info(
              "[WS-Cluster] 集群 KICK 关闭本地 Session: userId={}, sessionId={}, reason={}",
              userId,
              sessionId,
              msg.getKickReason());
        } catch (Exception e) {
          log.warn(
              "[WS-Cluster] 集群 KICK 关闭本地 Session 失败: userId={}, sessionId={}, err={}",
              userId,
              sessionId,
              e.getMessage());
        }
      }
      sessionRegistry.unregister(userId, sessionId);
    }
  }
}
