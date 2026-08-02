package com.njydsz.common.json.annotation;

import java.lang.annotation.*;

/**
 * Json 构造函数注解（参考 fastjson2 的@JSONCreator）
 *
 * <p>用于标注构造函数，指定反序列化时使用的构造器。</p>
 *
 * <p><b>主要功能：</b></p>
 * <ul>
 *   <li>指定反序列化时使用的构造函数</li>
 *   <li>支持多个构造函数，通过 default 属性指定默认构造函数</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * public class User {
 *     private Long id;
 *     private String name;
 *
 *     {@literal @}JsonCreator
 *     public User(Long id, String name) {
 *         this.id = id;
 *         this.name = name;
 *     }
 * }
 *
 * // 多个构造函数时指定默认构造函数
 * public class User {
 *     private Long id;
 *     private String name;
 *
 *     {@literal @}JsonCreator(defaultCreator = true)
 *     public User(Long id, String name) {
 *         this.id = id;
 *         this.name = name;
 *     }
 *
 *     {@literal @}JsonCreator
 *     public User(Long id) {
 *         this.id = id;
 *     }
 * }
 *
 * // 通过 parameterNames 显式指定 JSON 字段映射
 * public class User {
 *     private Long id;
 *     private String name;
 *
 *     {@literal @}JsonCreator(parameterNames = {"userId", "userName"})
 *     public User(Long id, String name) {
 *         this.id = id;
 *         this.name = name;
 *     }
 * }
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see JsonProperty
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.CONSTRUCTOR, ElementType.METHOD})
public @interface JsonCreator {

    /**
     * 是否为默认构造函数
     *
     * <p>当类中有多个构造函数时，通过此属性指定默认使用的构造函数。</p>
     *
     * <p>默认为 false，如果有多个构造函数且未指定 defaultCreator，
     * 则选择参数最多的构造函数。</p>
     *
     * @return 是否为默认构造函数
     */
    boolean defaultCreator() default false;

    /**
     * 参数名称映射
     *
     * <p>指定构造函数参数与 JSON 字段的映射关系。</p>
     *
     * <p>例如：{"id", "name"} 表示构造函数的第一个参数对应 JSON 的 id 字段，
     * 第二个参数对应 name 字段。</p>
     *
     * @return 参数名称数组
     */
    String[] parameterNames() default {};

    /**
     * 是否启用
     *
     * <p>如果为 false，则忽略此构造函数。</p>
     *
     * @return 是否启用
     */
    boolean enable() default true;
}
