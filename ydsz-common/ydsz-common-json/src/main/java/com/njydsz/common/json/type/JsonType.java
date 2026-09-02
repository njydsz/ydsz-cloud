package com.njydsz.common.json.type;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * JSON 类型引用（用于泛型反序列化）
 *
 * <p>提供类型安全的泛型反序列化支持，解决 Java 泛型类型擦除问题。
 *
 * <p><b>问题背景：</b>
 *
 * <ul>
 *   <li>Java 泛型使用类型擦除，运行时无法获取泛型具体类型
 *   <li>例如：List&lt;User&gt; 在运行时只能获取到 List
 *   <li>传统方式需要强制类型转换，不安全
 * </ul>
 *
 * <p><b>解决方案：</b>
 *
 * <ul>
 *   <li>通过匿名内部类 + 父类类型参数获取泛型类型
 *   <li>反射获取子类的泛型父类声明
 *   <li>实现 Comparable 接口支持排序
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>
 * // 传统方式（不安全）
 * List&lt;User&gt; users = (List&lt;User&gt;) YdszJson.fromJson(json, List.class);
 *
 * // 使用 JsonType（类型安全）
 * List&lt;User&gt; users = YdszJson.fromJson(json, new JsonType&lt;List&lt;User&gt;&gt;() {});
 *
 * // Map 泛型
 * Map&lt;String, User&gt; map = YdszJson.fromJson(json, new JsonType&lt;Map&lt;String, User&gt;&gt;() {});
 *
 * // 嵌套泛型
 * List&lt;Map&lt;String, List&lt;User&gt;&gt;&gt; complex =
 *     YdszJson.fromJson(json, new JsonType&lt;List&lt;Map&lt;String, List&lt;User&gt;&gt;&gt;&gt;() {});
 * </pre>
 *
 * <p><b>实现原理：</b>
 *
 * <ol>
 *   <li>创建匿名内部类继承 JsonType
 *   <li>获取子类的 genericSuperclass（即 ParameterizedType）
 *   <li>提取 actualTypeArguments[0] 获取泛型类型
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see Type
 * @see ParameterizedType
 */
/**
 * JsonType。
 *
 * @author ydsz-team
 * @since 26.09.01
 *
 * @param <T> 泛型类型
 */
public abstract class JsonType<T> implements Comparable<JsonType<T>> {

  /** 泛型类型 */
  protected final Type type;

  /**
   * 构造函数
   *
   * <p>通过反射获取泛型父类的类型参数。
   *
   * <p>示例：new JsonType&lt;List&lt;User&gt;&gt;() {}
   *
   * <p>则 type = List&lt;User&gt;
   */
  protected JsonType() {
    Class<?> superClass = getClass();
    Type type = getClass().getGenericSuperclass();

    while (superClass != null && superClass != Object.class) {
      if (type instanceof ParameterizedType) {
        ParameterizedType parameterizedType = (ParameterizedType) type;
        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();

        if (actualTypeArguments.length > 0) {
          Type firstArg = actualTypeArguments[0];
          if (firstArg instanceof Class) {
            if (JsonType.class.equals(firstArg)) {
              superClass = superClass.getSuperclass();
              type = superClass.getGenericSuperclass();
              continue;
            }
          }
          this.type = firstArg;
          return;
        }
      }
      superClass = superClass.getSuperclass();
      type = superClass.getGenericSuperclass();
    }

    throw new IllegalArgumentException(
        "JsonType must be created with an actual type parameter. "
            + "Example: new JsonType<List<User>>() {}");
  }

  /**
   * 获取泛型类型
   *
   * @return 泛型类型
   */
  public Type getType() {
    return type;
  }

  @Override
  public int compareTo(JsonType<T> other) {
    if (this == other) {
      return 0;
    }
    if (other == null) {
      return 1;
    }
    return this.getType().toString().compareTo(other.getType().toString());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof JsonType)) {
      return false;
    }
    JsonType<?> other = (JsonType<?>) obj;
    return this.getType().equals(other.getType());
  }

  @Override
  public int hashCode() {
    return getType().hashCode();
  }

  @Override
  public String toString() {
    return "JsonType<" + type.getTypeName() + ">";
  }
}
