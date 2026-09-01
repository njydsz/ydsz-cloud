package com.njydsz.common.json.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Json 构造函数注解（参考 fastjson2 的@JSONCreator）
 *
 * <p>用于标注构造函数，指定反序列化时使用的构造器。
 *
 * <p><b>主要功能：</b>
 *
 * <ul>
 *   <li>指定反序列化时使用的构造函数
 *   <li>支持多个构造函数，通过 default 属性指定默认构造函数
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
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
 * @since 26.09.01
 * @see JsonProperty
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.CONSTRUCTOR, ElementType.METHOD})
public @interface JsonCreator {

  /**
   * 是否为默认构造函数
   *
   * <p>当类中有多个构造函数时，通过此属性指定默认使用的构造函数。
   *
   * <p>默认为 false，如果有多个构造函数且未指定 defaultCreator， 则选择参数最多的构造函数。
   *
   * @return 是否为默认构造函数
   */
  boolean defaultCreator() default false;

  /**
   * 参数名称映射
   *
   * <p>指定构造函数参数与 JSON 字段的映射关系。
   *
   * <p>例如：{"id", "name"} 表示构造函数的第一个参数对应 JSON 的 id 字段， 第二个参数对应 name 字段。
   *
   * @return 参数名称数组
   */
  String[] parameterNames() default {};

  /**
   * 是否启用
   *
   * <p>如果为 false，则忽略此构造函数。
   *
   * @return 是否启用
   */
  boolean enable() default true;

  /**
   * 创建器模式（对标 Jackson {@code JsonCreator.Mode}）。
   *
   * <p>控制反序列化时如何将 JSON 映射到构造器/工厂方法参数：
   *
   * <ul>
   *   <li>{@link Mode#DEFAULT}：自动推断（单参+无 parameterNames 视为 DELEGATING，否则 PROPERTIES）
   *   <li>{@link Mode#PROPERTIES}：按属性名映射（配合 {@link #parameterNames()} 或 {@link JsonProperty}）
   *   <li>{@link Mode#DELEGATING}：整个 JSON 值作为单一参数传入（适用于 String→ValueObject 转换）
   * </ul>
   *
   * @return 创建器模式
   */
  Mode mode() default Mode.DEFAULT;

  /** 创建器模式枚举（与 Jackson 一致）。 */
  enum Mode {
    /** 自动推断 */
    DEFAULT,
    /** 按属性映射 */
    PROPERTIES,
    /** 委托：整 JSON 作为单参 */
    DELEGATING
  }
}
