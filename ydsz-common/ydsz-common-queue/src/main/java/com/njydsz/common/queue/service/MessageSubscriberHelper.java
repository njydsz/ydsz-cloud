package com.njydsz.common.queue.service;

import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.queue.domain.QueueMessage;

/**
 * 订阅者辅助工具类。
 *
 * <p>提供订阅者的组合操作（结构化消费、单次消费等）， 解耦自 {@link IMessageSubscriber} 接口，实现接口扁平化。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * IMessageSubscriber subscriber = queue.createSubscriber("topic");
 *
 * // 消费单条结构化消息
 * QueueMessage msg = MessageSubscriberHelper.subscribeMessage(subscriber);
 *
 * // 同步消费并处理单条消息
 * String traceId = MessageSubscriberHelper.subscribeOnce(subscriber, handler);
 *
 * // 异步消费
 * String consumerId = MessageSubscriberHelper.subscribeAsync(subscriber, handler);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see IMessageSubscriber
 */
public final class MessageSubscriberHelper {

  private MessageSubscriberHelper() {
    throw new UnsupportedOperationException("Utility class should not be instantiated");
  }

  /**
   * 同步消费消息（返回结构化消息）。
   *
   * <p>此方法为阻塞调用，将等待直到有一条消息可用。 返回 {@link QueueMessage} 对象，包含消息体及元数据。
   *
   * @param subscriber 订阅者实例，不可为 null
   * @return 消费到的消息对象，无消息时返回 null
   */
  public static QueueMessage subscribeMessage(IMessageSubscriber subscriber) {
    if (subscriber == null) {
      return null;
    }
    String message = subscriber.subscribe();
    return message != null ? QueueMessage.fromPayload(message) : null;
  }

  /**
   * 同步消费并处理单条消息（一次性消费）。
   *
   * <p>此方法消费一条消息并立即调用 handler 处理。 如果 handler 处理失败，异常会向上抛出，消息可能被重新投递。
   *
   * <p><b>注意：</b>此方法只消费一条消息，不适合持续监听场景。 如需持续消费，请使用 {@link #subscribeAsync(IMessageSubscriber,
   * IMessageHandler)}。
   *
   * @param subscriber 订阅者实例，不可为 null
   * @param handler 消息处理器，不可为 null
   * @return 消息 traceId，消费失败或无消息时返回 null
   * @throws RuntimeException 当 handler 处理失败时抛出
   */
  public static String subscribeOnce(IMessageSubscriber subscriber, IMessageHandler handler) {
    if (subscriber == null || handler == null) {
      return null;
    }
    QueueMessage message = subscribeMessage(subscriber);
    if (message == null) {
      return null;
    }
    try {
      handler.onMessage(message);
      return message.getTraceId();
    } catch (Exception e) {
      throw SysException.builder().message("消息处理失败: " + e.getMessage()).cause(e).build();
    }
  }

  /**
   * 异步订阅消息并返回消费者 ID。
   *
   * <p>等价于 {@link IMessageSubscriber#subscribeAsync(IMessageHandler)}， 提供更一致的静态方法调用风格。
   *
   * @param subscriber 订阅者实例，不可为 null
   * @param handler 消息处理回调，不可为 null
   * @return 消费者 ID，参数为 null 时返回 null
   */
  public static String subscribeAsync(IMessageSubscriber subscriber, IMessageHandler handler) {
    if (subscriber == null || handler == null) {
      return null;
    }
    return subscriber.subscribeAsync(handler);
  }
}
