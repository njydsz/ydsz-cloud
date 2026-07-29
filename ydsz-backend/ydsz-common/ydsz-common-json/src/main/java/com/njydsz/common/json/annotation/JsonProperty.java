package com.njydsz.common.json.annotation;

import java.lang.annotation.*;

/**
 * Jackson 兼容注解：指定 JSON 属性名称。
 *
 * <p>等价于 {@link YdszJsonField} 的 {@code value} 属性。当项目中同时存在 Jackson 和 YdszJson
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
 * @since 1.3.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface JsonProperty {

    /**
     * JSON 属性名称
     *
     * @return 属性名称
     */
    String value() default "";
}
