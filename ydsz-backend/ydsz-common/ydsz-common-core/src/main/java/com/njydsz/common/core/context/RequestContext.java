package com.njydsz.common.core.context;

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
 * @author ydsz-team
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
    /** 上下文键名：租户隔离跳过标记 */
    public static final String KEY_TENANT_ISOLATION_SKIPPED = "tenantIsolationSkipped";

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
     * 设置用户 ID
     *
     * @param userId 用户 ID
     */
    public static void setUserId(String userId) {
        put(KEY_USER_ID, userId);
    }

    /**
     * 获取用户 ID
     *
     * @return 用户 ID，如果不存在返回 null
     */
    public static String getUserId() {
        return (String) get(KEY_USER_ID);
    }

    /**
     * 设置租户 ID
     *
     * @param tenantId 租户 ID
     */
    public static void setTenantId(String tenantId) {
        put(KEY_TENANT_ID, tenantId);
    }

    /**
     * 获取租户 ID
     *
     * @return 租户 ID，如果不存在返回 null
     */
    public static String getTenantId() {
        return (String) get(KEY_TENANT_ID);
    }

    /**
     * 设置链路追踪 ID
     *
     * @param traceId 追踪 ID
     */
    public static void setTraceId(String traceId) {
        put(KEY_TRACE_ID, traceId);
    }

    /**
     * 获取链路追踪 ID
     *
     * @return 追踪 ID，如果不存在返回 null
     */
    public static String getTraceId() {
        return (String) get(KEY_TRACE_ID);
    }

    /**
     * 设置请求 ID
     *
     * @param requestId 请求 ID
     */
    public static void setRequestId(String requestId) {
        put(KEY_REQUEST_ID, requestId);
    }

    /**
     * 获取请求 ID
     *
     * @return 请求 ID，如果不存在返回 null
     */
    public static String getRequestId() {
        return (String) get(KEY_REQUEST_ID);
    }

    /**
     * 设置语言区域
     *
     * @param language 语言区域（如 zh-CN、en-US）
     */
    public static void setLanguage(String language) {
        put(KEY_LANGUAGE, language);
    }

    /**
     * 获取语言区域
     *
     * @return 语言区域，如果不存在返回 null
     */
    public static String getLanguage() {
        return (String) get(KEY_LANGUAGE);
    }

    /**
     * 设置租户隔离跳过标记。
     *
     * <p>当 Web 层拦截器判断当前请求 URL 在 anon-urls 白名单中时，
     * 调用此方法标记跳过租户隔离，SQL 拦截器将不注入租户条件。
     *
     * @param skipped true=跳过租户隔离
     */
    public static void setTenantIsolationSkipped(boolean skipped) {
        if (skipped) {
            put(KEY_TENANT_ISOLATION_SKIPPED, Boolean.TRUE);
        } else {
            remove(KEY_TENANT_ISOLATION_SKIPPED);
        }
    }

    /**
     * 检查当前请求是否应跳过租户隔离。
     *
     * @return true=跳过租户隔离
     */
    public static boolean isTenantIsolationSkipped() {
        return Boolean.TRUE.equals(get(KEY_TENANT_ISOLATION_SKIPPED));
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
     * @deprecated 项目全部使用 String key 方式，此方法从未被业务模块使用。
     */
    @Deprecated
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
     * @deprecated 项目全部使用 String key 方式，此方法从未被业务模块使用。
     */
    @Deprecated
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
     * 通过强类型 Key 获取属性（Optional）
     *
     * @param key 强类型 Key
     * @param <T> 关联类型
     * @return Optional 包装的属性值
     * @deprecated 项目全部使用 String key 方式，此方法从未被业务模块使用。
     */
    @Deprecated
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
     * @deprecated 项目全部使用 String key 方式，此方法从未被业务模块使用。
     */
    @Deprecated
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
     * 获取当前上下文快照
     *
     * @return 上下文 Map 的副本
     * @deprecated 项目使用 TTL（TransmittableThreadLocal）自动传播上下文，
     * 无需手动 snapshot/restore。保留仅供极端场景使用。
     */
    @Deprecated
    public static Map<String, Object> snapshot() {
        return new HashMap<>(CONTEXT_HOLDER.get());
    }

    /**
     * 从快照恢复上下文（覆盖当前线程的上下文）
     *
     * @param snapshot 之前通过 {@link #snapshot()} 获取的上下文快照
     * @deprecated 项目使用 TTL（TransmittableThreadLocal）自动传播上下文，
     * 无需手动 snapshot/restore。保留仅供极端场景使用。
     */
    @Deprecated
    public static void restore(Map<String, Object> snapshot) {
        if (snapshot != null) {
            CONTEXT_HOLDER.set(new HashMap<>(snapshot));
        }
    }

    /**
     * 包装 Callable，自动传播当前上下文到异步线程
     *
     * <p>在 Callable 执行前恢复上下文快照，执行后清除，防止内存泄漏。
     * 适用于手动提交到线程池的场景。</p>
     *
     * @param callable 原始 Callable
     * @param <T>      返回类型
     * @return 包装后的 Callable
     * @deprecated 项目使用 TTL（TransmittableThreadLocal）自动传播上下文，
     * 配合 common-thread 的 ThreadPoolTaskExecutor 使用，无需手动包装。
     */
    @Deprecated
    public static <T> Callable<T> wrapCallable(Callable<T> callable) {
        Map<String, Object> snapshot = snapshot();
        return () -> {
            Map<String, Object> previous = snapshot();
            try {
                restore(snapshot);
                return callable.call();
            } finally {
                if (previous.isEmpty()) {
                    clear();
                } else {
                    restore(previous);
                }
            }
        };
    }

    /**
     * 包装 Runnable，自动传播当前上下文到异步线程
     *
     * @param runnable 原始 Runnable
     * @return 包装后的 Runnable
     * @deprecated 项目使用 TTL（TransmittableThreadLocal）自动传播上下文，
     * 配合 common-thread 的 ThreadPoolTaskExecutor 使用，无需手动包装。
     */
    @Deprecated
    public static Runnable wrapRunnable(Runnable runnable) {
        Map<String, Object> snapshot = snapshot();
        return () -> {
            Map<String, Object> previous = snapshot();
            try {
                restore(snapshot);
                runnable.run();
            } finally {
                if (previous.isEmpty()) {
                    clear();
                } else {
                    restore(previous);
                }
            }
        };
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
