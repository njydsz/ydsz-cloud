package com.njydsz.common.core.context;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.MDC;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.njydsz.common.core.constant.HeaderConstants;

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
    /** 上下文键名：客户端 IP */
    public static final String KEY_CLIENT_IP = "clientIp";
    /** 上下文键名：请求来源（INTERNAL / OPEN_API / WEB_HOOK 等）*/
    public static final String KEY_REQUEST_SOURCE = "requestSource";
    /** 上下文键名：API 版本 */
    public static final String KEY_API_VERSION = "apiVersion";

    // ==================== v1.9 业务级上下文键（已下沉至 BizContextKeys） ====================
    // 以下常量仅为向后兼容保留，已 @Deprecated 并桥接至 {@link BizContextKeys}。
    // 新代码请直接引用 {@link BizContextKeys}，或在各自模块内声明类型安全的 {@link ContextKey}。

    /** @deprecated 请使用 {@link BizContextKeys#KEY_AUTH_INFO} */
    @Deprecated(since = "1.9.0", forRemoval = false)
    public static final String KEY_AUTH_INFO = BizContextKeys.KEY_AUTH_INFO;

    /** @deprecated 请使用 {@link BizContextKeys#KEY_LOGIN_USER} */
    @Deprecated(since = "1.9.0", forRemoval = false)
    public static final String KEY_LOGIN_USER = BizContextKeys.KEY_LOGIN_USER;

    /** @deprecated 请使用 {@link BizContextKeys#KEY_TENANT_CONTEXT} */
    @Deprecated(since = "1.9.0", forRemoval = false)
    public static final String KEY_TENANT_CONTEXT = BizContextKeys.KEY_TENANT_CONTEXT;

    /** @deprecated 请使用 {@link BizContextKeys#KEY_COLUMN_PERMISSION} */
    @Deprecated(since = "1.9.0", forRemoval = false)
    public static final String KEY_COLUMN_PERMISSION = BizContextKeys.KEY_COLUMN_PERMISSION;

    /** @deprecated 请使用 {@link BizContextKeys#KEY_AUDIT_DATA} */
    @Deprecated(since = "1.9.0", forRemoval = false)
    public static final String KEY_AUDIT_DATA = BizContextKeys.KEY_AUDIT_DATA;

    /** @deprecated 请使用 {@link BizContextKeys#KEY_HTTP_REQUEST}；建议改用 {@link #setRequestSnapshot(RequestSnapshot)} 的不可变快照 */
    @Deprecated(since = "1.9.0", forRemoval = false)
    public static final String KEY_HTTP_REQUEST = BizContextKeys.KEY_HTTP_REQUEST;

    /** @deprecated 请使用 {@link BizContextKeys#KEY_EXTRA_HEADERS} */
    @Deprecated(since = "1.9.0", forRemoval = false)
    public static final String KEY_EXTRA_HEADERS = BizContextKeys.KEY_EXTRA_HEADERS;

    /** @deprecated 请使用 {@link BizContextKeys#KEY_CACHED_USER_INFO_MAP} */
    @Deprecated(since = "1.9.0", forRemoval = false)
    public static final String KEY_CACHED_USER_INFO_MAP = BizContextKeys.KEY_CACHED_USER_INFO_MAP;

    /**
     * 请求上下文存储（懒初始化）。
     *
     * <p>使用 {@link TransmittableThreadLocal} 支持线程池场景下的上下文传递。
     * 采用懒初始化策略：仅当首次 {@link #put(String, Object)} 时才创建 Map，
     * 避免仅读取上下文（如仅调用 {@link #getUserId()} 判断）时无谓分配 HashMap。
     * 初始容量 8，适配内置 6 个键的典型场景。</p>
     */
    private static final TransmittableThreadLocal<Map<String, Object>> CONTEXT_HOLDER =
            new TransmittableThreadLocal<Map<String, Object>>() {
                @Override
                protected Map<String, Object> initialValue() {
                    return null; // 懒初始化：首次 put 时才创建
                }

                /**
                 * 传播上下文到子线程时创建防御性拷贝（浅拷贝，值以引用传递），
                 * 避免父线程与子线程共享同一 HashMap 导致并发修改异常。
                 */
                @Override
                public Map<String, Object> copy(Map<String, Object> parentValue) {
                    return parentValue == null ? null : new HashMap<>(parentValue);
                }
            };

    /**
     * 请求级用户信息缓存的存储（与通用上下文分离）。
     *
     * <p>该缓存仅为性能优化（避免同一请求内反复访问远程缓存），
     * 不随 TTL 跨线程传播：子线程各自懒重建，避免共享可变 Map 的并发风险，
     * 也避免大对象被无谓克隆放大拷贝成本。</p>
     */
    private static final TransmittableThreadLocal<Map<String, Object>> CACHE_HOLDER =
            new TransmittableThreadLocal<Map<String, Object>>() {
                @Override
                protected Map<String, Object> initialValue() {
                    return null;
                }

                @Override
                public Map<String, Object> copy(Map<String, Object> parentValue) {
                    // 不跨线程传播：返回 null，子线程按需重建本地缓存
                    return null;
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
     * 设置客户端 IP
     *
     * @param clientIp 客户端 IP 地址
     * @since 1.8.0
     */
    public static void setClientIp(String clientIp) {
        put(KEY_CLIENT_IP, clientIp);
    }

    /**
     * 获取客户端 IP
     *
     * @return 客户端 IP，不存在时返回 null
     * @since 1.8.0
     */
    public static String getClientIp() {
        return (String) get(KEY_CLIENT_IP);
    }

    /**
     * 设置请求来源
     *
     * <p>典型取值：{@code INTERNAL}（内部服务调用）、{@code OPEN_API}（开放接口）、
     * {@code WEB_HOOK}（第三方回调）等。
     *
     * @param requestSource 请求来源标识
     * @since 1.8.0
     */
    public static void setRequestSource(String requestSource) {
        put(KEY_REQUEST_SOURCE, requestSource);
    }

    /**
     * 获取请求来源
     *
     * @return 请求来源，不存在时返回 null
     * @since 1.8.0
     */
    public static String getRequestSource() {
        return (String) get(KEY_REQUEST_SOURCE);
    }

    /**
     * 设置 API 版本号
     *
     * <p>用于 API 生命周期管理 (v1/v2/...) 与灰度分流场景。
     *
     * @param apiVersion API 版本号
     * @since 1.8.0
     */
    public static void setApiVersion(String apiVersion) {
        put(KEY_API_VERSION, apiVersion);
    }

    /**
     * 获取 API 版本号
     *
     * @return API 版本号，不存在时返回 null
     * @since 1.8.0
     */
    public static String getApiVersion() {
        return (String) get(KEY_API_VERSION);
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
     * 使用类型安全的 {@link ContextKey} 设置属性（推荐）。
     *
     * <p>编译期保证类型安全，避免运行时的 ClassCastException。</p>
     *
     * @param <T>   值类型
     * @param key   上下文键
     * @param value 属性值
     * @since 1.5.0
     * @see ContextKey
     */
    public static <T> void put(ContextKey<T> key, T value) {
        put(key.key(), value);
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
     * 使用类型安全的 {@link ContextKey} 获取属性（推荐）。
     *
     * <p>返回值已是目标类型，无需手动转型。</p>
     *
     * @param <T> 值类型
     * @param key 上下文键
     * @return 属性值；不存在则返回 null
     * @since 1.5.0
     * @see ContextKey
     */
    public static <T> T get(ContextKey<T> key) {
        Object value = get(key.key());
        return key.cast(value);
    }

    /**
     * 使用类型安全的 {@link ContextKey} 获取属性，不存在时返回默认值（推荐）。
     *
     * <p>对标 {@link Map#getOrDefault(Object, Object)} 语义，
     * 避免调用方手动判空。</p>
     *
     * @param <T>          值类型
     * @param key          上下文键
     * @param defaultValue 不存在时返回的默认值
     * @return 属性值；不存在则返回 defaultValue
     * @since 1.7.0
     * @see ContextKey
     */
    public static <T> T getOrDefault(ContextKey<T> key, T defaultValue) {
        T value = get(key);
        return value != null ? value : defaultValue;
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
     * 使用类型安全的 {@link ContextKey} 移除属性（推荐）。
     *
     * @param key 上下文键
     * @since 1.5.0
     */
    public static void remove(ContextKey<?> key) {
        remove(key.key());
    }

    /**
     * 清空当前线程的上下文
     *
     * <p><b>重要：</b>必须在请求结束时调用此方法，防止 ThreadLocal 内存泄漏</p>
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
        CACHE_HOLDER.remove();
    }

    // ======================== v1.9 业务层便捷方法 ========================

    /**
     * 写入认证信息到请求上下文。
     *
     * <p>由认证 Filter/Interceptor 调用，供后续 {@link #getAuthInfo()} 读取。</p>
     *
     * @param authInfo 认证信息（可为 null，等同于移除）
     * @since 1.9.0
     */
    public static void setAuthInfo(Object authInfo) {
        if (authInfo == null) {
            remove(KEY_AUTH_INFO);
        } else {
            put(KEY_AUTH_INFO, authInfo);
        }
    }

    /**
     * 获取当前请求的认证信息。
     *
     * @return 认证信息（通常实现为 AuthInfo），不存在返回 null
     * @since 1.9.0
     */
    public static Object getAuthInfo() {
        return get(KEY_AUTH_INFO);
    }

    /**
     * 获取当前请求的认证信息（类型安全版本）。
     *
     * @param type 期望类型
     * @param <T> 类型参数
     * @return 认证信息，不存在或类型不匹配返回 null
     * @since 1.9.0
     */
    @SuppressWarnings("unchecked")
    public static <T> T getAuthInfo(Class<T> type) {
        Object authInfo = get(KEY_AUTH_INFO);
        return type.isInstance(authInfo) ? (T) authInfo : null;
    }

    /**
     * 写入登录用户信息（LoginUser）。
     *
     * @param loginUser 登录用户信息
     * @since 1.9.0
     */
    public static void setLoginUser(Object loginUser) {
        if (loginUser == null) {
            remove(KEY_LOGIN_USER);
        } else {
            put(KEY_LOGIN_USER, loginUser);
        }
    }

    /**
     * 获取当前登录用户（LoginUser）。
     *
     * @return 登录用户，不存在返回 null
     * @since 1.9.0
     */
    public static Object getLoginUser() {
        return get(KEY_LOGIN_USER);
    }

    /**
     * 获取当前登录用户（类型安全版本）。
     *
     * @param type 期望类型
     * @param <T> 类型参数
     * @return 登录用户，不存在或类型不匹配返回 null
     * @since 1.9.0
     */
    @SuppressWarnings("unchecked")
    public static <T> T getLoginUser(Class<T> type) {
        Object loginUser = get(KEY_LOGIN_USER);
        return type.isInstance(loginUser) ? (T) loginUser : null;
    }

    /**
     * 写入租户上下文（TenantContext）。
     *
     * <p>写入后将同时更新 {@link #setTenantId(String)} 以确保一致性。</p>
     *
     * @param tenantContext 租户上下文，null 等同于移除
     * @since 1.9.0
     */
    public static void setTenantContext(Object tenantContext) {
        if (tenantContext == null) {
            remove(KEY_TENANT_CONTEXT);
        } else {
            put(KEY_TENANT_CONTEXT, tenantContext);
        }
    }

    /**
     * 获取当前租户上下文（TenantContext）。
     *
     * @return 租户上下文，不存在返回 null
     * @since 1.9.0
     */
    public static Object getTenantContext() {
        return get(KEY_TENANT_CONTEXT);
    }

    /**
     * 写入列权限信息。
     *
     * @param columnPermission 列权限信息
     * @since 1.9.0
     */
    public static void setColumnPermission(Object columnPermission) {
        if (columnPermission == null) {
            remove(KEY_COLUMN_PERMISSION);
        } else {
            put(KEY_COLUMN_PERMISSION, columnPermission);
        }
    }

    /**
     * 获取当前列权限信息。
     *
     * @return 列权限信息，不存在返回 null
     * @since 1.9.0
     */
    public static Object getColumnPermission() {
        return get(KEY_COLUMN_PERMISSION);
    }

    /**
     * 写入 HTTP 请求不可变快照（推荐）。
     *
     * <p>相比 {@link #setHttpRequest(Object)} 直接持有活的 {@code HttpServletRequest}，
     * 快照在入口处一次性拷贝所需元数据，与 Servlet API 解耦，
     * 可在异步 / 线程池 / 序列化边界安全传递。</p>
     *
     * @param snapshot 请求快照（可为 null，等同于移除）
     * @since 1.9.1
     * @see RequestSnapshot
     */
    public static void setRequestSnapshot(RequestSnapshot snapshot) {
        if (snapshot == null) {
            remove(KEY_HTTP_REQUEST);
        } else {
            put(KEY_HTTP_REQUEST, snapshot);
        }
    }

    /**
     * 获取 HTTP 请求不可变快照（类型安全）。
     *
     * @return 请求快照；不存在或存入的是原生 {@code HttpServletRequest} 时返回 null
     * @since 1.9.1
     */
    public static RequestSnapshot getRequestSnapshot() {
        Object obj = get(KEY_HTTP_REQUEST);
        return obj instanceof RequestSnapshot ? (RequestSnapshot) obj : null;
    }

    /**
     * 写入 HTTP 请求对象引用（已废弃，仅向后兼容）。
     *
     * @deprecated 新代码请使用 {@link #setRequestSnapshot(RequestSnapshot)} 的不可变快照，
     *             避免持有活的 {@code HttpServletRequest}（不可序列化、异步边界易泄漏、绑死 Servlet API）
     * @param request HTTP 请求对象
     * @since 1.9.0
     */
    @Deprecated(since = "1.9.1", forRemoval = false)
    public static void setHttpRequest(Object request) {
        put(KEY_HTTP_REQUEST, request);
    }

    /**
     * 获取当前 HTTP 请求对象引用（已废弃，仅向后兼容）。
     *
     * @deprecated 请使用 {@link #getRequestSnapshot()}
     * @return HTTP 请求对象，不存在返回 null
     * @since 1.9.0
     */
    @Deprecated(since = "1.9.1", forRemoval = false)
    public static Object getHttpRequest() {
        return get(KEY_HTTP_REQUEST);
    }

    /**
     * 写入一个数据权限相关的虚拟请求头。
     *
     * <p>由数据权限解析器写入，SQL 拦截器通过 {@link #getExtraHeader(String)} 读取。</p>
     *
     * @param key header 名（如 X-Data-Scope）
     * @param value header 值
     * @since 1.9.0
     */
    public static void putExtraHeader(String key, String value) {
        Map<String, String> headers = (Map<String, String>) get(KEY_EXTRA_HEADERS);
        if (headers == null) {
            headers = new java.util.LinkedHashMap<>(4);
            put(KEY_EXTRA_HEADERS, headers);
        }
        headers.put(key, value);
    }

    /**
     * 获取一个数据权限相关的虚拟请求头。
     *
     * @param key header 名
     * @return header 值，不存在返回 null
     * @since 1.9.0
     */
    public static String getExtraHeader(String key) {
        Object obj = get(KEY_EXTRA_HEADERS);
        if (obj instanceof Map) {
            return ((Map<String, String>) obj).get(key);
        }
        return null;
    }

    /**
     * 获取全部虚拟请求头（用于 Feign 透传、SQL 拦截器）。
     *
     * @return 虚拟请求头 Map（不可变），不存在返回空 Map
     * @since 1.9.0
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> getExtraHeaders() {
        Object obj = get(KEY_EXTRA_HEADERS);
        if (obj instanceof Map) {
            return java.util.Collections.unmodifiableMap((Map<String, String>) obj);
        }
        return java.util.Collections.emptyMap();
    }

    /**
     * 在当前请求上下文基础上，克隆一份请求级用户信息缓存 Map。
     *
     * <p>由 RbacPermissionEvaluator 在启动时调用一次写入，
     * 供同一请求内多次权限校验复用，避免反复 Redis 调用。</p>
     *
     * @return 可变的缓存 Map
     * @since 1.9.0
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> createCachedUserInfoMap() {
        Map<String, Object> map = new java.util.LinkedHashMap<>(8);
        CACHE_HOLDER.set(map);
        return map;
    }

    /**
     * 获取请求级用户信息缓存 Map。
     *
     * <p>该缓存存储于与通用上下文分离的 {@code CACHE_HOLDER}，不随 TTL 跨线程传播。</p>
     *
     * @return 缓存 Map（可变），未创建返回 null
     * @since 1.9.0
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getCachedUserInfoMap() {
        return CACHE_HOLDER.get();
    }

    /**
     * 创建当前上下文的快照（不可变 Map），用于跨线程传递。
     *
     * <p>快照是<b>防御性拷贝</b>：保留原始值类型（如 Boolean、Long），
     * 对快照的修改不会影响当前线程上下文。
     * 配合 {@link #restore(Map)} 实现在子线程中恢复上下文（如异步任务、事件发布）。</p>
     *
     * @return 上下文的不可变快照；上下文为空时返回空 Map
     * @since 1.8.0
     */
    public static Map<String, Object> snapshot() {
        Map<String, Object> holder = CONTEXT_HOLDER.get();
        if (holder == null || holder.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new HashMap<>(holder));
    }

    /**
     * 恢复上下文快照。
     *
     * <p>会用快照中的<b>全部键值</b>覆盖当前线程上下文（快照中不存在的 key 会被保留，
     * 不会自动清除）。如需清空当前上下文再恢复，请调用方在执行前先 {@link #clear()}。</p>
     *
     * @param snapshot 通过 {@link #snapshot()} 获取的快照，可为 null（空操作）
     * @since 1.8.0
     */
    public static void restore(Map<String, Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
            if (entry.getValue() == null) {
                remove(entry.getKey());
            } else {
                put(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 将当前上下文桥接到 SLF4J MDC。
     *
     * <p>桥接 tenantId / userId / traceId / requestId 四个常用字段，
     * 使日志框架的 {@code %X{tenantId}} / %X{traceId}} 占位符生效。
     * 一般在 Filter/Interceptor 入口与 {@link RequestContext} 赋值后调用一次。</p>
     *
     * <p><b>清理：</b>桥接写入的 MDC 条目由调用方在请求结束时通过 {@link #clearMdc()} 清理。
     * 推荐使用 {@link #runWithCleanup(Runnable)} 工具方法，它会连同 {@link #clear()} 与 {@link #clearMdc()} 一并处理。</p>
     *
     * @since 1.8.0
     * @see #clearMdc()
     */
    public static void bridgeToMdc() {
        String tenantId = getTenantId();
        if (tenantId != null) {
            MDC.put(KEY_TENANT_ID, tenantId);
        }
        String userId = getUserId();
        if (userId != null) {
            MDC.put(KEY_USER_ID, userId);
        }
        String traceId = getTraceId();
        if (traceId != null) {
            MDC.put(KEY_TRACE_ID, traceId);
        }
        String requestId = getRequestId();
        if (requestId != null) {
            MDC.put(KEY_REQUEST_ID, requestId);
        }
    }

    /**
     * 清理由 {@link #bridgeToMdc()} 写入的 MDC 条目。
     *
     * @since 1.8.0
     * @see #bridgeToMdc()
     */
    public static void clearMdc() {
        MDC.remove(KEY_TENANT_ID);
        MDC.remove(KEY_USER_ID);
        MDC.remove(KEY_TRACE_ID);
        MDC.remove(KEY_REQUEST_ID);
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
    public static Map<String, Object> dump() {
        Map<String, Object> holder = CONTEXT_HOLDER.get();
        if (holder == null || holder.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new HashMap<>(holder));
    }

    /**
     * 在当前上下文中运行一段逻辑，并在执行后（无论是否抛异常）自动清理当前线程上下文与 MDC。
     *
     * <p>等价于：</p>
     * <pre>{@code
     * try (CleanupGuard ignored = newCleanupGuard()) {
     *     task.run();
     * }
     * }</pre>
     *
     * <p>相比直接使用 {@link #newCleanupGuard()}，该方法会在清理阶段额外调用 {@link #clearMdc()}，
     * 避免 {@link #bridgeToMdc()} 写入的 MDC 条目残留在复用线程上。</p>
     *
     * @param task 待执行逻辑
     * @since 1.8.0
     */
    public static void runWithCleanup(Runnable task) {
        try (CleanupGuard guard = newCleanupGuard()) {
            task.run();
        } finally {
            clearMdc();
        }
    }

    /**
     * 在当前上下文中运行一段带返回值的逻辑，并在执行后（无论是否抛异常）自动清理上下文与 MDC。
     *
     * @param <T>  返回值类型
     * @param supplier 待执行逻辑
     * @return 逻辑返回值
     * @since 1.8.0
     */
    public static <T> T supplyWithCleanup(java.util.function.Supplier<T> supplier) {
        try (CleanupGuard guard = newCleanupGuard()) {
            return supplier.get();
        } finally {
            clearMdc();
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
     * 上下文清理守卫，实现 {@link AutoCloseable} 以支持 try-with-resources 模式。
     *
     * <p>在 close() 时自动调用 {@link #clear()} 清理当前线程的上下文。</p>
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
