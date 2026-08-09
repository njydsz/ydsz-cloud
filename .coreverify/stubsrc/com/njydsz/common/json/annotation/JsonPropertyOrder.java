package com.njydsz.common.json.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JsonPropertyOrder {
    String[] value() default {};
    boolean alphabetic() default false;
}
