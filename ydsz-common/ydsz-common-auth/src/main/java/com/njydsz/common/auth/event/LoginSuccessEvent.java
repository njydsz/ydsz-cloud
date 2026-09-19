package com.njydsz.common.auth.event;

/**
 * 登录成功事件。
 *
 * <p>由认证过滤器/处理器在用户成功认证后发布，携带登录成功的关键上下文信息。 订阅方可用于：审计日志记录、登录通知、异地登录告警、用户活跃度统计等。
 *
 * @author ydsz-team
 * @since 26.09.18
 */
public class LoginSuccessEvent extends AuthenticationEvent {

  private static final long serialVersionUID = 1L;

  /** 用户 ID。 */
  private final String userId;

  /** 用户名。 */
  private final String username;

  /** 用户类型（如 admin/user/app）。 */
  private final String userType;

  /** 客户端 IP 地址。 */
  private final String clientIp;

  /** User-Agent 头（客户端标识）。 */
  private final String userAgent;

  /** 终端设备类型（web/app/api 等）。 */
  private final String deviceType;

  /** 认证耗时（纳秒）。 */
  private final long durationNanos;

  /**
   * 构造登录成功事件。
   *
   * @param source 事件源
   * @param userId 用户 ID
   * @param username 用户名
   * @param userType 用户类型
   * @param clientIp 客户端 IP
   * @param userAgent UA 头
   * @param deviceType 终端设备类型
   * @param durationNanos 认证耗时（纳秒）
   */
  public LoginSuccessEvent(
      String source,
      String userId,
      String username,
      String userType,
      String clientIp,
      String userAgent,
      String deviceType,
      long durationNanos) {
    super(source);
    this.userId = userId;
    this.username = username;
    this.userType = userType;
    this.clientIp = clientIp;
    this.userAgent = userAgent;
    this.deviceType = deviceType;
    this.durationNanos = durationNanos;
  }

  public String getUserId() {
    return userId;
  }

  public String getUsername() {
    return username;
  }

  public String getUserType() {
    return userType;
  }

  public String getClientIp() {
    return clientIp;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public String getDeviceType() {
    return deviceType;
  }

  public long getDurationNanos() {
    return durationNanos;
  }
}
