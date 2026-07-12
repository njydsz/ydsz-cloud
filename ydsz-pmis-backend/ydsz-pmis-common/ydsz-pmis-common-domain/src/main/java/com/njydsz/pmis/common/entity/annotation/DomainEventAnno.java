package com.njydsz.pmis.common.entity.annotation;

import java.lang.annotation.*;

/**
 * 领域事件标记注解 —— 标记 DDD 领域事件类。
 * <p>
 * 对标 remi-comm @DomainEventAnno，领域事件由聚合根通过
 * {@link com.njydsz.pmis.common.entity.RootEntity#registerEvent(Object)} 注册，
 * Repository.save 后统一发布。
 * </p>
 *
 * @author njydsz
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DomainEventAnno {

    /**
     * 事件主题（用于消息队列路由）。
     */
    String topic() default "";

    /**
     * 事件名称。
     */
    String value() default "";
}
