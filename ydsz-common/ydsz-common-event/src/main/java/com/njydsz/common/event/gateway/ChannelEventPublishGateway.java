package com.njydsz.common.event.gateway;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.event.model.OutboxMessage;

/**
 * 通道感知的投递网关装饰器（O-3）
 *
 * <p>根据 OutboxMessage 的 eventType 查找通道注册表，确定投递目标 topic， 将 {@code _channel} 和
 * {@code _target_topic} 信息写入扩展上下文中，委托底层网关投递。
 *
 * <p><b>路由逻辑：</b>
 *
 * <ol>
 *   <li>根据 message.getEventType() 查找 {@link EventChannelRegistry} 确定通道配置
 *   <li>将通道名和目标 topic 写入 OutboxMessage 的 metadata 字段（如存在）
 *   <li>委托给底层网关（RocketMQ / Kafka）统一投递，由网关实现根据 metadata 路由到对应 topic
 * </ol>
 *
 * <p>如果消息未匹配任何通道，则使用默认通道（{@code ydsz-outbox-events}）。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public class ChannelEventPublishGateway implements EventPublishGateway {

  /** 日志实例 */
  private static final Logger LOG = LoggerFactory.getLogger(ChannelEventPublishGateway.class);

  /** 底层网关委托 */
  private final EventPublishGateway delegate;

  /** 通道注册表 */
  private final EventChannelRegistry channelRegistry;

  /**
   * 构造通道感知网关
   *
   * @param delegate 底层网关委托（RocketMQ / Kafka / Noop）
   * @param channelRegistry 通道注册表
   */
  public ChannelEventPublishGateway(EventPublishGateway delegate,
      EventChannelRegistry channelRegistry) {
    this.delegate = delegate;
    this.channelRegistry = channelRegistry;
  }

  @Override
  public boolean publish(OutboxMessage message) throws Throwable {
    EventChannelDefinition channel = channelRegistry.resolveChannel(message.getEventType());
    String targetTopic = channel != null ? channel.getTopic() : "ydsz-outbox-events";

    // 记录通道路由日志
    LOG.debug("Routing event to channel={}, topic={}, eventType={}",
        channel != null ? channel.getChannelName() : "default",
        targetTopic, message.getEventType());

    // 委托底层网关投递（底层网关实现可根据需要读取 contextHolder 中的 targetTopic）
    ChannelContextHolder.setTargetTopic(targetTopic);
    try {
      return delegate.publish(message);
    } finally {
      ChannelContextHolder.clear();
    }
  }

  @Override
  public List<Boolean> publishBatch(List<OutboxMessage> messages) throws Throwable {
    List<Boolean> results = new java.util.ArrayList<>(messages.size());
    for (OutboxMessage message : messages) {
      try {
        results.add(publish(message));
      } catch (Throwable e) {
        LOG.warn("Batch publish error for message {}: {}", message.getId(), e.getMessage());
        results.add(false);
      }
    }
    return results;
  }

  /**
   * 通道上下文持有器（ThreadLocal）
   *
   * <p>临时存放当前线程的目标 topic，供底层网关实现（如 KafkaEventPublishGateway）读取以路由到不同 topic。
   */
  public static class ChannelContextHolder {

    private static final ThreadLocal<String> TARGET_TOPIC = new ThreadLocal<>();

    public static void setTargetTopic(String topic) {
      TARGET_TOPIC.set(topic);
    }

    public static String getTargetTopic() {
      return TARGET_TOPIC.get();
    }

    public static void clear() {
      TARGET_TOPIC.remove();
    }
  }
}
