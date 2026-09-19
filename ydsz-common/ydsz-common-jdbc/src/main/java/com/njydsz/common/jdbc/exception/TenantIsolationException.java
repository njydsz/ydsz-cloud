package com.njydsz.common.jdbc.exception;

import com.njydsz.common.exception.code.SecurityExceptionCode;

/**
 * 租户隔离异常。
 *
 * <p>当多租户隔离拦截器无法获取当前租户 ID 或字段值时抛出，遵循 fail-closed 原则拒绝执行 SQL，
 * 避免因上下文缺失导致跨租户数据泄露。
 *
 * <p>对应异常码 {@link SecurityExceptionCode#ACCESS_DENIED}，HTTP 状态码 403。
 *
 * <p><b>注意：</b>此异常同时被 common-jdbc（ColPermissionInnerInterceptor） 和
 * common-tenant（TenantIsolationInterceptor）使用。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class TenantIsolationException extends JdbcException {

  private static final long serialVersionUID = 1L;

  /**
   * 构造租户隔离异常
   *
   * @param message 异常详细信息
   */
  public TenantIsolationException(String message) {
    super(SecurityExceptionCode.ACCESS_DENIED, message);
  }
}
