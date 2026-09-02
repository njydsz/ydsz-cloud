package com.njydsz.userinfo.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 会话统计信息。
 *
 * <p>封装平台级会话统计数据，用于管理员仪表盘展示。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code totalActiveSessions}：当前活跃会话总数</li>
 *   <li>{@code activeUserCount}：当前活跃用户数（去重）</li>
 *   <li>{@code sessionsPerDevice}：分端会话统计（key=deviceType, value=会话数）</li>
 * </ul>
 *
 * @param totalActiveSessions 当前活跃会话总数
 * @param activeUserCount 当前活跃用户数（去重）
 * @param sessionsPerDevice 分端会话统计
 * @author ydsz-team
 * @since 26.09.01
 */
public record UserSessionStatisticsVO(
    int totalActiveSessions,
    int activeUserCount,
    Map<String, Integer> sessionsPerDevice) implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;
}
