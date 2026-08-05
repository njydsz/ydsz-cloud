package com.remisoft.common.core.context;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import org.slf4j.MDC;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 请求上下文（基于 TransmittableThreadLocal）。
 *
 * <p>凡是涉及请求级用户信息的读取/写入，都应通过本类，
 * 确保线程池场景下由 TTL 自动传播。
 *
 * <h3>防御性清理：</h3>
 * <p>推荐使用 {@link #runWithCleanup(Runnable)} 或 {@link #supplyWithCleanup(Supplier)} 方法，
 * 强制在 finally 中清理上下文，防止 ThreadLocal 泄漏。
 *
 * <h3>跨线程快照：</h3>
 * <p>在 TTL 无法自动传播的场景（如手动创建的 Executor），使用 {@link #snapshot()} +
 * {@link #restore(Map)} 显式传递上下文快照。
 *
 * @author remi-team
 * @since 1.0.0
 * @see RequestContextData
 */
public final class RequestContext {

    /**
     * MDC 中各字段的默认键名（与 TenantMdcFilter 保持一致）
     */
    public static final String MDC_TENANT_ID = "tenantId";
    public static final String MDC_USER_ID = "userId";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_REQUEST_ID = "requestId";

    private static final ThreadLocal<RequestContextData> CTL = new TransmittableThreadLocal<>();

    private RequestContext() {
    }

    // -------------------------------------------------------------------------
    // Typed Accessors
    // -------------------------------------------------------------------------

    public static String getUserId() {
        RequestContextData ctx = CTL.get();
        return ctx != null ? ctx.userId() : null;
    }

    public static void setUserId(String userId) {
        CTL.set(currentOrNew().withUserId(userId));
    }

    public static String getTenantId() {
        RequestContextData ctx = CTL.get();
        return ctx != null ? ctx.tenantId() : null;
    }

    public static void setTenantId(String tenantId) {
        CTL.set(currentOrNew().withTenantId(tenantId));
    }

    public static String getTraceId() {
        RequestContextData ctx = CTL.get();
        return ctx != null ? ctx.traceId() : null;
    }

    public static void setTraceId(String traceId) {
        CTL.set(currentOrNew().withTraceId(traceId));
    }

    public static String getRequestId() {
        RequestContextData ctx = CTL.get();
        return ctx != null ? ctx.requestId() : null;
    }

    public static void setRequestId(String requestId) {
        CTL.set(currentOrNew().withRequestId(requestId));
    }

    public static String getLanguage() {
        RequestContextData ctx = CTL.get();
        return ctx != null ? ctx.language() : null;
    }

    public static void setLanguage(String language) {
        CTL.set(currentOrNew().withLanguage(language));
    }

    public static boolean isTenantIsolationSkipped() {
        RequestContextData ctx = CTL.get();
        return ctx != null && ctx.tenantIsolationSkipped();
    }

    public static void setTenantIsolationSkipped(boolean skipped) {
        CTL.set(currentOrNew().withTenantIsolationSkipped(skipped));
    }

    /**
     * 获取请求客户端 IP（非空表示已设置）
     *
     * @return 客户端 IP 地址
     * @since 1.8.0
     */
    public static String getClientIp() {
        RequestContextData ctx = CTL.get();
        return ctx != null ? ctx.clientIp() : null;
    }

    /**
     * 设置请求客户端 IP
     *
     * @param clientIp 客户端 IP 地址
     * @since 1.8.0
     */
    public static void setClientIp(String clientIp) {
        CTL.set(currentOrNew().withClientIp(clientIp));
    }

    /**
     * 获取请求来源标识（INTERNAL / OPEN_API / WEB_HOOK 等）
     *
     * @return 请求来源
     * @since 1.8.0
     */
    public static String getRequestSource() {
        RequestContextData ctx = CTL.get();
        return ctx != null ? ctx.requestSource() : null;
    }

    /**
     * 设置请求来源标识
     *
     * @param requestSource 请求来源
     * @since 1.8.0
     */
    public static void setRequestSource(String requestSource) {
        CTL.set(currentOrNew().withRequestSource(requestSource));
    }

    /**
     * 获取当前 API 版本号（从请求头解析）
     *
     * @return API 版本号，未设置时返回 null
     * @since 1.8.0
     */
    public static String getApiVersion() {
        RequestContextData ctx = CTL.get();
        return ctx != null ? ctx.apiVersion() : null;
    }

    /**
     * 设置当前 API 版本号
     *
     * @param apiVersion API 版本号
     * @since 1.8.0
     */
    public static void setApiVersion(String apiVersion) {
        CTL.set(currentOrNew().withApiVersion(apiVersion));
    }

    // -------------------------------------------------------------------------
    // 快照与恢复（用于 TTL 无法自动传播的场景）
    // -------------------------------------------------------------------------

    /**
     * 获取当前上下文的不可变快照
     *
     * <p>返回快照后，当前线程的上下文不受影响。用于在跨线程边界时手动传递上下文。
     *
     * @return 当前上下文的快照 Map；若当前无线程上下文，返回空 Map
     */
    public static Map<String, String> snapshot() {
        RequestContextData ctx = CTL.get();
        Map<String, String> map = new HashMap<>();
        if (ctx != null) {
            putIfNotNull(map, MDC_TENANT_ID, ctx.tenantId());
            putIfNotNull(map, MDC_USER_ID, ctx.userId());
            putIfNotNull(map, MDC_TRACE_ID, ctx.traceId());
            putIfNotNull(map, MDC_REQUEST_ID, ctx.requestId());
            putIfNotNull(map, "language", ctx.language());
            putIfNotNull(map, "clientIp", ctx.clientIp());
            putIfNotNull(map, "requestSource", ctx.requestSource());
            putIfNotNull(map, "apiVersion", ctx.apiVersion());
        }
        return map;
    }

    /**
     * 从快照 Map 恢复上下文
     *
     * <p>通常在子线程入口处调用，将父线程的 snapshot() 传入恢复。
     *
     * @param snapshot 由 {@link #snapshot()} 获取的快照
     */
    public static void restore(Map<String, String> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        RequestContextData base = currentOrNew();
        RequestContextData data = base
            .withTenantId(snapshot.getOrDefault(MDC_TENANT_ID, base.tenantId()))
            .withUserId(snapshot.getOrDefault(MDC_USER_ID, base.userId()))
            .withTraceId(snapshot.getOrDefault(MDC_TRACE_ID, base.traceId()))
            .withRequestId(snapshot.getOrDefault(MDC_REQUEST_ID, base.requestId()))
            .withLanguage(snapshot.getOrDefault("language", base.language()))
            .withClientIp(snapshot.getOrDefault("clientIp", base.clientIp()))
            .withRequestSource(snapshot.getOrDefault("requestSource", base.requestSource()))
            .withApiVersion(snapshot.getOrDefault("apiVersion", base.apiVersion()));
        CTL.set(data);
    }

    // -------------------------------------------------------------------------
    // MDC 桥接
    // -------------------------------------------------------------------------

    /**
     * 将当前上下文桥接到 SLF4J MDC。
     *
     * <p>通常在请求入口 filter 中调用一次，使得后续日志输出自动包含上下文信息。
     * 桥接后，{@link #clear()} 会同步清理 MDC。
     */
    public static void bridgeToMdc() {
        RequestContextData ctx = CTL.get();
        if (ctx == null) {
            return;
        }
        putMdcIfNotNull(MDC_TENANT_ID, ctx.tenantId());
        putMdcIfNotNull(MDC_USER_ID, ctx.userId());
        putMdcIfNotNull(MDC_TRACE_ID, ctx.traceId());
        putMdcIfNotNull(MDC_REQUEST_ID, ctx.requestId());
    }

    // -------------------------------------------------------------------------
    // 防御性清理
    // -------------------------------------------------------------------------

    /**
     * 清理当前线程上下文（请求结束时必须调用）。
     *
     * <p>同时清理 {@link MDC} 中的相关条目，确保线程复用时不会串扰。
     */
    public static void clear() {
        CTL.remove();
        // 同步清理 MDC
        MDC.remove(MDC_TENANT_ID);
        MDC.remove(MDC_USER_ID);
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_REQUEST_ID);
    }

    /**
     * 在自动清理的上下文中执行任务。
     *
     * <p>推荐用法（替代手写 try-finally）：
     * <pre>{@code
     * RequestContext.runWithCleanup(() -> chain.doFilter(request, response));
     * }</pre>
     *
     * @param task 需要执行的任务
     */
    public static void runWithCleanup(Runnable task) {
        try {
            task.run();
        } finally {
            clear();
        }
    }

    /**
     * 在自动清理的上下文中执行任务并返回结果。
     *
     * <p>推荐用法：
     * <pre>{@code
     * return RequestContext.supplyWithCleanup(() -> processRequest(req));
     * }</pre>
     *
     * @param supplier 需要执行的任务
     * @param <T>      返回值类型
     * @return 任务执行结果
     */
    public static <T> T supplyWithCleanup(Supplier<T> supplier) {
        try {
            return supplier.get();
        } finally {
            clear();
        }
    }

    /**
     * 在自动清理的上下文中执行可能抛出异常的任务。
     *
     * @param callable 需要执行的任务
     * @param <T>      返回值类型
     * @return 任务执行结果
     * @throws Exception 任务执行过程中的异常
     * @since 1.8.0
     */
    public static <T> T callWithCleanup(Callable<T> callable) throws Exception {
        try {
            return callable.call();
        } finally {
            clear();
        }
    }

    // -------------------------------------------------------------------------
    // 内部辅助
    // -------------------------------------------------------------------------

    private static RequestContextData currentOrNew() {
        RequestContextData ctx = CTL.get();
        return ctx != null ? ctx : new RequestContextData();
    }

    private static void putIfNotNull(Map<String, String> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static void putMdcIfNotNull(String key, String value) {
        if (value != null) {
            MDC.put(key, value);
        }
    }
}
