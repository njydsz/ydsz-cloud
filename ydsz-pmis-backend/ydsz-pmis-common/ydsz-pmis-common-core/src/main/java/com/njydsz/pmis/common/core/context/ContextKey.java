package com.njydsz.pmis.common.core.context;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * 强类型上下文键
 *
 * <p>用于替代 {@code String} 类型的 Key，避免拼写错误与类型不匹配。
 * 配合 {@link RequestContext} 使用，提供编译期类型检查与 IDE 提示。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * // 1. 在公共常量类中声明强类型 Key
 * public final class ContextKeys {
 *     public static final ContextKey<String> USER_ID = ContextKey.of("userId", String.class);
 *     public static final ContextKey<Long> TENANT_ID = ContextKey.of("tenantId", Long.class);
 * }
 *
 * // 2. 业务代码使用
 * RequestContext.put(ContextKeys.USER_ID, "user-001");
 * String userId = RequestContext.get(ContextKeys.USER_ID);
 *
 * // 3. Optional 方式
 * Optional<String> opt = RequestContext.getOptional(ContextKeys.USER_ID);
 * }</pre>
 *
 * @param <T> 关联值类型
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class ContextKey<T> {

    private final String name;
    private final Class<T> type;
    private final T defaultValue;

    private ContextKey(String name, Class<T> type, T defaultValue) {
        this.name = Objects.requireNonNull(name, "ContextKey name must not be null");
        this.type = Objects.requireNonNull(type, "ContextKey type must not be null");
        this.defaultValue = defaultValue;
    }

    /**
     * 创建一个必填值的强类型 Key
     *
     * @param name 字符串 Key
     * @param type 关联值类型
     * @param <T>  关联值类型
     * @return ContextKey 实例
     */
    public static <T> ContextKey<T> of(String name, Class<T> type) {
        return new ContextKey<>(name, type, null);
    }

    /**
     * 创建一个带默认值的强类型 Key
     *
     * @param name         字符串 Key
     * @param type         关联值类型
     * @param defaultValue 当上下文不存在时返回的默认值
     * @param <T>          关联值类型
     * @return ContextKey 实例
     */
    public static <T> ContextKey<T> of(String name, Class<T> type, T defaultValue) {
        return new ContextKey<>(name, type, defaultValue);
    }

    /**
     * 获取 Key 的字符串名
     *
     * @return 字符串名
     */
    public String getName() {
        return name;
    }

    /**
     * 获取 Key 的关联类型
     *
     * @return 关联类型
     */
    public Class<T> getType() {
        return type;
    }

    /**
     * 从上下文获取值，使用默认值兜底
     *
     * @return 值；若不存在且默认值非空返回默认值
     */
    public T get() {
        return RequestContext.get(this);
    }

    /**
     * 从上下文获取值（Optional）
     *
     * @return Optional 包装的值
     */
    public Optional<T> getOptional() {
        return RequestContext.getOptional(this);
    }

    /**
     * 写入值到上下文
     *
     * @param value 值
     */
    public void set(T value) {
        RequestContext.put(this, value);
    }

    /**
     * 从上下文移除该 Key
     */
    public void remove() {
        RequestContext.remove(this);
    }

    /**
     * 将值映射为另一种类型，常用于上下文中的派生计算
     *
     * <p>注意：返回的新 Key 是独立的，不会自动同步原 Key 的值变化。
     * 调用方需自行确保在使用时原 Key 的值已正确设置。</p>
     *
     * @param mapper     映射函数
     * @param targetType 目标类型 Class（用于类型安全，避免推断）
     * @param <R>        目标类型
     * @return 目标类型 Key
     */
    public <R> ContextKey<R> map(Function<T, R> mapper, Class<R> targetType) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        Objects.requireNonNull(targetType, "targetType must not be null");
        T currentValue = this.get();
        R mappedValue = currentValue != null ? mapper.apply(currentValue) : null;
        return ContextKey.of(name + "_mapped", targetType, mappedValue);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ContextKey<?> other)) {
            return false;
        }
        return name.equals(other.name) && type.equals(other.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type);
    }

    @Override
    public String toString() {
        return "ContextKey[" + name + ":" + type.getSimpleName() + "]";
    }
}
