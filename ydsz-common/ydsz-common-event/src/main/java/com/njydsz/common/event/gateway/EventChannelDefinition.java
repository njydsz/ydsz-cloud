package com.njydsz.common.event.gateway;

/**
 * 事件通道定义（O-3）
 *
 * <p>通道的完整配置描述：通道名、目标 topic、优先级、序列化策略。 {@link EventChannelRegistry} 管理本定义的集合， 在 OutboxService
 * 写入时根据事件类型匹配对应通道，将通道标签写入 metadata {@code _channel} 字段。
 *
 * <p><b>设计原则：</b>参考 Spring Cloud Stream 的 {@code BindingServiceProperties} 语义， 但只保留最核心的通道 -&gt; topic
 * 映射，避免引入重量级配置绑定。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public class EventChannelDefinition {

  /** 通道名称 */
  private final String channelName;

  /** 目标 topic（未配置时使用 channelName 作为默认） */
  private final String topic;

  /** 投递优先级 */
  private final int priority;

  /** 序列化策略 */
  private final String serialization;

  /**
   * 构造通道定义
   *
   * @param channelName 通道名称
   * @param topic 目标 topic（空则使用 channelName）
   * @param priority 投递优先级
   * @param serialization 序列化策略
   */
  public EventChannelDefinition(String channelName, String topic, int priority,
      String serialization) {
    this.channelName = channelName;
    this.topic = topic != null && !topic.isEmpty() ? topic : channelName;
    this.priority = Math.max(0, Math.min(priority, 9));
    this.serialization = serialization != null ? serialization : "json";
  }

  /**
   * 获取通道名称
   *
   * @return 通道名称
   */
  public String getChannelName() {
    return channelName;
  }

  /**
   * 获取目标 topic
   *
   * @return topic 名称
   */
  public String getTopic() {
    return topic;
  }

  /**
   * 获取投递优先级
   *
   * @return 优先级（0-9）
   */
  public int getPriority() {
    return priority;
  }

  /**
   * 获取序列化策略
   *
   * @return 序列化策略标识
   */
  public String getSerialization() {
    return serialization;
  }

  @Override
  public String toString() {
    return String.format("EventChannelDefinition{name='%s', topic='%s', priority=%d, serialization='%s'}",
        channelName, topic, priority, serialization);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EventChannelDefinition that = (EventChannelDefinition) o;
    return java.util.Objects.equals(channelName, that.channelName);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(channelName);
  }
}
