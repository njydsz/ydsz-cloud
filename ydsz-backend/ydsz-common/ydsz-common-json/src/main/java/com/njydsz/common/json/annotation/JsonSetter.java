package com.njydsz.common.json.annotation;

import java.lang.annotation.*;

/**
 * Jackson 兼容注解：标记 setter 方法为 JSON 反序列化属性。
 *
 * <p>标注在 setter 方法上时，反序列化时将通过该方法设置属性值。
 * 可通过 {@code value} 指定 JSON 属性名，覆盖默认的字段名映射。</p>
 *
 * <p>使用示例：</p>
 * <pre><code>
 * public class User {
 *     private String name;
 *
 *     &#64;JsonSetter("user_name")
 *     public void setName(String name) {
 *         this.name = name != null ? name.trim() : null;
 *     }
 * }
 * </code></pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Deprecated(since = "1.0.0", forRemoval = true)
public @interface JsonSetter {

    /**
     * JSON 属性名称（空字符串表示使用方法名推断）
     *
     * @return 属性名称
     */
    String value() default "";
}
