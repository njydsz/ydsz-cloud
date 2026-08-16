package com.njydsz.common.json.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Jackson 兼容注解：指定 JSON 属性名称。
 *
 * <p>等价于字段级 {@code value} 属性。当项目中同时存在 Jackson 和 YdszJson
 * 时，可使用此注解替代 {@code @JsonProperty(com.fasterxml.jackson.annotation.JsonProperty)}，
 * 避免引入 Jackson 依赖。</p>
 *
 * <p>使用示例：</p>
 * <pre><code>
 * public class User {
 *     &#64;JsonProperty("user_id")
 *     private Long id;
 *
 *     &#64;JsonProperty("user_name")
 *     private String name;
 * }
 * </code></pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface JsonProperty {

    /**
     * JSON 属性名称
     *
     * @return 属性名称
     */
    String value() default "";

    /**
     * 是否必需（反序列化时字段缺失将抛出异常）。
     *
     * <p>对标 Jackson {@code JsonProperty.required()}。默认 false，缺失字段保持默认值。</p>
     *
     * @return 是否必需
     */
    boolean required() default false;

    /**
     * 默认值（反序列化时字段缺失时使用此值的字符串形式）。
     *
     * <p>对标 Jackson {@code JsonProperty.defaultValue()}。空字符串表示无默认值。</p>
     *
     * @return 默认值字符串
     */
    String defaultValue() default "";

    /**
     * 访问模式（控制字段参与序列化/反序列化的方向）。
     *
     * <p>对标 Jackson {@code JsonProperty.Access()}。默认 AUTO，遵循全局可见性配置。</p>
     *
     * @return 访问模式
     */
    Access access() default Access.AUTO;

    /**
     * 访问模式枚举（与 Jackson 一致）。
     */
    enum Access {
        /** 自动：遵循全局可见性配置 */
        AUTO,
        /** 仅读：参与反序列化，不参与序列化 */
        READ_ONLY,
        /** 仅写：参与序列化，不参与反序列化 */
        WRITE_ONLY,
        /** 读写：同时参与序列化和反序列化 */
        READ_WRITE
    }
}
