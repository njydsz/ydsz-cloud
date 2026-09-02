package com.njydsz.common.json.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Jackson 兼容注解：指定自定义反序列化器。
 *
 * <p>标注在类上，通过 {@code using} 指定自定义反序列化器， 反序列化该类时由 {@code DeserializationProvider} 优先调用自定义反序列化器，
 * 替代默认的反射字段赋值逻辑。
 *
 * <p>反序列化器需实现 {@link com.njydsz.common.json.deserializer.JsonDeserializer}， 使用 {@link
 * com.njydsz.common.json.reader.JSONReader} 进行流式解析，避免完整 JSON 字符串复制。
 *
 * <p>使用示例：
 *
 * <pre><code>
 * {@literal @}JsonDeserialize(using = MoneyDeserializer.class)
 * public class Money {
 *     private final long cents;
 * }
 *
 * public class MoneyDeserializer implements
 *     com.njydsz.common.json.deserializer.JsonDeserializer{@literal <}Money{@literal >} {
 *     {@literal @}Override
 *     public Money deserialize(JSONReader reader) {
 *         return new Money(reader.readLong());
 *     }
 * }
 * </code></pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.common.json.deserializer.JsonDeserializer 自定义反序列化器 SPI
 * @see JsonSerialize
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
public @interface JsonDeserialize {

  /**
   * 自定义反序列化器实现类。
   *
   * <p>必须实现 {@link com.njydsz.common.json.deserializer.JsonDeserializer}， 提供公开无参构造函数。默认为 {@code
   * Void.class} 表示不使用自定义反序列化器。
   *
   * @return 反序列化器实现类
   */
  Class<?> using() default Void.class;
}
