package com.njydsz.common.queue.service;

import com.njydsz.common.queue.domain.QueueMessage;

/**
 * 消息处理器接口
 *
 * <p>定义消息消费的业务逻辑处理函数式接口。 当订阅者从队列中获取到消息时，会调用此处理器执行具体的业务操作。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // Lambda 表达式方式
 * subscriber.subscribeAsync(message -> {
 *     String body = message.getBody();
 *     LOG.info("Received: {}", body);
 * });
 *
 * // 方法引用方式
 * subscriber.subscribeAsync(this::processMessage);
 *
 * // 完整实现方式
 * subscriber.subscribeAsync(new IMessageHandler() {
 *     @Override
 *     public void onMessage(QueueMessage message) throws Exception {
 *         // 业务处理逻辑
 *         LOG.info("Message: {}", message.getBody());
 *     }
 * });
 * }</pre>
 *
 * <p><b>最佳实践：</b>
 *
 * <ul>
 *   <li>处理器应该保持轻量级，避免执行耗时的操作
 *   <li>建议使用 try-catch 包裹业务逻辑，以便记录错误日志
 *   <li>对于可能失败的操作，建议记录 traceId 便于问题追踪
 *   <li>避免在处理器中抛出检查异常，建议转换为运行时异常
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@FunctionalInterface
public interface IMessageHandler {

  /**
   * 处理消息
   *
   * <p>当订阅者获取到消息时会调用此方法。 具体的消费逻辑（如数据持久化、业务计算等）在此方法中实现。
   *
   * <p><b>异常处理说明：</b> 如果处理过程中抛出异常：
   *
   * <ul>
   *   <li>对于 Redis List 模式：消息不会被删除，会被重新消费
   *   <li>对于 Redis Stream 模式：会根据重试策略决定是否重试或进入死信队列
   *   <li>对于 Redis PubSub 模式：异常仅被记录，不会影响其他消息处理
   * </ul>
   *
   * @param message 接收到的消息对象，包含消息体、头部信息、追踪ID等
   * @throws Throwable 如果处理消息时发生错误
   */
  void onMessage(QueueMessage message) throws Throwable;
}
