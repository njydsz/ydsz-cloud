package com.njydsz.pmis.common.json.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bean 属性可见性注解
 *
 * <p>用于控制哪些字段/方法在序列化/反序列化时可见，对标 Jackson @JsonAutoDetect。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 类级别：仅序列化公开字段
 * &#064;JsonVisibility(fields = Visibility.PUBLIC_ONLY)
 * public class User {
 *     private String name;        // 不可见（私有）
 *     public int age;             // 可见（公开）
 * }
 *
 * // 字段级别：强制可见
 * &#064;JsonField(visible = true)
 * private String secretKey;       // 强制可见
 * </pre>
 *
 * @since 1.3.0
 * @since 1.3.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
public @interface JsonVisibility {

    /**
     * 字段可见性级别
     */
    Visibility fields() default Visibility.ANY;

    /**
     * Getter 方法可见性级别
     */
    Visibility getters() default Visibility.ANY;

    /**
     * Setter 方法可见性级别
     */
    Visibility setters() default Visibility.ANY;

    /**
     * 可见性级别枚举
     */
    enum Visibility {
        /**
         * 所有字段/方法都可见（默认）
         */
        ANY,

        /**
         * 仅公开（public）字段/方法可见
         */
        PUBLIC_ONLY,

        /**
         * 公开、保护（protected）和包私有字段/方法可见
         */
        PROTECTED_AND_PUBLIC,

        /**
         * 无字段/方法可见（全部隐藏）
         */
        NONE
    }
}
