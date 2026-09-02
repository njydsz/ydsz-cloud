package com.njydsz.common.json.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Jackson 兼容注解：指定自定义序列化器。
 *
 * <p>标注在类上，通过 {@code using} 指定自定义序列化器， 序列化该类实例时由 {@code SerializationProvider} 优先调用自定义序列化器， 替代默认的
 * Bean 字段反射序列化逻辑。
 *
 * <p>序列化器需实现 {@link com.njydsz.common.json.serializer.JsonSerializer}， 直接写入 {@link
 * com.njydsz.common.json.writer.JSONWriter}，零拷贝、避免中间 String 分配。
 *
 * <p>使用示例：
 *
 * <pre><code>
 * {@literal @}JsonSerialize(using = MoneySerializer.class)
 * public class Money {
 *     private final long cents;
 * }
 *
 * public class MoneySerializer implements
 *     com.njydsz.common.json.serializer.JsonSerializer{@literal <}Money{@literal >} {
 *     {@literal @}Override
 *     public void serialize(Money value, JSONWriter out) {
 *         out.writeString(String.format("%.2f", value.getCents() / 100.0));
 *     }
 * }
 * </code></pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.common.json.serializer.JsonSerializer 自定义序列化器 SPI
 * @see JsonDeserialize
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
public @interface JsonSerialize {

  /**
   * 自定义序列化器实现类。
   *
   * <p>必须实现 {@link com.njydsz.common.json.serializer.JsonSerializer}， 提供公开无参构造函数。默认为 {@code
   * Void.class} 表示不使用自定义序列化器。
   *
   * @return 序列化器实现类
   */
  Class<?> using() default Void.class;
}
