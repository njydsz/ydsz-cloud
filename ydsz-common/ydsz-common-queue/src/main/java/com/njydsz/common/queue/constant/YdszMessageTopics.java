package com.njydsz.common.queue.constant;

/**
 * 消息中心通道常量定义
 *
 * <p>统一定义消息中心（ydsz-message）使用的所有 MQ Topic 和消费组常量。 遵循通道命名规范：{@code ydsz:message:<用途>}，其中：
 *
 * <ul>
 *   <li>消息 Topic：{@code ydsz:message:topic}
 *   <li>批量消息 Topic：{@code ydsz:message:topic-batch}
 *   <li>死信队列 Topic：{@code ydsz:message:dlq}
 * </ul>
 *
 * <p>Topic 名称全局唯一，作为消息中心与 RocketMQ/Kafka 等 MQ 中间件的契约。 如需新增通道，请同步更新本类和对应业务模块的配置文件。
 *
 * <p><b>命名规范</b>：
 *
 * <ul>
 *   <li>Topic：{@code ydsz:{module}:{topic-name}}
 *   <li>消费组：{@code ydsz:{module}:group-{用途}}
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class YdszMessageTopics {

  private YdszMessageTopics() {}

  /**
   * 消息投递 Topic（单条消息）
   *
   * <p>格式：{@code ydsz:message:topic}，支持带优先级 Tag（URGENT/HIGH/NORMAL/LOW）， destination 格式为 {@code
   * topic:tag}。
   */
  public static final String TOPIC_MESSAGE = "ydsz:message:topic";

  /** 消息投递消费组 */
  public static final String GROUP_MESSAGE = "ydsz:message:group";

  /**
   * 批量消息投递 Topic
   *
   * <p>消息体为 JSON 数组格式：{@code [MessageRequest, MessageRequest, ...]}
   */
  public static final String TOPIC_MESSAGE_BATCH = "ydsz:message:topic-batch";

  /** 批量消息投递消费组 */
  public static final String GROUP_MESSAGE_BATCH = "ydsz:message:group-batch";

  /**
   * 死信队列 Topic
   *
   * <p>重试耗尽（maxReconsumeTimes）的消息将路由到此 Topic， 由 {@code MessageDlqConsumer} 消费并落库 status=DEAD。
   */
  public static final String DLQ_MESSAGE = "ydsz:message:dlq";

  /** 死信队列消费组 */
  public static final String GROUP_DLQ_MESSAGE = "ydsz:message:group-dlq";
}
