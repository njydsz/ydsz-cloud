package com.njydsz.userinfo.domain.vo;

/**
 * 异常会话 VO。
 *
 * <p>记录检测到的异常会话信息，包括多地登录、异常活跃、长时间未活动等。
 *
 * <p>使用 {@link com.njydsz.common.json.YdszJson} 进行 JSON 序列化，字段名即为 JSON key。
 *
 * @param userId 用户 ID
 * @param username 用户名
 * @param anomalyType 异常类型（MULTI_IP/HIGH_ACTIVITY/STALE_SESSION）
 * @param description 异常描述
 * @param riskLevel 风险等级（HIGH/MEDIUM/LOW）
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public record AnomalySessionVO(
    String userId,
    String username,
    String anomalyType,
    String description,
    String riskLevel) {
}
