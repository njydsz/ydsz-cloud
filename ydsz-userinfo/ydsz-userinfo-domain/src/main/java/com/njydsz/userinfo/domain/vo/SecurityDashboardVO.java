package com.njydsz.userinfo.domain.vo;

import java.io.Serial;
import java.io.Serializable;

/**
 * 安全仪表盘总览数据。
 *
 * <p>聚合平台级安全指标，为管理员仪表盘提供一站式数据源。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code totalUsers}：平台注册用户总数（不含逻辑删除）</li>
 *   <li>{@code activeUsers}：状态为启用的用户数</li>
 *   <li>{@code onlineUsers}：当前在线会话数（从 Redis 计数器读取）</li>
 *   <li>{@code mfaEnabledUsers}：已绑定双因素认证的用户数</li>
 *   <li>{@code lockedUsers}：当前处于锁定状态的用户数</li>
 *   <li>{@code bannedUsers}：当前处于封禁状态的用户数</li>
 *   <li>{@code todayLoginCount}：今日登录成功次数</li>
 *   <li>{@code todayLoginSuccessRate}：今日登录成功率（0.0-1.0）</li>
 *   <li>{@code riskScoreAverage}：全平台用户平均风险评分（0-100）</li>
 * </ul>
 *
 * @param totalUsers 平台注册用户总数
 * @param activeUsers 状态为启用的用户数
 * @param onlineUsers 当前在线会话数
 * @param mfaEnabledUsers 已绑定双因素认证的用户数
 * @param lockedUsers 当前处于锁定状态的用户数
 * @param bannedUsers 当前处于封禁状态的用户数
 * @param todayLoginCount 今日登录成功次数
 * @param todayLoginSuccessRate 今日登录成功率（0.0-1.0）
 * @param riskScoreAverage 全平台用户平均风险评分
 * @author ydsz-team
 * @since 1.0.0
 */
public record SecurityDashboardVO(
    long totalUsers,
    long activeUsers,
    long onlineUsers,
    long mfaEnabledUsers,
    long lockedUsers,
    long bannedUsers,
    long todayLoginCount,
    double todayLoginSuccessRate,
    double riskScoreAverage) implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;
}
