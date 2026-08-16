package com.njydsz.common.base.constant;

import org.springframework.core.Ordered;

/**
 * Servlet Filter 执行顺序常量。
 *
 * <p>所有数字必须与 {@code docs/BASE_INTERCEPTOR_ORDER.md} 保持一致。
 * 修改任何数字前请先更新文档。
 *
 * <p>Servlet Filter 使用 {@link Ordered#HIGHEST_PRECEDENCE} 为基准的整数体系，
 * 数值越小优先级越高（最先执行）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class FilterOrder {

    private FilterOrder() {
        throw new UnsupportedOperationException("Constants class");
    }

    /** RequestIdResponseFilter：在所有业务 Filter 之前生成/透传 traceId */
    public static final int REQUEST_ID_RESPONSE_FILTER = Ordered.HIGHEST_PRECEDENCE + 10;

    /** ContentCachingFilter：在鉴权之前包装 request body */
    public static final int CONTENT_CACHING_FILTER = Ordered.HIGHEST_PRECEDENCE + 20;

    /** SecurityHeaderFilter：在响应中追加安全头 */
    public static final int SECURITY_HEADER_FILTER = Ordered.HIGHEST_PRECEDENCE + 30;

    /** TraceIdResponseFilter：traceId 注入到 response header */
    public static final int TRACE_ID_RESPONSE_FILTER = Ordered.HIGHEST_PRECEDENCE + 40;

    /** WebAuthFilter：JWT/Session 鉴权 */
    public static final int AUTH_FILTER = Ordered.HIGHEST_PRECEDENCE + 50;

    /** RequestContextCleanupFilter：请求结束清理 TTL（最低优先级） */
    public static final int REQUEST_CONTEXT_CLEANUP = Ordered.LOWEST_PRECEDENCE;

    /** 幂等性过滤器顺序 */
    public static final int IDEMPOTENT_FILTER = Ordered.HIGHEST_PRECEDENCE + 60;

    /** 限流过滤器顺序 */
    public static final int RATE_LIMIT_FILTER = Ordered.HIGHEST_PRECEDENCE + 70;

    /** 审计日志过滤器顺序 */
    public static final int AUDIT_FILTER = Ordered.HIGHEST_PRECEDENCE + 80;
}
