package com.njydsz.common.socket.context;

import com.njydsz.common.socket.constant.WebSocketConstants;

/**
 * WebSocket 租户上下文（ARCH-005）。
 *
 * <p>在握手阶段由 {@code WebSocketAuthInterceptor} 从 JWT 或网关透传头部提取 tenantId 后设置到
 * ThreadLocal，后续推送/存储链路基于当前上下文的 tenantId 构建租户隔离的 Redis key 与
 * Destination 路径。
 *
 * <p>使用完后应在 finally 块中调用 {@link #clear()} 清理，避免跨请求污染。
 *
 * <p>典型路径前缀：
 *
 * <ul>
 *   <li>Destination: {@code /topic/t/{tenantId}/user/{userId}/notifications}
 *   <li>Redis Key: {@code ydsz:ws:online:{tenantId}:{userId}}
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.20
 */
public final class WebSocketContext {

  private static final ThreadLocal<String> CURRENT_TENANT_ID = new ThreadLocal<>();

  private WebSocketContext() {}

  /**
   * 设置当前线程的租户 ID。
   *
   * @param tenantId 租户 ID
   */
  public static void setCurrentTenantId(String tenantId) {
    if (tenantId == null || tenantId.isEmpty()) {
      CURRENT_TENANT_ID.remove();
    } else {
      CURRENT_TENANT_ID.set(tenantId);
    }
  }

  /**
   * 获取当前线程的租户 ID。
   *
   * @return 租户 ID，未设置时返回 null
   */
  public static String getCurrentTenantId() {
    return CURRENT_TENANT_ID.get();
  }

  /**
   * 清理当前线程的租户上下文（必须在使用完毕后调用）。
   *
   * <p>推荐在 finally 块中调用 ThreadLocal.remove() 避免跨请求污染。
   */
  public static void clear() {
    CURRENT_TENANT_ID.remove();
  }

  /**
   * 构建租户隔离的在线用户 Redis key。
   *
   * <p>格式：{@code ydsz:ws:online:{tenantId}:{userId}}（tenantId 为 null 时回退为全局 key）。
   *
   * @param userId 用户 ID
   * @return 租户隔离的 Redis key
   */
  public static String buildOnlineKey(String userId) {
    String tenantId = getCurrentTenantId();
    return buildOnlineKey(tenantId, userId);
  }

  /**
   * 构建租户隔离的在线用户 Redis key（显式指定 tenantId）。
   *
   * @param tenantId 租户 ID，可为 null（回退为全局 key）
   * @param userId 用户 ID
   * @return Redis key
   */
  public static String buildOnlineKey(String tenantId, String userId) {
    if (tenantId != null && !tenantId.isEmpty()) {
      return WebSocketConstants.WS_TENANT_ONLINE_KEY_PREFIX + tenantId
          + WebSocketConstants.WS_TENANT_KEY_SEPARATOR + userId;
    }
    return WebSocketConstants.WS_ONLINE_KEY_PREFIX + userId;
  }

  /**
   * 构建租户隔离的离线消息 Redis key。
   *
   * @param userId 用户 ID
   * @return 租户隔离的 Redis key
   */
  public static String buildOfflineKey(String userId) {
    String tenantId = getCurrentTenantId();
    return buildOfflineKey(tenantId, userId);
  }

  /**
   * 构建租户隔离的离线消息 Redis key（显式指定 tenantId）。
   *
   * @param tenantId 租户 ID，可为 null
   * @param userId 用户 ID
   * @return Redis key
   */
  public static String buildOfflineKey(String tenantId, String userId) {
    if (tenantId != null && !tenantId.isEmpty()) {
      return WebSocketConstants.WS_TENANT_OFFLINE_KEY_PREFIX + tenantId
          + WebSocketConstants.WS_TENANT_KEY_SEPARATOR + userId;
    }
    return WebSocketConstants.WS_OFFLINE_KEY_PREFIX + userId;
  }

  /**
   * 构建租户隔离的 Destination 路径（用户私有频道）。
   *
   * <p>格式：{@code /topic/t/{tenantId}/user/{userId}/notifications}（tenantId 为 null 时回退为全局路径）。
   *
   * @param userId 用户 ID
   * @return Destination 路径
   */
  public static String buildUserDestination(String userId) {
    String tenantId = getCurrentTenantId();
    return buildUserDestination(tenantId, userId);
  }

  /**
   * 构建租户隔离的 Destination 路径（用户私有频道，显式指定 tenantId）。
   *
   * @param tenantId 租户 ID，可为 null
   * @param userId 用户 ID
   * @return Destination 路径
   */
  public static String buildUserDestination(String tenantId, String userId) {
    if (tenantId != null && !tenantId.isEmpty()) {
      return WebSocketConstants.WS_TENANT_DESTINATION_PREFIX + tenantId
          + "/user/" + userId + "/notifications";
    }
    return WebSocketConstants.WS_USER_DESTINATION_PREFIX + userId + "/notifications";
  }

  /**
   * 构建租户隔离的广播 Destination。
   *
   * @return Destination 路径
   */
  public static String buildBroadcastDestination() {
    String tenantId = getCurrentTenantId();
    return buildBroadcastDestination(tenantId);
  }

  /**
   * 构建租户隔离的广播 Destination（显式指定 tenantId）。
   *
   * @param tenantId 租户 ID
   * @return Destination 路径
   */
  public static String buildBroadcastDestination(String tenantId) {
    if (tenantId != null && !tenantId.isEmpty()) {
      return WebSocketConstants.WS_TENANT_DESTINATION_PREFIX + tenantId + "/broadcast";
    }
    return WebSocketConstants.WS_BROADCAST_DESTINATION;
  }
}
