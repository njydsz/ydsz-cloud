package com.njydsz.common.event.consumer;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.EventListener;

import com.njydsz.common.event.model.OutboxMessage;

/**
 * Outbox 订阅者分发器（YDIZ-EVENT-002）。
 *
 * <p>订阅 Spring {@link OutboxMessage} 事件，按 {@link OutboxMessage#getTopic()} 路由到匹配的
 * {@link OutboxSubscriber} 实现 Bean。支持精确匹配和通配（{@code topic="*"}）。
 *
 * <p>分发规则：
 *
 * <ul>
 *   <li>topic 精确匹配优先（订阅者 {@code getTopic()} 等于消息 {@code getTopic()}）
 *   <li>通配订阅者兜底（订阅者 {@code getTopic()} 为 {@code "*"}，在精确匹配无结果时触发）
 *   <li>无匹配订阅者时记录 DEBUG 日志（非异常，允许 topic 无消费方）
 *   <li>多个精确匹配订阅者全部触发（广播语义，顺序按 {@code @Order} 排序）
 * </ul>
 *
 * <p><b>YDIZ-EVENT-002 告知：</b>业务模块实现 {@link OutboxSubscriber} 即可订阅 Outbox 事件，
 * 无需自行编写 {@code @EventListener} 方法。
 *
 * @author ydsz-team
 * @since 26.09.24
 * @see OutboxSubscriber 订阅者 SPI 接口
 */
public class OutboxSubscriberDispatcher implements ApplicationListener<OutboxMessage> {

  private static final Logger LOG = LoggerFactory.getLogger(OutboxSubscriberDispatcher.class);

  private static final String WILDCARD_TOPIC = "*";

  /** topic → 订阅者列表索引（精确匹配） */
  private final Map<String, List<OutboxSubscriber>> exactSubscribers;

  /** 通配订阅者列表 */
  private final List<OutboxSubscriber> wildcardSubscribers;

  /**
   * 构造分发器并索引订阅者。
   *
   * @param subscribers 容器中所有 OutboxSubscriber 实现 Bean
   */
  public OutboxSubscriberDispatcher(List<OutboxSubscriber> subscribers) {
    this.exactSubscribers = subscribers.stream()
        .filter(s -> !WILDCARD_TOPIC.equals(s.getTopic()))
        .collect(Collectors.groupingBy(OutboxSubscriber::getTopic));
    this.wildcardSubscribers = subscribers.stream()
        .filter(s -> WILDCARD_TOPIC.equals(s.getTopic()))
        .toList();
    LOG.info("OutboxSubscriberDispatcher 初始化完成: 精确订阅 topic={}, 通配订阅者数={}",
        exactSubscribers.keySet(), wildcardSubscribers.size());
  }

  /**
   * 监听 OutboxMessage 事件并分发。
   *
   * <p>由 Spring 事件机制触发（{@link EventListener} 语义）。
   *
   * @param message Outbox 消息事件
   */
  @Override
  @EventListener
  public void onApplicationEvent(OutboxMessage message) {
    if (message == null || message.getTopic() == null) {
      LOG.warn("[OutboxDispatcher] 收到无效消息: topic={}", message != null ? message.getTopic() : "null");
      return;
    }

    String topic = message.getTopic();
    List<OutboxSubscriber> targets = exactSubscribers.get(topic);

    if (targets != null && !targets.isEmpty()) {
      for (OutboxSubscriber subscriber : targets) {
        invokeSubscriber(subscriber, message);
      }
      return;
    }

    // 无精确匹配，尝试通配订阅者
    if (!wildcardSubscribers.isEmpty()) {
      for (OutboxSubscriber subscriber : wildcardSubscribers) {
        invokeSubscriber(subscriber, message);
      }
      return;
    }

    LOG.debug("[OutboxDispatcher] topic={} 无匹配订阅者", topic);
  }

  /**
   * 调用单个订阅者并处理异常。
   *
   * @param subscriber 目标订阅者
   * @param message Outbox 消息
   */
  private void invokeSubscriber(OutboxSubscriber subscriber, OutboxMessage message) {
    String subscriberName = subscriber.getClass().getSimpleName();
    try {
      LOG.debug("[OutboxDispatcher] 分发消息 topic={} → {}", message.getTopic(), subscriberName);
      subscriber.onMessage(message);
    } catch (RuntimeException e) {
      LOG.error("[OutboxDispatcher] 订阅者 {} 消费异常: topic={} eventKey={} reason={}",
          subscriberName, message.getTopic(), message.getId(), e.getMessage(), e);
      throw e;
    }
  }
}
