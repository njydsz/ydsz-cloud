package com.njydsz.userinfo.domain.event.auth;

import java.time.LocalDateTime;

/**
 * 登录失败事件。
 *
 * <p>记录用户认证失败的详情，包含失败原因和累计失败次数，用于安全审计和账号锁定判断。
 *
 * @param userId 用户 ID
 * @param username 用户名
 * @param timestamp 事件发生时间
 * @param sourceIp 登录来源 IP
 * @param reason 失败原因
 * @param failCount 累计失败次数
 * @author ydsz-team
 * @since 26.09.01
 */
public record LoginFailedEvent(
    String userId,
    String username,
    LocalDateTime timestamp,
    String sourceIp,
    String reason,
    int failCount) {}
