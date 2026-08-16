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

    /** 在线用户 Redis key 前缀（Hash: ydsz:ws:online:{userId} -> sessionId） */
    public static final String WS_ONLINE_KEY_PREFIX = "ydsz:ws:online:";

    /** 离线消息 Redis List key 前缀（ydsz:ws:offline:{userId}） */
    public static final String WS_OFFLINE_KEY_PREFIX = "ydsz:ws:offline:";

    /** 集群广播 Redis Channel */
    public static final String WS_CLUSTER_CHANNEL = "ydsz:ws:cluster:push";

    /** 心跳 Redis Sorted Set key（score=最后心跳时间戳） */
    public static final String WS_HEARTBEAT_KEY = "ydsz:ws:heartbeat:sessions";

    // ========== 握手属性 Key ==========

    /** WebSocket 握手属性中的 userId key */
    public static final String WS_ATTR_USER_ID = "userId";

    /** WebSocket 握手属性中的 username key */
    public static final String WS_ATTR_USERNAME = "username";

    /** 握手请求中 JWT token 的查询参数名 */
    public static final String WS_TOKEN_PARAM = "token";

    /** 握手请求中 JWT token 的请求头名 */
    public static final String WS_TOKEN_HEADER = "Authorization";
}
