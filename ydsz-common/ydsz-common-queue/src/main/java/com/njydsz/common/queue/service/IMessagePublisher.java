package com.njydsz.common.queue.service;

import java.io.Closeable;
import java.util.List;

import com.njydsz.common.queue.domain.QueueMessage;

/**
 * 消息发布者接口（精简版）。
 *
 * <p>定义消息队列发布者的核心能力：单条发布、结构化发布、批量发布。 组合操作（顺序消息、延迟发布等）由 {@link MessagePublisherHelper} 提供。
 *
 * <p><b>线程安全：</b>实现类应保证 publish 方法的线程安全， 支持多线程并发发布。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * IMessagePublisher publisher = queue.createPublisher("order-events");
 * publisher.publish("simple text");
 * publisher.publish(QueueMessage.of("structured"));
 * publisher.publishBatch(List.of(msg1, msg2));
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see MessagePublisherHelper
 * @see IMessageSubscriber
 */
public interface IMessagePublisher extends Closeable {

  /**
   * 发布字符串消息。
   *
   * <p>将原始字符串包装为 {@link QueueMessage} 后发布。如果传入的字符串 可被反序列化为 QueueMessage 则保留原有属性，否则创建新消息。
   *
   * @param message 消息体字符串（JSON 或纯文本），为 null 时静默忽略
   */
  void publish(String message);

  /**
   * 发布结构化消息。
   *
   * <p>将消息序列化后写入目标队列。消息的 traceId、retryCount、 顺序消息字段等元数据会随消息一起传递。
   *
   * @param message 消息对象，为 null 时静默忽略
   */
  void publish(QueueMessage message);

  /**
   * 批量发布消息。
   *
   * <p>对于支持原生批量的队列实现（如 Redis Stream Pipeline、Kafka batch）， 建议覆盖此方法以利用原生批量 API 提升吞吐量。
   *
   * @param messages 消息列表，为空或 null 时静默忽略
   */
  void publishBatch(List<QueueMessage> messages);

  /**
   * 关闭发布者，释放底层资源。
   *
   * <p>关闭后不可再发布消息。重复调用 close() 应安全（幂等）。
   */
  @Override
  void close();
}
