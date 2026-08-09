package com.njydsz.common.domain.identity;

import java.io.Serializable;
import java.util.Objects;

/**
 * 类型安全的 ID 值对象（DDD Typed Identifier）。
 *
 * <p>提供编译期类型安全的主键包装，避免不同实体类型的 ID 在方法调用中混淆。
 * 例如，{@code TypedId<Project>} 与 {@code TypedId<User>} 是不同类型，
 * 即使底层都是 {@code Long}，也不能混用。</p>
 *
 * <p><b>不可变语义：</b>一旦创建，ID 值不可修改，天然线程安全。</p>
 *
 * <p><b>使用方式：</b></p>
 * <pre>{@code
 * // 定义实体时
 * public class Project extends BaseEntity<TypedId<Project>> {
 *     // ...
 * }
 *
 * // 创建时
 * TypedId<Project> projectId = TypedId.of(123L);
 *
 * // 获取原始值
 * Long rawId = projectId.value();
 * }</pre>
 *
 * <p><b>对标：</b>Axon 的 {@code Identifier}、Vlad Mihalcea 的 {@code DomainId}。</p>
 *
 * @param <T> 实体类型（phantom type，仅用于编译期区分，运行时擦除）
 * @author ydsz-team
 * @since 1.2.0
 */
public record TypedId<T>(Long value) implements Serializable, Comparable<TypedId<T>> {

    private static final long serialVersionUID = 1L;

    /**
     * 构造类型安全的 ID。
     *
     * @param value 底层 ID 值，不可为 null
     * @throws IllegalArgumentException 当 value 为 null 或 <= 0 时抛出
     */
    public TypedId {
        if (value == null) {
            throw new IllegalArgumentException("TypedId value must not be null");
        }
        if (value <= 0) {
            throw new IllegalArgumentException("TypedId value must be positive, got: " + value);
        }
    }

    /**
     * 工厂方法（语义清晰的别名）。
     *
     * @param value 底层 ID 值
     * @param <E>   实体类型
     * @return 新的 TypedId 实例
     */
    public static <E> TypedId<E> of(Long value) {
        return new TypedId<>(value);
    }

    /**
     * 从字符串解析 ID。
     *
     * @param value 字符串形式的 ID
     * @param <E>   实体类型
     * @return 新的 TypedId 实例
     * @throws NumberFormatException 当字符串不是有效数字时抛出
     */
    public static <E> TypedId<E> parse(String value) {
        return new TypedId<>(Long.parseLong(value));
    }

    /**
     * 获取底层 Long 值。
     *
     * @return ID 值
     */
    public Long value() {
        return value;
    }

    @Override
    public int compareTo(TypedId<T> other) {
        return this.value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypedId<?> typedId = (TypedId<?>) o;
        return Objects.equals(value, typedId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
