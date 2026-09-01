package com.njydsz.userinfo.domain.event.auth;

import java.time.LocalDateTime;

/**
 * 注销事件。
 *
 * <p>记录用户主动注销或会话超时退出的信息。
 *
 * @param userId 用户 ID
 * @param username 用户名
 * @param timestamp 事件发生时间
 * @param sourceIp 注销来源 IP
 * @param sessionDuration 会话持续时长（毫秒）
 * @author ydsz-team
 * @since 26.09.01
 */
public record LogoutEvent(
    String userId,
    String username,
    LocalDateTime timestamp,
    String sourceIp,
    long sessionDuration) {}
