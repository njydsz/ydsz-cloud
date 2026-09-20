package com.njydsz.common.netty.reliable;

import lombok.Data;

/**
 * ACK 确认消息 — 接收方回复发送方的消息送达确认。
 *
 * <p>消息结构：
 *
 * <ul>
 *   <li>ackOfMsgId：确认的消息 ID（对应 {@link ReliableMessage#getMsgId()}）
 *   <li>ackType：确认类型（ACK / NACK）
 * </ul>
 *
 * <pre>{@code
 * // 接收方收到 ReliableMessage 后回复 ACK
 * if (msg.isRequiresAck()) {
 *     session.send(new AckMessage(msg.getMsgId())); // 确认收到
 * }
 *
 * // 业务异常时回复 NACK（可选）
 * session.send(AckMessage.nack(msg.getMsgId()));
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ReliableMessage
 */
@Data
public class AckMessage {

  /** ACK 确认类型 */
  public enum AckType {
    /** 正常确认（已收到） */
    ACK,
    /** 异常确认（业务层拒绝处理） */
    NACK
  }

  /** 确认的消息 ID */
  private long ackOfMsgId;

  /** 确认类型 */
  private AckType ackType;

  /**
   * 创建 ACK 确认。
   *
   * @param ackOfMsgId 确认的消息 ID
   * @return AckMessage 实例
   */
  public static AckMessage ack(long ackOfMsgId) {
    AckMessage msg = new AckMessage();
    msg.setAckOfMsgId(ackOfMsgId);
    msg.setAckType(AckType.ACK);
    return msg;
  }

  /**
   * 创建 NACK 确认（业务层拒绝）。
   *
   * @param ackOfMsgId 确认的消息 ID
   * @return AckMessage 实例
   */
  public static AckMessage nack(long ackOfMsgId) {
    AckMessage msg = new AckMessage();
    msg.setAckOfMsgId(ackOfMsgId);
    msg.setAckType(AckType.NACK);
    return msg;
  }

  /**
   * 判断是否为 ACK（正常确认）。
   *
   * @return true 表示正常确认
   */
  public boolean isAck() {
    return ackType == AckType.ACK;
  }

  /**
   * 判断是否为 NACK（异常确认）。
   *
   * @return true 表示异常确认
   */
  public boolean isNack() {
    return ackType == AckType.NACK;
  }
}
