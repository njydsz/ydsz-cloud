package com.njydsz.pmis.common.excel.annotation;

/**
 * ContentFont 类
 *
 * @author ydsz-pmis-team
 * @email pmis-dev@njydsz.com
 * @version 1.0.0
 */
import java.lang.annotation.*;

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