package com.njydsz.common.queue.enums;

/**
 * 消息队列类型枚举。
 *
 * <p>定义系统支持的消息队列实现类型，包括 Redis 系列（List、Stream、PubSub） 和主流中间件（RabbitMQ、RocketMQ、Kafka）。
 *
 * <p><b>推荐选型：</b>
 *
 * <ul>
 *   <li>{@link #STREAM}（推荐）：支持消费组、消息确认、持久化等高级特性
 *   <li>{@link #KAFKA}（推荐）：高吞吐量分布式消息系统，适合日志收集和实时流处理
 *   <li>{@link #ROCKET}：支持事务消息和顺序消息
 * </ul>
 *
 * <p><b>已废弃（已移除）：</b>
 *
 * <ul>
 *   <li>LIST：已移除，使用 STREAM 替代
 *   <li>PUBSUB：已移除，使用 STREAM 替代
 *   <li>RABBIT：建议使用 KAFKA 或 ROCKET 替代
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum QueueType {

  /**
   * Redis Stream 队列。
   *
   * <p>支持消费组、消息确认、持久化等高级特性，是 Redis 队列的推荐实现。
   */
  STREAM("stream"),

  /**
   * RabbitMQ。
   *
   * <p>实现了 AMQP 协议的消息队列，支持丰富的路由功能。
   */
  RABBIT("rabbit"),

  /**
   * RocketMQ。
   *
   * <p>支持事务消息和顺序消息。
   */
  ROCKET("rocket"),

  /**
   * Kafka（推荐）。
   *
   * <p>高吞吐量分布式消息系统，适合日志收集和实时流处理。
   */
  KAFKA("kafka");

  private final String value;

  QueueType(String value) {
    this.value = value;
  }

  /**
   * 获取队列类型的字符串表示。
   *
   * @return 队列类型值
   */
  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return value;
  }

  /**
   * 根据字符串值反序列化为枚举。
   *
   * @param value 队列类型字符串
   * @return 对应的枚举值，value 为 null 时返回 null
   * @throws IllegalArgumentException 如果值不匹配任何枚举
   */
  public static QueueType fromValue(String value) {
    if (value == null) {
      return null;
    }
    for (QueueType type : values()) {
      if (type.value.equalsIgnoreCase(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的队列类型: " + value);
  }
}
