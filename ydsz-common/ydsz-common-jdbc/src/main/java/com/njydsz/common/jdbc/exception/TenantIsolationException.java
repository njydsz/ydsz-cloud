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
 * <p>推荐使用 {@link #TenantIsolationException(String, Diagnostics)} 构造器携带诊断信息，
 * 格式化为：{@code {message} [diagnostics]}，便于运维从错误日志中快速定位根因。
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

  /**
   * 构造携带诊断信息的租户隔离异常（推荐）。
   *
   * <p>诊断信息包含当前请求的运行时上下文，不影响异常码与 HTTP 状态码， 仅作为附加信息记录到异常消息末尾（{@code [{key}={value}, ...]}）。
   *
   * <param message 主异常描述
   * @param diagnostics 诊断信息，为 {@code null} 时不拼接
   * @since 26.09.19
   */
  public TenantIsolationException(String message, Diagnostics diagnostics) {
    super(SecurityExceptionCode.ACCESS_DENIED, formatMessage(message, diagnostics));
  }

  private static String formatMessage(String message, Diagnostics diagnostics) {
    if (diagnostics == null || diagnostics.isEmpty()) {
      return message;
    }
    return message + " " + diagnostics.format();
  }

  /**
   * 运行时诊断信息构建器。
   *
   * <p>用于收集 fail-closed 触发时的上下文线索，帮助快速定位租户上下文丢失原因。 典型用法：
   *
   * <pre>{@code
   * throw new TenantIsolationException("无法获取租户上下文", Diagnostics.of()
   *     .put("thread", Thread.currentThread().getName())
   *     .put("uri", requestUri)
   *     .put("hasRequestSnapshot", RequestContext.has(BizContextKeys.KEY_HTTP_REQUEST))
   *     .put("currentTenantId", TenantContextHolder.getTenantId()));
   * }</pre>
   *
   * @author ydsz-team
   * @since 26.09.19
   */
  public static final class Diagnostics {

    private final java.util.LinkedHashMap<String, String> data = new java.util.LinkedHashMap<>(8);

    private Diagnostics() {}

    /**
     * 创建空的诊断信息构建器。
     *
     * @return 新的 {@code Diagnostics} 实例
     */
    public static Diagnostics of() {
      return new Diagnostics();
    }

    /**
     * 添加一条诊断键值。
     *
     * @param key 诊断项名
     * @param value 诊断项值（可为 null，显示为 "null"）
     * @return this（链式调用）
     */
    public Diagnostics put(String key, Object value) {
      data.put(key, value != null ? value.toString() : "null");
      return this;
    }

    /**
     * 添加一条诊断键值（int 形式）。
     *
     * @param key 诊断项名
     * @param value 诊断项值
     * @return this（链式调用）
     */
    public Diagnostics put(String key, int value) {
      data.put(key, Integer.toString(value));
      return this;
    }

    /**
     * 添加一条诊断键值（boolean 形式）。
     *
     * @param key 诊断项名
     * @param value 诊断项值
     * @return this（链式调用）
     */
    public Diagnostics put(String key, boolean value) {
      data.put(key, Boolean.toString(value));
      return this;
    }

    boolean isEmpty() {
      return data.isEmpty();
    }

    String format() {
      StringBuilder sb = new StringBuilder("[");
      boolean first = true;
      for (java.util.Map.Entry<String, String> entry : data.entrySet()) {
        if (!first) {
          sb.append(", ");
        }
        sb.append(entry.getKey()).append("=").append(entry.getValue());
        first = false;
      }
      sb.append("]");
      return sb.toString();
    }
  }
}
