package com.njydsz.userinfo.server.security;

import org.springframework.context.ApplicationEvent;

import lombok.Builder;
import lombok.Getter;

/**
 * 登录审计事件（userinfo 模块本地版本）
 *
 * <p>由 {@code UserAccountServiceImpl.publishAudit} 在登录成功 / 失败 / 2FA 失败时发布，
 * 异步持久化至 {@code ydsz_login_audit} 表，供安全审计与风控使用。
 *
 * <p>原参考实现位于 ydsz-common-security 包，因 common 重构后该事件已迁移到各业务模块本地化。
 *
 * @since 1.0.0
 */
@Getter
public class LoginAuditEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /** 用户名 */
    private final String username;
    /** 用户 ID（登录失败时可能为 null） */
    private final String userId;
    /** 登录 IP */
    private final String loginIp;
    /** User-Agent */
    private final String userAgent;
    /** 登录状态 */
    private final LoginStatus status;
    /** 失败原因（成功时为 null） */
    private final String failReason;
    /** 是否启用了 2FA */
    private final boolean mfaUsed;
    /** 2FA 是否通过（未启用时为 null） */
    private final Boolean mfaSuccess;
    /** 链路追踪 ID */
    private final String traceId;
    /** 租户 ID */
    private final String tenantId;
    /** 事件发生时间（毫秒） */
    private final Long loginAt;

    @Builder
    public LoginAuditEvent(Object source, String username, String userId, String loginIp,
                           String userAgent, LoginStatus status, String failReason,
                           boolean mfaUsed, Boolean mfaSuccess, String traceId,
                           String tenantId, Long loginAt) {
        super(source);
        this.username = username;
        this.userId = userId;
        this.loginIp = loginIp;
        this.userAgent = userAgent;
        this.status = status;
        this.failReason = failReason;
        this.mfaUsed = mfaUsed;
        this.mfaSuccess = mfaSuccess;
        this.traceId = traceId;
        this.tenantId = tenantId;
        this.loginAt = loginAt;
    }

    public static LoginAuditEventBuilder builder() {
        return new LoginAuditEventBuilder();
    }

    public static class LoginAuditEventBuilder {
        private Object source;
        private String username;
        private String userId;
        private String loginIp;
        private String userAgent;
        private LoginStatus status;
        private String failReason;
        private boolean mfaUsed;
        private Boolean mfaSuccess;
        private String traceId;
        private String tenantId;
        private Long loginAt;

        public LoginAuditEventBuilder source(Object source) { this.source = source; return this; }
        public LoginAuditEventBuilder username(String username) { this.username = username; return this; }
        public LoginAuditEventBuilder userId(String userId) { this.userId = userId; return this; }
        public LoginAuditEventBuilder loginIp(String loginIp) { this.loginIp = loginIp; return this; }
        public LoginAuditEventBuilder userAgent(String userAgent) { this.userAgent = userAgent; return this; }
        public LoginAuditEventBuilder status(LoginStatus status) { this.status = status; return this; }
        public LoginAuditEventBuilder failReason(String failReason) { this.failReason = failReason; return this; }
        public LoginAuditEventBuilder mfaUsed(boolean mfaUsed) { this.mfaUsed = mfaUsed; return this; }
        public LoginAuditEventBuilder mfaSuccess(Boolean mfaSuccess) { this.mfaSuccess = mfaSuccess; return this; }
        public LoginAuditEventBuilder traceId(String traceId) { this.traceId = traceId; return this; }
        public LoginAuditEventBuilder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public LoginAuditEventBuilder loginAt(Long loginAt) { this.loginAt = loginAt; return this; }

        public LoginAuditEvent build() {
            return new LoginAuditEvent(source == null ? new Object() : source,
                    username, userId, loginIp, userAgent, status, failReason,
                    mfaUsed, mfaSuccess, traceId, tenantId, loginAt);
        }
    }
}
