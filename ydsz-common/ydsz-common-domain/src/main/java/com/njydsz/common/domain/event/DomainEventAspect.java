package com.njydsz.common.domain.event;

import java.lang.reflect.Method;
import java.time.Instant;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 领域事件 AOP 切面（自动注册 + 发布领域事件）。
 *
 * <p>拦截标注了 {@link DomainEvent} 的方法，在方法执行后自动将事件注册到
 * {@link EventRegistry} 实例，并通过 {@link ApplicationEventPublisher} 发布 Spring 应用事件。
 *
 * <p><b>v1.8.0 变更：</b>不再强制要求目标对象继承 {@code BaseEntity}，
 * 改为检查 {@link EventRegistry} 接口，任何实现此接口的类均可使用 {@code @DomainEvent}。
 *
 * <p><b>设计参考：</b>
 * <ul>
 *   <li>Spring Data {@code DomainEventPublications} — 声明式事件发布</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public class Order implements EventRegistry {
 *     &#64;DomainEvent("OrderPaid")
 *     public void pay() {
 *         // 业务逻辑...
 *         // 执行后自动注册领域事件并发布
 *     }
 *
 *     // 实现 EventRegistry 接口方法...
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.7.0
 * @since 1.8.0 改为检查 EventRegistry 接口，解耦 BaseEntity 依赖
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
        // 获取目标对象
        Object target = joinPoint.getTarget();

        // 先执行目标方法
        Object result = joinPoint.proceed();

        // 检查是否实现了 EventRegistry 接口
        if (!(target instanceof EventRegistry registry)) {
            if (log.isDebugEnabled()) {
                log.debug("@DomainEvent 标注的方法不在 EventRegistry 实现类中，跳过事件注册: {}",
                        target.getClass().getName());
            }
            return result;
        }

        // 构建领域事件
        String eventType = domainEvent.value().isEmpty()
                ? getDefaultEventType(joinPoint)
                : domainEvent.value();

        // 尝试获取聚合根 ID（如果有 getId 方法）
        Object aggregateId = extractAggregateId(target);

        DomainEventPayload eventPayload = new DomainEventPayload(
                eventType,
                aggregateId,
                Instant.now(),
                Thread.currentThread().getName()
        );

        // 注册到 EventRegistry
        registry.registerEvent(eventPayload);

        // 发布 Spring 应用事件
        eventPublisher.publishEvent(eventPayload);

        if (log.isDebugEnabled()) {
            log.debug("领域事件已注册: eventType={}, aggregateId={}, target={}",
                    eventType, aggregateId, target.getClass().getSimpleName());
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
     * 尝试提取聚合根 ID。
     *
     * <p>通过反射调用 getId() 方法获取，如果失败则返回 null。
     */
    private Object extractAggregateId(Object target) {
        try {
            Method getIdMethod = target.getClass().getMethod("getId");
            return getIdMethod.invoke(target);
        } catch (Exception e) {
            // 无 getId 方法，返回 null
            return null;
        }
    }

    /**
     * 领域事件负载对象。
     *
     * <p>封装事件元数据，携带聚合根 ID、事件类型、时间戳等信息。
     *
     * <p><b>序列化说明：</b>
     * <ul>
     *   <li>当前仅用于 Spring {@link ApplicationEventPublisher} 进程内事件发布</li>
     *   <li>record 的不可变性保证领域事件不会被篡改</li>
     *   <li>如需跨 JVM 传播（如 Spring Cloud Stream、Kafka），需迁移为带 {@code @class} 类型信息的 POJO</li>
     * </ul>
     */
    public record DomainEventPayload(
            String eventType,
            Object aggregateId,
            Instant occurredAt,
            String threadName
    ) {
    }
}
