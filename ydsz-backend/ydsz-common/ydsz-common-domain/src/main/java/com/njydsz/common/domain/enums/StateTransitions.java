package com.njydsz.common.domain.enums;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link StateTransition} 的容器注解（支持同一枚举常量上标注多个流转）。
 *
 * <p>由 Java 8+ 的 {@code @Repeatable} 机制自动生成，通常无需显式使用。
 *
 * @author ydsz-team
 * @since 1.4.0
 *
 * @see StateTransition
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface StateTransitions {

    /**
     * 状态流转声明列表
     *
     * @return 流转注解数组
     */
    StateTransition[] value();
}
