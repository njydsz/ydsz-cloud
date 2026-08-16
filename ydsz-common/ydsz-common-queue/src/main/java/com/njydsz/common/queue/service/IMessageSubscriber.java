package com.njydsz.common.queue.service;

/**
 * 消息订阅者接口（精简版）。
 *
 * <p>定义消息队列订阅者的核心能力：同步订阅、异步订阅、停止、状态查询。 组合操作（结构化消费、单次消费、状态查询等）由 {@link MessageSubscriberHelper} 提供。
 *
 * <p><b>线程安全：</b>subscribeAsync 启动消费线程后，stop() 应能安全中断。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * IMessageSubscriber subscriber = queue.createSubscriber("order-events");
 *
 * // 异步持续消费
 * subscriber.subscribeAsync(msg -> {
 *     LOG.info("Received: {}", msg.getBody());
 * });
 *
 * // 同步阻塞消费
 * String message = subscriber.subscribe();
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see MessageSubscriberHelper
 * @see IMessagePublisher
 */
public interface IMessageSubscriber {

  /**
   * 同步订阅消息（阻塞等待）。
   *
   * <p>此方法为阻塞调用，将等待直到有一条消息可用。 返回原始消息字符串，需要调用方自行反序列化。
   *
   * @return 消息字符串，无消息或中断时返回 null
   */
  String subscribe();

  /**
   * 异步订阅消息（启动后台消费线程）。
   *
   * <p>启动一个后台线程持续消费消息，每消费一条消息调用一次 handler。 消费失败时是否重试取决于具体实现。
   *
   * @param handler 消息处理回调，不可为 null
   * @return 消费者 ID（用于日志追踪和 stop 操作）
   */
  String subscribeAsync(IMessageHandler handler);

  /**
   * 停止订阅，释放消费线程和底层资源。
   *
   * <p>停止后不可再订阅消息。重复调用 stop() 应安全（幂等）。
   */
  void stop();

  /**
   * 检查订阅者是否正在运行。
   *
   * @return true 如果订阅者处于运行状态
   */
  boolean isRunning();
}
