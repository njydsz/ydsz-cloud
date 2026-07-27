package com.njydsz.common.jdbc.exception;

import com.njydsz.common.exception.code.UnifiedExceptionCode;
import com.njydsz.common.exception.custom.YdszSecurityException;

/**
 * 租户隔离异常。
 *
 * <p>当多租户隔离拦截器无法获取当前租户 ID 时抛出，遵循 fail-closed 原则
 * 拒绝执行 SQL，避免因上下文缺失导致跨租户数据泄露。
 *
 * <p>对应异常码 {@link UnifiedExceptionCode#ACCESS_DENIED}，HTTP 状态码 403。
 *
 * <p><b>注意：</b>此异常同时被 common-jdbc（ColPermissionInnerInterceptor）
 * 和 common-tenant（TenantIsolationInterceptor）使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class TenantIsolationException extends YdszSecurityException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造租户隔离异常
     *
     * @param message 异常详细信息
     */
    public TenantIsolationException(String message) {
        super(UnifiedExceptionCode.ACCESS_DENIED);
        // 保留原始 message 便于日志排查
        this.message = message;
        this.messageKey = UnifiedExceptionCode.ACCESS_DENIED.getKey();
        this.messageParams = new Object[0];
    }
}
