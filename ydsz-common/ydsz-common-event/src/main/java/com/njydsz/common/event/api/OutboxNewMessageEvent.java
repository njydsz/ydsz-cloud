package com.njydsz.common.event.api;

import org.springframework.context.ApplicationEvent;

/**
 * Outbox 新消息事件
 *
 * <p>当 OutboxService 检测到新的 PENDING 消息写入并提交成功后发布此事件， 供 {@link
 * com.njydsz.common.event.processor.OutboxProcessor} 订阅以触发即时轮询（推模式）。
 *
 * <p>结合原有的定时轮询（拉模式），实现自适应的推拉结合策略：
 *
 * <ul>
 *   <li>定时轮询（兜底）：确保即使事件丢失或订阅器未启动，消息也能被投递
 *   <li>事件驱动（提速）：新消息写入后立即触发一次轮询，将投递延迟从秒级降低到毫秒级
 * </ul>
 *
 * <p><b>设计考量：</b>使用 Spring 原生 ApplicationEvent + ReentrantLock 防止重复投递， 避免引入额外的消息中间件或
 * Thread.signal 机制，保持轻量级。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public class OutboxNewMessageEvent extends ApplicationEvent {

  /** 新写入的消息 ID */
  private final String messageId;

  /**
   * 创建 Outbox 新消息事件
   *
   * @param source 事件源（通常为 OutboxService）
   * @param messageId 新写入的消息 ID
   */
  public OutboxNewMessageEvent(Object source, String messageId) {
    super(source);
    this.messageId = messageId;
  }

  /**
   * 获取新消息 ID
   *
   * @return 消息 ID
   */
  public String getMessageId() {
    return messageId;
  }
}
