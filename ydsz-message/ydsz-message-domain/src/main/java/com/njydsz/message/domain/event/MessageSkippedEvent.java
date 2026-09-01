package com.njydsz.message.domain.event;

import java.io.Serial;

/**
 * 消息被拦截（跳过）领域事件。
 *
 * <p>在消息被去重、限流、DND 等规则拦截时发布，携带拦截原因与处理阶段。 用于审计与风控分析。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class MessageSkippedEvent extends MessageDomainEvent {

  @Serial private static final long serialVersionUID = 1L;

  /** 拦截阶段（DEDUP / LIMIT / DND / PREFERENCE 等） */
  private final String skipReason;

  /** 通道 */
  private final String channel;

  /** 业务类型 */
  private final String bizType;

  /**
   * 构造消息被拦截事件。
   *
   * @param tenantId 租户 ID
   * @param messageId 消息 ID（可为 null，拦截发生在日志入库前）
   * @param skipReason 拦截原因
   * @param channel 通道
   * @param bizType 业务类型
   */
  public MessageSkippedEvent(
      String tenantId, String messageId, String skipReason, String channel, String bizType) {
    super(tenantId, messageId, null);
    this.skipReason = skipReason;
    this.channel = channel;
    this.bizType = bizType;
  }

  public String getSkipReason() {
    return skipReason;
  }

  public String getChannel() {
    return channel;
  }

  public String getBizType() {
    return bizType;
  }
}
