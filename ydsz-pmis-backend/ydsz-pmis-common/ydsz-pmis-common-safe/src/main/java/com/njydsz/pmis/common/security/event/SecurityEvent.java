package com.njydsz.pmis.common.security.event;

import java.time.Instant;
import java.util.Map;

/**
 * 安全事件 —— 登录、权限变更、敏感操作等安全相关事件的统一载体。
 * <p>
 * 对标 remi-comm SecurityEvent，用于安全审计和威胁检测。
 * </p>
 *
 * @author njydsz
 * @since 1.0.0
 */
public class SecurityEvent {

    public enum Type {
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        LOGOUT,
        PERMISSION_DENIED,
        RATE_LIMIT_EXCEEDED,
        TOKEN_REVOKED,
        SUSPICIOUS_ACTIVITY,
        PASSWORD_CHANGED,
        SENSITIVE_DATA_ACCESS
    }

    private final Type type;
    private final Instant timestamp;
    private final String userId;
    private final String clientIp;
    private final String userAgent;
    private final Map<String, Object> details;

    public SecurityEvent(Type type, String userId, String clientIp, String userAgent, Map<String, Object> details) {
        this.type = type;
        this.timestamp = Instant.now();
        this.userId = userId;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.details = details;
    }

    public Type getType() {
        return type;
    }

    public Instant getTimestamp() {
        return timestamp;
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

    public Map<String, Object> getDetails() {
        return details;
    }

    @Override
    public String toString() {
        return "SecurityEvent{" +
                "type=" + type +
                ", timestamp=" + timestamp +
                ", userId='" + userId + '\'' +
                ", clientIp='" + clientIp + '\'' +
                '}';
    }
}
