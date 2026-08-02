package com.njydsz.common.json.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.njydsz.common.json.api.JsonDeserializer;

/**
 * Jackson 兼容注解：指定自定义反序列化器。
 *
 * <p>标注在类上，通过 {@code using} 指定一个实现 {@link JsonDeserializer} 的自定义反序列化器，
 * 反序列化该类时由 {@code DeserializationProvider} 优先调用自定义反序列化器，
 * 替代默认的反射字段赋值逻辑。</p>
 *
 * <p>使用示例：</p>
 * <pre><code>
 * {@literal @}JsonDeserialize(using = MoneyDeserializer.class)
 * public class Money {
 *     private final long cents;
 *     // ...
 * }
 *
 * public class MoneyDeserializer implements JsonDeserializer{@literal <}Money{@literal >} {
 *     {@literal @}Override
 *     public Money deserialize(String json, Class{@literal <}Money{@literal >} type) {
 *         return new Money((long) (Double.parseDouble(json.replace("\"", "")) * 100));
 *     }
 * }
 * </code></pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see JsonDeserializer
 * @see JsonSerialize
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JsonDeserialize {

    /**
     * 自定义反序列化器实现类。
     *
     * <p>必须实现 {@link JsonDeserializer} 接口并提供公开无参构造函数。
     * 默认为 {@code Void.class} 表示不使用自定义反序列化器（走默认反序列化路径）。</p>
     *
     * @return 反序列化器实现类
     */
    Class<?> using() default Void.class;
}
