package com.njydsz.pmis.common.websocket.constant;

/**
 * WebSocket 公共常量集中定义。
 *
 * <p>从 {@code MessageConstants} 上迁的 WS_ 前缀常量，供 common-websocket 模块及业务侧复用。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
public final class WebSocketConstants {

    private WebSocketConstants() {
    }

    // ========== STOMP Destination 前缀 ==========

    /** WebSocket 用户订阅前缀 */
    public static final String WS_USER_DESTINATION_PREFIX = "/topic/user/";

    /** WebSocket 广播目的地 */
    public static final String WS_BROADCAST_DESTINATION = "/topic/broadcast";

    /** WebSocket 主题目的地前缀 */
    public static final String WS_TOPIC_DESTINATION_PREFIX = "/topic/";

    // ========== Redis Key 前缀 ==========

    /** 在线用户 Redis key 前缀（Hash: pmis:ws:online:{userId} -> sessionId） */
    public static final String WS_ONLINE_KEY_PREFIX = "pmis:ws:online:";

    /** 离线消息 Redis List key 前缀（pmis:ws:offline:{userId}） */
    public static final String WS_OFFLINE_KEY_PREFIX = "pmis:ws:offline:";

    /** 集群广播 Redis Channel */
    public static final String WS_CLUSTER_CHANNEL = "pmis:ws:cluster:push";

    // ========== 离线消息配置 ==========

    /** 离线消息缓存最大条数（防止内存溢出，FIFO 淘汰） */
    public static final int WS_OFFLINE_MAX_CACHE = 100;

    /** 离线消息缓存 TTL（秒），默认 30 天 */
    public static final long WS_OFFLINE_TTL_SECONDS = 30 * 24 * 3600L;

    /** Redis 离线消息溢出后的数据库持久化阈值（超过此数量时写入数据库） */
    public static final int WS_OFFLINE_DB_PERSIST_THRESHOLD = 50;

    // ========== 握手属性 Key ==========

    /** WebSocket 握手属性中的 userId key */
    public static final String WS_ATTR_USER_ID = "userId";

    /** WebSocket 握手属性中的 username key */
    public static final String WS_ATTR_USERNAME = "username";

    /** 握手请求中 JWT token 的查询参数名 */
    public static final String WS_TOKEN_PARAM = "token";

    /** 握手请求中 JWT token 的请求头名 */
    public static final String WS_TOKEN_HEADER = "Authorization";

    // ========== Session TTL ==========

    /** session 记录 TTL，心跳未续期时自动清理（秒） */
    public static final long SESSION_TTL_SECONDS = 3600L;
}
