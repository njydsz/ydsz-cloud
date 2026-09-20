package com.njydsz.common.socket.presence;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket 在线状态变更事件（Presence，UX-003）。
 *
 * <p>用户上线或下线时，由 {@link WebSocketPresenceService} 发布到 STOMP 目的地
 * {@code /topic/presence/{userId}}，业务侧（好友列表、客服会话、协同文档等）订阅此主题
 * 据此渲染用户的实时在线状态。
 *
 * <p>事件结构（兼容前后端旧约定）：
 *
 * <ul>
 *   <li>{@code ONLINE} — 上线（包含 "lastSeen" 字段）
 *   <li>{@code OFFLINE} — 下线（包含 "lastSeen" 字段作为最后一次在线时刻）
 * </ul>
 *
 * <p>注意：集群多节点场景下，仅当用户在该节点"首次上线"或"最后一下线"时才应广播，
 * 避免中间态 Session 变化淹没 Presence 主题；当前实现依靠 {@code OnlineUserService} 内的
 * 计数器判定，并通过配置项 {@code ydsz.websocket.presence.enabled} 控制默认关闭。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketPresenceEvent {

  /** 事件类型：ONLINE / OFFLINE */
  public enum Type {
    ONLINE,
    OFFLINE
  }

  /** 用户 ID */
  private String userId;

  /** 事件类型 */
  private Type type;

  /** 事件时间戳（毫秒 Epoch） */
  private long timestamp;

  /**
   * 末次在线时间戳（OFFLINE 事件时为用户提供 lastSeen）。
   *
   * <p>业务侧可通过此字段渲染 "最后在线于 X 分钟前"。
   */
  private Long lastSeenAt;

  /** 客户端类型（WEB / MOBILE / DESKTOP），可选 */
  private String clientType;

  /** 当前 Session 数（判定是否多端同时在线） */
  private int sessionCount;

  /**
   * 构造 ONLINE 事件。
   *
   * @param userId 用户 ID
   * @param clientType 客户端类型
   * @param sessionCount 当前 Session 数
   * @return Presence 事件
   */
  public static WebSocketPresenceEvent online(String userId, String clientType, int sessionCount) {
    return new WebSocketPresenceEvent(
        userId, Type.ONLINE, System.currentTimeMillis(), null, clientType, sessionCount);
  }

  /**
   * 构造 OFFLINE 事件。
   *
   * @param userId 用户 ID
   * @param lastSeenAt 末次在线时间戳
   * @param clientType 客户端类型
   * @param sessionCount 当前剩余 Session 数（通常 0）
   * @return Presence 事件
   */
  public static WebSocketPresenceEvent offline(
      String userId, long lastSeenAt, String clientType, int sessionCount) {
    return new WebSocketPresenceEvent(
        userId, Type.OFFLINE, System.currentTimeMillis(), lastSeenAt, clientType, sessionCount);
  }
}
