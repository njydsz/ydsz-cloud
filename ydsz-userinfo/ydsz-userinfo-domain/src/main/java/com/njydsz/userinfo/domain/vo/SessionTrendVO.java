package com.njydsz.userinfo.domain.vo;

import java.time.LocalDate;

/**
 * 会话趋势 VO。
 *
 * <p>按日期统计的新增会话和活跃会话数据。
 *
 * <p>使用 {@link com.njydsz.common.json.YdszJson} 进行 JSON 序列化，字段名即为 JSON key。
 *
 * @param date 统计日期
 * @param newSessions 新增会话数
 * @param activeSessions 活跃会话数
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public record SessionTrendVO(
    LocalDate date,
    int newSessions,
    int activeSessions) {
}
