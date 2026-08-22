package com.njydsz.userinfo.domain.event.auth;

import java.time.LocalDateTime;

/**
 * 账号解封事件。
 *
 * <p>当用户账号封禁期满或被管理员手动解封时发出此事件。
 *
 * @param userId 用户 ID
 * @param username 用户名
 * @param timestamp 事件发生时间
 * @param unbannedBy 解封操作者（"AUTO" 表示封禁期满自动解封）
 * @author ydsz-team
 * @since 1.0.0
 */
public record AccountUnbannedEvent(
    String userId, String username, LocalDateTime timestamp, String unbannedBy) {}
