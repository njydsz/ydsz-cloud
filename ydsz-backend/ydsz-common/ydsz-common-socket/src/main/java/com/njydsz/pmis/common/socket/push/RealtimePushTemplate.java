package com.njydsz.common.socket.push;

/**
 * 统一实时推送模板接口。
 *
 * <p>定义 WebSocket 实时推送的标准 API，业务服务通过依赖此接口实现消息推送，
 * 无需关心底层 STOMP / Redis Pub/Sub 集群广播 / 降级策略的具体实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface RealtimePushTemplate {

    /**
     * 向指定用户推送通知（集群广播，带优先级）。
     *
     * @param userId   用户 ID
     * @param type     消息类型标签
     * @param payload  消息内容
     * @param priority 消息优先级（P1-4）
     */
    void pushToUser(String userId, String type, Object payload, String priority);

    /**
     * 向指定用户推送通知（集群广播）。
     *
     * @param userId  用户 ID
     * @param type    消息类型（NOTIFICATION/ALERT/DASHBOARD 等）
     * @param payload 消息内容
     */
    void pushToUser(String userId, String type, Object payload);

    /**
     * 向指定用户推送通知，离线时缓存等待补偿。
     *
     * <p>策略：
     * <ul>
     *   <li>用户在线：通过集群广播推送</li>
     *   <li>用户离线：缓存到离线存储，待上线时补偿</li>
     *   <li>在线检查异常：降级为直接推送（保证消息不丢）</li>
     * </ul>
     *
     * @param userId  用户 ID
     * @param type    消息类型标签
     * @param payload 消息内容
     */
    void pushToUserWithOffline(String userId, String type, Object payload);

    /**
     * 向所有在线用户广播消息。
     *
     * @param payload 消息内容
     */
    void broadcast(Object payload);

    /**
     * 向所有在线用户广播消息（带类型标签）。
     *
     * @param type    消息类型标签（如 BROADCAST / ALERT）
     * @param payload 消息内容
     */
    void broadcast(String type, Object payload);

    /**
     * 向指定主题推送消息（如驾驶舱数据刷新）。
     *
     * @param topic   主题路径
     * @param payload 消息内容
     */
    void pushToTopic(String topic, Object payload);

    /**
     * 向指定用户推送通知，带消息 TTL（P3-4）。
     *
     * <p>消息超过 TTL 后自动过期，不再补偿推送。
     *
     * @param userId   用户 ID
     * @param type     消息类型标签
     * @param payload  消息内容
     * @param ttlSeconds 消息 TTL（秒），0 表示不过期
     */
    void pushToUserWithTtl(String userId, String type, Object payload, long ttlSeconds);

    /**
     * 刷新重试队列中到期的消息（P0-4）。
     *
     * <p>定时调用此方法，拉取到期重试消息并重新推送。
     */
    void flushRetryMessages();
}
