package com.njydsz.common.json.annotation;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
public @interface JsonInclude {
    enum Include {
        ALWAYS,
        NON_NULL,
        NON_EMPTY,
        NON_DEFAULT,
        USE_DEFAULTS
    }
    Include value() default Include.ALWAYS;
}
