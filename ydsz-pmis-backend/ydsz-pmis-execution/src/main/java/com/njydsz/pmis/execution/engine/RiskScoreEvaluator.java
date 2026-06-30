package com.njydsz.pmis.execution.engine;

import com.njydsz.pmis.execution.entity.RiskDO;
import com.njydsz.pmis.execution.enums.RiskLevel;
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

    public static RiskLevel evaluate(String probability, String impact) {
        int p = weightOf(probability);
        int i = weightOf(impact);
        int score = p * i;
        RiskLevel level = RiskLevel.fromScore(score);
        log.debug("[RiskScore] probability={} impact={} -> score={} level={}", probability, impact, score, level);
        return level;
    }

    public static RiskLevel evaluate(RiskDO risk) {
        if (risk == null) return RiskLevel.LOW;
        return evaluate(risk.getProbability(), risk.getImpact());
    }

    private static int weightOf(String level) {
        if (level == null) return 2;
        return switch (level.trim().toUpperCase()) {
            case "LOW" -> 1;
            case "HIGH" -> 3;
            default -> 2;
        };
    }
}
