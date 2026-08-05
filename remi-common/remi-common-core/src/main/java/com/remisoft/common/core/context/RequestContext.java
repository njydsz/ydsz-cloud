package com.remisoft.common.core.context;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.remisoft.common.core.constant.HeaderConstants;

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
 * @author remi-team
 * @since 1.0.0
 * 
 */
public final class RequestContext {

    /** 上下文键名：用户ID */
    public static final String KEY_USER_ID = "userId";
    /** 上下文键名：租户ID */
    public static final String KEY_TENANT_ID = "tenantId";
    /** 上下文键名：链路追踪ID（值与 {@link HeaderConstants#MDC_TRACE_ID_KEY} 保持一致） */
    public static final String KEY_TRACE_ID = HeaderConstants.MDC_TRACE_ID_KEY;
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

    /**
     * 跨服务传播的元数据存储（懒初始化）。
     *
     * <p>专用于存储需要在服务间透传的用户自定义键值对（如 {@code appId}、{@code businessLine} 等）。
     * 与 {@link #CONTEXT_HOLDER} 中的内置上下文键分离，语义更清晰，
     * 且可根据需要独立控制是否参与传播。</p>
     *
     * <p>使用独立的 TTL 持有者，确保清理主上下文时 metadata 也同步清理。</p>
     *
     * @since 2.0.0
     * @see #putMetadata(String, String)
     * @see #exportMetadata()
     */
    private static final ThreadLocal<Map<String, String>> METADATA_HOLDER =
            new TransmittableThreadLocal<Map<String, String>>() {
                @Override
                protected Map<String, String> initialValue() {
                    return null; // 懒初始化
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
     * 清空当前线程的上下文和元数据。
     *
     * <p><b>重要：</b>必须在请求结束时调用此方法，防止 ThreadLocal 内存泄漏。
     * 同时清理主上下文和 {@link #METADATA_HOLDER} 中的元数据。</p>
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
        METADATA_HOLDER.remove();
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
     * 获取当前线程上下文的只读实时视图（零拷贝）。
     *
     * <p>与 {@link #dump()} 不同，此方法返回<b>实时视图</b>而非快照，
     * 避免了 HashMap 拷贝开销。调用方<b>不应修改</b>返回的 Map
     * （修改行为未定义，可能导致 ConcurrentModificationException）。</p>
     *
     * <p>适用场景：仅需要检查键是否存在、获取少量值、高频日志输出等<b>只读</b>场景。
     * 若需要可修改的快照或跨线程使用，请改用 {@link #dump()}。</p>
     *
     * <p><b>注意：</b>如果上下文未被初始化，返回 {@link Collections#emptyMap()}。</p>
     *
     * @return 当前线程上下文的只读实时视图（禁止修改）
     * @since 1.7.0
     */
    public static java.util.Map<String, Object> view() {
        Map<String, Object> holder = CONTEXT_HOLDER.get();
        if (holder == null || holder.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        return java.util.Collections.unmodifiableMap(holder);
    }

    // ======================== 跨服务元数据传播（v2.0 新增） ========================

    /**
     * 设置跨服务传播的元数据键值对。
     *
     * <p>专用于存储需要在服务间透传的用户自定义数据（如 {@code appId}、{@code businessLine} 等）。
     * 与主上下文键分离，语义更清晰，且不会与内置上下文键冲突。</p>
     *
     * <p>使用场景：注入下游服务透传信息，如多租户场景下的 {@code X-App-Id}、
     * 场景链追踪的 {@code X-Scenario-Code} 等。对应的值可通过
     * {@link #exportMetadata()} 导出为 HTTP 请求头键值对。</p>
     *
     * @param key   元数据键（不可为 null 或空）
     * @param value 元数据值（null 等同于移除该键）
     * @since 2.0.0
     * @see #exportMetadata()
     */
    public static void putMetadata(String key, String value) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("metadata key must not be null or empty");
        }
        if (value == null) {
            removeMetadata(key);
            return;
        }
        Map<String, String> holder = METADATA_HOLDER.get();
        if (holder == null) {
            holder = new HashMap<>(4);
            METADATA_HOLDER.set(holder);
        }
        holder.put(key, value);
    }

    /**
     * 获取元数据值。
     *
     * @param key 元数据键
     * @return 元数据值；不存在时返回 null
     * @since 2.0.0
     */
    public static String getMetadata(String key) {
        Map<String, String> holder = METADATA_HOLDER.get();
        return holder != null ? holder.get(key) : null;
    }

    /**
     * 获取所有跨服务传播的元数据（只读视图）。
     *
     * @return 当前线程的元数据 Map；未初始化时返回空 Map
     * @since 2.0.0
     */
    public static Map<String, String> getMetadata() {
        Map<String, String> holder = METADATA_HOLDER.get();
        if (holder == null || holder.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(holder);
    }

    /**
     * 移除元数据键值对。
     *
     * @param key 元数据键
     * @since 2.0.0
     */
    public static void removeMetadata(String key) {
        Map<String, String> holder = METADATA_HOLDER.get();
        if (holder != null) {
            holder.remove(key);
        }
    }

    /**
     * 导出所有元数据为 HTTP 请求头键值对。
     *
     * <p>用于下游 HTTP 调用时将元数据注入请求头（如 Feign 拦截器、RestTemplate 拦截器）。
     * 返回的 Map 是新的副本，修改不影响原 metadata。</p>
     *
     * <p><b>使用示例（Feign 拦截器）：</b></p>
     * <pre>{@code
     * public class MetadataPropagationInterceptor implements RequestInterceptor {
     *     @Override
     *     public void apply(RequestTemplate template) {
     *         RequestContext.exportMetadata().forEach(template::header);
     *     }
     * }
     * }</pre>
     *
     * @return 包含所有元数据的 Map；无元数据时返回空 Map
     * @since 2.0.0
     * @see #putMetadata(String, String)
     */
    public static Map<String, String> exportMetadata() {
        Map<String, String> holder = METADATA_HOLDER.get();
        if (holder == null || holder.isEmpty()) {
            return Collections.emptyMap();
        }
        return new HashMap<>(holder);
    }

    /**
     * 从 HTTP 请求头导入元数据。
     *
     * <p>在请求入口（如网关、过滤器）调用，将上游传递的元数据导入当前线程，
     * 供下游处理和业务逻辑使用。</p>
     *
     * <p><b>使用示例：</b></p>
     * <pre>{@code
     * // 在入口过滤器中注入上游元数据
     * Enumeration<String> metadataHeaders = request.getHeaders("X-Metadata-");
     * Map<String, String> imported = new HashMap<>();
     * while (metadataHeaders.hasMoreElements()) {
     *     String key = metadataHeaders.nextElement();
     *     imported.put(key.substring("X-Metadata-".length()), request.getHeader(key));
     * }
     * RequestContext.importMetadata(imported);
     * }</pre>
     *
     * @param metadata 要导入的元数据 Map（可为 null 或空）
     * @since 2.0.0
     * @see #exportMetadata()
     */
    public static void importMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        Map<String, String> holder = METADATA_HOLDER.get();
        if (holder == null) {
            holder = new HashMap<>(metadata.size());
            METADATA_HOLDER.set(holder);
        }
        holder.putAll(metadata);
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
     *     RequestContext.setTraceId(TraceIdGenerator.generateTraceId());
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
     * 创建一个带有 TTL（Time-To-Live）检测的上下文清理守卫。
     *
     * <p>在 try 块结束时（无论正常或异常）：
     * <ol>
     *   <li>自动调用 {@link #clear()} 清理当前线程的上下文</li>
     *   <li>检查上下文持有时间是否超过 {@code maxHoldTime}，若超过则输出 WARN 日志</li>
     * </ol>
     * </p>
     *
     * <p>适用于排查"线程池复用导致上下文未清理"的泄漏场景 — 如果 context 持有时间过长，
     * 通常意味着业务逻辑耗时过长或上下文未被及时清理。</p>
     *
     * <p><b>使用示例：</b></p>
     * <pre>{@code
     * // 设置最大持有时间为 30 秒
     * try (RequestContext.CleanupGuard guard = RequestContext.newCleanupGuard(Duration.ofSeconds(30))) {
     *     RequestContext.setUserId("user123");
     *     // ... 业务逻辑
     * } // 若超过 30 秒，日志将输出 WARN
     * }</pre>
     *
     * @param maxHoldTime 最大允许持有时间（不可为 null 或 negative）
     * @return 带 TTL 检测的 CleanupGuard 实例
     * @since 1.7.0
     */
    public static CleanupGuard newCleanupGuard(Duration maxHoldTime) {
        if (maxHoldTime == null || maxHoldTime.isNegative()) {
            throw new IllegalArgumentException("maxHoldTime must not be null or negative");
        }
        return new CleanupGuard(maxHoldTime);
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
     *         .traceId(TraceIdGenerator.generateTraceId())
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

        /** 自定义扩展属性存储，供开放式扩展使用。 */
        private Map<String, Object> extensions;

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
         * 设置自定义上下文属性（开放式扩展）。
         *
         * <p>用于内置键之外的自定义属性扩展，如 {@code appId}、{@code businessLine} 等。
         * 避免频繁修改 Builder 类即可支持新的上下文维度。</p>
         *
         * <p><b>注意：</b>key 不可为 null 或空字符串。</p>
         *
         * @param key   属性键（不可为 null 或空）
         * @param value 属性值（null 等同于移除该键）
         * @return this
         * @since 1.7.0
         */
        public Builder set(String key, Object value) {
            if (key == null || key.isEmpty()) {
                throw new IllegalArgumentException("Context key must not be null or empty");
            }
            if (value == null) {
                if (extensions != null) {
                    extensions.remove(key);
                }
                return this;
            }
            if (extensions == null) {
                extensions = new LinkedHashMap<>(4);
            }
            extensions.put(key, value);
            return this;
        }

        /**
         * 批量设置自定义上下文属性（开放式扩展）。
         *
         * @param attrs 属性 Map（可为 null 或空）
         * @return this
         * @since 1.7.0
         */
        public Builder setAll(Map<String, Object> attrs) {
            if (attrs != null && !attrs.isEmpty()) {
                if (extensions == null) {
                    extensions = new LinkedHashMap<>(attrs.size());
                }
                extensions.putAll(attrs);
            }
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
            // 写入自定义扩展属性
            if (extensions != null) {
                for (Map.Entry<String, Object> entry : extensions.entrySet()) {
                    put(entry.getKey(), entry.getValue());
                }
            }
            return new CleanupGuard();
        }
    }

    /**
     * 上下文清理守卫，实现 {@link AutoCloseable} 以支持 try-with-resources 模式
     *
     * <p>可选支持 TTL 泄漏检测：当上下文持有时间超过阈值时，输出 WARN 日志。</p>
     */
    public static final class CleanupGuard implements AutoCloseable {

        /** 上下文创建时间（用于 TTL 检测）。 */
        private final Instant createdAt;

        /** 最大允许持有时间（null 表示不检测）。 */
        private final Duration maxHoldTime;

        private CleanupGuard() {
            this.maxHoldTime = null;
            this.createdAt = null;
        }

        private CleanupGuard(Duration maxHoldTime) {
            this.maxHoldTime = maxHoldTime;
            this.createdAt = Instant.now();
        }

        @Override
        public void close() {
            clear();
            // TTL 泄漏检测
            if (maxHoldTime != null && createdAt != null) {
                Duration holdTime = Duration.between(createdAt, Instant.now());
                if (holdTime.compareTo(maxHoldTime) > 0) {
                    org.slf4j.LoggerFactory.getLogger(RequestContext.class)
                            .warn("RequestContext hold time {} exceeded maxHoldTime {}, possible leak detected",
                                    holdTime, maxHoldTime);
                }
            }
        }
    }
}
