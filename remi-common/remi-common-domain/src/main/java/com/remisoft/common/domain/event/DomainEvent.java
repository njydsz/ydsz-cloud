package com.remisoft.common.domain.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 领域事件声明注解（对标 Spring Data @DomainEvents）。
 *
 * <p>标注在聚合根方法上，表示方法执行后应自动注册领域事件。
 * 配合 {@link DomainEventAspect} 切面自动捕获状态变更并注册事件。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public class Order extends BaseEntity<Long> {
 *     private String status;
 *
 *     @DomainEvent("OrderStatusChanged")
 *     public void markPaid() {
 *         this.status = "PAID";
 *         // 切面自动注册 OrderStatusChangedEvent
 *     }
 * }
 * }</pre>
 *
 * <p><b>设计参考：</b>
 * <ul>
 *   <li>Spring Data {@code @DomainEvents} — 声明式事件注册</li>
 *   <li>Axon Framework {@code @EventHandler} — 事件处理标记</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.7.0
 * @see DomainEventAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DomainEvent {

    /**
     * 事件类型名称（用于日志、监控、事件路由）。
     *
     * @return 事件类型，默认使用方法名
     */
    String value() default "";

    /**
     * 事件延迟发布（秒）。
     *
     * <p>用于延迟事件场景（如订单超时取消），默认立即发布。
     *
     * @return 延迟秒数，默认 0（立即发布）
     */
    long delaySeconds() default 0;

    /**
     * 是否异步发布。
     *
     * <p>默认同步发布，设为 true 后由线程池异步执行事件处理。
     *
     * @return 异步发布返回 true
     */
    boolean async() default false;
}
