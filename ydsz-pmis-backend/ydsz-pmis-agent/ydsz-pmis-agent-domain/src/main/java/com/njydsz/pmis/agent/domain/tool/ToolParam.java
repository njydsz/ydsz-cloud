package com.njydsz.pmis.agent.domain.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具参数注解（描述方法参数的用途）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ToolParam {

    /**
     * 参数描述
     */
    String value() default "";

    /**
     * 是否必填
     */
    boolean required() default true;
}
