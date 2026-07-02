package com.njydsz.pmis.project.engine;

import com.njydsz.pmis.project.entity.OpportunityDO;
import com.njydsz.pmis.project.enums.OpportunityLevel;
import com.njydsz.pmis.project.enums.OpportunityStatus;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 商机赢率评估器
 *
 * <p>采用多因子加权模型：
 * <ul>
 *   <li>客户资质（20%）</li>
 *   <li>项目分级（15%）</li>
 *   <li>跟进阶段（30%）</li>
 *   <li>竞争态势（15%）</li>
 *   <li>历史合作（20%）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class WinRateEvaluator {

    /** 赢单率上限（1.0） */
    private static final BigDecimal MAX = BigDecimal.ONE;

    /**
     * 默认评估（无客户资质/历史合作加成）。
     *
     * @param opp 商机实体
     * @return 赢单率（0-1）；商机为 null 返回 0
     */
    public static BigDecimal evaluate(OpportunityDO opp) {
        return evaluate(opp, null, false);
    }

    /**
     * 完整评估。
     *
     * <p>终态商机直接返回：WON/CONVERTED 返回 1，LOST/INVALID 返回 0。
     *
     * @param opp                商机实体，为 null 返回 0
     * @param customerCredit     客户信用等级：A/B/C/D，null 时按 B 计
     * @param hasHistoricalCoop  是否有历史合作
     * @return 赢单率（0-1）
     */
    public static BigDecimal evaluate(OpportunityDO opp, String customerCredit, boolean hasHistoricalCoop) {
        if (opp == null) {
            return BigDecimal.ZERO;
        }
        OpportunityStatus s = OpportunityStatus.fromCode(opp.getStatus());
        // 终态直接判定
        if (s == OpportunityStatus.WON) {
            return MAX;
        }
        if (s == OpportunityStatus.LOST || s == OpportunityStatus.INVALID) {
            return BigDecimal.ZERO;
        }
        BigDecimal rate = BigDecimal.ZERO;

        // 1) 跟进阶段权重（30%）
        BigDecimal stageScore = stageScore(opp.getStatus());
        rate = rate.add(stageScore.multiply(new BigDecimal("0.30")));

        // 2) 分级权重（15%）
        BigDecimal levelScore = levelScore(opp.getLevel());
        rate = rate.add(levelScore.multiply(new BigDecimal("0.15")));

        // 3) 客户资质（20%）
        BigDecimal creditScore = creditScore(customerCredit);
        rate = rate.add(creditScore.multiply(new BigDecimal("0.20")));

        // 4) 竞争态势（15%）
        BigDecimal compScore = competitionScore(opp.getCompetitor());
        rate = rate.add(compScore.multiply(new BigDecimal("0.15")));

        // 5) 历史合作（20%）
        BigDecimal historyScore = hasHistoricalCoop ? new BigDecimal("1.0") : new BigDecimal("0.4");
        rate = rate.add(historyScore.multiply(new BigDecimal("0.20")));

        rate = rate.setScale(4, RoundingMode.HALF_UP);
        if (rate.compareTo(MAX) > 0) {
            return MAX;
        }
        if (rate.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        log.debug("[WinRate] 评估商机 {} (code={}) -> rate={}",
                opp.getOpportunityName(), opp.getOpportunityCode(), rate);
        return rate;
    }

    /**
     * 根据商机状态计算跟进阶段得分。
     *
     * @param status 商机状态码，可空
     * @return 阶段得分（0-1）；越接近赢单状态得分越高
     */
    private static BigDecimal stageScore(String status) {
        OpportunityStatus s = OpportunityStatus.fromCode(status);
        if (s == null) return new BigDecimal("0.30");
        return switch (s) {
            case FOLLOWING -> new BigDecimal("0.30");
            case QUOTED -> new BigDecimal("0.60");
            case NEGOTIATING -> new BigDecimal("0.85");
            case WON -> new BigDecimal("1.0");
            case CONVERTED -> new BigDecimal("1.0");
            case LOST, INVALID -> new BigDecimal("0");
        };
    }

    /**
     * 根据商机分级计算得分。
     *
     * @param level 商机分级（A/B/C），可空
     * @return 分级得分（0-1）；A 最高、C 最低
     */
    private static BigDecimal levelScore(String level) {
        OpportunityLevel l = OpportunityLevel.fromCode(level);
        return switch (l) {
            case A -> new BigDecimal("1.0");
            case B -> new BigDecimal("0.7");
            case C -> new BigDecimal("0.4");
        };
    }

    /**
     * 根据客户信用等级计算得分。
     *
     * @param credit 客户信用等级（A/B/C/D），可空
     * @return 信用得分（0-1）；为空按 0.6 计
     */
    private static BigDecimal creditScore(String credit) {
        if (credit == null) return new BigDecimal("0.6");
        return switch (credit.trim().toUpperCase()) {
            case "A" -> new BigDecimal("1.0");
            case "B" -> new BigDecimal("0.7");
            case "C" -> new BigDecimal("0.4");
            case "D" -> new BigDecimal("0.1");
            default -> new BigDecimal("0.5");
        };
    }

    /**
     * 根据竞争对手数量计算竞争态势得分。
     *
     * @param competitor 竞争对手描述（多个用逗号/分号分隔），可空
     * @return 竞争得分（0-1）；竞争对手越多分数越低
     */
    private static BigDecimal competitionScore(String competitor) {
        if (competitor == null || competitor.isBlank()) {
            return new BigDecimal("0.7");
        }
        // 多家竞争对手 -> 分数下降
        int n = competitor.split("[,，;；]").length;
        if (n >= 3) return new BigDecimal("0.3");
        if (n == 2) return new BigDecimal("0.5");
        return new BigDecimal("0.7");
    }
}
