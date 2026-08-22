package com.njydsz.userinfo.domain.event.auth;

import java.time.LocalDateTime;

/**
 * MFA 验证成功事件。
 *
 * <p>当用户成功完成多因素认证时发出此事件。
 *
 * @param userId 用户 ID
 * @param username 用户名
 * @param timestamp 事件发生时间
 * @param mfaType MFA 类型（TOTP/SMS/EMAIL）
 * @author ydsz-team
 * @since 1.0.0
 */
public record MfaVerifiedEvent(
    String userId, String username, LocalDateTime timestamp, String mfaType) {}
