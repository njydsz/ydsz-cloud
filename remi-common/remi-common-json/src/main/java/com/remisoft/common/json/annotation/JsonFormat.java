package com.remisoft.common.json.annotation;

import java.lang.annotation.*;

/**
 * Jackson 兼容注解：指定日期/数字的序列化格式。
 *
 * <p>指定字段序列化/反序列化时的格式模式。当项目中同时存在 Jackson 和 RemiJson
 * 时，可使用此注解替代 {@code @JsonFormat(com.fasterxml.jackson.annotation.JsonFormat)}，
 * 避免引入 Jackson 依赖。</p>
 *
 * <p>使用示例：</p>
 * <pre><code>
 * public class User {
 *     &#64;JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
 *     private LocalDateTime createTime;
 *
 *     &#64;JsonFormat(pattern = "yyyy-MM-dd")
 *     private LocalDate birthday;
 * }
 * </code></pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface JsonFormat {

    /**
     * 日期/数字格式模式
     *
     * @return 格式字符串
     */
    String pattern() default "";

    /**
     * 格式化的形状（目前仅支持 STRING，预留兼容性）
     *
     * @return 形状
     */
    Shape shape() default Shape.ANY;

    /**
     * 区域设置（预留兼容性，暂未实现）
     *
     * @return 区域设置字符串
     */
    String locale() default "";

    /**
     * 时区（预留兼容性，暂未实现）
     *
     * @return 时区字符串
     */
    String timezone() default "";

    /**
     * 是否宽松解析（对标 Jackson {@code JsonFormat.lenient}）。
     *
     * <p>默认 false，采用严格解析（如日期格式不匹配时抛出异常）。
     * 设为 true 时允许更宽松的解析规则，例如容忍多余字段或可空的可选项。</p>
     *
     * @return 是否宽松解析
     */
    boolean lenient() default false;

    /**
     * 格式化形状枚举
     */
    enum Shape {
        ANY,
        STRING,
        NUMBER
    }
}
