package com.njydsz.userinfo.domain.event.auth;

import java.time.LocalDateTime;

/**
 * 账号锁定事件。
 *
 * <p>当用户账号因登录失败次数过多或被管理员手动锁定时发出此事件。
 *
 * @param userId 用户 ID
 * @param username 用户名
 * @param timestamp 事件发生时间
 * @param lockDuration 锁定持续时长（分钟），-1 表示永久锁定
 * @param reason 锁定原因
 * @author ydsz-team
 * @since 1.6.0
 */
public record AccountLockedEvent(
    String userId, String username, LocalDateTime timestamp, long lockDuration, String reason) {}
