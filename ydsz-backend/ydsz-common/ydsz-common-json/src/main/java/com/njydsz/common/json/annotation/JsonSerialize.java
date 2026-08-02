package com.njydsz.common.json.annotation;

import java.lang.annotation.*;

/**
 * 自定义序列化器（参考 Jackson 的 @JsonSerialize）。
 *
 * <p>标注在类或字段上，指定序列化时使用的自定义序列化器类。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * {@literal @}JsonSerialize(using = MyDateSerializer.class)
 * public class MyDate {
 *     // ...
 * }
 *
 * public class MyDateSerializer implements com.njydsz.common.json.api.JsonSerializer&lt;MyDate&gt; {
 *     {@literal @}Override
 *     public String serialize(MyDate value) {
 *         // 自定义序列化逻辑
 *     }
 * }
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
public @interface JsonSerialize {

    /**
     * 指定自定义序列化器类。
     *
     * <p>该类必须实现 {@code com.njydsz.common.json.api.JsonSerializer} 接口。</p>
     *
     * @return 序列化器类
     */
    Class<?> using() default Void.class;
}
