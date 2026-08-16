package com.njydsz.common.socket.session;

import com.njydsz.common.socket.audit.WebSocketAuditService;
import com.njydsz.common.socket.cluster.WebSocketClusterMessage;
import com.njydsz.common.socket.cluster.WebSocketClusterPublisher;
import com.njydsz.common.socket.config.WebSocketProperties;
import com.njydsz.common.socket.constant.WebSocketConstants;
import com.njydsz.common.socket.heartbeat.WebSocketHeartbeatHandler;
import com.njydsz.common.socket.lifecycle.WebSocketConnectionListener;
import com.njydsz.common.socket.offline.OfflineMessageStore;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * WebSocket 会话事件监听器。
 *
 * <p>监听 STOMP {@link SessionConnectedEvent} / {@link SessionDisconnectEvent}， 维护用户在线状态并在上线时补偿离线消息：
 *
 * <ul>
 *   <li>连接成功：标记用户上线，拉取并推送离线消息，通知连接监听器
 *   <li>断开连接：标记用户下线，审计断开事件，通知连接监听器
 * </ul>
 *
 * <p>集成心跳注册/注销（P0-3）和连接生命周期钩子（P3-5）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class WebSocketSessionEventListener {

  private final OnlineUserService onlineUserService;
  private final OfflineMessageStore offlineMessageStore;
  private final SimpMessagingTemplate messagingTemplate;
  private final WebSocketHeartbeatHandler heartbeatHandler;
  private final WebSocketAuditService auditService;
  private final List<WebSocketConnectionListener> connectionListeners;
  private final WebSocketProperties properties;
  private final LocalSessionRegistry sessionRegistry;

  /** P2-8: 集群广播发布者（多端策略踢出后同步到其他节点） */
  private final WebSocketClusterPublisher clusterPublisher;

  /** 本节点活跃连接计数器（供 HealthIndicator 读取） */
  private final AtomicLong activeConnections = new AtomicLong(0);

  /** Session ID → 连接时间戳（用于计算连接时长） */
  private final Map<String, Long> connectTimes = new ConcurrentHashMap<>();

  public WebSocketSessionEventListener(
      OnlineUserService onlineUserService,
      OfflineMessageStore offlineMessageStore,
      SimpMessagingTemplate messagingTemplate,
      WebSocketHeartbeatHandler heartbeatHandler,
      WebSocketAuditService auditService,
      List<WebSocketConnectionListener> connectionListeners,
      WebSocketProperties properties,
      LocalSessionRegistry sessionRegistry,
      WebSocketClusterPublisher clusterPublisher) {
    this.onlineUserService = onlineUserService;
    this.offlineMessageStore = offlineMessageStore;
    this.messagingTemplate = messagingTemplate;
    this.heartbeatHandler = heartbeatHandler;
    this.auditService = auditService;
    this.connectionListeners = connectionListeners != null ? connectionListeners : List.of();
    this.properties = properties;
    this.sessionRegistry = sessionRegistry;
    this.clusterPublisher = clusterPublisher;
  }

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
    activeConnections.incrementAndGet();
    connectTimes.put(sessionId, System.currentTimeMillis());
    if (heartbeatHandler != null) {
      heartbeatHandler.registerSession(sessionId, userId);
    }
    enforceMultiDevicePolicy(userId, sessionId);
    log.info(
        "[WS-Session] 用户连接: userId={}, sessionId={}, localActive={}",
        userId,
        sessionId,
        activeConnections.get());
    notifyConnected(userId, sessionId);
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
    activeConnections.decrementAndGet();
    Long connectTime = connectTimes.remove(sessionId);
    if (heartbeatHandler != null) {
      heartbeatHandler.unregisterSession(sessionId);
    }
    if (auditService != null) {
      long duration = connectTime != null ? System.currentTimeMillis() - connectTime : 0;
      auditService.auditDisconnect(userId, sessionId, duration);
    }
    log.info(
        "[WS-Session] 用户断开: userId={}, sessionId={}, localActive={}",
        userId,
        sessionId,
        activeConnections.get());
    notifyDisconnected(userId, sessionId);
  }

  /**
   * 执行多端登录策略校验。
   *
   * <p>根据配置的多端策略对旧 Session 进行清理：
   *
   * <ul>
   *   <li>MUTEX — 关闭该用户在本节点的所有旧 Session
   *   <li>NEW_REPLACE_OLD — 超过最大 Session 数时关闭最早的 Session
   *   <li>ALLOW_ALL — 不做处理
   * </ul>
   *
   * @param userId 新连接的用户 ID
   * @param sessionId 新连接的 Session ID
   */
  private void enforceMultiDevicePolicy(String userId, String sessionId) {
    WebSocketProperties.MultiDevice multiDevice = properties.getMultiDevice();
    String policy = multiDevice.getPolicy();
    if ("ALLOW_ALL".equals(policy)) {
      return;
    }
    int maxSessions = multiDevice.getMaxSessionsPerUser();
    List<String> existingSessionIds = sessionRegistry.getSessionIds(userId);
    if ("MUTEX".equals(policy)) {
      for (String existingId : existingSessionIds) {
        if (existingId.equals(sessionId)) {
          continue;
        }
        closeOldSession(userId, existingId);
      }
    } else if ("NEW_REPLACE_OLD".equals(policy)) {
      int currentCount = existingSessionIds.size();
      if (currentCount > maxSessions) {
        String oldest = existingSessionIds.get(0);
        closeOldSession(userId, oldest);
      }
    }
  }

  /**
   * 关闭旧 Session 并注销，同时发布集群 KICK 消息同步踢出其他节点。
   *
   * <p>P2-8：多端策略执行后，通过 Redis Pub/Sub 广播 KICK 消息， 其他节点收到后关闭同用户在本节点的 Session，消除集群盲区。
   *
   * @param userId 用户 ID
   * @param sessionId 待关闭的 Session ID
   */
  private void closeOldSession(String userId, String sessionId) {
    org.springframework.web.socket.WebSocketSession oldSession =
        sessionRegistry.getSession(sessionId);
    if (oldSession != null && oldSession.isOpen()) {
      try {
        oldSession.close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);
        log.info("[WS-Session] 多端策略关闭旧 Session: userId={}, sessionId={}", userId, sessionId);
      } catch (Exception e) {
        log.warn(
            "[WS-Session] 关闭旧 Session 失败: userId={}, sessionId={}, err={}",
            userId,
            sessionId,
            e.getMessage());
      }
    }
    sessionRegistry.unregister(userId, sessionId);
    // P2-8: 集群同步踢出（Redis 发布失败不影响本地流程）
    publishKickToCluster(userId);
  }

  /**
   * P2-8: 发布集群 KICK 消息，通知其他节点踢出同用户 Session。
   *
   * @param userId 待踢出的用户 ID
   */
  private void publishKickToCluster(String userId) {
    if (clusterPublisher == null || !properties.getCluster().isEnabled()) {
      return;
    }
    try {
      clusterPublisher.publish(WebSocketClusterMessage.forKick(userId));
      log.debug("[WS-Session] 发布集群 KICK 消息: userId={}", userId);
    } catch (Exception e) {
      log.warn(
          "[WS-Session] 发布集群 KICK 消息失败（本地已踢出，不影响本节点）: userId={}, err={}", userId, e.getMessage());
    }
  }

  /**
   * 获取本节点活跃连接数（供 HealthIndicator 使用）。
   *
   * @return 当前活跃连接数
   */
  public long getActiveConnections() {
    return activeConnections.get();
  }

  /**
   * 获取活跃连接计数器引用（供 HealthIndicator 直接引用）。
   *
   * @return AtomicLong 计数器实例
   */
  public AtomicLong getActiveConnectionsCounter() {
    return activeConnections;
  }

  /** 通知所有注册的连接监听器：连接建立。 */
  private void notifyConnected(String userId, String sessionId) {
    for (WebSocketConnectionListener listener : connectionListeners) {
      try {
        listener.onConnected(userId, sessionId);
      } catch (Exception e) {
        log.warn(
            "[WS-Session] 连接监听器异常: listener={}, err={}",
            listener.getClass().getSimpleName(),
            e.getMessage());
      }
    }
  }

  /** 通知所有注册的连接监听器：连接断开。 */
  private void notifyDisconnected(String userId, String sessionId) {
    for (WebSocketConnectionListener listener : connectionListeners) {
      try {
        listener.onDisconnected(userId, sessionId);
      } catch (Exception e) {
        log.warn(
            "[WS-Session] 断开监听器异常: listener={}, err={}",
            listener.getClass().getSimpleName(),
            e.getMessage());
      }
    }
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
      String destination =
          WebSocketConstants.WS_USER_DESTINATION_PREFIX + userId + "/notifications";
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
