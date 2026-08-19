package com.njydsz.userinfo.domain.event.auth;

import java.time.LocalDateTime;

/**
 * 会话驱逐事件。
 *
 * <p>当用户会话被管理员强制下线或被新登录挤出时发出此事件。
 *
 * @param userId 用户 ID
 * @param username 用户名
 * @param timestamp 事件发生时间
 * @param evictedBy 驱逐操作者（"SYSTEM" 表示系统自动挤出）
 * @param reason 驱逐原因
 * @author ydsz-team
 * @since 1.6.0
 */
public record SessionEvictedEvent(
    String userId, String username, LocalDateTime timestamp, String evictedBy, String reason) {}
