package com.njydsz.common.socket.push;

/**
 * 推送上下文（P1-6）。
 *
 * <p>封装一次推送请求的全维度上下文，供 {@link com.njydsz.common.socket.filter.MessageFilter}
 * 链做细粒度过滤决策（如基于业务类型、优先级、消息体内容、用户属性等）。
 *
 * <p>相比仅传递 {@code (userId, pushType, payloadHash)} 的旧接口，PushContext 携带原始 payload
 * 对象、业务类型标签、messageId、优先级等丰富信息， 使过滤器能实现更精细的策略（如：按业务类型限流、按优先级豁免频率限制等）。
 *
 * @param userId 目标用户 ID（广播/TOPIC 时为 null）
 * @param pushType 推送类型（USER / BROADCAST / TOPIC）
 * @param type 业务类型标签（如 NOTIFICATION / ALERT / DASHBOARD）
 * @param payload 原始消息负载对象（未序列化）
 * @param messageId 业务级消息唯一 ID（为空时由框架自动生成）
 * @param priority 消息优先级（MessagePriority 枚举名）
 * @param timestamp 推送发起时间戳（毫秒）
 * @param topic 主题路径（仅 TOPIC 类型时非 null）
 * @author ydsz-team
 * @since 1.0.0
 */
public record PushContext(
    String userId,
    String pushType,
    String type,
    Object payload,
    String messageId,
    String priority,
    long timestamp,
    String topic) {

  /**
   * 创建用户单播上下文。
   *
   * @param userId 用户 ID
   * @param type 业务类型标签
   * @param payload 消息负载
   * @param priority 优先级
   * @return PushContext
   */
  public static PushContext forUser(String userId, String type, Object payload, String priority) {
    return new PushContext(
        userId, "USER", type, payload, null, priority, System.currentTimeMillis(), null);
  }

  /**
   * 创建用户单播上下文（带消息 ID）。
   *
   * @param userId 用户 ID
   * @param type 业务类型标签
   * @param payload 消息负载
   * @param messageId 业务级消息 ID
   * @param priority 优先级
   * @return PushContext
   */
  public static PushContext forUser(
      String userId, String type, Object payload, String messageId, String priority) {
    return new PushContext(
        userId, "USER", type, payload, messageId, priority, System.currentTimeMillis(), null);
  }

  /**
   * 创建广播上下文。
   *
   * @param type 业务类型标签
   * @param payload 消息负载
   * @param priority 优先级
   * @return PushContext
   */
  public static PushContext forBroadcast(String type, Object payload, String priority) {
    return new PushContext(
        null, "BROADCAST", type, payload, null, priority, System.currentTimeMillis(), null);
  }

  /**
   * 创建主题推送上下文。
   *
   * @param topic 主题路径
   * @param type 业务类型标签
   * @param payload 消息负载
   * @param priority 优先级
   * @return PushContext
   */
  public static PushContext forTopic(String topic, String type, Object payload, String priority) {
    return new PushContext(
        null, "TOPIC", type, payload, null, priority, System.currentTimeMillis(), topic);
  }
}
