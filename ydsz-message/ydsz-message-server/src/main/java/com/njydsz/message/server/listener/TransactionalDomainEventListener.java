package com.njydsz.message.server.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.njydsz.message.domain.event.MessageRecalledEvent;
import com.njydsz.message.domain.event.MessageSentEvent;

/**
 * 事务性领域事件监听器 — 在事务提交后处理消息领域事件。
 *
 * <p>使用 {@link TransactionalEventListener} 替代普通 {@code @EventListener}, 确保事件处理逻辑在数据库事务成功提交后才执行,
 * 避免事务回滚后事件已被处理的不一致场景。
 *
 * <p><b>监听事件：</b>
 * <ul>
 *   <li>{@link MessageSentEvent} — 消息成功投递到通道后触发
 *   <li>{@link MessageRecalledEvent} — 消息撤回操作完成后触发
 * </ul>
 *
 * <p><b>设计定位：</b>作为 Outbox 模式下事务提交后的回调钩子, 后续可扩展为更新统计表、触发 Webhook、写入审计日志等。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.message.server.event.OutboxDomainEventPublisher
 */
@Slf4j
@Component
public class TransactionalDomainEventListener {

  /**
   * 事务提交后处理消息发送成功事件。
   *
   * <p>此时消息已成功落库且事务已提交, 可安全执行后续副作用(更新统计、发送 Webhook 等)。
   *
   * @param event 消息发送成功事件
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onMessageSent(MessageSentEvent event) {
    log.info(
        "[TxEventListener] 消息发送成功事件, msgId={} channel={} bizType={} elapsedMs={}",
        event.getMessageId(),
        event.getChannel(),
        event.getBizType(),
        event.getElapsedMs());
  }

  /**
   * 事务提交后处理消息撤回事件。
   *
   * <p>此时消息撤回状态已持久化且事务已提交, 可安全通知下游系统。
   *
   * @param event 消息撤回事件
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onMessageRecalled(MessageRecalledEvent event) {
    log.info(
        "[TxEventListener] 消息撤回事件, msgId={} channel={} recallSucceeded={} failureReason={}",
        event.getMessageId(),
        event.getChannel(),
        event.isRecallSucceeded(),
        event.getFailureReason());
  }
}
