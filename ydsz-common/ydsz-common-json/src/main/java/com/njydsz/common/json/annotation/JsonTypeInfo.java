package com.njydsz.common.json.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 多态类型注解
 *
 * <p>用于在反序列化时识别具体的子类类型，对标 Jackson @JsonTypeInfo。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>
 * // 基类
 * &#064;JsonTypeInfo(
 *     property = "type",
 *     visible = true
 * )
 * &#064;JsonSubTypes({
 *     &#064;JsonSubType(value = Dog.class, name = "dog"),
 *     &#064;JsonSubType(value = Cat.class, name = "cat")
 * })
 * public abstract class Animal {
 *     private String name;
 * }
 *
 * // 序列化输出
 * {"type":"dog","name":"Buddy","breed":"Labrador"}
 *
 * // 反序列化自动识别类型
 * Animal animal = YdszJson.fromJson(json, Animal.class);
 * // 返回 Dog 实例
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JsonTypeInfo {

  /**
   * 类型标识属性名
   *
   * <p>用于在 JSON 中存储类型信息，默认为 "type"。
   *
   * <p>仅在 {@link #include()} 为 {@link As#PROPERTY} 时生效。
   *
   * @return 类型标识属性名
   */
  String property() default "type";

  /**
   * 是否保留类型属性
   *
   * <p>true: 反序列化后类型属性仍保留在对象中 false: 反序列化后类型属性从对象中移除（仅用于类型识别）
   *
   * @return 是否保留
   */
  boolean visible() default false;

  /**
   * 类型标识使用方式（对标 Jackson {@code JsonTypeInfo.Id}）。
   *
   * <p>控制类型信息以何种形式写入 JSON：
   *
   * <ul>
   *   <li>{@link Id#NAME}：使用子类型逻辑名（配合 {@link JsonTypeName} / {@link JsonSubType#name()}），默认
   *   <li>{@link Id#CLASS}：使用 Java 完整类名
   *   <li>{@link Id#MINIMAL_CLASS}：使用相对类名（截断基类包名前缀）
   *   <li>{@link Id#NONE}：不写入类型信息
   * </ul>
   *
   * @return 类型标识方式
   */
  Id use() default Id.NAME;

  /**
   * 类型标识包含方式（对标 Jackson {@code JsonTypeInfo.As}）。
   *
   * <p>控制类型信息在 JSON 中的物理结构：
   *
   * <ul>
   *   <li>{@link As#PROPERTY}：作为对象的一个属性（由 {@link #property()} 指定键名），默认
   *   <li>{@link As#WRAPPER_ARRAY}：以 {@code ["子类型名",{...}]} 包装数组形式
   *   <li>{@link As#WRAPPER_OBJECT}：以 {@code {"子类型名":{...}}} 包装对象形式
   * </ul>
   *
   * @return 包含方式
   */
  As include() default As.PROPERTY;

  /** 类型标识方式枚举（与 Jackson JsonTypeInfo.Id 一致）。 */
  enum Id {
    /** 不写入类型信息 */
    NONE,
    /** 使用 Java 完整类名 */
    CLASS,
    /** 使用相对类名 */
    MINIMAL_CLASS,
    /** 使用逻辑名（默认） */
    NAME
  }

  /**
   * 类型标识包含结构枚举（与 Jackson JsonTypeInfo.As 一致）。
   *
   * <p>注：仅 {@link #PROPERTY} 已在引擎中完整实现；WRAPPER_ARRAY / WRAPPER_OBJECT 为预留变体（当前引擎以 PROPERTY
   * 方式解析）。EXISTING_PROPERTY / EXTERNAL_PROPERTY 两个未实现的占位符变体已在 1.2.1 移除，避免虚假 API 表面积。
   */
  enum As {
    /** 作为对象属性 */
    PROPERTY,
    /** 包装为数组 {@code ["type",{...}]}（预留，当前引擎按 PROPERTY 解析） */
    WRAPPER_ARRAY,
    /** 包装为对象 {@code {"type":{...}}}（预留，当前引擎按 PROPERTY 解析） */
    WRAPPER_OBJECT
  }
}
