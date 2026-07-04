package com.njydsz.pmis.common.util;

import java.io.Serializable;
import java.util.function.Function;

/**
 * 可序列化的 {@link Function}（用于 lambda 方法引用元数据提取）。
 *
 * <p>通过让函数式接口继承 {@link Serializable}，Lambda 表达式在编译时会生成
 * {@code java.lang.invoke.SerializedLambda} 形式的实现，可通过反射读取其指向的方法
 * （调用方、返回类型、参数类型），从而实现「方法引用 → 属性名」的类型安全转换，
 * 避免 {@code Sort.by(Sort.Direction.DESC, "createdAt")} 中硬编码字符串导致的重构失同步。
 *
 * <p>典型用法（搭配 {@link SortBy}）：
 * <pre>{@code
 * // 等价于 Sort.by(Sort.Direction.DESC, "createdAt")，但 createdAt 为编译期类型安全的引用
 * Sort sort = SortBy.desc(ProjectSearchDoc::getCreatedAt);
 * }</pre>
 *
 * <p>说明：仅用于编译期产生方法引用，运行时不会真正序列化，因此 lambda 实现类无需
 * {@code serialVersionUID}。
 *
 * @param <T> 输入类型（通常是实体类）
 * @param <R> 返回类型（实体字段类型）
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@FunctionalInterface
public interface SerializableFunction<T, R> extends Function<T, R>, Serializable {

    /**
     * 默认 serialVersionUID（SerializableFunction 自身不会真被序列化到磁盘或网络，
     * 这里仅满足 Serializable 的契约要求）。
     */
    long serialVersionUID = 1L;
}
