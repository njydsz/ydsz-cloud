package com.njydsz.common.json.annotation;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface JsonClass {
    String description() default "";
    String[] ordering() default {};
    String[] ignores() default {};
    String[] includes() default {};
    NamingStrategy naming() default NamingStrategy.CAMEL_CASE;
    boolean writeClassName() default false;
    String dateFormat() default "";
    boolean writeNulls() default false;
    boolean serializeEnumUsingOrdinal() default false;
    String typeKey() default "@type";
    Class<?>[] seeAlso() default {};
    String[] seeAlsoNames() default {};
    boolean autoType() default false;

    enum NamingStrategy {
        CAMEL_CASE,
        SNAKE_CASE,
        KEBAB_CASE,
        ORIGINAL;
        public com.njydsz.common.json.naming.PropertyNamingStrategy toPropertyNamingStrategy() {
            switch (this) {
                case SNAKE_CASE: return com.njydsz.common.json.naming.PropertyNamingStrategy.SNAKE_CASE;
                case KEBAB_CASE: return com.njydsz.common.json.naming.PropertyNamingStrategy.KEBAB_CASE;
                case CAMEL_CASE: return com.njydsz.common.json.naming.PropertyNamingStrategy.LOWER_CAMEL_CASE;
                default: return null;
            }
        }
    }
}
