package com.njydsz.common.safe.csrf;

import java.time.Instant;

/**
 * CSRF 令牌模型
 *
 * <p>存储 CSRF 令牌的相关信息。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class CsrfToken {

  private final String token;
  private final String sessionId;
  private final Instant createdAt;
  private final Instant expiresAt;

  public CsrfToken(String token, String sessionId, long expirationSeconds) {
    this.token = token;
    this.sessionId = sessionId;
    this.createdAt = Instant.now();
    this.expiresAt = createdAt.plusSeconds(expirationSeconds);
  }

  public String getToken() {
    return token;
  }

  public String getSessionId() {
    return sessionId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  /**
   * 判断令牌是否已过期。
   *
   * <p>以当前系统时间为基准与过期时间比较；令牌过期后应立即判为无效并阻止请求放行。
   *
   * @return {@code true} 表示已过期，否则为 {@code false}
   */
  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }

  @Override
  public String toString() {
    return "CsrfToken{"
        + "token='****"
        + (token != null && token.length() >= 4 ? token.substring(token.length() - 4) : "")
        + '\''
        + ", sessionId='****"
        + (sessionId != null && sessionId.length() >= 4
            ? sessionId.substring(sessionId.length() - 4)
            : "")
        + '\''
        + ", createdAt="
        + createdAt
        + ", expiresAt="
        + expiresAt
        + '}';
  }
}
