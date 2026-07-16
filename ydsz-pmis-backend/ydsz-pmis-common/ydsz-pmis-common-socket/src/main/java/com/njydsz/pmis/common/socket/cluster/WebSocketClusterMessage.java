package com.njydsz.pmis.common.socket.cluster;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket 集群广播消息（Redis Pub/Sub 载荷）。
 *
 * <p>多实例部署下，推送指令封装为本对象发布到 Redis Channel，所有实例订阅后
 * 各自推送到本地 WebSocket session，从而实现跨节点广播。
 *
 * <p>推送类型：
 * <ul>
 *   <li>{@code USER}：推送到指定用户的个人频道 {@code /topic/user/{userId}/notifications}</li>
 *   <li>{@code BROADCAST}：推送到广播频道 {@code /topic/broadcast}</li>
 *   <li>{@code TOPIC}：推送到指定主题 {@code /topic/{topic}}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketClusterMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 推送类型：USER / BROADCAST / TOPIC */
    private String pushType;

    /** 目标用户 ID（pushType=USER 时使用） */
    private String userId;

    /** 目标主题（pushType=TOPIC 时使用） */
    private String topic;

    /** 消息类型标签（如 NOTIFICATION / ALERT / DASHBOARD） */
    private String type;

    /** 消息内容（JSON 字符串，由推送端序列化） */
    private String payloadJson;

    /** 链路追踪 ID（P1-1） */
    private String traceId;

    /** 灰度标签（P3-3），客户端匹配 tags 后才推送 */
    private List<String> tags;

    /** 消息优先级（P1-4）：URGENT / HIGH / NORMAL / LOW */
    private String priority;

    /**
     * 构造用户推送消息。
     *
     * @param userId      用户 ID
     * @param type        消息类型标签
     * @param payloadJson 消息内容 JSON
     * @return 集群推送消息
     */
    public static WebSocketClusterMessage forUser(String userId, String type, String payloadJson) {
        return new WebSocketClusterMessage("USER", userId, null, type, payloadJson, null, null, null);
    }

    /**
     * 构造广播消息。
     *
     * @param type        消息类型标签
     * @param payloadJson 消息内容 JSON
     * @return 集群推送消息
     */
    public static WebSocketClusterMessage forBroadcast(String type, String payloadJson) {
        return new WebSocketClusterMessage("BROADCAST", null, null, type, payloadJson, null, null, null);
    }

    /**
     * 构造主题推送消息。
     *
     * @param topic       主题
     * @param payloadJson 消息内容 JSON
     * @return 集群推送消息
     */
    public static WebSocketClusterMessage forTopic(String topic, String payloadJson) {
        return new WebSocketClusterMessage("TOPIC", null, topic, null, payloadJson, null, null, null);
    }
}
