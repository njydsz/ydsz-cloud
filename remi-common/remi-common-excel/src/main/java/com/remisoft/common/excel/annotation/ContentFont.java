package com.remisoft.common.excel.annotation;

/**
 * ContentFont 类
 *
 * @author remi-team
 * @email remi-dev@remisoft.com
 * @version 1.0.0
 */
import java.lang.annotation.*;

/**
 * ContentFont 注解类型，提供声明式标记能力。
 *
 * <p>所属包：{@code com.remisoft.common.excel.annotation}
 *
 * @author remi-team
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ContentFont {

    String fontName() default "Calibri";

    int fontSize() default 11;

    boolean bold() default false;

    boolean italic() default false;

    short color() default 0;
}