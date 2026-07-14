package com.njydsz.pmis.common.json.annotation;

import java.lang.annotation.*;

/**
 * Json 构造函数注解（参考 fastjson2 的@JSONCreator）
 *
 * <p>用于标注构造函数或静态工厂方法，指定反序列化时使用的构造器。</p>
 *
 * <p><b>主要功能：</b></p>
 * <ul>
 *   <li>指定反序列化时使用的构造函数</li>
 *   <li>支持多个构造函数，通过 default 属性指定默认构造函数</li>
 *   <li>支持静态工厂方法</li>
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
 * // 使用静态工厂方法
 * public class User {
 *     private Long id;
 *     private String name;
 *
 *     {@literal @}JsonCreator
 *     public static User create(Long id, String name) {
 *         User user = new User();
 *         user.setId(id);
 *         user.setName(name);
 *         return user;
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
 * </pre>
 *
 * @since 1.3.0
 * @since 1.3.0
 * @see JsonField
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
     * 构造函数名称（用于静态工厂方法）
     *
     * <p>当标注在静态工厂方法上时，指定方法名。</p>
     *
     * <p>默认为空，自动识别静态方法。</p>
     *
     * @return 方法名
     */
    String name() default "";

    /**
     * 参数名称映射
     *
     * <p>指定构造函数参数与 JSON 字段的映射关系。</p>
     *
     * <p>例如：{"id", "userId"} 表示构造函数的第一个参数对应 JSON 的 userId 字段。</p>
     *
     * @return 参数名称数组
     */
    String[] parameterNames() default {};

    /**
     * 参数类型
     *
     * <p>指定构造函数参数的类型，用于精确匹配构造函数。</p>
     *
     * @return 参数类型数组
     */
    Class<?>[] parameterTypes() default {};

    /**
     * 是否启用
     *
     * <p>如果为 false，则忽略此构造函数。</p>
     *
     * @return 是否启用
     */
    boolean enable() default true;
}
