package com.njydsz.userinfo.domain.event.auth;

import java.time.LocalDateTime;

/**
 * 密码修改事件。
 *
 * <p>当用户修改密码或由管理员重置密码时发出此事件。
 *
 * @param userId 用户 ID
 * @param username 用户名
 * @param timestamp 事件发生时间
 * @param changedBy 操作者（"SELF" 表示用户自行修改，其他为操作者用户名）
 * @author ydsz-team
 * @since 26.09.01
 */
public record PasswordChangedEvent(
    String userId, String username, LocalDateTime timestamp, String changedBy) {}
