package com.njydsz.common.netty.reliable;

import lombok.Data;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 可靠投递消息 — 业务消息的 ACK 协议封装。
 *
 * <p>通过为业务消息添加唯一 {@code msgId} 和 {@code requiresAck} 标记， 实现发送方的超时重传和投递确认。
 *
 * <p>协议设计：
 *
 * <ul>
 *   <li>msgId：long 类型，全局唯一，ACK 消息回带此 ID
 *   <li>requiresAck：boolean 类型，标记是否需要 ACK
 *   <li>body：业务消息对象
 * </ul>
 *
 * <p>当 requiresAck=true 时，发送方启动超时计时器，对端收到后应发送 {@link AckMessage}。 超时未收到 ACK 时触发重传回调（由业务层决定是否重试）。
 *
 * <pre>{@code
 * // 发送方
 * ReliableMessage msg = ReliableMessage.of(loginRequest, true); // 需要 ACK
 * session.send(msg);
 * // 重传逻辑由 ReliableMessageHandler 管理
 *
 * // 接收方（在业务 Handler 中）
 * if (received instanceof ReliableMessage reliable && reliable.isRequiresAck()) {
 *     process(reliable.getBody());
 *     session.send(new AckMessage(reliable.getMsgId())); // 回复 ACK
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see AckMessage
 * @see ReliableMessageHandler
 */
@Data
public class ReliableMessage {

  /** 全局消息 ID 生成器 */
  private static final AtomicLong MSG_ID_GENERATOR = new AtomicLong(0);

  /** 消息唯一 ID */
  private long msgId;

  /** 是否需要 ACK 确认（YDIZ-OOP-006: 布尔字段必须带 is 前缀） */
  private boolean isRequiresAck;

  /** 业务消息体（原始业务对象） */
  private Object body;

  /** 发送时间戳（毫秒） */
  private long sendTime;

  /** 最大重传次数 */
  private int maxRetryCount;

  /**
   * 创建需要 ACK 的可靠投递消息。
   *
   * @param body 业务对象
   * @param requiresAck 是否需要 ACK 确认
   * @return ReliableMessage 实例
   */
  public static ReliableMessage of(Object body, boolean requiresAck) {
    ReliableMessage msg = new ReliableMessage();
    msg.setMsgId(MSG_ID_GENERATOR.incrementAndGet());
    msg.setRequiresAck(requiresAck);
    msg.setBody(body);
    msg.setSendTime(System.currentTimeMillis());
    msg.setMaxRetryCount(3);
    return msg;
  }

  /**
   * 创建需要 ACK 的可靠投递消息（带自定义重试次数）。
   *
   * @param body 业务对象
   * @param requiresAck 是否需要 ACK 确认
   * @param maxRetryCount 最大重传次数（0 = 不重试）
   * @return ReliableMessage 实例
   */
  public static ReliableMessage of(Object body, boolean requiresAck, int maxRetryCount) {
    ReliableMessage msg = of(body, requiresAck);
    msg.setMaxRetryCount(maxRetryCount);
    return msg;
  }

  /**
   * 创建普通消息（不需要 ACK）。
   *
   * @param body 业务对象
   * @return ReliableMessage 实例
   */
  public static ReliableMessage fireAndForget(Object body) {
    return of(body, false);
  }

  /**
   * 判断消息是否需要 ACK。
   *
   * @return true 需要 ACK
   */
  public boolean isRequiresAck() {
    return isRequiresAck;
  }
}
