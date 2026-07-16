package com.njydsz.pmis.common.event.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.njydsz.pmis.common.core.context.RequestContext;
import com.njydsz.pmis.common.event.config.EventProperties;
import com.njydsz.pmis.common.event.gateway.EventPublishGateway;
import com.njydsz.pmis.common.event.model.OutboxMessage;
import com.njydsz.pmis.common.event.model.OutboxStatus;
import com.njydsz.pmis.common.event.repository.OutboxRepository;

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
 *   <li>自动生成 deduplicationId（基于内容哈希）</li>
 *   <li>payload 大小校验（防数据库行过大 / MQ 投递失败）</li>
 *   <li>支持事件 Schema 版本号和内容类型</li>
 *   <li>支持消息优先级</li>
 *   <li>幂等去重（可选，基于 deduplicationId）</li>
 *   <li>同步投递模式（事务提交后立即投递）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * @Service
 * public class OrderService {
 *     private final OutboxService outboxService;
 *
 *     @Transactional
 *     public void createOrder(OrderCreateDTO dto) {
 *         Order order = orderMapper.insert(dto);
 *
 *         // 同一事务写入 Outbox
 *         outboxService.appendToOutbox(
 *             "Order", order.getId(), "OrderCreated",
 *             toJson(order)
 *         );
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxRepository outboxRepository;
    private final EventProperties properties;

    /** 同步投递网关（可选，仅 enableSyncPublish=true 时注入） */
    private final EventPublishGateway syncPublishGateway;

    /**
     * @param outboxRepository Outbox 仓储
     * @param properties       事件配置属性
     */
    public OutboxService(OutboxRepository outboxRepository, EventProperties properties) {
        this(outboxRepository, properties, null);
    }

    /**
     * @param outboxRepository   Outbox 仓储
     * @param properties         事件配置属性
     * @param syncPublishGateway 同步投递网关（可选）
     */
    public OutboxService(OutboxRepository outboxRepository,
                         EventProperties properties,
                         EventPublishGateway syncPublishGateway) {
        this.outboxRepository = outboxRepository;
        this.properties = properties;
        this.syncPublishGateway = syncPublishGateway;
    }

    /**
     * 追加事件到 Outbox（在当前数据库事务中执行）
     *
     * @param aggregateType 聚合根类型
     * @param aggregateId   聚合根 ID
     * @param eventType     事件类型
     * @param payload       事件负载（JSON）
     */
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
     */
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
     * 追加事件到 Outbox（完整参数）
     *
     * @param aggregateType    聚合根类型
     * @param aggregateId      聚合根 ID
     * @param eventType        事件类型
     * @param payload          事件负载（JSON）
     * @param headers          扩展头
     * @param priority         优先级（0-9，9 最高）
     * @param schemaVersion    Schema 版本号
     * @param contentType      内容类型
     * @param deduplicationId  幂等去重 ID（null 表示自动生成）
     */
    @Transactional
    public void appendToOutbox(String aggregateType, String aggregateId,
                               String eventType, String payload,
                               Map<String, String> headers,
                               int priority, String schemaVersion,
                               String contentType, String deduplicationId) {
        appendToOutbox(OutboxMessage.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payload)
                .headers(headers)
                .priority(priority)
                .schemaVersion(schemaVersion)
                .contentType(contentType)
                .deduplicationId(deduplicationId));
    }

    /**
     * 追加事件到 Outbox（基于 Builder 模式，自动填充系统字段）
     *
     * <p>此方法自动填充以下字段：
     * <ul>
     *   <li>id - UUID</li>
     *   <li>tenantId - 从 RequestContext 获取</li>
     *   <li>traceId - 从 RequestContext / MDC 获取</li>
     *   <li>deduplicationId - 若未指定，基于内容自动生成</li>
     *   <li>schemaVersion - 若未指定，使用默认值</li>
     *   <li>priority - 若未指定，使用默认值</li>
     *   <li>status - PENDING</li>
     *   <li>时间戳 - 当前时间</li>
     * </ul>
     *
     * @param partialBuilder 部分填充的 Builder（业务字段）
     */
    @Transactional
    public void appendToOutbox(OutboxMessage.OutboxMessageBuilder partialBuilder) {
        // payload 大小校验
        validatePayloadSize(partialBuilder);

        Instant now = Instant.now();
        String tenantId = resolveTenantId();
        String traceId = resolveTraceId();
        String deduplicationId = resolveDeduplicationId(partialBuilder);

        // 幂等去重检查
        if (deduplicationId != null && outboxRepository.existsByDeduplicationId(deduplicationId)) {
            log.info("Outbox message skipped (duplicate): aggregateType={}, aggregateId={}, eventType={}, deduplicationId={}",
                    partialBuilder.build().getAggregateType(),
                    partialBuilder.build().getAggregateId(),
                    partialBuilder.build().getEventType(),
                    deduplicationId);
            return;
        }

        OutboxMessage message = partialBuilder
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .traceId(traceId)
                .deduplicationId(deduplicationId)
                .schemaVersion(partialBuilder.build().getSchemaVersion() != null
                        ? partialBuilder.build().getSchemaVersion()
                        : properties.getDefaultSchemaVersion())
                .priority(partialBuilder.build().getPriority() != 0
                        ? partialBuilder.build().getPriority()
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

        // 注册同步投递回调（事务提交后立即投递）
        if (properties.isEnableSyncPublish() && syncPublishGateway != null) {
            registerSyncPublishCallback(message);
        }
    }

    /**
     * 校验 payload 大小
     *
     * @param builder 消息 Builder
     * @throws IllegalArgumentException payload 超过最大限制
     */
    private void validatePayloadSize(OutboxMessage.OutboxMessageBuilder builder) {
        String payload = builder.build().getPayload();
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
     * 从 RequestContext 获取租户 ID
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
     * 从 RequestContext / MDC 获取链路追踪 ID
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
     * 生成幂等去重 ID（基于内容 SHA-256 哈希）
     *
     * @param builder 消息 Builder
     * @return 去重 ID，若 Builder 已指定则直接返回
     */
    private String resolveDeduplicationId(OutboxMessage.OutboxMessageBuilder builder) {
        OutboxMessage partial = builder.build();
        if (partial.getDeduplicationId() != null && !partial.getDeduplicationId().isBlank()) {
            return partial.getDeduplicationId();
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
            return UUID.randomUUID().toString();
        }
    }

    /**
     * 注册事务提交后的同步投递回调
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
            @Override
            public void afterCommit() {
                doSyncPublish(message);
            }
        });
    }

    /**
     * 执行同步投递
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
            log.warn("Sync publish failed, message will be picked up by async poller: id={}, error={}",
                    message.getId(), e.getMessage());
        }
    }
}
