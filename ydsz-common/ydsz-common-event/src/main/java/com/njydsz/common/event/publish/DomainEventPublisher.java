package com.njydsz.common.event.publish;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.common.event.service.OutboxService;
import com.njydsz.common.json.YdszJson;

/**
 * 统一领域事件发布门面。
 *
 * <p>消除业务模块重复实现的 publishEvent 私有方法，统一事件发布语义：
 * <ul>
 *   <li>将 {@link DomainEvent} 转换为 {@link OutboxMessage} 并委托 {@link OutboxService} 写入</li>
 *   <li>发布失败仅告警不影响主流程（异步投递由轮询器兜底）</li>
 *   <li>OutboxService 不可用时静默降级（模块未引入 event 包时不影响业务）</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * &#64;Service
 * public class OrderService {
 *     private final DomainEventPublisher eventPublisher;
 *
 *     &#64;Transactional
 *     public void createOrder(OrderCreateDTO dto) {
 *         Order order = orderMapper.insert(dto);
 *         eventPublisher.publish(DomainEvent.builder()
 *             .aggregateType("Order")
 *             .aggregateId(order.getId())
 *             .eventType(DomainEventTypes.ORDER_CREATED)
 *             .metadata("source", "API")
 *             .build());
 *     }
 * }
 * }</pre>
 *
 * <p><b>编码规范要求</b>：业务模块发布跨模块事件时，
 * <b>必须使用本门面</b>，禁止直接注入 {@link OutboxService} 构建 {@link OutboxMessage}。
 *
 * @author ydsz-team
 * @since 1.8.0
 */
@Slf4j
@Component
public class DomainEventPublisher {

    /** Outbox 写入服务（可选依赖，未配置时安全降级） */
    private final ObjectProvider<OutboxService> outboxServiceProvider;

    /**
     * 构造函数。
     *
     * <p>使用 {@code @Lazy} 延迟初始化，避免在 OutboxService 不可用时启动失败。
     *
     * @param outboxServiceProvider Outbox 服务提供者
     */
    public DomainEventPublisher(@Lazy ObjectProvider<OutboxService> outboxServiceProvider) {
        this.outboxServiceProvider = outboxServiceProvider;
    }

    /**
     * 发布领域事件到 Outbox。
     *
     * <p>内部将 {@link DomainEvent} 转换为 {@link OutboxMessage.Builder}，
     * 委托 {@link OutboxService#appendToOutbox} 完成持久化与发布。
     *
     * @param event 领域事件，不可为 null
     */
    public void publish(DomainEvent event) {
        if (event == null) {
            return;
        }
        OutboxService outboxService = outboxServiceProvider.getIfAvailable();
        if (outboxService == null) {
            log.debug("[DomainEventPublisher] OutboxService not available, skip event: type={}, id={}",
                    event.getEventType(), event.getAggregateId());
            return;
        }
        try {
            outboxService.appendToOutbox(OutboxMessage.builder()
                    .aggregateType(event.getAggregateType())
                    .aggregateId(event.getAggregateId())
                    .eventType(event.getEventType())
                    .payload(toJsonPayload(event)));
        } catch (Exception e) {
            log.warn("[DomainEventPublisher] Failed to publish event: type={}, id={}, err={}",
                    event.getEventType(), event.getAggregateId(), e.getMessage());
        }
    }

    /**
     * 将领域事件序列化为 JSON payload。
     *
     * <p>序列化包含 eventId、occurredAt、eventType、aggregateId、aggregateType、metadata 全量字段，
     * 确保消费方能够访问完整的事件元数据。
     *
     * @param event 领域事件
     * @return JSON 字符串
     */
    private String toJsonPayload(DomainEvent event) {
        return YdszJson.toJson(event);
    }
}
