package com.njydsz.pmis.system.server.security;

import org.springframework.context.ApplicationEvent;

import lombok.Builder;
import lombok.Getter;

/**
 * 登录审计事件（system 模块本地版本）
 *
 * <p>由登录链路在登录成功 / 失败 / 2FA 失败时发布，由 {@code LoginAuditListener}
 * 异步持久化至 {@code pmis_login_audit} 表，供安全审计与风控使用。
 *
 * <p>原参考实现位于 common-security 包，因 common 重构后已迁移到各业务模块本地化。
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
    private final Boolean mfaUsed;
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
                           Boolean mfaUsed, Boolean mfaSuccess, String traceId,
                           String tenantId, Long loginAt) {
        super(source == null ? new Object() : source);
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
}
