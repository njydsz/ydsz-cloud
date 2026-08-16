package com.njydsz.common.base.constant;

import org.springframework.core.Ordered;

/**
 * Base 模块横切点执行顺序常量
 *
 * <p>所有数字必须与 {@code docs/BASE_INTERCEPTOR_ORDER.md} 保持一致。
 * 修改任何数字前请先更新文档。</p>
 *
 * <p><b>已拆分：</b>此类仅作向后兼容保留，新代码请使用：
 * <ul>
 *   <li>{@link FilterOrder} - Servlet Filter 顺序</li>
 *   <li>{@link InterceptorOrder} - Spring MVC Interceptor 顺序</li>
 *   <li>{@link AdviceOrder} - ControllerAdvice 顺序</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 使用 {@link FilterOrder}、{@link InterceptorOrder}、{@link AdviceOrder} 替代
 */
@Deprecated
public final class BaseFilterOrders {

    private BaseFilterOrders() {
        throw new UnsupportedOperationException("Constants class");
    }

    // ==================== Filter 顺序（委托至 FilterOrder） ====================

    /** RequestIdResponseFilter：在所有业务 Filter 之前生成/透传 traceId */
    public static final int REQUEST_ID_RESPONSE_FILTER = FilterOrder.REQUEST_ID_RESPONSE_FILTER;

    /** ContentCachingFilter：在鉴权之前包装 request body */
    public static final int CONTENT_CACHING_FILTER = FilterOrder.CONTENT_CACHING_FILTER;

    /** SecurityHeaderFilter：在响应中追加安全头 */
    public static final int SECURITY_HEADER_FILTER = FilterOrder.SECURITY_HEADER_FILTER;

    /** TraceIdResponseFilter：traceId 注入到 response header */
    public static final int TRACE_ID_RESPONSE_FILTER = FilterOrder.TRACE_ID_RESPONSE_FILTER;

    /** WebAuthFilter：JWT/Session 鉴权 */
    public static final int AUTH_FILTER = FilterOrder.AUTH_FILTER;

    /** RequestContextCleanupFilter：请求结束清理 TTL（最低优先级） */
    public static final int REQUEST_CONTEXT_CLEANUP = FilterOrder.REQUEST_CONTEXT_CLEANUP;

    // ==================== Interceptor 顺序（委托至 InterceptorOrder） ====================

    /** Interceptor：HttpInterceptor - 跨域/字符编码基础设置 */
    public static final int INTERCEPTOR_HTTP = InterceptorOrder.HTTP;

    /** Interceptor：RequestLogInterceptor - 请求/响应日志 */
    public static final int INTERCEPTOR_REQUEST_LOG = InterceptorOrder.REQUEST_LOG;

    /** Interceptor：AuthApiPermissionInterceptor - API 权限 */
    public static final int INTERCEPTOR_AUTH_API = InterceptorOrder.AUTH_API;

    /** Interceptor：AuthMenuPermissionInterceptor - 菜单权限 */
    public static final int INTERCEPTOR_AUTH_MENU = InterceptorOrder.AUTH_MENU;

    /** Interceptor：AuthRowPermissionInterceptor - 行级权限 */
    public static final int INTERCEPTOR_AUTH_ROW = InterceptorOrder.AUTH_ROW;

    /** Interceptor：AuthColPermissionInterceptor - 列级权限 */
    public static final int INTERCEPTOR_AUTH_COL = InterceptorOrder.AUTH_COL;

    // ==================== Advice 顺序（委托至 AdviceOrder） ====================

    /** Advice：GlobalResponseAdvice - 统一响应包装（最先） */
    public static final int ADVICE_GLOBAL_RESPONSE = AdviceOrder.GLOBAL_RESPONSE;

    /** Advice：BaseExceptionHandler - 业务异常 */
    public static final int ADVICE_BASE_EXCEPTION = AdviceOrder.BASE_EXCEPTION;

    /** Advice：MvcExceptionHandler - MVC 框架异常 */
    public static final int ADVICE_MVC_EXCEPTION = AdviceOrder.MVC_EXCEPTION;

    /** Advice：ValidationExceptionHandler - 参数校验异常 */
    public static final int ADVICE_VALIDATION_EXCEPTION = AdviceOrder.VALIDATION_EXCEPTION;
}
