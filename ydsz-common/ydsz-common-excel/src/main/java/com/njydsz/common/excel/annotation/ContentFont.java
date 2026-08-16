package com.njydsz.common.excel.annotation;

/**
 * ContentFont 类
 *
 * @author ydsz-team
 * @email ydsz-dev@ydszsoft.com
 * @version 1.0.0
 */
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ContentFont 注解类型，提供声明式标记能力。
 *
 * <p>所属包：{@code com.njydsz.common.excel.annotation}
 *
 * @author ydsz-team
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
