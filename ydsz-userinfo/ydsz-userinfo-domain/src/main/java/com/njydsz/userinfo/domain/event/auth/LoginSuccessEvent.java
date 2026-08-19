package com.njydsz.userinfo.domain.event.auth;

import java.time.LocalDateTime;

/**
 * 登录成功事件。
 *
 * <p>记录用户认证成功的关键信息，包括登录来源 IP、浏览器 User-Agent 和设备类型。
 *
 * @param userId 用户 ID
 * @param username 用户名
 * @param timestamp 事件发生时间
 * @param sourceIp 登录来源 IP
 * @param userAgent 浏览器 User-Agent
 * @param deviceType 设备类型（PC/MOBILE/TABLET）
 * @author ydsz-team
 * @since 1.6.0
 */
public record LoginSuccessEvent(
    String userId,
    String username,
    LocalDateTime timestamp,
    String sourceIp,
    String userAgent,
    String deviceType) {}
