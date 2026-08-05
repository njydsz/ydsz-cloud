package com.remisoft.common.util.auth;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import com.alibaba.ttl.TransmittableThreadLocal;

import lombok.extern.slf4j.Slf4j;

/**
 * 请求上下文持有器（ThreadLocal）。
 *
 * <p>基于 {@link TransmittableThreadLocal}（TTL）实现，在请求线程及其线程池子线程中安全传递
 * 认证信息（{@link AuthInfo}）、HTTP 请求对象（{@link HttpServletRequest}）及额外虚拟请求头。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>认证上下文：存储当前请求的 {@link AuthInfo}，供 {@link AuthInfoUtils} 快捷读取</li>
 *   <li>HTTP 请求对象：存储原始 {@link HttpServletRequest}，供非 Controller 层获取请求信息</li>
 *   <li>虚拟请求头（extra headers）：异步线程或 AOP 场景下补充数据权限 header，供 SQL 拦截器/Feign 透传读取</li>
 *   <li>线程复用检测：add() 时检测上一个请求是否已正确 remove()，防止上下文泄露</li>
 * </ul>
 *
 * <h2>线程池透传</h2>
 * <p>使用阿里 TTL 替代 {@link InheritableThreadLocal}，解决线程池复用时子线程无法继承父线程上下文的问题。
 * 需配合 TTL Agent 或 {@code TtlRunnable.get(runnable)} 使用。
 *
 * <h2>强制约定</h2>
 * <p>所有 Filter / Interceptor <b>必须</b>在 {@code finally} 块中调用 {@link #remove()}，否则会导致内存泄漏。
 *
 * <h2>正确用法</h2>
 * <pre>{@code
 * try {
 *     RequestHolder.add(authInfo);
 *     RequestHolder.add(request);
 *     chain.doFilter(req, resp);
 * } finally {
 *     RequestHolder.remove();
 * }
 * }</pre>
 *
 * @see AuthInfo
 * @see AuthInfoUtils
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
public class RequestHolder {

    /**
     * 认证信息 ThreadLocal，使用 TTL 支持线程池场景下安全透传。
     */
    private static final ThreadLocal<AuthInfo> authInfoHolder = new TransmittableThreadLocal<>();

    /**
     * HTTP 请求对象 ThreadLocal，存储原始 HttpServletRequest 供非 Controller 层使用。
     */
    private static final ThreadLocal<HttpServletRequest> requestHolder = new TransmittableThreadLocal<>();
    /**
     * 额外 header（虚拟请求头）。
     *
     * <p>用途：补齐真实 HttpServletRequest.getHeader(...) 不可用或不完整时的上下文透传。
     * 典型用于数据权限：X-Data-Scope/X-Company-Ids/X-Dept-Ids/X-Visible-Columns 等。
     *
     * <p>使用 {@link TransmittableThreadLocal} 确保线程池场景下 extra headers 也能透传，
     * 与 authInfoHolder/requestHolder 行为一致。
     */
    private static final ThreadLocal<Map<String, String>> extraHeadersHolder = new TransmittableThreadLocal<>();

    /**
     * 请求初始化标记：add() 时置为 true，remove() 时置为 false。
     *
     * <p><b>使用普通 {@link ThreadLocal} 而非 {@link TransmittableThreadLocal}</b>：
     * 该标记仅用于检测同一物理线程的复用（上一个请求是否已 remove），
     * 不应透传到线程池子线程，否则子线程会继承父线程的 {@code true} 并在
     * 调用 {@link #add(AuthInfo)} 时产生误报。
     */
    private static final ThreadLocal<Boolean> initialized = new ThreadLocal<>();

    /**
     * 写入认证信息到当前线程上下文。
     *
     * <p>写入前检测线程复用：若上一个请求未调用 {@link #remove()} 清理，
     * 将打印告警日志并强制清理，防止上下文串号。
     *
     * @param authInfo 认证信息，不允许为 null
     */
    public static void add(AuthInfo authInfo) {
        if (initialized.get() != null && initialized.get()) {
            log.warn("RequestHolder -> 线程复用检测: 上一个请求的上下文未被清理，已强制清理");
            remove();
        }
        initialized.set(true);
        authInfoHolder.set(authInfo);
    }

    /**
     * 写入 HTTP 请求对象到当前线程上下文。
     *
     * @param request HTTP 请求对象
     */
    public static void add(HttpServletRequest request) {
        requestHolder.set(request);
    }

    /**
     * 获取当前线程的认证信息。
     *
     * <p>若未写入则打印告警日志并返回 null，调用方应做空值判断。
     *
     * @return 认证信息；未写入时返回 null
     */
    public static AuthInfo getAuthInfo() {
        AuthInfo authInfo = authInfoHolder.get();
        if (authInfo == null) {
            log.warn("RequestHolder -> 当前线程 AuthInfo 为空，请检查拦截器配置");
        }
        return authInfo;
    }

    /**
     * 获取指定类型的认证信息（泛型增强）。
     *
     * <p>当项目使用自定义 {@link AuthInfo} 实现类时，可通过此方法安全转型。
     *
     * @param clazz 目标认证信息类型
     * @param <T>   认证信息泛型
     * @return 类型匹配的认证信息；不匹配或未写入时返回 null
     */
    public static <T extends AuthInfo> T getAuthInfo(Class<T> clazz) {
        AuthInfo authInfo = getAuthInfo();
        if (clazz.isInstance(authInfo)) {
            return clazz.cast(authInfo);
        }
        return null;
    }

    /**
     * 获取 remi 统一认证上下文信息。
     *
     * <p>快捷方法，等价于 {@code getAuthInfo(RemiAuthInfo.class)}。
     *
     * @return RemiAuthInfo 实例；非该类型或未写入时返回 null
     */
    public static RemiAuthInfo getRemiAuthInfo() {
        return getAuthInfo(RemiAuthInfo.class);
    }

    /**
     * 获取当前线程的 HTTP 请求对象。
     *
     * @return HttpServletRequest；未写入时返回 null
     */
    public static HttpServletRequest getCurrentRequest() {
        return requestHolder.get();
    }

    /**
     * 获取全部额外请求头的不可变视图。
     *
     * @return header 名到值的映射；无数据时返回空 Map
     */
    public static Map<String, String> getExtraHeaders() {
        Map<String, String> map = extraHeadersHolder.get();
        if (map == null || map.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * 按名称获取单个额外请求头值。
     *
     * @param name header 名称
     * @return header 值；不存在时返回 null
     */
    public static String getExtraHeader(String name) {
        if (name == null) {
            return null;
        }
        Map<String, String> map = extraHeadersHolder.get();
        if (map == null || map.isEmpty()) {
            return null;
        }
        return map.get(name);
    }

    /**
     * 写入单个额外请求头。
     *
     * <p>value 为 null 时移除该 header。
     *
     * @param name  header 名称，null 时忽略
     * @param value header 值，null 时移除
     */
    public static void putExtraHeader(String name, String value) {
        if (name == null) {
            return;
        }
        if (value == null) {
            removeExtraHeader(name);
            return;
        }
        Map<String, String> map = extraHeadersHolder.get();
        if (map == null) {
            map = new HashMap<>();
            extraHeadersHolder.set(map);
        }
        map.put(name, value);
    }

    /**
     * 移除单个额外请求头。
     *
     * @param name header 名称，null 时忽略
     */
    public static void removeExtraHeader(String name) {
        if (name == null) {
            return;
        }
        Map<String, String> map = extraHeadersHolder.get();
        if (map == null || map.isEmpty()) {
            return;
        }
        map.remove(name);
    }

    /**
     * 对额外请求头创建可变快照副本。
     *
     * <p>用于跨线程传递上下文：在父线程快照，在子线程通过 {@link #restoreExtraHeaders} 恢复。
     *
     * @return 可变 Map 副本；无数据时返回空 Map
     */
    public static Map<String, String> snapshotExtraHeaders() {
        Map<String, String> map = extraHeadersHolder.get();
        if (map == null || map.isEmpty()) {
            return Collections.emptyMap();
        }
        return new HashMap<>(map);
    }

    /**
     * 恢复 extra headers 快照。
     *
     * <p>用于 AOP/拦截器在本线程临时写入上下文后，确保调用链结束不泄露到后续逻辑。
     */
    public static void restoreExtraHeaders(Map<String, String> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            extraHeadersHolder.remove();
            return;
        }
        extraHeadersHolder.set(new HashMap<>(snapshot));
    }

    /**
     * 以指定认证信息执行任务，执行完毕后恢复原有上下文。
     *
     * <p>典型场景：内部定时任务、回调等需要以系统身份执行、执行完毕后恢复用户上下文。
     * 避免手动 set/remove 容易遗漏清理导致上下文泄露。
     *
     * <pre>{@code
     * RequestHolder.withContext(systemAuth, () -> {
     *     // 以系统身份执行的逻辑
     * });
     * // 此处上下文已恢复为调用前状态
     * }</pre>
     *
     * @param authInfo 临时认证信息；为 null 时清除当前认证上下文
     * @param runnable 待执行任务
     * @since 1.0.0
     */
    public static void withContext(AuthInfo authInfo, Runnable runnable) {
        AuthInfo originalAuth = authInfoHolder.get();
        Boolean originalInitialized = initialized.get();
        try {
            if (authInfo != null) {
                authInfoHolder.set(authInfo);
                initialized.set(true);
            } else {
                authInfoHolder.remove();
                initialized.remove();
            }
            runnable.run();
        } finally {
            if (originalAuth != null) {
                authInfoHolder.set(originalAuth);
            } else {
                authInfoHolder.remove();
            }
            if (Boolean.TRUE.equals(originalInitialized)) {
                initialized.set(true);
            } else {
                initialized.remove();
            }
        }
    }

    /**
     * 对当前线程的完整上下文创建不可变快照。
     *
     * <p>涵盖 authInfo、HttpServletRequest、extra headers 及 initialized 标记，
     * 用于跨线程完整上下文透传（例如线程池提交任务前快照、子线程中恢复）。
     *
     * @return 完整上下文快照
     * @since 1.4.0
     */
    public static ContextSnapshot snapshot() {
        return new ContextSnapshot(
                authInfoHolder.get(),
                requestHolder.get(),
                snapshotExtraHeaders(),
                initialized.get()
        );
    }

    /**
     * 恢复完整上下文快照到当前线程。
     *
     * <p>与 {@link #snapshot()} 配对使用，将快照中的所有上下文恢复。
     * 适合在线程池子线程中恢复父线程上下文。
     *
     * @param snapshot 上下文快照（null 时无操作）
     * @since 1.4.0
     */
    public static void restore(ContextSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        if (snapshot.authInfo != null) {
            authInfoHolder.set(snapshot.authInfo);
        } else {
            authInfoHolder.remove();
        }
        if (snapshot.request != null) {
            requestHolder.set(snapshot.request);
        } else {
            requestHolder.remove();
        }
        restoreExtraHeaders(snapshot.extraHeaders);
        if (Boolean.TRUE.equals(snapshot.initialized)) {
            initialized.set(true);
        } else {
            initialized.remove();
        }
    }

    /**
     * 完整上下文快照，包含 authInfo、request、extraHeaders 和 initialized 标记。
     *
     * <p>用于跨线程传递完整请求上下文。所有字段均为不可变快照，
     * 确保快照创建后不受原始线程后续修改的影响。
     *
     * @since 1.4.0
     */
    public static final class ContextSnapshot {
        private final AuthInfo authInfo;
        private final HttpServletRequest request;
        private final Map<String, String> extraHeaders;
        private final Boolean initialized;

        ContextSnapshot(AuthInfo authInfo, HttpServletRequest request,
                        Map<String, String> extraHeaders, Boolean initialized) {
            this.authInfo = authInfo;
            this.request = request;
            this.extraHeaders = extraHeaders;
            this.initialized = initialized;
        }

        public AuthInfo getAuthInfo() { return authInfo; }
        public HttpServletRequest getRequest() { return request; }
        public Map<String, String> getExtraHeaders() { return extraHeaders; }
        public Boolean getInitialized() { return initialized; }
    }

    /**
     * 释放资源 (必须在请求结束时调用，防止内存泄漏)
     */
    public static void remove() {
        authInfoHolder.remove();
        requestHolder.remove();
        extraHeadersHolder.remove();
        initialized.remove();
    }
}
