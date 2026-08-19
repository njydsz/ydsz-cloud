package com.njydsz.userinfo.domain.vo;

/**
 * 风险等级分布 VO。
 *
 * <p>按风险等级（高/中/低）聚合用户数量。
 *
 * <p>使用 {@link com.njydsz.common.json.YdszJson} 进行 JSON 序列化，字段名即为 JSON key。
 *
 * @param highRisk 高风险用户数
 * @param mediumRisk 中风险用户数
 * @param lowRisk 低风险用户数
 *
 * @author ydsz-team
 * @since 1.6.0
 */
public record RiskLevelDistributionVO(
    int highRisk,
    int mediumRisk,
    int lowRisk) {
}
