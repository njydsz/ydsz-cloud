package com.njydsz.common.event.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.domain.event.DomainEvent;
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
 * @author ydsz-team
 * @since 1.0.0
 */
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxRepository outboxRepository;
    private final EventProperties properties;

    /** 同步投递网关（可选，仅 enableSyncPublish=true 时注入） */
    private final EventPublishGateway syncPublishGateway;

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
     */
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
        if (event.getVersion() > 0) {
            headers.put("_eventVersion", String.valueOf(event.getVersion()));
        }
        if (event.getUserId() != null) {
            headers.put("_userId", event.getUserId());
        }

        appendToOutbox(OutboxMessage.builder()
                .aggregateType(event.getAggregateType())
                .aggregateId(event.getAggregateId())
                .eventType(event.getEventType())
                .payload(YdszJson.toJson(event))
                .headers(headers)
                .deduplicationId(event.getEventId())
                .tenantId(event.getTenantId())
                .traceId(event.getTraceId()));
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
     * @param deduplicationId  幂等去重 ID（null 表示不自动生成，除非 auto-dedup=true）
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
                .id(UUID.randomUUID().toString())
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

        // 注册同步投递回调（事务提交后立即投递）
        if (properties.isEnableSyncPublish() && syncPublishGateway != null) {
            registerSyncPublishCallback(message);
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
            return UUID.randomUUID().toString();
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
            @Override
            public void afterCommit() {
                doSyncPublish(message);
            }
        });
    }

    /**
     * 执行同步投递
     *
     * <p>投递成功则标记为 SENT，失败则由异步轮询器后续处理。
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
