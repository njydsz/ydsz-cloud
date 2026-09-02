package com.njydsz.common.queue.delayed;

import java.util.concurrent.TimeUnit;

import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.service.IMessagePublisher;

/**
 * 延时消息发送器接口
 *
 * <p>定义延时消息的统一语义抽象，屏蔽底层 MQ 实现差异。 不同 MQ 对延时消息的支持能力不同：
 *
 * <ul>
 *   <li>RocketMQ：原生支持 18 个延迟级别
 *   <li>Kafka：需配合外部延时队列实现
 *   <li>Redis：需配合 ZSet + 轮询实现
 * </ul>
 *
 * <p>本接口提供统一的延时消息发送能力，实现类负责适配具体 MQ 的特性。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 发送延迟 30 秒的消息
 * delayedSender.publishDelayed(QueueMessage.of("hello"), 30, TimeUnit.SECONDS);
 *
 * // 使用 Builder 模式构建并发送
 * delayedSender.publish(
 *     DelayedMessage.of(QueueMessage.of("hello"))
 *         .delay(60, TimeUnit.SECONDS)
 * );
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface DelayedMessageSender {

  /**
   * 发送延时消息
   *
   * <p>消息将在指定的延迟时间后被投递到目标队列。 如果底层 MQ 不支持原生延时，实现类应自动降级到本地定时器方案。
   *
   * @param message 待发送的消息
   * @param delay 延迟时间数值
   * @param timeUnit 延迟时间单位
   */
  void publishDelayed(QueueMessage message, long delay, TimeUnit timeUnit);

  /**
   * 发送延时消息（使用 DelaySpec 指定延迟参数）
   *
   * @param message 待发送的消息
   * @param delaySpec 延迟参数规范
   */
  void publishDelayed(QueueMessage message, DelaySpec delaySpec);

  /**
   * 取消尚未投递的延时消息
   *
   * <p>仅在定时器方案下有效，原生延时 MQ 通常不支持取消。
   *
   * @param messageId 消息 ID
   * @return true 表示成功取消，false 表示消息已投递或不存在
   */
  boolean cancelDelayed(String messageId);

  /**
   * 获取关联的消息发布者
   *
   * @return 底层消息发布者
   */
  IMessagePublisher getPublisher();

  /** 关闭延时发送器，释放定时器资源 */
  void close();
}
