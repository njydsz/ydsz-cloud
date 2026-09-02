package com.njydsz.userinfo.domain.vo;

import java.time.LocalDateTime;

/**
 * 活跃用户排行 VO。
 *
 * <p>基于最近登录次数的用户排名数据。
 *
 * <p>使用 {@link com.njydsz.common.json.YdszJson} 进行 JSON 序列化，字段名即为 JSON key。
 *
 * @param userId 用户 ID
 * @param username 用户名
 * @param loginCount 登录次数
 * @param lastLoginTime 最近登录时间
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public record ActiveUserVO(
    String userId,
    String username,
    int loginCount,
    LocalDateTime lastLoginTime) {
}
