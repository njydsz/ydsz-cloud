package com.njydsz.common.json.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 多态子类型列表注解
 *
 * <p>与 {@link JsonTypeInfo} 配合使用，定义基类的所有可能子类型。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * &#064;JsonTypeInfo(property = "type")
 * &#064;JsonSubTypes({
 *     &#064;JsonSubType(value = Dog.class, name = "dog"),
 *     &#064;JsonSubType(value = Cat.class, name = "cat")
 * })
 * public abstract class Animal { }
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Deprecated(since = "1.0.0", forRemoval = true)
public @interface JsonSubTypes {

    /**
     * 子类型列表
     */
    JsonSubType[] value();
}
