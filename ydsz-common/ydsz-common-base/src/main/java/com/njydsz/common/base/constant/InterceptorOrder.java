package com.njydsz.common.base.constant;

import org.springframework.core.Ordered;

/**
 * Spring MVC Interceptor 执行顺序常量。
 *
 * <p>所有数字必须与 {@code docs/BASE_INTERCEPTOR_ORDER.md} 保持一致。
 * 修改任何数字前请先更新文档。
 *
 * <p>Spring MVC Interceptor 使用自然数体系（0, 10, 20...），
 * 数值越小优先级越高（最先执行）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class InterceptorOrder {

    private InterceptorOrder() {
        throw new UnsupportedOperationException("Constants class");
    }

    /** HttpInterceptor - 跨域/字符编码基础设置 */
    public static final int HTTP = 0;

    /** RequestLogInterceptor - 请求/响应日志 */
    public static final int REQUEST_LOG = 10;

    /** AuthApiPermissionInterceptor - API 权限 */
    public static final int AUTH_API = 20;

    /** AuthMenuPermissionInterceptor - 菜单权限 */
    public static final int AUTH_MENU = 30;

    /** AuthRowPermissionInterceptor - 行级权限 */
    public static final int AUTH_ROW = 40;

    /** AuthColPermissionInterceptor - 列级权限 */
    public static final int AUTH_COL = 50;

    /** 幂等性拦截器顺序 */
    public static final int IDEMPOTENT = 60;

    /** 限流拦截器顺序 */
    public static final int RATE_LIMIT = 70;

    /** 审计日志拦截器顺序 */
    public static final int AUDIT = 80;

    /** RequestContextCleanupInterceptor - 请求结束清理 TTL（最低优先级） */
    public static final int REQUEST_CONTEXT_CLEANUP = Ordered.LOWEST_PRECEDENCE;
}
