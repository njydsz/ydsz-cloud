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
 * <p>标注在类上，通过 {@code using} 指定自定义序列化器，
 * 序列化该类实例时由 {@code SerializationProvider} 优先调用自定义序列化器，
 * 替代默认的 Bean 字段反射序列化逻辑。</p>
 *
 * <p>支持两种序列化器接口：</p>
 * <ul>
 *   <li><b>推荐：</b> {@link com.njydsz.common.json.serializer.JsonSerializer}（JSONWriter 版，零拷贝）</li>
 *   <li><b>兼容：</b> {@link JsonSerializer}（String 返回版，已废弃）</li>
 * </ul>
 *
 * <p>使用示例（推荐写法）：</p>
 * <pre><code>
 * {@literal @}JsonSerialize(using = MoneySerializer.class)
 * public class Money {
 *     private final long cents;
 * }
 *
 * public class MoneySerializer implements com.njydsz.common.json.serializer.JsonSerializer{@literal <}Money{@literal >} {
 *     {@literal @}Override
 *     public void serialize(Money value, JSONWriter out) {
 *         out.writeString(String.format("%.2f", value.getCents() / 100.0));
 *     }
 * }
 * </code></pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see JsonSerializer 旧版 SPI（已废弃，请迁移到 serializer.JsonSerializer）
 * @see com.njydsz.common.json.serializer.JsonSerializer 新版 SPI（推荐）
 * @see JsonDeserialize
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SuppressWarnings("deprecation")
public @interface JsonSerialize {

    /**
     * 自定义序列化器实现类。
     *
     * <p>必须实现 {@link JsonSerializer}（旧版）或
     * {@link com.njydsz.common.json.serializer.JsonSerializer}（新版推荐）。
     * 提供公开无参构造函数。默认为 {@code Void.class} 表示不使用自定义序列化器。</p>
     *
     * @return 序列化器实现类
     */
    Class<?> using() default Void.class;
}
