package com.njydsz.userinfo.domain.event.auth;

import java.time.LocalDateTime;

/**
 * 账号解锁事件。
 *
 * <p>当用户账号由管理员手动解锁或自动到期解锁时发出此事件。
 *
 * @param userId 用户 ID
 * @param username 用户名
 * @param timestamp 事件发生时间
 * @param unlockedBy 解锁操作者（"AUTO" 表示自动解锁）
 * @author ydsz-team
 * @since 26.09.01
 */
public record AccountUnlockedEvent(
    String userId, String username, LocalDateTime timestamp, String unlockedBy) {}
