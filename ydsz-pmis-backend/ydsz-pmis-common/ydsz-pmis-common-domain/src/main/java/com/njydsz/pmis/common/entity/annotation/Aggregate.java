package com.njydsz.pmis.common.entity.annotation;

import java.lang.annotation.*;

/**
 * 领域聚合根标记注解 —— 标记 DDD 聚合根类。
 * <p>
 * 对标 remi-comm @Aggregate，用于：
 * <ul>
 *   <li>文档化聚合根边界</li>
 *   <li>ArchUnit 架构约束校验</li>
 *   <li>未来自动装配入口发现</li>
 * </ul>
 * </p>
 *
 * @author njydsz
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Aggregate {

    /**
     * 聚合根名称（默认使用类名）。
     */
    String value() default "";

    /**
     * 所属限界上下文。
     */
    String context() default "";
}
