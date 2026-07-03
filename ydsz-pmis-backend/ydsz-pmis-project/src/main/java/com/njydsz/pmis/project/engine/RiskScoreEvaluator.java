package com.njydsz.pmis.project.engine;

import com.njydsz.pmis.project.entity.RiskDO;
import com.njydsz.pmis.project.enums.RiskLevel;
import lombok.extern.slf4j.Slf4j;

/**
 * 风险评分引擎：probability × impact 矩阵
 *
 * <ul>
 *   <li>probability 权重：LOW=1, MEDIUM=2, HIGH=3</li>
 *   <li>impact 权重：LOW=1, MEDIUM=2, HIGH=3</li>
 *   <li>score = weight * weight</li>
 *   <li>LOW: 1-2, MEDIUM: 3-5, HIGH: 6-9</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class RiskScoreEvaluator {

    /**
     * 根据概率与影响评估风险等级
     *
     * @param probability 概率等级（LOW/MEDIUM/HIGH）
     * @param impact      影响等级（LOW/MEDIUM/HIGH）
     * @return 风险等级
     */
    public static RiskLevel evaluate(String probability, String impact) {
        int p = weightOf(probability);
        int i = weightOf(impact);
        int score = p * i;
        RiskLevel level = RiskLevel.fromScore(score);
        log.debug("[RiskScore] probability={} impact={} -> score={} level={}", probability, impact, score, level);
        return level;
    }

    /**
     * 根据风险实体评估风险等级
     *
     * @param risk 风险实体
     * @return 风险等级（入参为 null 时返回 LOW）
     */
    public static RiskLevel evaluate(RiskDO risk) {
        if (risk == null) return RiskLevel.LOW;
        return evaluate(risk.getProbability(), risk.getImpact());
    }

    /**
     * 将概率/影响等级转换为权重值
     *
     * @param level 等级编码（LOW/MEDIUM/HIGH）
     * @return 权重值（LOW=1, MEDIUM=2, HIGH=3）；null 返回 2
     */
    private static int weightOf(String level) {
        if (level == null) return 2;
        return switch (level.trim().toUpperCase()) {
            case "LOW" -> 1;
            case "HIGH" -> 3;
            default -> 2;
        };
    }
}
