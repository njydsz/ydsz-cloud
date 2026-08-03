package com.njydsz.common.core.context;

import java.util.HashMap;
import java.util.Map;
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

    /**
     * 请求上下文存储（懒初始化）。
     *
     * <p>使用 {@link TransmittableThreadLocal} 支持线程池场景下的上下文传递。
     * 采用懒初始化策略：仅当首次 {@link #put(String, Object)} 时才创建 Map，
     * 避免仅读取上下文（如仅调用 {@link #getUserId()} 判断）时无谓分配 HashMap。
     * 初始容量 8，适配内置 6 个键的典型场景。</p>
     */
    private static final ThreadLocal<Map<String, Object>> CONTEXT_HOLDER =
            new TransmittableThreadLocal<Map<String, Object>>() {
                @Override
                protected Map<String, Object> initialValue() {
                    return null; // 懒初始化：首次 put 时才创建
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
     * <p>首次调用时创建上下文 Map（懒初始化）。
     * 传入 {@code null} 值等同于 {@link #remove(String)}。</p>
     *
     * @param key   属性键
     * @param value 属性值
     */
    public static void put(String key, Object value) {
        if (key == null) {
            throw new NullPointerException("key cannot be null");
        }
        if (value == null) {
            remove(key);
            return;
        }
        Map<String, Object> holder = CONTEXT_HOLDER.get();
        if (holder == null) {
            holder = new HashMap<>(8);
            CONTEXT_HOLDER.set(holder);
        }
        holder.put(key, value);
    }

    /**
     * 获取属性
     *
     * <p>上下文未初始化时返回 null，不触发 Map 创建。</p>
     *
     * @param key 属性键
     * @return 属性值，如果不存在返回 null
     */
    public static Object get(String key) {
        Map<String, Object> holder = CONTEXT_HOLDER.get();
        return holder != null ? holder.get(key) : null;
    }

    /**
     * 移除属性
     *
     * @param key 属性键
     */
    public static void remove(String key) {
        Map<String, Object> holder = CONTEXT_HOLDER.get();
        if (holder != null) {
            holder.remove(key);
        }
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
     * 创建当前线程上下文的诊断快照。
     *
     * <p>返回当前线程上下文的<b>不可变浅拷贝</b>（Map 本身不可变，值为原引用），
     * 用于开发诊断、日志输出、链路排查等场景。快照不影响原上下文。</p>
     *
     * <p><b>使用示例：</b></p>
     * <pre>{@code
     * // 联调时输出完整上下文
     * log.debug("Request context: {}", RequestContext.dump());
     *
     * // 诊断特定键是否存在
     * if (!RequestContext.dump().containsKey(RequestContext.KEY_TENANT_ID)) {
     *     log.warn("tenantId missing in request context");
     * }
     * }</pre>
     *
     * @return 当前线程上下文的不可变快照；上下文未初始化时返回空 Map
     */
    public static java.util.Map<String, Object> dump() {
        Map<String, Object> holder = CONTEXT_HOLDER.get();
        if (holder == null || holder.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        return java.util.Collections.unmodifiableMap(new HashMap<>(holder));
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
     * 创建请求上下文 Builder，用于一次性批量设置多个属性。
     *
     * <p>调用 {@link Builder#apply()} 将全部属性写入当前线程上下文。</p>
     *
     * <p><b>使用示例：</b></p>
     * <pre>{@code
     * RequestContext.builder()
     *         .userId("user123")
     *         .tenantId("tenant456")
     *         .traceId(TraceIdGenerator.generate())
     *         .language("zh-CN")
     *         .apply();
     * }</pre>
     *
     * @return Builder 实例
     * @since 1.2.0
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 请求上下文 Builder。
     *
     * <p>提供类型化的 setter 链式调用，{@link #apply()} 一次性提交到当前线程上下文。</p>
     *
     * @since 1.2.0
     */
    public static final class Builder {

        private String userId;
        private String tenantId;
        private String traceId;
        private String requestId;
        private String language;
        private Boolean tenantIsolationSkipped;

        private Builder() {
            // 私有构造，仅允许通过 RequestContext.builder() 创建
        }

        /**
         * 设置用户 ID。
         *
         * @param userId 用户 ID
         * @return this
         */
        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        /**
         * 设置租户 ID。
         *
         * @param tenantId 租户 ID
         * @return this
         */
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        /**
         * 设置链路追踪 ID。
         *
         * @param traceId 追踪 ID
         * @return this
         */
        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        /**
         * 设置请求 ID。
         *
         * @param requestId 请求 ID
         * @return this
         */
        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        /**
         * 设置语言区域。
         *
         * @param language 语言区域（如 zh-CN、en-US）
         * @return this
         */
        public Builder language(String language) {
            this.language = language;
            return this;
        }

        /**
         * 设置租户隔离跳过标记。
         *
         * @param skipped true=跳过租户隔离
         * @return this
         */
        public Builder tenantIsolationSkipped(boolean skipped) {
            this.tenantIsolationSkipped = skipped;
            return this;
        }

        /**
         * 将 Builder 中的属性一次性写入当前线程上下文。
         *
         * <p>仅写入非 null 属性；null 属性不覆盖已有上下文值。</p>
         *
         * @return CleanupGuard 实例，供 try-with-resources 使用
         */
        public CleanupGuard apply() {
            if (userId != null) {
                setUserId(userId);
            }
            if (tenantId != null) {
                setTenantId(tenantId);
            }
            if (traceId != null) {
                setTraceId(traceId);
            }
            if (requestId != null) {
                setRequestId(requestId);
            }
            if (language != null) {
                setLanguage(language);
            }
            if (tenantIsolationSkipped != null) {
                setTenantIsolationSkipped(tenantIsolationSkipped);
            }
            return new CleanupGuard();
        }
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
