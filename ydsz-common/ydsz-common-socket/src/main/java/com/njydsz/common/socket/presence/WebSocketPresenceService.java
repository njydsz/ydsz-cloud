package com.njydsz.common.socket.presence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.njydsz.common.socket.config.WebSocketProperties;
import com.njydsz.common.socket.constant.WebSocketConstants;
import com.njydsz.common.socket.session.LocalSessionRegistry;
import com.njydsz.common.socket.session.OnlineUserService;

/**
 * WebSocket Presence 在线状态广播服务（UX-003）。
 *
 * <p>在用户"首次上线"或"下线最后一 Session"时，向 Presence 主题
 * {@code /topic/presence/{userId}} 广播 {@link WebSocketPresenceEvent} 事件，
 * 好友列表 / 会话面板 / 协同文档等业务侧借此感知用户实时在线状态。
 *
 * <p>触发条件：
 *
 * <ul>
 *   <li>markOnline：用户全局 Session 数从 0 → 1 → 广播 ONLINE</li>
 *   <li>markOffline：用户全局 Session 数从 1 → 0 → 广播 OFFLINE（含 lastSeenAt）</li>
 * </ul>
 *
 * <p>本服务通过 {@link WebSocketConnectionListener} 钩子接入，避免直接引入对
 * {@code WebSocketSessionEventListener} 的耦合；默认关闭（opt-in），避免对未开通业务的模块造成 STOMP 噪声。
 *
 * <p>线程安全：本服务方法均为无状态，可被并发调用；内部通过 {@link OnlineUserService#getSessionCount} 与
 * {@link LocalSessionRegistry#getSessionIds} 做最终一致性判定（Redis 与本地注册表可能在
 * 集群广播窗口内不一致，容忍瞬时差异，靠下次事件收敛）。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
@Slf4j
public class WebSocketPresenceService implements com.njydsz.common.socket.lifecycle.WebSocketConnectionListener {

  private final SimpMessagingTemplate messagingTemplate;
  private final OnlineUserService onlineUserService;
  private final LocalSessionRegistry localSessionRegistry;
  private final WebSocketProperties properties;

  public WebSocketPresenceService(
      SimpMessagingTemplate messagingTemplate,
      OnlineUserService onlineUserService,
      LocalSessionRegistry localSessionRegistry,
      WebSocketProperties properties) {
    this.messagingTemplate = messagingTemplate;
    this.onlineUserService = onlineUserService;
    this.localSessionRegistry = localSessionRegistry;
    this.properties = properties;
  }

  /**
   * 是否启用 Presence 广播（从配置项读取）。
   *
   * @return true 表示开启
   */
  public boolean isEnabled() {
    return properties.getPresence() != null && properties.getPresence().isEnabled();
  }

  @Override
  public void onConnected(String userId, String sessionId) {
    if (!isEnabled()) {
      return;
    }
    try {
      long sessionCount = onlineUserService.getSessionCount(userId);
      // 仅当首次上线（sessionCount == 1）时广播 ONLINE
      if (sessionCount == 1) {
        WebSocketPresenceEvent event =
            WebSocketPresenceEvent.online(userId, null, (int) sessionCount);
        broadcastPresence(userId, event);
      }
    } catch (Exception e) {
      log.warn("[WS-Presence] 上线广播异常: userId={}, err={}", userId, e.getMessage());
    }
  }

  @Override
  public void onDisconnected(String userId, String sessionId) {
    if (!isEnabled()) {
      return;
    }
    try {
      long sessionCount = onlineUserService.getSessionCount(userId);
      // 仅当最后一 Session 下线（sessionCount == 0）时广播 OFFLINE
      if (sessionCount == 0) {
        long lastSeenAt = System.currentTimeMillis();
        WebSocketPresenceEvent event =
            WebSocketPresenceEvent.offline(userId, lastSeenAt, null, (int) sessionCount);
        broadcastPresence(userId, event);
      }
    } catch (Exception e) {
      log.warn("[WS-Presence] 下线广播异常: userId={}, err={}", userId, e.getMessage());
    }
  }

  /**
   * 直接广播 Presence 事件（业务侧可独立调用，不经过连接监听器判定）。
   *
   * <p>公开此方法以便：
   *
   * <ul>
   *   <li>业务端主动刷新（如心跳续约后主动推送 HEARTBEAT 类型）</li>
   *   <li>多节点场景下通过集群广播消息驱动（{@code WebSocketClusterSubscriber} 解析后调用）</li>
   * </ul>
   *
   * @param userId 用户 ID
   * @param event Presence 事件
   */
  public void broadcastPresence(String userId, WebSocketPresenceEvent event) {
    String destination = WebSocketConstants.WS_PRESENCE_DESTINATION_PREFIX + userId;
    try {
      messagingTemplate.convertAndSend(destination, event);
      log.debug("[WS-Presence] 广播: userId={}, type={}", userId, event.getType());
    } catch (Exception e) {
      log.warn("[WS-Presence] 广播失败: userId={}, type={}, err={}",
          userId, event.getType(), e.getMessage());
    }
  }
}
