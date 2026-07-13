package com.njydsz.pmis.common.excel.annotation;

/**
 * ContentStyle 类
 *
 * @author ydsz-pmis-team
 * @email pmis-dev@njydsz.com
 * @version 1.0.0
 */
import java.lang.annotation.*;

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