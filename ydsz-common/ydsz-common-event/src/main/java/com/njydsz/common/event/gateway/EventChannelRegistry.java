package com.njydsz.common.event.gateway;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事件通道注册表（O-3）
 *
 * <p>管理 {@link EventChannelDefinition} 的全局注册表：
 *
 * <ul>
 *   <li>注册通道定义（如 {@code register(new EventChannelDefinition("order-events", ...))}）
 *   <li>根据事件类型查找对应通道（通过事件类型 {@code eventType} 查找， O(1) 复杂度）
 * </ul>
 *
 * <p><b>使用场景：</b>
 *
 * <ol>
 *   <li>应用启动时：业务模块通过 {@link #register(EventChannelDefinition)} 注册通道配置
 *   <li>Outbox 写入时：OutboxService 调用 {@link #resolveChannel(String)} 根据 eventType 查找通道
 *   <li>获取到通道后：将 {@code _channel} 标签写入 metadata，EventPublishGateway 根据 _channel 路由到对应 topic
 * </ol>
 *
 * <p><b>设计参考：</b>Spring Cloud Stream 的 {@code BinderFactory} + {@code BindingServiceProperties}，
 * * 但此处为单 JVM 的内存注册表，不涉及 Spring Cloud Stream 重量级启动逻辑。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public class EventChannelRegistry {

  /** eventType → 通道定义的映射表 */
  private final Map<String, EventChannelDefinition> eventChannelMap = new ConcurrentHashMap<>(32);

  /** 通道名称 → 通道定义的映射表（去重） */
  private final Map<String, EventChannelDefinition> channelDefinitionMap = new ConcurrentHashMap<>(32);

  /** 默认通道（未匹配到 eventType 时使用） */
  private volatile EventChannelDefinition defaultChannel;

  /**
   * 注册事件通道定义
   *
   * @param definition 通道定义（不可为 null）
   */
  public void register(EventChannelDefinition definition) {
    if (definition == null) {
      return;
    }
    channelDefinitionMap.put(definition.getChannelName(), definition);
  }

  /**
   * 注册 eventType → 通道的映射
   *
   * <p>将事件类型与通道关联，在 OutboxService 写入时根据事件类型 lookup。
   *
   * @param eventType 事件类型（如 "OrderCreated"）
   * @param channelName 通道名称
   */
  public void registerEventType(String eventType, String channelName) {
    EventChannelDefinition definition = channelDefinitionMap.get(channelName);
    if (definition != null) {
      eventChannelMap.put(eventType, definition);
    }
  }

  /**
   * 根据事件类型解析通道配置
   *
   * @param eventType 事件类型
   * @return 通道定义，未找到时返回默认通道
   */
  public EventChannelDefinition resolveChannel(String eventType) {
    EventChannelDefinition definition = eventChannelMap.get(eventType);
    return definition != null ? definition : defaultChannel;
  }

  /**
   * 获取通道定义
   *
   * @param channelName 通道名称
   * @return 通道定义，未找到时返回 null
   */
  public EventChannelDefinition getChannel(String channelName) {
    return channelDefinitionMap.get(channelName);
  }

  /**
   * 获取所有已注册的通道定义
   *
   * @return 通道定义集合
   */
  public Collection<EventChannelDefinition> getAllChannels() {
    return Collections.unmodifiableCollection(channelDefinitionMap.values());
  }

  /**
   * 设置默认通道
   *
   * @param defaultChannel 默认通道定义
   */
  public void setDefaultChannel(EventChannelDefinition defaultChannel) {
    this.defaultChannel = defaultChannel;
  }

  /**
   * 获取默认通道
   *
   * @return 默认通道定义
   */
  public EventChannelDefinition getDefaultChannel() {
    return defaultChannel;
  }
}
