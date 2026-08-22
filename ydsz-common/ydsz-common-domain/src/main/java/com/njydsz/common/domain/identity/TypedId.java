package com.njydsz.common.domain.identity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 编译期类型安全 ID（Phantom Type ID）。
 *
 * <p>通过泛型参数 {@code T}（Phantom Type）在编译期区分不同业务实体的 ID， 避免将 {@code projectId} 误传给 {@code userId} 参数。
 *
 * <p>底层存储为 {@code long} 值，解析和构造均做非空、正数校验。
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * // 定义 phantom type
 * class Project {}
 * class User {}
 *
 * // 创建类型安全 ID
 * TypedId<Project> projectId = TypedId.of(123L);
 * TypedId<User> userId = TypedId.parse("456");
 *
 * // 编译期阻止混用
 * // someMethod(projectId); // 如果 someMethod 期望 TypedId<User>，编译报错
 *
 * // 获取底层值
 * long rawValue = projectId.value();
 * }</pre>
 *
 * @param <T> Phantom Type，用于编译区分的标记类型（无需实际实例）
 * @author ydsz-team
 * @since 1.0.0
 */
public final class TypedId<T> implements Comparable<TypedId<T>>, Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 底层 ID 值 */
  private final long value;

  /**
   * 私有构造器，通过静态工厂方法创建实例。
   *
   * @param value 底层 ID 值（必须为正数）
   */
  private TypedId(long value) {
    this.value = value;
  }

  /**
   * 从 Long 值创建 TypedId。
   *
   * @param <T> Phantom Type
   * @param value ID 值（必须为正数，非 null）
   * @return TypedId 实例
   * @throws IllegalArgumentException 值为 null、0 或负数时抛出
   * @since 1.0.0
   */
  public static <T> TypedId<T> of(Long value) {
    if (value == null) {
      throw new IllegalArgumentException("TypedId value must not be null");
    }
    if (value <= 0) {
      throw new IllegalArgumentException("TypedId value must be positive, got: " + value);
    }
    return new TypedId<>(value);
  }

  /**
   * 从字符串解析 TypedId。
   *
   * @param <T> Phantom Type
   * @param text ID 字符串表示
   * @return TypedId 实例
   * @throws NumberFormatException 字符串无法解析为数字时抛出
   * @throws IllegalArgumentException 解析值为 0 或负数时抛出
   * @since 1.0.0
   */
  public static <T> TypedId<T> parse(String text) {
    return of(Long.parseLong(text));
  }

  /**
   * 获取底层 ID 值。
   *
   * @return 底层 ID 值
   * @since 1.0.0
   */
  public long value() {
    return value;
  }

  @Override
  public int compareTo(TypedId<T> other) {
    return Long.compare(this.value, other.value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TypedId<?> other)) {
      return false;
    }
    return this.value == other.value;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(value);
  }

  @Override
  public String toString() {
    return Long.toString(value);
  }
}
