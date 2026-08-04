package com.remisoft.common.json.annotation;

import java.lang.annotation.*;

/**
 * Jackson 兼容注解：标记 getter 方法为 JSON 序列化属性。
 *
 * <p>标注在 getter 方法上时，该方法的返回值将作为 JSON 属性输出。
 * 可通过 {@code value} 指定 JSON 属性名，覆盖默认的字段名映射。</p>
 *
 * <p>使用示例：</p>
 * <pre><code>
 * public class User {
 *     private String firstName;
 *     private String lastName;
 *
 *     // 计算属性：序列化时输出 fullName
 *     &#64;JsonGetter("fullName")
 *     public String getFullName() {
 *         return firstName + " " + lastName;
 *     }
 * }
 * </code></pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface JsonGetter {

    /**
     * JSON 属性名称（空字符串表示使用方法名推断）
     *
     * @return 属性名称
     */
    String value() default "";
}
