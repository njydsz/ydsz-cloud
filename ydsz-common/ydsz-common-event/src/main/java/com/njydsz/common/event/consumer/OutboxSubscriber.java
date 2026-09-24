package com.njydsz.common.event.consumer;

import com.njydsz.common.event.model.OutboxMessage;

/**
 * Outbox 消费端 SPI 接口（YDIZ-EVENT-002）。
 *
 * <p>业务模块实现此接口以订阅 Outbox 消息，由 {@link OutboxSubscriberDispatcher} 统一分发——
 * 仅当 {@link #getTopic()} 与消息 {@link OutboxMessage#getTopic()} 匹配时触发 {@link #onMessage(OutboxMessage)}。
 *
 * <p>设计要点：
 *
 * <ul>
 *   <li>单一 topic：一个订阅者仅处理一种主题，多主题需注册多个 Bean 或使用 {@code topic="*"}
 *       通配（通配订阅者需自行在 {@link #onMessage(OutboxMessage)} 内过滤）
 *   <li>幂等保证：幂等逻辑由业务侧通过 {@link OutboxIdempotentConsumer} 注解实现，不在 SPI 层约束
 *   <li>异常处理：抛出异常时由分发器记录日志并向上传播（事务内由 OutboxService 兜底，事务外由分发器兜底）
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * &#64;Component
 * public class WebhookOutboxSubscriber implements OutboxSubscriber {
 *
 *   &#64;Override
 *   public String getTopic() {
 *     return "webhook";
 *   }
 *
 *   &#64;Override
 *   public void onMessage(OutboxMessage message) {
 *     // 处理 webhook 事件逻辑
 *   }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.24
 * @see OutboxSubscriberDispatcher 分发器
 * @see OutboxIdempotentConsumer 幂等消费注解
 */
public interface OutboxSubscriber {

  /**
   * 返回订阅的主题标识。
   *
   * <p>与 {@link OutboxMessage#getTopic()} 精确匹配时触发 {@link #onMessage(OutboxMessage)}。
   * 返回 {@code "*"} 表示订阅所有主题（业务侧自行过滤）。
   *
   * @return 主题标识（非 null，非 blank）
   */
  String getTopic();

  /**
   * 处理 Outbox 消息。
   *
   * <p><b>约定：</b>
   * <ul>
   *   <li>方法在 Outbox 事务已提交后调用（消息已持久化），无需再入 Outbox</li>
   *   <li>幂等消费通过 {@link OutboxIdempotentConsumer} 注解实现，不强制在方法体内去重</li>
   *   <li>抛出运行时异常由分发器记录 error 日志（不会导致 Outbox 重投递，仅作异常传播）</li>
   * </ul>
   *
   * @param message Outbox 消息（非 null）
   */
  void onMessage(OutboxMessage message);
}
