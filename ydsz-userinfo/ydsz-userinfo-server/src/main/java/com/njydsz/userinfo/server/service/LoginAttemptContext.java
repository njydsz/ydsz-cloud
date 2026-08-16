package com.njydsz.userinfo.server.service;

/**
 * 登录尝试上下文（参数对象）。
 *
 * <p>封装登录尝试的核心身份信息，作为 {@link LoginHistoryService#recordLoginAttempt} 参数对象版本的入参，避免方法签名出现过多 String 参数。
 *
 * @param userId   用户 ID（可能为 null，如用户名不存在时）
 * @param username 用户名
 * @param loginIp  登录 IP
 * @author ydsz-team
 * @since 1.0.0
 */
public record LoginAttemptContext(String userId, String username, String loginIp) {}
