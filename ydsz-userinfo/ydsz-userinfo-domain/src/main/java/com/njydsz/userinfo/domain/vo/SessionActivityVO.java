package com.njydsz.userinfo.domain.vo;

import java.io.Serial;
import java.io.Serializable;

/**
 * 会话活跃度概览。
 *
 * <p>聚合平台级会话活跃度指标，为管理员仪表盘提供会话维度的实时数据。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code totalActiveSessions}：当前活跃会话总数</li>
 *   <li>{@code activeUserCount}：当前活跃用户数（去重）</li>
 *   <li>{@code avgSessionDuration}：平均会话持续时长（分钟）</li>
 * </ul>
 *
 * @param totalActiveSessions 当前活跃会话总数
 * @param activeUserCount 当前活跃用户数（去重）
 * @param avgSessionDuration 平均会话持续时长（分钟）
 * @author ydsz-team
 * @since 1.6.0
 */
public record SessionActivityVO(
    int totalActiveSessions,
    int activeUserCount,
    double avgSessionDuration) implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;
}
