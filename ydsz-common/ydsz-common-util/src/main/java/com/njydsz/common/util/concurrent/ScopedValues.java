package com.njydsz.common.util.concurrent;

import java.util.concurrent.Callable;

/**
 * ScopedValue 工具——在虚拟线程场景下替代 TransmittableThreadLocal 的更优方案。
 *
 * <h2>背景：ThreadLocal 在虚拟线程场景的问题</h2>
 * <ul>
 *   <li>虚拟线程数量可能达到百万级，每个 ThreadLocal 条目占用内存显著</li>
 *   <li>平台线程池复用场景下 ThreadLocal 忘记 remove() 导致内存泄漏</li>
 *   <li>TransmittableThreadLocal 需要手动包装线程池，增加认知负担</li>
 * </ul>
 *
 * <h2>ScopedValue 优势</h2>
 * <ul>
 *   <li><b>作用域限定：</b>值仅在 ScopedValue.runWhere 作用域内可见，出作用域自动清除</li>
 *   <li><b>零泄漏：</b>GC 友好，无需手动 remove()</li>
 *   <li><b>结构化并发天然集成：</b>子任务继承父作用域的 ScopedValue 绑定</li>
 *   <li><b>性能：</b>不可变绑定（read-only），JIT 可内联优化</li>
 * </ul>
 *
 * <p><b>预测未来：</b>JDK 21+ 逐步替代 ThreadLocal 作为上下文传递的首选方案。
 * 本项目 SDK 可在 JDK 21+ 环境下使用，JDK 17 环境仍使用 TransmittableThreadLocal。
 *
 * <p>使用示例：
 * <pre>{@code
 *   // 定义 ScopedValue
 *   private static final ScopedValue&lt;String&gt; TRACE_ID = ScopedValue.newInstance();
 *
 *   // 在作用域内执行（值自动传播到子任务）
 *   return ScopedValues.runWhere(TRACE_ID, "trace-123456", () -> {
 *     // 当前线程可读取
 *     String trace = TRACE_ID.get(); // "trace-123456"
 *
 *     // 并行子任务自动继承
 *     try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
 *       scope.fork(() -> {
 *         String childTrace = TRACE_ID.get(); // 自动继承："trace-123456"
 *         return callRemote(childTrace);
 *       });
 *       scope.join();
 *       scope.throwIfFailed();
 *     }
 *     return result;
 *   });
 * }</pre>
 *
 * @author ydsz-team
 * @since 3.0.0（依赖 JDK 21+）
 * @see java.lang.ScopedValue
 */
public final class ScopedValues {

    private ScopedValues() {
        throw new UnsupportedOperationException("ScopedValues is a utility class");
    }

    // ==================== 预定义全局 ScopedValue ====================

    /** 当前请求的 traceId（全链路追踪） */
    public static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();

    /** 当前操作者 userId */
    public static final ScopedValue<String> OPERATOR_ID = ScopedValue.newInstance();

    /** 当前 tenantId（多租户隔离） */
    public static final ScopedValue<String> TENANT_ID = ScopedValue.newInstance();

    /** 当前请求 ID（日志关联） */
    public static final ScopedValue<String> REQUEST_ID = ScopedValue.newInstance();

    /** 当前语言环境（i18n） */
    public static final ScopedValue<String> LOCALE = ScopedValue.newInstance();

    // ==================== 便捷方法 ====================

    /**
     * 绑定单个值到 ScopedValue 并执行任务——出作用域自动清除。
     *
     * @param scoped 要绑定的 ScopedValue
     * @param value  绑定的值
     * @param task   要执行的任务
     * @return 任务返回值
     * @throws Exception 任务抛出的异常
     */
    public static <T, V> T runWhere(ScopedValue<V> scoped, V value, Callable<T> task) throws Exception {
        return ScopedValue.runWhere(scoped, value, task);
    }

    /**
     * 绑定多个值到 ScopedValue 并执行任务。
     *
     * @param bindings ScopedValue 绑定（通过 ScopedValue.where() 构建）
     * @param task      要执行的任务
     * @return 任务返回值
     * @throws Exception 任务抛出的异常
     */
    public static <T> T runWhere(ScopedValue.Bindings<?> bindings, Callable<T> task) throws Exception {
        return ScopedValue.runWhere(bindings, task);
    }

    /**
     * 构建 ScopedValue 绑定：{@code ScopedValues.bind(TRACE_ID, "abc", TENANT_ID, "t1")}。
     *
     * @param firstScoped  第一个 ScopedValue
     * @param firstValue   第一个绑定的值
     * @return ScopedValue.where 构建的 Bindings（可继续链式 .where() 添加更多绑定）
     */
    public static <V> ScopedValue.Bindings<?> bind(ScopedValue<V> firstScoped, V firstValue) {
        return ScopedValue.where(firstScoped, firstValue);
    }

    /**
     * 检查 ScopedValue 在当前作用域是否已绑定。
     *
     * @param scoped 要检查的 ScopedValue
     * @return 已绑定返回 true
     */
    public static <V> boolean isBound(ScopedValue<V> scoped) {
        return scoped.isBound();
    }

    /**
     * 获取 ScopedValue 的当前绑定值，未绑定则返回默认值。
     *
     * @param scoped       目标 ScopedValue
     * @param defaultValue 默认值
     * @return 当前绑定值或默认值
     */
    public static <V> V getOrDefault(ScopedValue<V> scoped, V defaultValue) {
        return scoped.orElse(defaultValue);
    }
}
