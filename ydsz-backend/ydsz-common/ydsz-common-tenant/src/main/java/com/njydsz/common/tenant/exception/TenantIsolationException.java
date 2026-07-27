package com.njydsz.common.tenant.exception;

/**
 * 租户隔离异常。
 *
 * <p>当 SQL 拦截器无法获取租户上下文时抛出此异常（fail-closed 原则）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class TenantIsolationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TenantIsolationException(String message) {
        super(message);
    }

    public TenantIsolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
