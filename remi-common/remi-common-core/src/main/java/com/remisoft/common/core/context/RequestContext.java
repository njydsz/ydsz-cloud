package com.remisoft.common.core.context;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 请求上下文（基于 TransmittableThreadLocal）。
 *
 * <p>凡是涉及请求级用户信息的读取/写入，都应通过本类，
 * 确保线程池场景下由 TTL 自动传播。
 */
public final class RequestContext {

    private static final ThreadLocal<Context> CTL = new TransmittableThreadLocal<>();

    private RequestContext() {
    }

    public static String getUserId() {
        return ctl().userId;
    }

    public static void setUserId(String userId) {
        ctl().userId = userId;
    }

    public static String getTenantId() {
        return ctl().tenantId;
    }

    public static void setTenantId(String tenantId) {
        ctl().tenantId = tenantId;
    }

    public static String getTraceId() {
        return ctl().traceId;
    }

    public static void setTraceId(String traceId) {
        ctl().traceId = traceId;
    }

    public static String getRequestId() {
        return ctl().requestId;
    }

    public static void setRequestId(String requestId) {
        ctl().requestId = requestId;
    }

    public static String getLanguage() {
        return ctl().language;
    }

    public static void setLanguage(String language) {
        ctl().language = language;
    }

    public static boolean isTenantIsolationSkipped() {
        return ctl().tenantIsolationSkipped;
    }

    public static void setTenantIsolationSkipped(boolean skipped) {
        ctl().tenantIsolationSkipped = skipped;
    }

    /**
     * 清理当前线程上下文（请求结束时必须调用）。
     */
    public static void clear() {
        CTL.remove();
    }

    private static Context ctl() {
        Context ctx = CTL.get();
        if (ctx == null) {
            ctx = new Context();
            CTL.set(ctx);
        }
        return ctx;
    }

    private static class Context {
        String userId;
        String tenantId;
        String traceId;
        String requestId;
        String language;
        boolean tenantIsolationSkipped;
    }
}
