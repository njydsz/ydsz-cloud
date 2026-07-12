package com.njydsz.pmis.common.entity.annotation;

import java.lang.annotation.*;

/**
 * 领域值对象标记注解 —— 标记 DDD 值对象类。
 * <p>
 * 对标 remi-comm @ValueObjectAnno，用于文档化和 ArchUnit 约束校验。
 * </p>
 *
 * @author njydsz
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ValueObjectAnno {

    /**
     * 值对象名称。
     */
    String value() default "";
}
