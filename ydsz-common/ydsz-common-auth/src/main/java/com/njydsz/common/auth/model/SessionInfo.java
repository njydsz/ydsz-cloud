package com.njydsz.common.auth.model;

import java.io.Serializable;

/**
 * 会话信息。
 *
 * <p>描述用户的一个活跃会话（令牌），用于会话管理和远程踢出。
 *
 * @author ydsz-team
 * @since 26.09.18
 */
public class SessionInfo implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 会话 ID（jti）。 */
  private final String sessionId;

  /** 用户 ID。 */
  private final String userId;

  /** 客户端 IP。 */
  private final String clientIp;

  /** 用户代理头。 */
  private final String userAgent;

  /** 签发时间（毫秒）。 */
  private final long issuedAtMs;

  /** 过期时间（毫秒）。 */
  private final long expiresAtMs;

  /** 设备类型（web/app/api）。 */
  private final String deviceType;

  /**
   * 构造会话信息。
   *
   * @param sessionId 会话 ID（jti）
   * @param userId 用户 ID
   * @param clientIp 客户端 IP
   * @param userAgent 用户代理
   * @param issuedAtMs 签发时间毫秒
   * @param expiresAtMs 过期时间毫秒
   * @param deviceType 设备类型
   */
  public SessionInfo(
      String sessionId,
      String userId,
      String clientIp,
      String userAgent,
      long issuedAtMs,
      long expiresAtMs,
      String deviceType) {
    this.sessionId = sessionId;
    this.userId = userId;
    this.clientIp = clientIp;
    this.userAgent = userAgent;
    this.issuedAtMs = issuedAtMs;
    this.expiresAtMs = expiresAtMs;
    this.deviceType = deviceType;
  }

  public String getSessionId() {
    return sessionId;
  }

  public String getUserId() {
    return userId;
  }

  public String getClientIp() {
    return clientIp;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public long getIssuedAtMs() {
    return issuedAtMs;
  }

  public long getExpiresAtMs() {
    return expiresAtMs;
  }

  public String getDeviceType() {
    return deviceType;
  }
}
