package com.njydsz.common.domain.event;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 领域事件发布器
 *
 * <p>基于 Spring {@link ApplicationEventPublisher} 的领域事件发布机制。
 * 支持三种发布模式：
 * <ul>
 *   <li><b>同步发布（默认）</b>：事件在当前线程同步发布，监听器按顺序执行</li>
 *   <li><b>异步发布</b>：通过 {@link TaskExecutor} 在独立线程池中发布，不阻塞当前事务</li>
 *   <li><b>事务后发布</b>：在当前事务成功提交后再发布事件，确保数据一致性</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * &#64;Autowired
 * private DomainEventPublisher domainEventPublisher;
 *
 * // 同步发布
 * domainEventPublisher.publish(new OrderCreatedEvent(orderId));
 *
 * // 异步发布
 * domainEventPublisher.publishAsync(new OrderCreatedEvent(orderId));
 *
 * // 事务提交后发布（推荐）
 * domainEventPublisher.publishAfterCommit(new OrderCreatedEvent(orderId));
 *
 * // 批量发布
 * domainEventPublisher.publishAll(order.getDomainEvents());
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final ApplicationEventPublisher eventPublisher;

    private final TaskExecutor taskExecutor;

    /**
     * 构造领域事件发布器（同步模式）
     *
     * @param eventPublisher Spring 应用事件发布器
     */
    public DomainEventPublisher(ApplicationEventPublisher eventPublisher) {
        this(eventPublisher, null);
    }

    /**
     * 构造领域事件发布器（支持异步模式）
     *
     * @param eventPublisher Spring 应用事件发布器
     * @param taskExecutor   异步任务执行器，为 null 时退化为同步模式
     */
    public DomainEventPublisher(ApplicationEventPublisher eventPublisher, TaskExecutor taskExecutor) {
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.taskExecutor = taskExecutor;
    }

    /**
     * 发布领域事件（同步）
     *
     * <p>将领域事件发布到 Spring 应用上下文，触发所有匹配的监听器。
     * 事件发布是同步的，监听器按顺序执行。
     *
     * @param event 领域事件，不能为 null
     */
    public void publish(DomainEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        log.debug("Publishing domain event (sync): type={}, eventId={}",
                  event.getEventType(), event.getEventId());
        eventPublisher.publishEvent(event);
    }

    /**
     * 异步发布领域事件
     *
     * <p>通过 {@link TaskExecutor} 在独立线程中发布事件，不阻塞当前线程。
     * 如果未配置 TaskExecutor，则退化为同步发布。
     *
     * @param event 领域事件，不能为 null
     */
    public void publishAsync(DomainEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        if (taskExecutor == null) {
            log.warn("TaskExecutor not configured, fallback to sync publish for event: {}",
                     event.getEventType());
            publish(event);
            return;
        }
        log.debug("Publishing domain event (async): type={}, eventId={}",
                  event.getEventType(), event.getEventId());
        taskExecutor.execute(() -> {
            try {
                eventPublisher.publishEvent(event);
            } catch (Exception e) {
                log.error("Failed to publish domain event async: type={}, eventId={}",
                          event.getEventType(), event.getEventId(), e);
            }
        });
    }

    /**
     * 在事务提交后发布领域事件
     *
     * <p>注册 Spring {@link TransactionSynchronization} 回调，
     * 在当前事务成功提交后再发布事件。如果当前没有活跃的事务，则直接同步发布。
     *
     * <p><b>使用场景：</b>确保数据库变更和事件发布的一致性，
     * 避免事务回滚但事件已发出的问题。
     *
     * @param event 领域事件，不能为 null
     */
    public void publishAfterCommit(DomainEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.debug("No active transaction, publish synchronously: type={}", event.getEventType());
            publish(event);
            return;
        }
        log.debug("Registering domain event for post-commit publish: type={}, eventId={}",
                  event.getEventType(), event.getEventId());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.debug("Publishing domain event after commit: type={}, eventId={}",
                          event.getEventType(), event.getEventId());
                try {
                    eventPublisher.publishEvent(event);
                } catch (Exception e) {
                    log.error("Failed to publish domain event after commit: type={}, eventId={}",
                              event.getEventType(), event.getEventId(), e);
                }
            }
        });
    }

    /**
     * 批量发布领域事件（同步）
     *
     * <p>按顺序逐个发布事件，任意事件发布失败将中断后续发布。
     *
     * @param events 领域事件列表，不能为 null
     */
    public void publishAll(Iterable<DomainEvent> events) {
        Objects.requireNonNull(events, "events must not be null");
        for (DomainEvent event : events) {
            publish(event);
        }
    }

    /**
     * 批量发布领域事件（事务提交后）
     *
     * <p>将所有事件注册为事务后发布。如果当前没有活跃的事务，则直接同步发布。
     *
     * @param events 领域事件列表，不能为 null
     */
    public void publishAllAfterCommit(Iterable<DomainEvent> events) {
        Objects.requireNonNull(events, "events must not be null");
        for (DomainEvent event : events) {
            publishAfterCommit(event);
        }
    }

    /**
     * 判断是否支持异步发布
     *
     * @return 配置了 TaskExecutor 返回 true
     */
    public boolean isAsyncSupported() {
        return taskExecutor != null;
    }
}
