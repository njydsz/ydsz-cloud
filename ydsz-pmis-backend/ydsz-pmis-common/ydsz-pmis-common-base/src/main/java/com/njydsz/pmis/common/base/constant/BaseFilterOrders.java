package com.njydsz.pmis.common.base.constant;

/**
 * Base 模块横切点执行顺序常量
 *
 * <p>所有数字必须与 {@code docs/BASE_INTERCEPTOR_ORDER.md} 保持一致。
 * 修改任何数字前请先更新文档。</p>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.5.0
 */
public final class BaseFilterOrders {

    private BaseFilterOrders() {
        throw new UnsupportedOperationException("Constants class");
    }

    /** RequestIdResponseFilter：在所有业务 Filter 之前生成/透传 traceId */
    public static final int REQUEST_ID_RESPONSE_FILTER = OrderedConstants.HIGHEST_PRECEDENCE + 10;

    /** ContentCachingFilter：在鉴权之前包装 request body */
    public static final int CONTENT_CACHING_FILTER = OrderedConstants.HIGHEST_PRECEDENCE + 20;

    /** SecurityHeaderFilter：在响应中追加安全头 */
    public static final int SECURITY_HEADER_FILTER = OrderedConstants.HIGHEST_PRECEDENCE + 30;

    /** TraceIdResponseFilter：traceId 注入到 response header */
    public static final int TRACE_ID_RESPONSE_FILTER = OrderedConstants.HIGHEST_PRECEDENCE + 40;

    /** WebAuthFilter：JWT/Session 鉴权 */
    public static final int AUTH_FILTER = OrderedConstants.HIGHEST_PRECEDENCE + 50;

    /** RequestContextCleanupFilter：请求结束清理 TTL（最低优先级） */
    public static final int REQUEST_CONTEXT_CLEANUP = OrderedConstants.LOWEST_PRECEDENCE;

    /** Interceptor：HttpInterceptor - 跨域/字符编码基础设置 */
    public static final int INTERCEPTOR_HTTP = 0;

    /** Interceptor：RequestLogInterceptor - 请求/响应日志 */
    public static final int INTERCEPTOR_REQUEST_LOG = 10;

    /** Interceptor：AuthApiPermissionInterceptor - API 权限 */
    public static final int INTERCEPTOR_AUTH_API = 20;

    /** Interceptor：AuthMenuPermissionInterceptor - 菜单权限 */
    public static final int INTERCEPTOR_AUTH_MENU = 30;

    /** Interceptor：AuthRowPermissionInterceptor - 行级权限 */
    public static final int INTERCEPTOR_AUTH_ROW = 40;

    /** Interceptor：AuthColPermissionInterceptor - 列级权限 */
    public static final int INTERCEPTOR_AUTH_COL = 50;

    /** Advice：GlobalResponseAdvice - 统一响应包装（最先） */
    public static final int ADVICE_GLOBAL_RESPONSE = 0;

    /** Advice：BaseExceptionHandler - 业务异常 */
    public static final int ADVICE_BASE_EXCEPTION = 10;

    /** Advice：MvcExceptionHandler - MVC 框架异常 */
    public static final int ADVICE_MVC_EXCEPTION = 20;

    /** Advice：ValidationExceptionHandler - 参数校验异常 */
    public static final int ADVICE_VALIDATION_EXCEPTION = 30;

    /**
     * Spring Ordered 常量，避免外部依赖
     */
    public static final class OrderedConstants {
        public static final int HIGHEST_PRECEDENCE = Integer.MIN_VALUE;
        public static final int LOWEST_PRECEDENCE = Integer.MAX_VALUE;
        private OrderedConstants() {}
    }
}
