package com.njydsz.userinfo.domain.event.auth;

import java.time.LocalDateTime;

/**
 * MFA 验证失败事件。
 *
 * <p>当用户多因素认证失败时发出此事件，用于安全告警。
 *
 * @param userId 用户 ID
 * @param username 用户名
 * @param timestamp 事件发生时间
 * @param mfaType MFA 类型（TOTP/SMS/EMAIL）
 * @param reason 失败原因
 * @author ydsz-team
 * @since 1.6.0
 */
public record MfaFailedEvent(
    String userId, String username, LocalDateTime timestamp, String mfaType, String reason) {}
