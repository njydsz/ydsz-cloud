package com.njydsz.common.queue.trace;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息轨迹实体
 *
 * <p>记录消息从生产到消费的完整生命周期轨迹， 包含消息ID、主题、生产者/消费者、状态、时间戳等信息。
 *
 * <p><b>状态流转：</b>
 *
 * <pre>{@code
 * SENT -> DELIVERED -> CONSUMED
 *                  \-> FAILED
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageTrace implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 消息ID */
  private String messageId;

  /** 主题/通道名称 */
  private String topic;

  /** 生产者ID */
  private String producerId;

  /** 消费者ID */
  private String consumerId;

  /**
   * 消息状态
   *
   * <p>SENT: 已发送, DELIVERED: 已投递, CONSUMED: 已消费, FAILED: 消费失败
   */
  private TraceStatus status;

  /**
   * 各阶段时间戳
   *
   * <p>key: 阶段名称(sent/delivered/consumed/failed), value: 时间戳(毫秒)
   */
  @Builder.Default private transient Map<String, Long> timestamps = new HashMap<>(16);