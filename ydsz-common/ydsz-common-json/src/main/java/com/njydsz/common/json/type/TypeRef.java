package com.njydsz.common.json.type;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 类型引用工具类（Jackson TypeReference 风格）。
 *
 * <p>提供更直观的泛型类型构造 API，避免用户手写 {@code new JsonType<T>(){}} 的繁琐语法。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>
 * // List 类型
 * List&lt;User&gt; users = YdszJson.fromJson(json, TypeRef.list(User.class));
 *
 * // Map 类型
 * Map&lt;String, User&gt; map = YdszJson.fromJson(json, TypeRef.map(String.class, User.class));
 *
 * // Set 类型
 * Set&lt;String&gt; set = YdszJson.fromJson(json, TypeRef.set(String.class));
 *
 * // 复杂嵌套
 * List&lt;Map&lt;String, User&gt;&gt; complex = YdszJson.fromJson(json,
 *     TypeRef.list(TypeRef.map(String.class, User.class)));
 * </pre>
 *
 * <p>对于不支持的复杂泛型类型，仍可使用 {@link JsonType} 匿名内部类方式：
 *
 * <pre>
 * List&lt;Map&lt;String, List&lt;User&gt;&gt;&gt; complex = YdszJson.fromJson(json,
 *     new JsonType&lt;List&lt;Map&lt;String, List&lt;User&gt;&gt;&gt;&gt;() {});
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see JsonType
 */
public final class TypeRef {

  private TypeRef() {
    throw new UnsupportedOperationException("TypeRef is a utility class");
  }

  /**
   * 构造 List 元素类型引用。
   *
   * @param elementType 元素类型
   * @param <T> 元素类型参数
   * @return 可用于泛型反序列化的 JsonType
   */
  public static <T> JsonType<List<T>> list(Class<T> elementType) {
    return new JsonType<List<T>>() {};
  }

  /**
   * 构造 Set 元素类型引用。
   *
   * @param elementType 元素类型
   * @param <T> 元素类型参数
   * @return 可用于泛型反序列化的 JsonType
   */
  public static <T> JsonType<Set<T>> set(Class<T> elementType) {
    return new JsonType<Set<T>>() {};
  }

  /**
   * 构造 Map 类型引用。
   *
   * @param keyType 键类型
   * @param valueType 值类型
   * @param <K> 键类型参数
   * @param <V> 值类型参数
   * @return 可用于泛型反序列化的 JsonType
   */
  public static <K, V> JsonType<Map<K, V>> map(Class<K> keyType, Class<V> valueType) {
    return new JsonType<Map<K, V>>() {};
  }

  /**
   * 构造带泛型嵌套的 Map 类型引用（值类型为泛型集合）。
   *
   * <p>例如：{@code TypeRef.mapOfList(String.class, User.class)} 构造 {@code Map<String, List<User>>}
   * 类型引用。
   *
   * @param keyType 键类型
   * @param elementType 值集合的元素类型
   * @param <K> 键类型参数
   * @param <E> 值集合元素类型参数
   * @return 可用于泛型反序列化的 JsonType
   */
  public static <K, E> JsonType<Map<K, List<E>>> mapOfList(Class<K> keyType, Class<E> elementType) {
    // 使用复合嵌套 JsonType
    return new JsonType<Map<K, List<E>>>() {};
  }

  /**
   * 获取底层的 {@link java.lang.reflect.Type}。
   *
   * <p>供需要直接操作 Type 的框架集成场景使用。
   *
   * @param elementType 元素类型
   * @param <T> 类型参数
   * @return List&lt;T&gt; 的 Type 表示
   */
  public static <T> Type listType(Class<T> elementType) {
    return TypeFactory.getInstance().constructCollectionType(List.class, elementType);
  }

  /**
   * 获取底层的 {@link java.lang.reflect.Type}。
   *
   * @param keyType 键类型
   * @param valueType 值类型
   * @param <K> 键类型参数
   * @param <V> 值类型参数
   * @return Map&lt;K, V&gt; 的 Type 表示
   */
  public static <K, V> Type mapType(Class<K> keyType, Class<V> valueType) {
    return TypeFactory.getInstance().constructMapType(Map.class, keyType, valueType);
  }

  /**
   * 获取底层的 {@link java.lang.reflect.Type}。
   *
   * @param elementType 元素类型
   * @param <T> 类型参数
   * @return Set&lt;T&gt; 的 Type 表示
   */
  public static <T> Type setType(Class<T> elementType) {
    return TypeFactory.getInstance().constructCollectionType(Set.class, elementType);
  }
}
