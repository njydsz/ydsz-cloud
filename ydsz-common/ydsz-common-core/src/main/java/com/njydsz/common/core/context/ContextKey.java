package com.njydsz.common.core.context;

/**
 * 上下文键，提供类型安全的上下文存取。
 *
 * <p>替代 {@code Map<String, Object> + String key} 的字符串键模式， 在编译期提供类型保证，避免运行时的 {@code
 * ClassCastException}。
 *
 * <h3>设计参考</h3>
 *
 * <ul>
 *   <li><b>Netty</b>：{@code AttributeKey<T>}
 *   <li><b>Spring Cloud Sleuth</b>：{@code BaggageField}
 * </ul>
 *
 * <h3>使用对比</h3>
 *
 * <pre>{@code
 * // 旧方式（字符串键 + 强制类型转换）
 * RequestContext.put("customField", someInt);
 * Integer value = (Integer) RequestContext.get("customField");  // 可能 ClassCastException
 *
 * // 新方式（TypedKey，编译期安全）
 * ContextKey<Integer> CUSTOM_FIELD = ContextKey.of("customField", Integer.class);
 * RequestContext.put(CUSTOM_FIELD, 42);
 * Integer value = RequestContext.get(CUSTOM_FIELD);  // 返回值已是 Integer，无需强转
 * }</pre>
 *
 * @param <T> 存储值的类型
 * @author ydsz-team
 * @since 1.0.0
 * @see RequestContext
 */
public final class ContextKey<T> {

  private final String key;
  private final Class<T> type;

  private ContextKey(String key, Class<T> type) {
    this.key = key;
    this.type = type;
  }

  /**
   * 创建一个 String 类型的上下文键。
   *
   * @param key 键名
   * @return ContextKey 实例
   */
  public static ContextKey<String> ofString(String key) {
    return new ContextKey<>(key, String.class);
  }

  /**
   * 创建一个 Long 类型的上下文键。
   *
   * @param key 键名
   * @return ContextKey 实例
   */
  public static ContextKey<Long> ofLong(String key) {
    return new ContextKey<>(key, Long.class);
  }

  /**
   * 创建一个 Integer 类型的上下文键。
   *
   * @param key 键名
   * @return ContextKey 实例
   */
  public static ContextKey<Integer> ofInt(String key) {
    return new ContextKey<>(key, Integer.class);
  }

  /**
   * 创建一个 Boolean 类型的上下文键。
   *
   * @param key 键名
   * @return ContextKey 实例
   */
  public static ContextKey<Boolean> ofBoolean(String key) {
    return new ContextKey<>(key, Boolean.class);
  }

  /**
   * 创建一个指定类型的上下文键。
   *
   * @param key 键名
   * @param type 值的类型
   * @param <T> 值类型泛型
   * @return ContextKey 实例
   */
  public static <T> ContextKey<T> of(String key, Class<T> type) {
    return new ContextKey<>(key, type);
  }

  /**
   * 获取键名。
   *
   * @return 返回值说明
   */
  public String key() {
    return key;
  }

  /**
   * 获取值类型。
   *
   * @return 返回值说明
   */
  public Class<T> type() {
    return type;
  }

  /**
   * 安全类型转换。
   *
   * @param value 原始值
   * @return 转换后的值
   */
  @SuppressWarnings("unchecked")
  T cast(Object value) {
    if (value == null) {
      return null;
    }
    if (type.isInstance(value)) {
      return (T) value;
    }
    throw new ClassCastException(
        "ContextKey '"
            + key
            + "' expects "
            + type.getName()
            + " but got "
            + value.getClass().getName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ContextKey)) {
      return false;
    }
    ContextKey<?> that = (ContextKey<?>) o;
    return key.equals(that.key) && type.equals(that.type);
  }

  @Override
  public int hashCode() {
    return 31 * key.hashCode() + type.hashCode();
  }

  @Override
  public String toString() {
    return "ContextKey{key='" + key + "', type=" + type.getSimpleName() + '}';
  }
}
