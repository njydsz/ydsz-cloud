package com.njydsz.pmis.common.json.annotation;

import java.lang.annotation.*;

/**
 * Json Builder 模式注解（参考 fastjson2 的@JSONPOJOBuilder）
 *
 * <p>用于标注 Builder 类，支持使用 Builder 模式进行反序列化。</p>
 *
 * <p><b>主要功能：</b></p>
 * <ul>
 *   <li>指定 Builder 类</li>
 *   <li>指定构建方法（build 方法）</li>
 *   <li>指定 with 前缀</li>
 *   <li>支持链式调用</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * {@literal @}JsonBuilder
 * public class User {
 *     private Long id;
 *     private String name;
 *
 *     public User(Long id, String name) {
 *         this.id = id;
 *         this.name = name;
 *     }
 *
 *     // Getters and Setters
 *
 *     public static Builder builder() {
 *         return new Builder();
 *     }
 *
 *     {@literal @}JsonBuilder(builderClass = Builder.class)
 *     public static class Builder {
 *         private Long id;
 *         private String name;
 *
 *         public Builder id(Long id) {
 *             this.id = id;
 *             return this;
 *         }
 *
 *         public Builder name(String name) {
 *             this.name = name;
 *             return this;
 *         }
 *
 *         public User build() {
 *             return new User(id, name);
 *         }
 *     }
 * }
 *
 * // 自定义构建方法名
 * {@literal @}JsonBuilder(buildMethod = "create")
 * public class Product {
 *     // ...
 * }
 *
 * // 自定义 with 前缀
 * {@literal @}JsonBuilder(withPrefix = "set")
 * public class Order {
 *     // ...
 * }
 * </pre>
 *
 * @since 1.3.0
 * @since 1.3.0
 * @see JsonCreator
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface JsonBuilder {

    /**
     * Builder 类
     *
     * <p>指定 Builder 类的类型。</p>
     *
     * <p>默认为 void，自动识别内部 Builder 类。</p>
     *
     * @return Builder 类
     */
    Class<?> builderClass() default void.class;

    /**
     * 构建方法名
     *
     * <p>指定返回最终对象的方法名。</p>
     *
     * <p>默认为 "build"。</p>
     *
     * @return 构建方法名
     */
    String buildMethod() default "build";

    /**
     * with 前缀
     *
     * <p>指定 setter 方法的前缀。</p>
     *
     * <p>默认为 "with"，即方法名格式为 withXxx。</p>
     *
     * <p>例如：withPrefix = "set"，则方法名为 setXxx。</p>
     *
     * @return with 前缀
     */
    String withPrefix() default "with";

    /**
     * 是否启用
     *
     * <p>如果为 false，则不使用 Builder 模式。</p>
     *
     * @return 是否启用
     */
    boolean enable() default true;

    /**
     * 是否自动检测 Builder 类
     *
     * <p>如果为 true，则自动检测内部静态 Builder 类。</p>
     *
     * @return 是否自动检测
     */
    boolean autoDetect() default true;

    /**
     * Builder 类的构造函数名
     *
     * <p>指定 Builder 类的构造函数名。</p>
     *
     * <p>默认为空，使用默认构造函数。</p>
     *
     * @return 构造函数名
     */
    String builderConstructor() default "";

    /**
     * 是否支持链式调用
     *
     * <p>如果为 true，则 Builder 方法返回 Builder 自身。</p>
     *
     * @return 是否支持链式调用
     */
    boolean chainMethod() default true;

    /**
     * 忽略的方法
     *
     * <p>指定 Builder 类中忽略的方法名。</p>
     *
     * @return 方法名数组
     */
    String[] ignoreMethods() default {};
}
