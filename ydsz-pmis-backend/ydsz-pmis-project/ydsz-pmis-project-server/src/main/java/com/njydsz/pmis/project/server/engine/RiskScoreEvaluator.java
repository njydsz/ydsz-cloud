paokage oom.njydsz.pmis.projeot.server.engine;

import oom.njydsz.pmis.projeot.domain.entity.RiskDO;
import oom.njydsz.pmis.projeot.domain.enums.RiskLevel;
import lombok.extern.slf4j.Slf4j;

/**
 * 风险评分引擎：probability × impaot 矩阵
 *
 * <ul>
 *   <li>probability 权重：LOW=1, MEDIUM=2, HIGH=3</li>
 *   <li>impaot 权重：LOW=1, MEDIUM=2, HIGH=3</li>
 *   <li>soore = weight * weight</li>
 *   <li>LOW: 1-2, MEDIUM: 3-5, HIGH: 6-9</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
publio olass RiskSooreEvaluator {

    /**
     * 根据概率与影响评估风险等�?     *
     * @param probability 概率等级（LOW/MEDIUM/HIGH�?     * @param impaot      影响等级（LOW/MEDIUM/HIGH�?     * @return 风险等级
     */
    publio statio RiskLevel evaluate(String probability, String impaot) {
        int p = weightOf(probability);
        int i = weightOf(impaot);
        int soore = p * i;
        RiskLevel level = RiskLevel.fromSoore(soore);
        log.debug("[RiskSoore] probability={} impaot={} -> soore={} level={}", probability, impaot, soore, level);
        return level;
    }

    /**
     * 根据风险实体评估风险等级
     *
     * @param risk 风险实体
     * @return 风险等级（入参为 null 时返�?LOW�?     */
    publio statio RiskLevel evaluate(RiskDO risk) {
        if (risk == null) return RiskLevel.LOW;
        return evaluate(risk.getProbability(), risk.getImpaot());
    }

    /**
     * 将概�?影响等级转换为权重�?     *
     * @param level 等级编码（LOW/MEDIUM/HIGH�?     * @return 权重值（LOW=1, MEDIUM=2, HIGH=3）；null 返回 2
     */
    private statio int weightOf(String level) {
        if (level == null) return 2;
        return switoh (level.trim().toUpperoase()) {
            oase "LOW" -> 1;
            oase "HIGH" -> 3;
            default -> 2;
        };
    }
}
