package com.njydsz.common.queue.scheduler;

import lombok.Data;

/**
 * 死信消息条目（结构化元数据）
 *
 * <p>扩展自原本 {@code DeadLetterQueueServiceImpl.DeadLetterMessage} 私有内部类，增加了：
 *
 * <ul>
 *   <li>{@link #engineType}：投递目标引擎类型（用于选择回放策略）
 *   <li>{@link #consumerGroup}：消费组标识（RocketMQ 回溯消费时需要）
 *   <li>{@link #partition} / {@link #offset}：Kafka 分区 + 偏移量（{@code consumer.seek} 语义回放时使用）
 *   <li>{@link #consumeTimestampMs}：RocketMQ 消费时间戳毫秒（{@code setConsumeTimestamp} 语义回放时使用）
 * </ul>
 *
 * <p>这些字段由 {@link
 * com.njydsz.common.queue.service.impl.DeadLetterQueueServiceImpl#sendToDeadLetter} 在投递时根据
 * 当前上下文填充；普通场景下无需设置，保持向后兼容。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
@Data
public class DeadLetterEntry {

  /** 消息唯一 ID（traceId） */
  private String messageId;

  /** 原始消息体（JSON 字符串） */
  private String messageBody;

  /** 失败原因（截断后存储） */
  private String failureReason;

  /** 进入死信队列时间（yyyy-MM-dd HH:mm:ss.SSS 格式） */
  private String enterTime;

  /** 已重试次数 */
  private int retryCount;

  /** 目标投递引擎类型（取值来自 {@link
   *  com.njydsz.common.queue.enums.QueueType#getValue()}，可为 null 表示未知/旧数据） */
  private String engineType;

  /** 消息主题（topic / channel 名称） */
  private String topic;

  /** 消费组标识（RocketMQ 回溯时使用，可为 null） */
  private String consumerGroup;

  /** Kafka 分区号（{@code consumer.seek} 时使用，可为 null） */
  private Integer partition;

  /** Kafka 偏移量（{@code consumer.seek} 时使用，可为 null） */
  private Long offset;

  /** RocketMQ 消费者起始时间戳毫秒（{@code setConsumeTimestamp} 时使用，可为 null） */
  private Long consumeTimestampMs;
}
