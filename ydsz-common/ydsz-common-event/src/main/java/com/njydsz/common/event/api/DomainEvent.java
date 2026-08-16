package com.njydsz.common.event.api;

import com.njydsz.common.util.id.IdGenerator;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.ApplicationEvent;

/**
 * 领域事件基类 — 模块间事件契约的基础。
 *
 * <p>继承 Spring {@link ApplicationEvent}，可直接通过 {@code ApplicationEventPublisher} 发布并由
 * {@code @EventListener} 消费。在领域驱动设计（DDD）中，领域事件表示 领域中已经发生、具有业务含义的重要事情，用于实现聚合之间的解耦通信。
 *
 * <p><b>核心语义：</b>
 *
 * <ul>
 *   <li><b>已发生的事实：</b>领域事件描述的是"已经发生的事"，命名应使用过去时
 *   <li><b>不可变性：</b>领域事件一旦创建，其状态不可改变
 *   <li><b>业务含义：</b>领域事件应表达明确的业务语义，而非技术细节
 *   <li><b>跨模块契约：</b>所有跨模块事件均应继承本类，确保统一的元数据字段
 * </ul>
 *
 * <p><b>上下文感知：</b>租户 / 用户 / 链路追踪等上下文不重复存放在事件内， 由 {@code RequestContext} / MDC 自动传透，事件仅保留业务语义字段。
 * 如需持久化上下文（如 Outbox），由写入方在落库时从 RequestContext 解析。
 *
 * <p>跨模块事件类型常量定义在 {@link DomainEventTypes}。
 *
 * <p><b>创建方式：</b>推荐使用 Builder 模式创建领域事件，自动填充 eventId、occurredAt：
 *
 * <pre>{@code
 * DomainEvent event = DomainEvent.builder()
 *     .eventType("OrderCreated")
 *     .aggregateId("order-123")
 *     .aggregateType("Order")
 *     .metadata("source", "API")
 *     .build();
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @since 1.4.0 精简：移除 version / tenantId / userId / traceId 四个字段。 事件版本号无业务使用（非事件溯源）；上下文字段与
 *     RequestContext 重复， 改由消费/落库方在需要时自行解析。
 * @since 1.5.0 由 common-domain 迁入 common-event，事件抽象与 Outbox 实现统一归属事件模块
 * @since 1.7.0 移除 Serializable 接口和 Builder 中的 clock 参数，回归简洁
 */
public class DomainEvent extends ApplicationEvent {

  /** 事件唯一标识 */
  private final String eventId;

  /** 事件发生时间 */
  private final LocalDateTime occurredAt;

  /** 事件类型 */
  private final String eventType;

  /** 聚合根ID */
  private final String aggregateId;

  /** 聚合根类型 */
  private final String aggregateType;

  /** 扩展元数据 */
  private final Map<String, Object> metadata;

  /**
   * 构造领域事件（全参数）
   *
   * @param eventId 事件唯一标识
   * @param occurredAt 事件发生时间
   * @param eventType 事件类型
   * @param aggregateId 聚合根ID
   * @param aggregateType 聚合根类型
   * @param metadata 扩展元数据
   */
  public DomainEvent(
      String eventId,
      LocalDateTime occurredAt,
      String eventType,
      String aggregateId,
      String aggregateType,
      Map<String, Object> metadata) {
    super(eventType);
    this.eventId = eventId;
    this.occurredAt = occurredAt;
    this.eventType = eventType;
    this.aggregateId = aggregateId;
    this.aggregateType = aggregateType;
    this.metadata =
        metadata != null
            ? Collections.unmodifiableMap(new HashMap<>(metadata))
            : Collections.emptyMap();
  }

  /** 获取 Builder 实例 */
  public static Builder builder() {
    return new Builder();
  }

  public String getEventId() {
    return eventId;
  }

  public LocalDateTime getOccurredAt() {
    return occurredAt;
  }

  public String getEventType() {
    return eventType;
  }

  public String getAggregateId() {
    return aggregateId;
  }

  public String getAggregateType() {
    return aggregateType;
  }

  /** 获取扩展元数据（不可变） */
  public Map<String, Object> getMetadata() {
    return metadata;
  }

  /** 获取指定元数据项，不存在返回 null */
  public Object getMetadata(String key) {
    return metadata.get(key);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DomainEvent that = (DomainEvent) o;
    return Objects.equals(eventId, that.eventId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(eventId);
  }

  @Override
  public String toString() {
    return String.format(
        "DomainEvent{eventId='%s', occurredAt=%s, eventType='%s', aggregateId='%s', "
            + "aggregateType='%s', metadata=%s}",
        eventId, occurredAt, eventType, aggregateId, aggregateType, metadata);
  }

  /**
   * DomainEvent 构建器。
   *
   * <p>提供链式调用方式创建不可变的领域事件。默认自动填充 eventId、occurredAt。
   */
  public static class Builder {
    private String eventId;
    private LocalDateTime occurredAt;
    private String eventType;
    private String aggregateId;
    private String aggregateType;
    private final Map<String, Object> metadata = new HashMap<>();

    private Builder() {}

    /**
     * 显式指定事件唯一标识。
     *
     * <p>通常无需调用：{@link #build()} 会自动生成 UUID。 仅在需要保证幂等（如消息重投时复用同一事件 ID）或从持久化事件流恢复时使用。
     *
     * @param eventId 事件唯一标识；传 {@code null} 则退回自动生成
     * @return 当前 Builder，便于链式调用
     */
    public Builder eventId(String eventId) {
      this.eventId = eventId;
      return this;
    }

    /**
     * 显式指定事件发生时间。
     *
     * <p>通常无需调用：{@link #build()} 会取当前系统时间。 仅在补录历史事件、或事件溯源回放需还原原始时间时使用。
     *
     * @param occurredAt 事件发生时间；传 {@code null} 则退回自动取值
     * @return 当前 Builder，便于链式调用
     */
    public Builder occurredAt(LocalDateTime occurredAt) {
      this.occurredAt = occurredAt;
      return this;
    }

    /** 设置事件类型 */
    public Builder eventType(String eventType) {
      this.eventType = eventType;
      return this;
    }

    /** 设置聚合根ID */
    public Builder aggregateId(String aggregateId) {
      this.aggregateId = aggregateId;
      return this;
    }

    /** 设置聚合根类型 */
    public Builder aggregateType(String aggregateType) {
      this.aggregateType = aggregateType;
      return this;
    }

    /**
     * 追加单个元数据项（增量追加，重复 key 覆盖旧值）。
     *
     * @param key 元数据键，不建议为 {@code null}
     * @param value 元数据值，允许为 {@code null}
     * @return 当前 Builder，便于链式调用
     */
    public Builder metadata(String key, Object value) {
      this.metadata.put(key, value);
      return this;
    }

    /**
     * 设置元数据（覆盖已有元数据）
     *
     * @param metadata 元数据 Map
     * @return 当前 Builder
     */
    public Builder metadata(Map<String, Object> metadata) {
      this.metadata.clear();
      if (metadata != null) {
        this.metadata.putAll(metadata);
      }
      return this;
    }

    /**
     * 构建领域事件实例。
     *
     * <p>组装 Builder 已设置的字段并自动填充缺失项：eventId 缺省时生成 UUID， occurredAt 缺省时取当前系统时间。构建完成后事件不可变（metadata
     * 为不可变 Map）。
     *
     * @return 组装完成的领域事件
     * @throws EventBuildException 当 eventType 为 null 或空字符串时抛出， 事件类型是跨模块契约的必需字段
     */
    public DomainEvent build() {
      if (eventType == null || eventType.isEmpty()) {
        throw new EventBuildException("eventType must not be null or empty");
      }
      String eid = eventId != null ? eventId : IdGenerator.nextIdStr();
      LocalDateTime occurred = occurredAt != null ? occurredAt : LocalDateTime.now();
      return new DomainEvent(eid, occurred, eventType, aggregateId, aggregateType, metadata);
    }
  }
}
