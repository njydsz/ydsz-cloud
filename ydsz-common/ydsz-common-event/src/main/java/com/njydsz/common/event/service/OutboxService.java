package com.njydsz.common.event.service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.api.OutboxNewMessageEvent;
import com.njydsz.common.event.config.EventProperties;
import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.common.event.model.OutboxStatus;
import com.njydsz.common.event.repository.OutboxRepository;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.id.SnowflakeIdGenerator;

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
 *   <li>大 payload 自动压缩（超过 {@value #COMPRESS_THRESHOLD_BYTES} 字节启用 GZIP）
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
 *         outboxService.appendToOutbox(DomainEvent.builder()
 *             .aggregateType("Order")
 *             .aggregateId(order.getId())
 *             .eventType("OrderCreated")
 *             .build());
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @since 26.09.01 移除 JSON Schema 校验框架和同步投递模式，精简职责回归异步 Outbox 本质
 * @since 26.09.19 E-2 字段对齐：deduplicationId → idempotencyKey
 * @since 26.09.19 O-4 写入时自动设置 schemaVersion（默认 1）
 * @since 26.09.19 P3 写入时检测 payload 大小，超过阈值自动 GZIP 压缩
 */
public class OutboxService {

  /** 日志实例 */
  private static final Logger LOG = LoggerFactory.getLogger(OutboxService.class);

  /** payload 超过此字节数自动启用 GZIP 压缩存储 */
  private static final int COMPRESS_THRESHOLD_BYTES = 4096;

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
   *   <li>idempotencyKey - 若显式指定则使用
   *   <li>schemaVersion - 当前 schema 版本号（默认 1）
   *   <li>compressed - 根据 payload 大小自动判断
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
    String idempotencyKey = resolveIdempotencyKey(partial);
    String processedPayload = maybeCompress(partial.getPayload());
    boolean isCompressed = !processedPayload.equals(partial.getPayload());
    int schemaVersion = partial.getSchemaVersion() > 0 ? partial.getSchemaVersion() : 1;

    // 幂等去重检查（仅当有 idempotencyKey 时）
    if (idempotencyKey != null && outboxRepository.existsByIdempotencyKey(idempotencyKey)) {
      LOG.info(
          "Outbox message skipped (duplicate): aggregateType={}, aggregateId={}, eventType={}, "
              + "idempotencyKey={}",
          partial.getAggregateType(),
          partial.getAggregateId(),
          partial.getEventType(),
          idempotencyKey);
      return;
    }

    OutboxMessage message =
        partialBuilder
            .id(String.valueOf(snowflakeIdGenerator.nextId()))
            .tenantId(tenantId)
            .traceId(traceId)
            .idempotencyKey(idempotencyKey)
            .schemaVersion(schemaVersion)
            .isCompressed(isCompressed)
            .payload(processedPayload)
            .status(OutboxStatus.PENDING)
            .retryCount(0)
            .maxRetries(properties.getMaxRetries())
            .nextRetryAt(now)
            .createdAt(now)
            .updatedAt(now)
            .build();

    outboxRepository.save(message);
    LOG.debug(
        "Outbox message appended: id={}, type={}, aggregate={}/{}, tenant={}, compressed={}",
        message.getId(),
        message.getEventType(),
        message.getAggregateType(),
        message.getAggregateId(),
        message.getTenantId(),
        message.isCompressed());

    // 注册事务提交后的事件发布回调
    registerDomainEventPublishCallback(message);
  }

  /**
   * 追加领域事件到 Outbox（便捷重载，自动序列化为 JSON payload）
   *
   * <p>等价于 {@link #appendToOutbox(OutboxMessage.OutboxMessageBuilder)} 的全构建方式， 避免调用方手动拼接 {@link
   * OutboxMessageBuilder}。 自动使用 eventId 作为 idempotencyKey 实现幂等去重。
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
   * @since 26.09.01
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
            .idempotencyKey(event.getEventId())
            .schemaVersion(event.getSchemaVersion()));
  }

  /**
   * 注册事务提交后的领域事件发布回调
   *
   * <p>事务提交成功后发布 {@link OutboxMessage} 作为 Spring 事件， 供进程内 {@code @EventListener} 订阅（如
   * CrossModuleEventListener）。 事务回滚时不触发，确保只发布已持久化的消息。
   *
   * <p><b>O-2 钩子升级：</b>如需在事务回滚时执行补偿逻辑（如 Saga 补偿、资源清理）， 请使用 {@link
   * #registerTransactionPhaseCallback} 注册 {@link TransactionPhase#AFTER_ROLLBACK} 回调。
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
            // P-1 推模式：事务提交后通知 OutboxProcessor 触发即时轮询
            eventPublisher.publishEvent(new OutboxNewMessageEvent(this, message.getId()));
          }
        });
  }

  /**
   * 事务阶段枚举
   *
   * <p>借鉴 Spring {@code TransactionPhase} 设计，用于 {@link #registerTransactionPhaseCallback}
   * 注册不同事务阶段的生命周期回调。
   *
   * @since 26.09.19
   */
  public enum TransactionPhase {
    /** 事务提交成功后 */
    AFTER_COMMIT,
    /** 事务回滚后 */
    AFTER_ROLLBACK
  }

  /**
   * 注册事务阶段回调（O-2）
   *
   * <p>允许业务模块注册在事务提交或回滚后执行的操作。典型场景：
   *
   * <ul>
   *   <li>AFTER_COMMIT: RPC 调用、发送邮件（只对已提交数据生效）
   *   <li>AFTER_ROLLBACK: Saga 补偿、记录回滚日志、释放预扣资源
   * </ul>
   *
   * <p>回调仅在当前存在数据库事务时注册；无事务上下文时回立即执行（与 afterCommit 行为一致）。
   *
   * @param phase 事务阶段（AFTER_COMMIT 或 AFTER_ROLLBACK）
   * @param callback 回调任务（抛异常不影响主事务回滚，仅记录 WARN 日志）
   * @since 26.09.19
   */
  public void registerTransactionPhaseCallback(TransactionPhase phase, Runnable callback) {
    if (callback == null) {
      return;
    }
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      // 无事务上下文，立即执行
      executeCallback(callback);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            if (phase == TransactionPhase.AFTER_COMMIT) {
              executeCallback(callback);
            }
          }

          @Override
          public void afterCompletion(int status) {
            if (phase == TransactionPhase.AFTER_ROLLBACK
                && status == TransactionSynchronization.STATUS_ROLLED_BACK) {
              executeCallback(callback);
            }
          }
        });
  }

  /**
   * 执行回调（捕获异常，不影响主流程）
   *
   * @param callback 回调任务
   */
  private void executeCallback(Runnable callback) {
    try {
      callback.run();
    } catch (Exception e) {
      LOG.warn("Transaction phase callback execution failed: phase error={}", e.getMessage(), e);
    }
  }

  /**
   * 发布领域事件到 Spring 事件总线
   *
   * @param message Outbox 消息
   */
  private void doPublishDomainEvent(OutboxMessage message) {
    try {
      eventPublisher.publishEvent(message);
      LOG.debug(
          "Domain event published to Spring event bus: id={}, type={}",
          message.getId(),
          message.getEventType());
    } catch (Exception e) {
      // 事件发布失败不影响主流程（异步投递由轮询器兜底）
      LOG.warn(
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
    // 使用原始字节长度判断（压缩后仍超过限制则拒绝）
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
   * 大 payload 自动压缩
   *
   * <p>当 payload 超过 {@value #COMPRESS_THRESHOLD_BYTES} 字节时启用 GZIP 压缩， 以 Base64 编码存储为字符串，显著减少
   * DB 存储和网络传输开销。
   *
   * @param payload 原始 payload
   * @return 原始 payload 或压缩后 Base64 字符串
   */
  String maybeCompress(String payload) {
    if (payload == null) {
      return null;
    }
    if (payload.getBytes(StandardCharsets.UTF_8).length <= COMPRESS_THRESHOLD_BYTES) {
      return payload;
    }
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
        gzip.write(payload.getBytes(StandardCharsets.UTF_8));
      }
      String compressed = Base64.getEncoder().encodeToString(baos.toByteArray());
      LOG.debug(
          "Payload compressed: original={} bytes, compressed={} bytes",
          payload.length(),
          compressed.length());
      return compressed;
    } catch (Exception e) {
      LOG.warn("Failed to compress payload, fallback to original: {}", e.getMessage());
      return payload;
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
      LOG.debug("RequestContext 不可用，tenantId 返回 null", e);
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
    } catch (NoClassDefFoundError | Exception e) {
      LOG.debug("RequestContext 不可用，降级从 MDC 获取 traceId", e);
    }
    // 从 MDC 获取
    try {
      String mdcTraceId = MDC.get("traceId");
      if (mdcTraceId != null && !mdcTraceId.isBlank()) {
        return mdcTraceId;
      }
    } catch (Exception e) {
      LOG.debug("MDC 不可用，traceId 返回 null", e);
    }
    return null;
  }

  /**
   * 解析幂等去重键
   *
   * <p>若调用方显式指定的 idempotencyKey 非空则使用，否则返回 null（不进行去重）。
   *
   * @param partial 消息快照
   * @return 幂等去重键，若不启用则返回 null
   */
  private String resolveIdempotencyKey(OutboxMessage partial) {
    if (partial.getIdempotencyKey() != null && !partial.getIdempotencyKey().isBlank()) {
      return partial.getIdempotencyKey();
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
   *   <li>幂等去重在批量模式下不做逐条检查（trade-off 性能）， 如需幂等保证请在调用前自行过滤或通过 idempotencyKey 唯一约束保障
   *   <li>Spring 事件发布在批量模式下会为每个消息独立发布（afterCommit）
   * </ul>
   *
   * @param events 领域事件列表
   * @since 26.09.01
   */
  @Transactional
  public void appendAllToOutbox(List<DomainEvent> events) {
    if (events == null || events.isEmpty()) {
      return;
    }
    Instant now = Instant.now();
    String tenantId = resolveTenantId();
    String traceId = resolveTraceId();

    List<OutboxMessage> messages = new ArrayList<>(events.size());
    for (DomainEvent event : events) {
      String rawPayload = YdszJson.toJson(event);
      validatePayloadSize(rawPayload);
      String processedPayload = maybeCompress(rawPayload);
      boolean isCompressed = !processedPayload.equals(rawPayload);

      OutboxMessage message =
          OutboxMessage.builder()
              .id(String.valueOf(snowflakeIdGenerator.nextId()))
              .aggregateType(event.getAggregateType())
              .aggregateId(event.getAggregateId())
              .eventType(event.getEventType())
              .payload(processedPayload)
              .idempotencyKey(event.getEventId())
              .tenantId(tenantId)
              .traceId(traceId)
              .schemaVersion(1)
              .isCompressed(isCompressed)
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
    LOG.debug("Batch appended {} outbox messages", messages.size());

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
            // P-1 推模式：批量事务提交后通知 OutboxProcessor
            messages.forEach(msg ->
                eventPublisher.publishEvent(new OutboxNewMessageEvent(this, msg.getId())));
          }
        });
  }
}
