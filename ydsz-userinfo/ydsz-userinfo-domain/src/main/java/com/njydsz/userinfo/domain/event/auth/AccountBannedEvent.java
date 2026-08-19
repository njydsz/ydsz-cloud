package com.njydsz.userinfo.domain.event.auth;

import java.time.LocalDateTime;

/**
 * 账号封禁事件。
 *
 * <p>当用户账号因违规行为被运营侧封禁时发出此事件。
 *
 * @param userId 用户 ID
 * @param username 用户名
 * @param timestamp 事件发生时间
 * @param banType 封禁类型（TEMPORARY/PERMANENT）
 * @param reason 封禁原因
 * @param bannedBy 封禁操作者
 * @author ydsz-team
 * @since 1.6.0
 */
public record AccountBannedEvent(
    String userId,
    String username,
    LocalDateTime timestamp,
    String banType,
    String reason,
    String bannedBy) {}
