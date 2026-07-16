package com.njydsz.userinfo.server.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ApplicationEvent;

import lombok.Builder;
import lombok.Getter;

/**
 * 账号锁定事件（userinfo 模块本地版本）
 *
 * <p>由 {@code AuthServiceImpl.recordLoginFailure} 在登录失败次数达到阈值时发布，
 * {@code org.springframework.context.ApplicationEventPublisher} 异步分发至监听器，
 * 触发邮件 / 短信 / 站内信通知。
 *
 * <p>原参考实现位于 ydsz-common-security 包，因 common 重构后该事件已迁移到各业务模块本地化。
 *
 * @since 1.0.0
 */
@Getter
public class AccountLockedEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private final String userId;
    /** 用户名（用于通知正文） */
    private final String username;
    /** 锁定到期时间 */
    private final java.time.LocalDateTime lockedUntil;
    /** 累计失败次数 */
    private final Integer failCount;
    /** 锁定时长（分钟） */
    private final Integer lockMinutes;
    /** 链路追踪 ID */
    private final String traceId;
    /** 租户 ID */
    private final String tenantId;
    /** 事件发生时间（毫秒） */
    private final Long lockedAt;

    @Builder
    public AccountLockedEvent(Object source, String userId, String username,
                              java.time.LocalDateTime lockedUntil, Integer failCount,
                              Integer lockMinutes, String traceId, String tenantId, Long lockedAt) {
        super(source);
        this.userId = userId;
        this.username = username;
        this.lockedUntil = lockedUntil;
        this.failCount = failCount;
        this.lockMinutes = lockMinutes;
        this.traceId = traceId;
        this.tenantId = tenantId;
        this.lockedAt = lockedAt;
    }

    /**
     * 兼容无 source 的 Builder 入口（用于 {@code AccountLockedEvent.builder().xxx.build()} 旧调用）
     */
    public static AccountLockedEventBuilder builder() {
        return new AccountLockedEventBuilder();
    }

    public static class AccountLockedEventBuilder {
        private Object source;
        private String userId;
        private String username;
        private java.time.LocalDateTime lockedUntil;
        private Integer failCount;
        private Integer lockMinutes;
        private String traceId;
        private String tenantId;
        private Long lockedAt;

        public AccountLockedEventBuilder source(Object source) {
            this.source = source;
            return this;
        }
        public AccountLockedEventBuilder userId(String userId) { this.userId = userId; return this; }
        public AccountLockedEventBuilder username(String username) { this.username = username; return this; }
        public AccountLockedEventBuilder lockedUntil(java.time.LocalDateTime lockedUntil) { this.lockedUntil = lockedUntil; return this; }
        public AccountLockedEventBuilder failCount(Integer failCount) { this.failCount = failCount; return this; }
        public AccountLockedEventBuilder lockMinutes(Integer lockMinutes) { this.lockMinutes = lockMinutes; return this; }
        public AccountLockedEventBuilder traceId(String traceId) { this.traceId = traceId; return this; }
        public AccountLockedEventBuilder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public AccountLockedEventBuilder lockedAt(Long lockedAt) { this.lockedAt = lockedAt; return this; }

        public AccountLockedEvent build() {
            return new AccountLockedEvent(source == null ? new Object() : source,
                    userId, username, lockedUntil, failCount, lockMinutes, traceId, tenantId, lockedAt);
        }
    }
}
