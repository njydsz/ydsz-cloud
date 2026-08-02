package com.njydsz.common.json.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Jackson 兼容注解：指定多态子类型的逻辑名称。
 *
 * <p>对标 Jackson {@code @JsonTypeName}，用于配合 {@code @JsonTypeInfo}
 * 实现多态类型识别。当 {@link PolymorphicTypeResolver} 解析多态类型时，
 * 会从子类的 {@code @JsonTypeName} 获取类型判别值。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * {@code @JsonTypeInfo(property = "type")}
 * public abstract class Animal { }
 *
 * {@code @JsonTypeName("dog")}
 * public class Dog extends Animal { }
 * </pre>
 *
 * <p>序列化 Dog 对象时，JSON 中会包含 {@code "type": "dog"}。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Deprecated(since = "1.0.0", forRemoval = true)
public @interface JsonTypeName {

    /**
     * 子类型的逻辑名称。
     *
     * @return 类型判别值
     */
    String value() default "";
}
