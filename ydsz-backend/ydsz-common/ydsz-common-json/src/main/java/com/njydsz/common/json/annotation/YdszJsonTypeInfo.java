package com.njydsz.common.json.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 多态类型注解
 *
 * <p>用于在反序列化时识别具体的子类类型，对标 Jackson @YdszJsonTypeInfo。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 基类
 * &#064;YdszJsonTypeInfo(
 *     property = "type",
 *     visible = true
 * )
 * &#064;YdszJsonSubTypes({
 *     &#064;YdszJsonSubType(value = Dog.class, name = "dog"),
 *     &#064;YdszJsonSubType(value = Cat.class, name = "cat")
 * })
 * public abstract class Animal {
 *     private String name;
 * }
 *
 * // 序列化输出
 * {"type":"dog","name":"Buddy","breed":"Labrador"}
 *
 * // 反序列化自动识别类型
 * Animal animal = YdszJson.toObject(json, Animal.class);
 * // 返回 Dog 实例
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface YdszJsonTypeInfo {

    /**
     * 类型标识属性名
     *
     * <p>用于在 JSON 中存储类型信息，默认为 "type"</p>
     */
    String property() default "type";

    /**
     * 是否保留类型属性
     *
     * <p>true: 反序列化后类型属性仍保留在对象中
     * false: 反序列化后类型属性从对象中移除（仅用于类型识别）</p>
     */
    boolean visible() default false;
}
