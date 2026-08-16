package com.njydsz.common.socket.constant;

/**
 * WebSocket 公共常量集中定义。
 *
 * <p>从 {@code MessageConstants} 上迁的 WS_ 前缀常量，供 common-socket 模块及业务侧复用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class WebSocketConstants {

  private WebSocketConstants() {}

  // ========== STOMP Destination 前缀 ==========

  /** WebSocket 用户订阅前缀 */
  public static final String WS_USER_DESTINATION_PREFIX = "/topic/user/";

  /** WebSocket 广播目的地 */
  public static final String WS_BROADCAST_DESTINATION = "/topic/broadcast";

  /** WebSocket 主题目的地前缀 */
  public static final String WS_TOPIC_DESTINATION_PREFIX = "/topic/";

  // ========== Redis Key 前缀 ==========

  /** 在线用户 Redis key 前缀（Hash: ydsz:ws:online:{userId} -> sessionId） */
  public static final String WS_ONLINE_KEY_PREFIX = "ydsz:ws:online:";

  /** 离线消息 Redis List key 前缀（ydsz:ws:offline:{userId}） */
  public static final String WS_OFFLINE_KEY_PREFIX = "ydsz:ws:offline:";

  /** 集群广播 Redis Channel */
  public static final String WS_CLUSTER_CHANNEL = "ydsz:ws:cluster:push";

  /** 心跳 Redis Sorted Set key（score=最后心跳时间戳） */
  public static final String WS_HEARTBEAT_KEY = "ydsz:ws:heartbeat:sessions";

  // ========== 离线消息常量 ==========

  /**
   * 离线消息 Redis 缓存最大条数（单用户）。
   *
   * <p>与 {@code WebSocketProperties.Offline.maxCache} 默认值保持一致。
   */
  public static final int WS_OFFLINE_MAX_CACHE = 100;

  /**
   * 离线消息 Redis 缓存 TTL（秒）。
   *
   * <p>默认 30 天，与 {@code WebSocketProperties.Offline.ttl} 默认值保持一致。
   */
  public static final long WS_OFFLINE_TTL_SECONDS = 2592000L;

  /**
   * 离线消息 DB 持久化阈值（单用户）。
   *
   * <p>当 Redis 缓存条数超过此阈值时，触发异步 DB 持久化以防止 Redis 内存溢出； 建议设置为 {@link #WS_OFFLINE_MAX_CACHE} 的 80%。
   */
  public static final int WS_OFFLINE_DB_PERSIST_THRESHOLD = 80;

  // ========== 握手属性 Key ==========

  /** WebSocket 握手属性中的 userId key */
  public static final String WS_ATTR_USER_ID = "userId";

  /** WebSocket 握手属性中的 username key */
  public static final String WS_ATTR_USERNAME = "username";

  /** 握手请求中 JWT token 的查询参数名 */
  public static final String WS_TOKEN_PARAM = "token";

  /** 握手请求中 JWT token 的请求头名 */
  public static final String WS_TOKEN_HEADER = "Authorization";

  // ========== 网关透传认证头（P1-5） ==========

  /** 网关透传的用户 ID 头（与 AuthHeaderConstants.X_USER_ID 保持一致） */
  public static final String WS_GATEWAY_USER_ID_HEADER = "X-User-Id";

  /** 网关透传的用户名头（与 AuthHeaderConstants.X_USERNAME 保持一致） */
  public static final String WS_GATEWAY_USERNAME_HEADER = "X-Username";

  /** 网关透传的共享密钥头，用于验证请求确实来自网关 */
  public static final String WS_GATEWAY_SECRET_HEADER = "X-Gateway-Secret";
}
