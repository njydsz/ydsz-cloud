package com.remisoft.common.domain.event;

import java.lang.reflect.Method;
import java.time.Instant;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.remisoft.common.domain.entity.BaseEntity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 领域事件 AOP 切面（自动注册 + 发布领域事件）。
 *
 * <p>拦截标注了 {@link DomainEvent} 的方法，在方法执行后自动将事件注册到聚合根的 domainEvents 列表，
 * 并通过 {@link ApplicationEventPublisher} 发布 Spring 应用事件。
 *
 * <p><b>设计参考：</b>
 * <ul>
 *   <li>Spring Data {@code DomainEventPublications} — 声明式事件发布</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public class Order extends BaseEntity<Long> {
 *     @DomainEvent("OrderPaid")
 *     public void pay() {
 *         // 业务逻辑...
 *         // 执行后自动注册领域事件并发布
 *     }
 * }
 * }</pre>
 *
 * @author remi-team
 * @since 1.7.0
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DomainEventAspect {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 拦截标注 @DomainEvent 的方法。
     *
     * @param joinPoint 切点
     * @param domainEvent 注解
     * @return 方法返回值
     * @throws Throwable 方法执行异常
     */
    @Around("@annotation(domainEvent)")
    public Object handleDomainEvent(ProceedingJoinPoint joinPoint, DomainEvent domainEvent) throws Throwable {
        // 获取目标对象（聚合根）
        Object target = joinPoint.getTarget();

        // 先执行目标方法
        Object result = joinPoint.proceed();

        // 检查是否为 BaseEntity 子类
        if (!(target instanceof BaseEntity)) {
            if (log.isDebugEnabled()) {
                log.debug("@DomainEvent 标注的方法不在 BaseEntity 子类中，跳过事件注册: {}",
                        target.getClass().getName());
            }
            return result;
        }

        BaseEntity<?> aggregate = (BaseEntity<?>) target;

        // 构建领域事件
        String eventType = domainEvent.value().isEmpty()
                ? getDefaultEventType(joinPoint)
                : domainEvent.value();

        DomainEventPayload eventPayload = new DomainEventPayload(
                eventType,
                aggregate.getId(),
                Instant.now(),
                Thread.currentThread().getName()
        );

        // 注册到聚合根事件列表
        aggregate.registerEvent(eventPayload);

        // 发布 Spring 应用事件
        eventPublisher.publishEvent(eventPayload);

        if (log.isDebugEnabled()) {
            log.debug("领域事件已注册: eventType={}, aggregateId={}, aggregateType={}",
                    eventType, aggregate.getId(), aggregate.getClass().getSimpleName());
        }

        return result;
    }

    /**
     * 获取默认事件类型（使用类名 + 方法名）。
     */
    private String getDefaultEventType(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    /**
     * 领域事件负载对象。
     *
     * <p>封装事件元数据，携带聚合根 ID、事件类型、时间戳等信息。
     */
    public record DomainEventPayload(
            String eventType,
            Object aggregateId,
            Instant occurredAt,
            String threadName
    ) {
    }
}
