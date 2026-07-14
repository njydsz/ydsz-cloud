package com.njydsz.pmis.common.domain.event;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;


/**
 * 领域事件发布。
 *
 * <p>基于 Spring {@link ApplicationEventPublisher} 的领域事件发布机制。
 * 业务代码通过调用 {@link #publish(DomainEvent)} 方法发布领域事件。
 * 通过 {@link #publishAll(Iterable)} 方法批量发布领域事件。
 *
 * <p>本类为单。Bean，线程安全。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * &#64;Autowired
 * private DomainEventPublisher domainEventPublisher;
 *
 * // 单个发布
 * domainEventPublisher.publish(new OrderCreatedEvent(orderId));
 *
 * // 批量发布
 * domainEventPublisher.publishAll(order.getDomainEvents());
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 构造领域事件发布器
     *
     * @param eventPublisher Spring 应用事件发布。
     */
    public DomainEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * 发布领域事件
     *
     * <p>将领域事件发布到 Spring 应用上下文，触发所有匹配的监听器。
     * 事件发布是同步的，监听器按顺序执行。
     *
     * @param event 领域事件，不能为 null
     * @throws NullPointerException 。event 。null 时抛出
     */
    public void publish(DomainEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        log.debug("Publishing domain event: type={}", event.getEventType());
        eventPublisher.publishEvent(event);
    }

    /**
     * 批量发布领域事件
     *
     * <p>按顺序逐个发布事件，任意事件发布失败将中断后续发布。
     * 适用于聚合根提交时一次性发布所有领域事件的场景。
     *
     * @param events 领域事件列表，不能为 null
     * @throws NullPointerException 。events 。null 时抛出
     */
    public void publishAll(Iterable<DomainEvent> events) {
        Objects.requireNonNull(events, "events must not be null");
        for (DomainEvent event : events) {
            publish(event);
        }
    }
}
