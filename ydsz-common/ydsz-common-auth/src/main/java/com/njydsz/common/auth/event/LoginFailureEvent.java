package com.njydsz.common.auth.event;

/**
 * 登录失败事件。
 *
 * <p>由认证过滤器/处理器在认证失败时发布。订阅方可用于：暴力破解检测、失败次数统计、安全告警（异常 IP / 频繁失败）、验证码策略触发等。
 *
 * @author ydsz-team
 * @since 26.09.18
 */
public class LoginFailureEvent extends AuthenticationEvent {

  private static final long serialVersionUID = 1L;

  /** 尝试登录的用户名（可能不存在）。 */
  private final String username;

  /** 失败原因码（如 INVALID_PASSWORD / USER_DISABLED / CAPTCHA_ERROR）。 */
  private final String reason;

  /** 客户端 IP 地址。 */
  private final String clientIp;

  /** User-Agent 头。 */
  private final String userAgent;

  /** 认证耗时（纳秒）。 */
  private final long durationNanos;

  /**
   * 构造登录失败事件。
   *
   * @param source 事件源
   * @param username 尝试登录的用户名
   * @param reason 失败原因
   * @param clientIp 客户端 IP
   * @param userAgent UA 头
   * @param durationNanos 认证耗时（纳秒）
   */
  public LoginFailureEvent(
      String source,
      String username,
      String reason,
      String clientIp,
      String userAgent,
      long durationNanos) {
    super(source);
    this.username = username;
    this.reason = reason;
    this.clientIp = clientIp;
    this.userAgent = userAgent;
    this.durationNanos = durationNanos;
  }

  public String getUsername() {
    return username;
  }

  public String getReason() {
    return reason;
  }

  public String getClientIp() {
    return clientIp;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public long getDurationNanos() {
    return durationNanos;
  }
}
