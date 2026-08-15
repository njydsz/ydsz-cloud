package com.njydsz.common.event.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.api.JsonSchemaRegistry;
import com.njydsz.common.event.api.JsonSchemaValidator;
import com.njydsz.common.event.api.SchemaValidationException;
import com.njydsz.common.event.api.SchemaValidationResult;
import com.njydsz.common.event.config.EventProperties;
import com.njydsz.common.event.gateway.EventPublishGateway;
import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.common.event.model.OutboxStatus;
import com.njydsz.common.event.repository.OutboxRepository;
import com.njydsz.common.json.YdszJson;

/**
 * Outbox 写入服务
 *
 * <p>核心入口：业务代码在数据库事务中调用 {@link #appendToOutbox}，
 * 将领域事件写入 Outbox 表。事务提交后，后台轮询器异步投递。
 *
 * <p>增强能力：
 * <ul>
 *   <li>自动注入 traceId（从 RequestContext / MDC 获取）</li>
 *   <li>自动注入 tenantId（从 RequestContext 获取）</li>
 *   <li>幂等去重（可选，基于 deduplicationId，需开启 auto-dedup 或显式传入）</li>
 *   <li>payload 大小校验（防数据库行过大 / MQ 投递失败）</li>
 *   <li>支持事件 Schema 版本号和内容类型</li>
 *   <li>支持消息优先级（0-9，正确处理 priority=0）</li>
 *   <li>同步投递模式（事务提交后立即投递）</li>
 *   <li>事务内事件发布（afterCommit 发布 Spring 事件供进程内订阅）</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
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
 * @since 1.6.0 新增 ApplicationEventPublisher 注入和事务内事件发布能力，
 *             修复 CrossModuleEventListener 订阅链路断裂问题
 */
public class OutboxService {

    /** 日志实例 */
    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    /** Outbox 仓储 */
    private final OutboxRepository outboxRepository;

    /** 事件配置属性 */
    private final EventProperties properties;

    /** 同步投递网关（可选，仅 enableSyncPublish=true 时注入） */
    private final EventPublishGateway syncPublishGateway;

    /** 分布式 ID 生成器 */
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    /** Spring 事件发布器 */
    private final ApplicationEventPublisher eventPublisher;

    /** JSON Schema 校验器 */
    private final JsonSchemaValidator schemaValidator;

    /** JSON Schema 注册中心 */
    private final JsonSchemaRegistry schemaRegistry;

    /**
     * 构造函数
     *
     * @param outboxRepository   Outbox 仓储
     * @param properties         事件配置属性
     * @param syncPublishGateway 同步投递网关（可选）
     * @param snowflakeIdGenerator 分布式 ID 生成器
     * @param eventPublisher     Spring 事件发布器
     * @param schemaValidator    JSON Schema 校验器
     * @param schemaRegistry     JSON Schema 注册中心
     */
    public OutboxService(OutboxRepository outboxRepository,
                         EventProperties properties,
                         EventPublishGateway syncPublishGateway,
                         SnowflakeIdGenerator snowflakeIdGenerator,
                         ApplicationEventPublisher eventPublisher,
                         JsonSchemaValidator schemaValidator,
                         JsonSchemaRegistry schemaRegistry) {
        this.outboxRepository = outboxRepository;
        this.properties = properties;
        this.syncPublishGateway = syncPublishGateway;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.eventPublisher = eventPublisher;
        this.schemaValidator = schemaValidator;
        this.schemaRegistry = schemaRegistry;
    }

    /**
     * 追加领域事件到 Outbox（在当前数据库事务中执行）
     *
     * <p>将 {@link DomainEvent} 转换为 Outbox 消息并写入。自动处理：
     * <ul>
     *   <li>eventType / aggregateId / aggregateType 从 DomainEvent 提取</li>
     *   <li>payload 为 DomainEvent 序列化后的 JSON</li>
     *   <li>metadata 转换为 headers</li>
     *   <li>eventId 作为 deduplicationId（天然唯一）</li>
     * </ul>
     *
     * @param event 领域事件
     * @deprecated 自 1.6.0 起废弃，推荐使用 {@link #appendToOutbox(OutboxMessage.OutboxMessageBuilder)}
     *             以获取完整的字段控制能力。本类将在 2.0.0 版本移除。
     */
    @Deprecated
    @Transactional
    public void appendToOutbox(DomainEvent event) {
        Map<String, String> headers = new HashMap<>();
        if (event.getMetadata() != null) {
            event.getMetadata().forEach((k, v) -> {
                if (v != null) {
                    headers.put(k, v.toString());
                }
            });
        }
        String userId = null;
        try {
            userId = RequestContext.getUserId();
        } catch (NoClassDefFoundError | Exception ignored) {
            // RequestContext 不可用
        }
        if (userId != null) {
            headers.put("_userId", userId);
        }

        appendToOutbox(OutboxMessage.builder()
                .aggregateType(event.getAggregateType())
                .aggregateId(event.getAggregateId())
                .eventType(event.getEventType())
                .payload(YdszJson.toJson(event))
                .headers(headers)
                .deduplicationId(event.getEventId()));
    }

    /**
     * 追加事件到 Outbox（在当前数据库事务中执行）
     *
     * @param aggregateType 聚合根类型
     * @param aggregateId   聚合根 ID
     * @param eventType     事件类型
     * @param payload       事件负载（JSON）
     * @deprecated 自 1.6.0 起废弃，推荐使用 {@link #appendToOutbox(OutboxMessage.OutboxMessageBuilder)}。
     *             本类将在 2.0.0 版本移除。
     */
    @Deprecated
    @Transactional
    public void appendToOutbox(String aggregateType, String aggregateId,
                               String eventType, String payload) {
        appendToOutbox(aggregateType, aggregateId, eventType, payload, null);
    }

    /**
     * 追加事件到 Outbox（带扩展头）
     *
     * @param aggregateType 聚合根类型
     * @param aggregateId   聚合根 ID
     * @param eventType     事件类型
     * @param payload       事件负载（JSON）
     * @param headers       扩展头
     * @deprecated 自 1.6.0 起废弃，推荐使用 {@link #appendToOutbox(OutboxMessage.OutboxMessageBuilder)}。
     *             本类将在 2.0.0 版本移除。
     */
    @Deprecated
    @Transactional
    public void appendToOutbox(String aggregateType, String aggregateId,
                               String eventType, String payload,
                               Map<String, String> headers) {
        appendToOutbox(OutboxMessage.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payload)
                .headers(headers));
    }

    /**
     * 追加事件到 Outbox（基于 Builder 模式，自动填充系统字段）
     *
     * <p>此方法自动填充以下字段：
     * <ul>
     *   <li>id - 雪花 ID</li>
     *   <li>tenantId - 从 RequestContext 获取</li>
     *   <li>traceId - 从 RequestContext / MDC 获取</li>
     *   <li>deduplicationId - 若显式指定则使用；若 auto-dedup=true 则基于内容自动生成</li>
     *   <li>schemaVersion - 若未指定，使用默认值</li>
     *   <li>priority - 若未指定（null），使用默认值；显式设置 0 时保留 0</li>
     *   <li>status - PENDING</li>
     *   <li>时间戳 - 当前时间</li>
     * </ul>
     *
     * @param partialBuilder 部分填充的 Builder（业务字段）
     */
    @Transactional
    public void appendToOutbox(OutboxMessage.OutboxMessageBuilder partialBuilder) {
        // 构建一次快照，避免重复 build() 调用
        OutboxMessage partial = partialBuilder.build();

        // payload 大小校验
        validatePayloadSize(partial.getPayload());

        // JSON Schema 校验（可选，需显式启用）
        if (properties.isEnableSchemaValidation()) {
            validateSchema(partial.getEventType(), partial.getPayload());
        }

        Instant now = Instant.now();
        String tenantId = resolveTenantId();
        String traceId = resolveTraceId();
        String deduplicationId = resolveDeduplicationId(partial);

        // 幂等去重检查（仅当有 deduplicationId 时）
        if (deduplicationId != null && outboxRepository.existsByDeduplicationId(deduplicationId)) {
            log.info("Outbox message skipped (duplicate): aggregateType={}, aggregateId={}, eventType={}, deduplicationId={}",
                    partial.getAggregateType(),
                    partial.getAggregateId(),
                    partial.getEventType(),
                    deduplicationId);
            return;
        }

        OutboxMessage message = partialBuilder
                .id(String.valueOf(snowflakeIdGenerator.nextId()))
                .tenantId(tenantId)
                .traceId(traceId)
                .deduplicationId(deduplicationId)
                .schemaVersion(partial.getSchemaVersion() != null
                        ? partial.getSchemaVersion()
                        : properties.getDefaultSchemaVersion())
                .priority(partial.getPriority() != null
                        ? partial.getPriority()
                        : properties.getDefaultPriority())
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .maxRetries(properties.getMaxRetries())
                .nextRetryAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        outboxRepository.save(message);
        log.debug("Outbox message appended: id={}, type={}, aggregate={}/{}, tenant={}, priority={}",
                message.getId(), message.getEventType(),
                message.getAggregateType(), message.getAggregateId(),
                message.getTenantId(), message.getPriority());

        // 注册事务提交后的事件发布回调
        if (properties.isEnableDomainEventPublish()) {
            registerDomainEventPublishCallback(message);
        }

        // 注册同步投递回调（事务提交后立即投递）
        if (properties.isEnableSyncPublish() && syncPublishGateway != null) {
            registerSyncPublishCallback(message);
        }
    }

    /**
     * 注册事务提交后的领域事件发布回调
     *
     * <p>事务提交成功后发布 {@link OutboxMessage} 作为 Spring 事件，
     * 供进程内 {@code @EventListener} 订阅（如 CrossModuleEventListener）。
     * 事务回滚时不触发，确保只发布已持久化的消息。
     *
     * @param message Outbox 消息
     */
    private void registerDomainEventPublishCallback(OutboxMessage message) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 无事务上下文，直接发布
            doPublishDomainEvent(message);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
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
            log.debug("Domain event published to Spring event bus: id={}, type={}",
                    message.getId(), message.getEventType());
        } catch (Exception e) {
            // 事件发布失败不影响主流程（异步投递由轮询器兜底）
            log.warn("Failed to publish domain event to Spring event bus: id={}, type={}, err={}",
                    message.getId(), message.getEventType(), e.getMessage());
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
                    "Outbox payload size " + size + " exceeds maximum "
                            + properties.getMaxPayloadSizeBytes() + " bytes");
        }
    }

    /**
     * 校验事件 payload 是否符合已注册的 JSON Schema
     *
     * <p>仅当 {@link EventProperties#isEnableSchemaValidation()} 为 true 时执行。
     * 若该 eventType 未注册 Schema，则跳过校验（宽松策略）。
     *
     * <p>校验失败时：
     * <ul>
     *   <li>fail-fast=true：抛出 {@link SchemaValidationException}</li>
     *   <li>fail-fast=false：记录 WARN 日志，不阻断写入</li>
     * </ul>
     *
     * @param eventType 事件类型
     * @param payload   事件 payload JSON
     */
    private void validateSchema(String eventType, String payload) {
        if (!schemaRegistry.hasSchema(eventType)) {
            // 未注册 Schema 的事件类型不做校验
            return;
        }
        String schemaJson = schemaRegistry.getSchema(eventType);
        SchemaValidationResult result = schemaValidator.validate(eventType, payload, schemaJson);
        if (!result.isValid()) {
            if (properties.isSchemaValidationFailFast()) {
                throw new SchemaValidationException(eventType, result.getErrors());
            }
            log.warn("Schema validation failed for eventType={}, errors={} (fail-fast=false, message still written)",
                    eventType, result.getErrors());
        }
    }

    /**
     * 解析租户 ID
     *
     * <p>优先从 RequestContext 获取，若未启用租户隔离或 RequestContext 不可用则返回 null。
     *
     * @return 租户 ID，若未启用租户隔离则返回 null
     */
    private String resolveTenantId() {
        if (!properties.isEnableTenantIsolation()) {
            return null;
        }
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
     * <p>优先级：
     * <ol>
     *   <li>调用方显式指定的 deduplicationId</li>
     *   <li>若 auto-dedup=true，基于内容 SHA-256 自动生成</li>
     *   <li>否则返回 null（不进行去重）</li>
     * </ol>
     *
     * @param partial 消息快照
     * @return 去重 ID，若不启用则返回 null
     */
    private String resolveDeduplicationId(OutboxMessage partial) {
        if (partial.getDeduplicationId() != null && !partial.getDeduplicationId().isBlank()) {
            return partial.getDeduplicationId();
        }
        if (!properties.isAutoDedup()) {
            return null;
        }
        // 基于 aggregateType + aggregateId + eventType + payload 生成
        String content = partial.getAggregateType() + ":"
                + partial.getAggregateId() + ":"
                + partial.getEventType() + ":"
                + (partial.getPayload() != null ? partial.getPayload() : "");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 应该总是可用，降级为 UUID
            return String.valueOf(snowflakeIdGenerator.nextId());
        }
    }

    /**
     * 注册事务提交后的同步投递回调
     *
     * <p>若无活跃事务上下文，则直接同步投递。
     *
     * @param message Outbox 消息
     */
    private void registerSyncPublishCallback(OutboxMessage message) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 无事务上下文，直接同步投递
            doSyncPublish(message);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 事务提交成功后回调：投递 Outbox 消息。
             *
             * <p>事务回滚时不会触发，确保只投递已随事务持久化的消息，避免"先投递、后回滚"造成下游脏数据。
             */
            @Override
            public void afterCommit() {
                doSyncPublish(message);
            }
        });
    }

    /**
     * 批量追加领域事件到 Outbox（在当前数据库事务中执行）
     *
     * <p>使用 JDBC batchUpdate 实现真正的批量插入，相比逐条 {@link #appendToOutbox(DomainEvent)}
     * 可显著减少数据库往返次数（100 条事件从 100 次网络往返降为 1 次）。
     *
     * <p><b>注意：</b>
     * <ul>
     *   <li>幂等去重在批量模式下不做逐条检查（trade-off 性能），
     *       如需幂等保证请在调用前自行过滤或通过 deduplicationId 唯一约束保障</li>
     *   <li>Spring 事件发布在批量模式下会为每个消息独立发布（afterCommit）</li>
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
            Map<String, String> headers = new HashMap<>();
            if (event.getMetadata() != null) {
                event.getMetadata().forEach((k, v) -> {
                    if (v != null) {
                        headers.put(k, v.toString());
                    }
                });
            }

            String payload = YdszJson.toJson(event);
            validatePayloadSize(payload);

            OutboxMessage message = OutboxMessage.builder()
                    .id(String.valueOf(snowflakeIdGenerator.nextId()))
                    .aggregateType(event.getAggregateType())
                    .aggregateId(event.getAggregateId())
                    .eventType(event.getEventType())
                    .payload(payload)
                    .headers(headers)
                    .deduplicationId(event.getEventId())
                    .tenantId(tenantId)
                    .traceId(traceId)
                    .schemaVersion(properties.getDefaultSchemaVersion())
                    .priority(properties.getDefaultPriority())
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
        if (properties.isEnableDomainEventPublish()) {
            registerBatchDomainEventPublishCallback(messages);
        }
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
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                messages.forEach(OutboxService.this::doPublishDomainEvent);
            }
        });
    }

    /**
     * 执行同步投递
     *
     * <p>异常分类：
     * <ul>
     *   <li><b>可恢复异常</b>（网络超时、连接拒绝）：WARN 日志后依赖异步轮询器兜底重试</li>
     *   <li><b>不可恢复异常</b>（序列化失败、消息体过大）：此类异常重试无意义，
     *       但当前实现仍由异步轮询器处理（轮询 maxRetries 后进入 DEAD_LETTER）</li>
     * </ul>
     *
     * @param message Outbox 消息
     */
    private void doSyncPublish(OutboxMessage message) {
        try {
            boolean success = syncPublishGateway.publish(message);
            if (success) {
                outboxRepository.markAsSent(message.getId());
                log.debug("Sync publish succeeded: id={}, type={}", message.getId(), message.getEventType());
            } else {
                log.warn("Sync publish returned false, message will be picked up by async poller: id={}",
                        message.getId());
            }
        } catch (Exception e) {
            // 异常分类：可恢复异常依赖异步轮询兜底；不可恢复异常在轮询 maxRetries 后进入 DEAD_LETTER
            if (isRecoverableException(e)) {
                log.warn("Sync publish failed (recoverable), message will be retried by async poller: id={}, err={}",
                        message.getId(), e.getMessage());
            } else {
                log.error("Sync publish failed (non-recoverable): id={}, type={}, err={}",
                        message.getId(), message.getEventType(), e.getMessage());
            }
        }
    }

    /**
     * 判断异常是否可恢复
     *
     * <p>可恢复异常（Retriable）：网络超时、连接拒绝、服务暂时不可用等，这类异常
     * 通过后续重试可能成功。不可恢复异常（Non-retriable）：序列化失败、消息体校验错误等，
     * 重试无意义。
     *
     * @param e 投递异常
     * @return true 表示可恢复（应重试）
     */
    private boolean isRecoverableException(Exception e) {
        String className = e.getClass().getSimpleName();
        // 网络/连接/超时类异常通常可恢复
        return className.contains("Timeout")
                || className.contains("Connect")
                || className.contains("Socket")
                || className.contains("IOException")
                || className.contains("Retriable")
                || className.contains("Transient")
                || e.getCause() instanceof java.io.IOException;
    }
}
