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
 * <p>标注在类上，通过 {@code using} 指定自定义反序列化器，
 * 反序列化该类时由 {@code DeserializationProvider} 优先调用自定义反序列化器，
 * 替代默认的反射字段赋值逻辑。</p>
 *
 * <p>支持两种反序列化器接口：</p>
 * <ul>
 *   <li><b>推荐：</b> {@link com.njydsz.common.json.deserializer.JsonDeserializer}（JSONReader 流式解析版）</li>
 *   <li><b>兼容：</b> {@link JsonDeserializer}（String 入参版，已废弃）</li>
 * </ul>
 *
 * <p>使用示例（推荐写法）：</p>
 * <pre><code>
 * {@literal @}JsonDeserialize(using = MoneyDeserializer.class)
 * public class Money {
 *     private final long cents;
 * }
 *
 * public class MoneyDeserializer implements com.njydsz.common.json.deserializer.JsonDeserializer{@literal <}Money{@literal >} {
 *     {@literal @}Override
 *     public Money deserialize(JSONReader reader, java.lang.reflect.Type type) {
 *         // 使用 JSONReader 逐 token 流式解析
 *         return new Money(reader.readLong());
 *     }
 * }
 * </code></pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see JsonDeserializer 旧版 SPI（已废弃，请迁移到 deserializer.JsonDeserializer）
 * @see com.njydsz.common.json.deserializer.JsonDeserializer 新版 SPI（推荐）
 * @see JsonSerialize
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SuppressWarnings("deprecation")
public @interface JsonDeserialize {

    /**
     * 自定义反序列化器实现类。
     *
     * <p>必须实现 {@link JsonDeserializer}（旧版）或
     * {@link com.njydsz.common.json.deserializer.JsonDeserializer}（新版推荐）。
     * 提供公开无参构造函数。默认为 {@code Void.class} 表示不使用自定义反序列化器。</p>
     *
     * @return 反序列化器实现类
     */
    Class<?> using() default Void.class;
}
