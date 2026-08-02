package com.njydsz.common.json.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.njydsz.common.json.api.JsonSerializer;

/**
 * Jackson 兼容注解：指定自定义序列化器。
 *
 * <p>标注在类上，通过 {@code using} 指定一个实现 {@link JsonSerializer} 的自定义序列化器，
 * 序列化该类实例时由 {@code SerializationProvider} 优先调用自定义序列化器，
 * 替代默认的 Bean 字段反射序列化逻辑。</p>
 *
 * <p>使用示例：</p>
 * <pre><code>
 * {@literal @}JsonSerialize(using = MoneySerializer.class)
 * public class Money {
 *     private final long cents;
 *     // ...
 * }
 *
 * public class MoneySerializer implements JsonSerializer{@literal <}Money{@literal >} {
 *     {@literal @}Override
 *     public String serialize(Money value) {
 *         return "\"" + (value.getCents() / 100.0) + "\"";
 *     }
 * }
 * </code></pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see JsonSerializer
 * @see JsonDeserialize
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JsonSerialize {

    /**
     * 自定义序列化器实现类。
     *
     * <p>必须实现 {@link JsonSerializer} 接口并提供公开无参构造函数。
     * 默认为 {@code Void.class} 表示不使用自定义序列化器（走默认序列化路径）。</p>
     *
     * @return 序列化器实现类
     */
    Class<?> using() default Void.class;
}
