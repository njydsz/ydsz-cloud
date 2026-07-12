package com.njydsz.pmis.common.safe.csrf;

import java.time.Instant;

/**
 * CSRF 令牌模型
 *
 * <p>存储 CSRF 令牌的相关信息。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
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

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    @Override
    public String toString() {
        return "CsrfToken{" +
                "token='****" + (token != null && token.length() >= 4 ? token.substring(token.length() - 4) : "") + '\'' +
                ", sessionId='****" + (sessionId != null && sessionId.length() >= 4 ? sessionId.substring(sessionId.length() - 4) : "") + '\'' +
                ", createdAt=" + createdAt +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
