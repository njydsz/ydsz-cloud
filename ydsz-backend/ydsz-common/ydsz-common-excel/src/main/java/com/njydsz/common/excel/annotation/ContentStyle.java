package com.njydsz.common.excel.annotation;

/**
 * ContentStyle 类
 *
 * @author ydsz-team
 * @email ydsz-dev@njydsz.com
 * @version 1.0.0
 */
import java.lang.annotation.*;

/**
 * ContentStyle 注解类型，提供声明式标记能力。
 *
 * <p>所属包：{@code com.njydsz.common.excel.annotation}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ContentStyle {

    boolean hidden() default false;

    boolean locked() default true;

    short horizontalAlignment() default -1;

    short verticalAlignment() default -1;

    short backgroundColor() default -1;

    String dataFormat() default "";

    boolean wrapText() default false;

    boolean shrinkToFit() default false;
}