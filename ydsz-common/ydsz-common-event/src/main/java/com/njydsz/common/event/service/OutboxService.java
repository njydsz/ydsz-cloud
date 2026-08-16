package com.njydsz.common.event.service;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.config.EventProperties;
import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.common.event.model.OutboxStatus;
import com.njydsz.common.event.repository.OutboxRepository;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Outbox 写入服务
 *
 * <p>核心入口：业务代码在数据库事务中调用 {@link #appendToOutbox}， 将领域事件写入 Outbox 表。事务提交后，后台轮询器异步投递。
 *
 * <p>增强能力：
 *
 * <ul>
 *   <li>自动注入 traceId（从 RequestContext / MDC 获取）
 *   <li>自动注入 tenantId（从 RequestContext 获取）
 *   <li>payload 大小校验（防数据库行过大 / MQ 投递失败）
 *   <li>事务内事件发布（afterCommit 发布 Spring 事件供进程内订阅）
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * &#64;Service
 * public class OrderService {
 *     private final OutboxService outboxService;
 *
 *     &#64;Transactional
 *     public void createOrder(OrderCreateDTO dto) {
 *         Order order = orderMapper.insert(dto);
 *
 *         // 同一事务写入 Outbox
 *         outboxService.appendToOutbox(OutboxMessage.builder()
 *             .aggregateType("Order")
 *             .aggregateId(order.getId())
 *             .eventType("OrderCreated")
 *             .payload(toJson(order))
 *         );
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @since 1.7.0 移除 JSON Schema 校验框架和同步投递模式，精简职责回归异步 Outbox 本质
 */
public class OutboxService {

  /** 日志实例 */
  private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

  /** Outbox 仓储 */
  private final OutboxRepository outboxRepository;

  /** 事件配置属性 */
  private final EventProperties properties;

  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  /** Spring 事件发布器 */
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 构造函数
   *
   * @param outboxRepository Outbox 仓储
   * @param properties 事件配置属性
   * @param snowflakeIdGenerator 分布式 ID 生成器
   * @param eventPublisher Spring 事件发布器
   */
  public OutboxService(
      OutboxRepository outboxRepository,
      EventProperties properties,
      SnowflakeIdGenerator snowflakeIdGenerator,
      ApplicationEventPublisher eventPublisher) {
    this.outboxRepository = outboxRepository;
    this.properties = properties;
    this.snowflakeIdGenerator = snowflakeIdGenerator;
    this.eventPublisher = eventPublisher;
  }

  /**
   * 追加事件到 Outbox（基于 Builder 模式，自动填充系统字段）
   *
   * <p>此方法自动填充以下字段：
   *
   * <ul>
   *   <li>id - 雪花 ID
   *   <li>tenantId - 从 RequestContext 获取
   *   <li>traceId - 从 RequestContext / MDC 获取
   *   <li>deduplicationId - 若显式指定则使用
   *   <li>status - PENDING
   *   <li>时间戳 - 当前时间
   * </ul>
   *
   * @param partialBuilder 部分填充的 Builder（业务字段）
   */
  @Transactional
  public void appendToOutbox(OutboxMessage.OutboxMessageBuilder partialBuilder) {
    OutboxMessage partial = partialBuilder.build();

    // payload 大小校验
    validatePayloadSize(partial.getPayload());

    Instant now = Instant.now();
    String tenantId = resolveTenantId();
    String traceId = resolveTraceId();
    String deduplicationId = resolveDeduplicationId(partial);

    // 幂等去重检查（仅当有 deduplicationId 时）
    if (deduplicationId != null && outboxRepository.existsByDeduplicationId(deduplicationId)) {
      log.info(
          "Outbox message skipped (duplicate): aggregateType={}, aggregateId={}, eventType={}, "
              + "deduplicationId={}",
          partial.getAggregateType(),
          partial.getAggregateId(),
          partial.getEventType(),
          deduplicationId);
      return;
    }

    OutboxMessage message =
        partialBuilder
            .id(String.valueOf(snowflakeIdGenerator.nextId()))
            .tenantId(tenantId)
            .traceId(traceId)
            .deduplicationId(deduplicationId)
            .status(OutboxStatus.PENDING)
            .retryCount(0)
            .maxRetries(properties.getMaxRetries())
            .nextRetryAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build();

    outboxRepository.save(message);
    log.debug(
        "Outbox message appended: id={}, type={}, aggregate={}/{}, tenant={}",
        message.getId(),
        message.getEventType(),
        message.getAggregateType(),
        message.getAggregateId(),
        message.getTenantId());

    // 注册事务提交后的事件发布回调
    registerDomainEventPublishCallback(message);
  }

  /**
   * 追加领域事件到 Outbox（便捷重载，自动序列化为 JSON payload）
   *
   * <p>等价于 {@link #appendToOutbox(OutboxMessage.OutboxMessageBuilder)} 的全构建方式， 避免调用方手动拼接 {@link
   * OutboxMessageBuilder}。
   *
   * <p><b>使用示例：</b>
   *
   * <pre>{@code
   * outboxService.appendToOutbox(DomainEvent.builder()
   *     .aggregateType("Order")
   *     .aggregateId(order.getId())
   *     .eventType("OrderCreated")
   *     .build());
   * }</pre>
   *
   * @param event 领域事件
   * @since 1.8.0
   */
  @Transactional
  public void appendToOutbox(DomainEvent event) {
    if (event == null) {
      return;
    }
    appendToOutbox(
        OutboxMessage.builder()
            .aggregateType(event.getAggregateType())
            .aggregateId(event.getAggregateId())
            .eventType(event.getEventType())
            .payload(YdszJson.toJson(event))
            .deduplicationId(event.getEventId()));
  }

  /**
   * 注册事务提交后的领域事件发布回调
   *
   * <p>事务提交成功后发布 {@link OutboxMessage} 作为 Spring 事件， 供进程内 {@code @EventListener} 订阅（如
   * CrossModuleEventListener）。 事务回滚时不触发，确保只发布已持久化的消息。
   *
   * @param message Outbox 消息
   */
  private void registerDomainEventPublishCallback(OutboxMessage message) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      // 无事务上下文，直接发布
      doPublishDomainEvent(message);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            doPublishDomainEvent(message);
          }
        });
  }

  /**
   * 发布领域事件到 Spring 事件总线
   *
   * @param message Outbox 消息
   */
  private void doPublishDomainEvent(OutboxMessage message) {
    try {
      eventPublisher.publishEvent(message);
      log.debug(
          "Domain event published to Spring event bus: id={}, type={}",
          message.getId(),
          message.getEventType());
    } catch (Exception e) {
      // 事件发布失败不影响主流程（异步投递由轮询器兜底）
      log.warn(
          "Failed to publish domain event to Spring event bus: id={}, type={}, err={}",
          message.getId(),
          message.getEventType(),
          e.getMessage());
    }
  }

  /**
   * 校验 payload 大小是否超过配置的最大限制
   *
   * @param payload 消息负载
   * @throws IllegalArgumentException payload 超过最大限制
   */
  private void validatePayloadSize(String payload) {
    if (payload == null) {
      return;
    }
    int size = payload.getBytes(StandardCharsets.UTF_8).length;
    if (size > properties.getMaxPayloadSizeBytes()) {
      throw new IllegalArgumentException(
          "Outbox payload size "
              + size
              + " exceeds maximum "
              + properties.getMaxPayloadSizeBytes()
              + " bytes");
    }
  }

  /**
   * 解析租户 ID
   *
   * <p>优先从 RequestContext 获取，若 RequestContext 不可用则返回 null。
   *
   * @return 租户 ID，若不可用则返回 null
   */
  private String resolveTenantId() {
    try {
      return RequestContext.getTenantId();
    } catch (NoClassDefFoundError | Exception e) {
      // RequestContext 不可用时返回 null
      return null;
    }
  }

  /**
   * 解析链路追踪 ID
   *
   * <p>优先级：RequestContext > MDC > null
   *
   * @return traceId，若不可用则返回 null
   */
  private String resolveTraceId() {
    // 优先从 RequestContext 获取
    try {
      String traceId = RequestContext.getTraceId();
      if (traceId != null && !traceId.isBlank()) {
        return traceId;
      }
    } catch (NoClassDefFoundError | Exception ignored) {
      // RequestContext 不可用
    }
    // 从 MDC 获取
    try {
      String mdcTraceId = MDC.get("traceId");
      if (mdcTraceId != null && !mdcTraceId.isBlank()) {
        return mdcTraceId;
      }
    } catch (Exception ignored) {
      // MDC 不可用
    }
    return null;
  }

  /**
   * 解析幂等去重 ID
   *
   * <p>若调用方显式指定的 deduplicationId 非空则使用，否则返回 null（不进行去重）。
   *
   * @param partial 消息快照
   * @return 去重 ID，若不启用则返回 null
   */
  private String resolveDeduplicationId(OutboxMessage partial) {
    if (partial.getDeduplicationId() != null && !partial.getDeduplicationId().isBlank()) {
      return partial.getDeduplicationId();
    }
    return null;
  }

  /**
   * 批量追加领域事件到 Outbox（在当前数据库事务中执行）
   *
   * <p>使用 JDBC batchUpdate 实现真正的批量插入，相比逐条调用 {@link
   * #appendToOutbox(OutboxMessage.OutboxMessageBuilder)} 可显著减少数据库往返。
   *
   * <p><b>注意：</b>
   *
   * <ul>
   *   <li>幂等去重在批量模式下不做逐条检查（trade-off 性能）， 如需幂等保证请在调用前自行过滤或通过 deduplicationId 唯一约束保障
   *   <li>Spring 事件发布在批量模式下会为每个消息独立发布（afterCommit）
   * </ul>
   *
   * @param events 领域事件列表
   * @since 1.6.0
   */
  @Transactional
  public void appendAllToOutbox(List<DomainEvent> events) {
    if (events == null || events.isEmpty()) {
      return;
    }
    Instant now = Instant.now();
    String tenantId = resolveTenantId();
    String traceId = resolveTraceId();

    List<OutboxMessage> messages = new java.util.ArrayList<>(events.size());
    for (DomainEvent event : events) {
      String payload = YdszJson.toJson(event);
      validatePayloadSize(payload);

      OutboxMessage message =
          OutboxMessage.builder()
              .id(String.valueOf(snowflakeIdGenerator.nextId()))
              .aggregateType(event.getAggregateType())
              .aggregateId(event.getAggregateId())
              .eventType(event.getEventType())
              .payload(payload)
              .deduplicationId(event.getEventId())
              .tenantId(tenantId)
              .traceId(traceId)
              .status(OutboxStatus.PENDING)
              .retryCount(0)
              .maxRetries(properties.getMaxRetries())
              .nextRetryAt(now)
              .createdAt(now)
              .updatedAt(now)
              .build();
      messages.add(message);
    }

    outboxRepository.saveBatch(messages);
    log.debug("Batch appended {} outbox messages", messages.size());

    // 注册批量事件发布回调
    registerBatchDomainEventPublishCallback(messages);
  }

  /**
   * 注册批量领域事件发布回调（事务提交后为每个消息发布 Spring 事件）
   *
   * @param messages Outbox 消息列表
   */
  private void registerBatchDomainEventPublishCallback(List<OutboxMessage> messages) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      messages.forEach(this::doPublishDomainEvent);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            messages.forEach(OutboxService.this::doPublishDomainEvent);
          }
        });
  }
}
