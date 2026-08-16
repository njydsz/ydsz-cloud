package com.njydsz.common.queue.group;

/**
 * 消费者组 Rebalance 事件监听器
 *
 * <p>当 Redis Stream 消费组中消费者发生变化（加入/离开/重平衡）时触发回调。 实现此接口可将自定义逻辑绑定到消费组拓扑变化事件。
 *
 * <p><b>典型用途：</b>
 *
 * <ul>
 *   <li>消费者上下线告警
 *   <li>消费组拓扑变化日志记录
 *   <li>动态调整消费策略（如扩缩容时重置本地缓存）
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * rebalanceMonitor.addListener(event -> {
 *     if (event.getEventType() == ConsumerGroupEvent.EventType.CONSUMER_ADDED) {
 *         log.info("新消费者加入: {}", event.getConsumerName());
 *     }
 * });
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FunctionalInterface
public interface ConsumerGroupRebalanceListener {

  /**
   * 当消费组发生变化时调用
   *
   * @param event 消费者组事件
   */
  void onGroupChange(ConsumerGroupEvent event);
}
