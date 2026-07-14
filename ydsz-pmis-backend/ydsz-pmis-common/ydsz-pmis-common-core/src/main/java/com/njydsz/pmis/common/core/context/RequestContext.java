package com.njydsz.pmis.common.core.context;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 请求上下文持有者
 *
 * <p>基于 TransmittableThreadLocal 的请求上下文传递机制，用于在请求处理链中传递：</p>
 * <ul>
 *   <li>userId - 用户ID</li>
 *   <li>tenantId - 租户ID</li>
 *   <li>traceId - 链路追踪ID</li>
 *   <li>其他自定义属性</li>
 * </ul>
 *
 * <p>TransmittableThreadLocal 支持线程池场景下的自动上下文传递，
 * 配合 TtlExecutors 或 Java Agent 使用时，无需手动 capture/restore。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 设置上下文
 * RequestContext.setUserId("user123");
 * RequestContext.setTenantId("tenant456");
 *
 * // 获取上下文
 * String userId = RequestContext.getUserId();
 *
 * // 清理上下文（重要！）
 * RequestContext.clear();
 * </pre>
 *
 * <p><b>try-with-resources 用法（推荐）：</b></p>
 * <pre>
 * try (RequestContext.CleanupGuard guard = RequestContext.newCleanupGuard()) {
 *     RequestContext.setUserId("user123");
 *     RequestContext.setTenantId("tenant456");
 *     // ... 业务逻辑
 * } // 自动清理上下文，防止内存泄漏
 * </pre>
 *
 * <p><b>注意事项：</b></p>
 * <ul>
 *   <li>必须在请求结束时调用 {@link #clear()} 防止内存泄漏</li>
 *   <li>建议在拦截器或过滤器的 finally 块中统一管理生命周期</li>
 *   <li>使用 TTL 线程池时，配合 {@link com.alibaba.ttl.TtlExecutors} 或 {@link com.alibaba.ttl.TtlRunnable} 自动传播上下文</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public final class RequestContext {

    /** 上下文键名：用户ID */
    public static final String KEY_USER_ID = "userId";
    /** 上下文键名：租户ID */
    public static final String KEY_TENANT_ID = "tenantId";
    /** 上下文键名：链路追踪ID */
    public static final String KEY_TRACE_ID = "traceId";
    /** 上下文键名：请求ID */
    public static final String KEY_REQUEST_ID = "requestId";
    /** 上下文键名：语言区域 */
    public static final String KEY_LANGUAGE = "language";

    private static final ThreadLocal<Map<String, Object>> CONTEXT_HOLDER =
            new TransmittableThreadLocal<Map<String, Object>>() {
                @Override
                protected Map<String, Object> initialValue() {
                    return new HashMap<>();
                }
            };

    private RequestContext() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 设置用户ID
     *
     * @param userId 用户ID
     */
    public static void setUserId(String userId) {
        put(KEY_USER_ID, userId);
    }

    /**
     * 获取用户ID
     *
     * @return 用户ID，如果不存在返回 null
     */
    public static String getUserId() {
        return (String) get(KEY_USER_ID);
    }

    /**
     * 设置租户ID
     *
     * @param tenantId 租户ID
     */
    public static void setTenantId(String tenantId) {
        put(KEY_TENANT_ID, tenantId);
    }

    /**
     * 获取租户ID
     *
     * @return 租户ID，如果不存在返回 null
     */
    public static String getTenantId() {
        return (String) get(KEY_TENANT_ID);
    }

    /**
     * 设置链路追踪ID
     *
     * @param traceId 追踪ID
     */
    public static void setTraceId(String traceId) {
        put(KEY_TRACE_ID, traceId);
    }

    /**
     * 获取链路追踪ID
     *
     * @return 追踪ID，如果不存在返回 null
     */
    public static String getTraceId() {
        return (String) get(KEY_TRACE_ID);
    }

    /**
     * 设置属性
     *
     * @param key   属性键
     * @param value 属性值
     */
    public static void put(String key, Object value) {
        if (key == null) {
            throw new NullPointerException("key cannot be null");
        }
        if (value == null) {
            CONTEXT_HOLDER.get().remove(key);
            return;
        }
        CONTEXT_HOLDER.get().put(key, value);
    }

    /**
     * 通过强类型 Key 设置属性
     *
     * @param key   强类型 Key
     * @param value 属性值
     * @param <T>   关联类型
     */
    public static <T> void put(ContextKey<T> key, T value) {
        put(key.getName(), value);
    }

    /**
     * 获取属性
     *
     * @param key 属性键
     * @return 属性值，如果不存在返回 null
     */
    public static Object get(String key) {
        return CONTEXT_HOLDER.get().get(key);
    }

    /**
     * 通过强类型 Key 获取属性（类型不匹配时抛异常）
     *
     * @param key 强类型 Key
     * @param <T> 关联类型
     * @return 属性值；不存在时返回 null
     */
    public static <T> T get(ContextKey<T> key) {
        Object value = CONTEXT_HOLDER.get().get(key.getName());
        if (value == null) {
            return null;
        }
        if (!key.getType().isInstance(value)) {
            throw new IllegalStateException("ContextKey[" + key.getName() + "] expected "
                    + key.getType().getName() + " but was " + value.getClass().getName());
        }
        return (T) value;
    }

    /**
     * 获取属性（Optional）
     *
     * <p><b>注意：</b>此方法使用了 unchecked cast，存在类型安全风险。
     * 推荐使用 {@link #getOptional(ContextKey)} 强类型版本。</p>
     *
     * @param key 属性键
     * @param <T> 类型
     * @return Optional 包装的属性值
     * @deprecated 使用 {@link #getOptional(ContextKey)} 替代，提供编译期类型安全
     */
    @Deprecated
    public static <T> Optional<T> getOptional(String key) {
        Object value = CONTEXT_HOLDER.get().get(key);
        Optional<T> result = Optional.empty();
        if (value != null) {
            result = Optional.of((T) value);
        }
        return result;
    }

    /**
     * 通过强类型 Key 获取属性（Optional）
     *
     * @param key 强类型 Key
     * @param <T> 关联类型
     * @return Optional 包装的属性值
     */
    public static <T> Optional<T> getOptional(ContextKey<T> key) {
        return Optional.ofNullable(get(key));
    }

    /**
     * 移除属性
     *
     * @param key 属性键
     */
    public static void remove(String key) {
        CONTEXT_HOLDER.get().remove(key);
    }

    /**
     * 通过强类型 Key 移除属性
     *
     * @param key 强类型 Key
     */
    public static void remove(ContextKey<?> key) {
        remove(key.getName());
    }

    /**
     * 清空当前线程的上下文
     *
     * <p><b>重要：</b>必须在请求结束时调用此方法，防止 ThreadLocal 内存泄漏</p>
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * 在当前上下文中执行 Supplier，执行完毕后自动清除上下文
     *
     * <p><b>注意：</b>无论执行是否成功，finally 块都会调用 {@link #clear()} 清除当前线程的上下文，
     * 请确保调用此方法时上下文的生命周期确实应当在此处结束。</p>
     *
     * @param supplier 要执行的逻辑
     * @param <T>      返回值类型
     * @return supplier 的返回值
     */
    public static <T> T runAndClear(Supplier<T> supplier) {
        try {
            return supplier.get();
        } finally {
            clear();
        }
    }

    /**
     * 在当前上下文中执行 Runnable，执行完毕后自动清除上下文
     *
     * <p><b>注意：</b>无论执行是否成功，finally 块都会调用 {@link #clear()} 清除当前线程的上下文，
     * 请确保调用此方法时上下文的生命周期确实应当在此处结束。</p>
     *
     * @param runnable 要执行的逻辑
     */
    public static void runAndClear(Runnable runnable) {
        try {
            runnable.run();
        } finally {
            clear();
        }
    }

    /**
     * 捕获当前线程的上下文为 Map，用于异步场景的上下文传播
     *
     * <p>在父线程中调用此方法获取上下文快照，然后在子线程中通过
     * {@link #runWithContext(Map, Supplier)} 或 {@link #wrapCallable(Callable, Map)}
     * 恢复上下文执行。</p>
     *
     * @return 上下文 Map 的副本
     * @deprecated 项目已使用 TransmittableThreadLocal，配合 TTL Agent 或 TtlExecutors
     * 可自动传播上下文，无需手动捕获/恢复
     */
    @Deprecated
    public static Map<String, Object> capture() {
        Map<String, Object> current = CONTEXT_HOLDER.get();
        return current.isEmpty() ? new HashMap<>() : new HashMap<>(current);
    }

    /**
     * 在指定上下文中执行 Supplier，执行完毕后自动清除上下文
     *
     * <p>用于异步场景：先在父线程通过 {@link #capture()} 捕获上下文，
     * 再在子线程中调用此方法恢复上下文执行逻辑。</p>
     *
     * @param context  通过 {@link #capture()} 捕获的上下文
     * @param supplier 要执行的逻辑
     * @param <T>      返回值类型
     * @return supplier 的返回值
     * @deprecated 使用 TransmittableThreadLocal + TtlExecutors 自动传播替代
     */
    @Deprecated
    public static <T> T runWithContext(Map<String, Object> context, Supplier<T> supplier) {
        try {
            restore(context);
            return supplier.get();
        } finally {
            clear();
        }
    }

    /**
     * 在指定上下文中执行 Runnable，执行完毕后自动清除上下文
     *
     * <p>用于异步场景：先在父线程通过 {@link #capture()} 捕获上下文，
     * 再在子线程中调用此方法恢复上下文执行逻辑。</p>
     *
     * @param context  通过 {@link #capture()} 捕获的上下文
     * @param runnable 要执行的逻辑
     * @deprecated 使用 TransmittableThreadLocal + TtlExecutors 自动传播替代
     */
    @Deprecated
    public static void runWithContext(Map<String, Object> context, Runnable runnable) {
        try {
            restore(context);
            runnable.run();
        } finally {
            clear();
        }
    }

    /**
     * 包装 Callable 以在执行时自动传播指定的上下文
     *
     * <p>适用于 {@link CompletableFuture}、线程池等异步场景：</p>
     * <pre>
     * Map&lt;String, Object&gt; ctx = RequestContext.capture();
     * CompletableFuture.supplyAsync(RequestContext.wrapCallable(() -&gt; {
     *     return RequestContext.getUserId();
     * }, ctx));
     * </pre>
     *
     * @param callable 要包装的 Callable
     * @param context  通过 {@link #capture()} 捕获的上下文
     * @param <T>      返回值类型
     * @return 包装后的 Callable，执行时会自动恢复和清理上下文
     * @deprecated 使用 TransmittableThreadLocal + TtlExecutors.getTtlExecutor() 自动传播替代
     */
    @Deprecated
    public static <T> Callable<T> wrapCallable(Callable<T> callable, Map<String, Object> context) {
        return () -> {
            try {
                restore(context);
                return callable.call();
            } finally {
                clear();
            }
        };
    }

    /**
     * 获取当前上下文快照
     *
     * @return 上下文 Map 的副本
     */
    public static Map<String, Object> snapshot() {
        return new HashMap<>(CONTEXT_HOLDER.get());
    }

    /**
     * 恢复上下文到当前线程
     *
     * <p>先清除当前线程已有的上下文，再将指定的上下文快照恢复到当前线程。
     * 用于异步场景中子线程恢复父线程捕获的上下文。</p>
     *
     * @param context 通过 {@link #capture()} 捕获的上下文快照
     */
    private static void restore(Map<String, Object> context) {
        CONTEXT_HOLDER.remove();
        if (context != null && !context.isEmpty()) {
            CONTEXT_HOLDER.set(new HashMap<>(context));
        }
    }

    /**
     * 创建一个上下文清理守卫，用于 try-with-resources 模式
     *
     * <p>在 try 块结束时（无论正常或异常），自动调用 {@link #clear()} 清理当前线程的上下文。</p>
     *
     * <p><b>使用示例：</b></p>
     * <pre>
     * try (RequestContext.CleanupGuard guard = RequestContext.newCleanupGuard()) {
     *     RequestContext.setUserId("user123");
     *     RequestContext.setTenantId("tenant456");
     *     // ... 业务逻辑
     * } // 自动清理上下文，防止内存泄漏
     * </pre>
     *
     * @return CleanupGuard 实例
     */
    public static CleanupGuard newCleanupGuard() {
        return new CleanupGuard();
    }

    /**
     * 上下文清理守卫，实现 {@link AutoCloseable} 以支持 try-with-resources 模式
     *
     * <p>此类的唯一用途是配合 try-with-resources 语法，在 try 块结束时自动清理请求上下文。</p>
     */
    public static final class CleanupGuard implements AutoCloseable {

        private CleanupGuard() {
            // 私有构造，仅允许通过 RequestContext.newCleanupGuard() 创建
        }

        @Override
        public void close() {
            clear();
        }
    }
}
