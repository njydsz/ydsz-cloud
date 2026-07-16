package com.njydsz.pmis.project.server.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.njydsz.pmis.project.domain.entity.OpportunityDO;
import com.njydsz.pmis.project.domain.enums.OpportunityLevel;
import com.njydsz.pmis.project.domain.enums.OpportunityStatus;

import lombok.extern.slf4j.Slf4j;

/**
 * 商机赢率评估器（增强版）
 *
 * <p>采用多因子加权模型：
 * <ul>
 *   <li>客户资质（15%）</li>
 *   <li>项目分级（10%）</li>
 *   <li>跟进阶段（25%）</li>
 *   <li>竞争态势（10%）</li>
 *   <li>历史合作（15%）</li>
 *   <li>跟进活跃度（15%）—— 近30天跟进次数加权，活跃度越高赢率越高</li>
 *   <li>商机金额（10%）—— 金额适中得分高，过大过小均降分</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class WinRateEvaluator {

    /** 赢单率上限（1.0） */
    private static final BigDecimal MAX = BigDecimal.ONE;

    /** 适中金额下限（50万） */
    private static final BigDecimal IDEAL_AMOUNT_MIN = new BigDecimal("500000");
    /** 适中金额上限（500万） */
    private static final BigDecimal IDEAL_AMOUNT_MAX = new BigDecimal("5000000");

    /**
     * 默认评估（无客户资质/历史合作加成）。
     *
     * @param opp 商机实体
     * @return 赢单率（0-1）；商机为 null 返回 0
     */
    public static BigDecimal evaluate(OpportunityDO opp) {
        return evaluate(opp, null, false, 0, null);
    }

    /**
     * 兼容旧调用：不含跟进活跃度和金额因子。
     *
     * @param opp                商机实体
     * @param customerCredit     客户信用等级
     * @param hasHistoricalCoop  是否有历史合作
     * @return 赢单率（0-1）
     */
    public static BigDecimal evaluate(OpportunityDO opp, String customerCredit, boolean hasHistoricalCoop) {
        return evaluate(opp, customerCredit, hasHistoricalCoop, 0, null);
    }

    /**
     * 完整评估（增强版）。
     *
     * <p>终态商机直接返回：WON/CONVERTED 返回 1，LOST/INVALID 返回 0。
     *
     * @param opp                商机实体，为 null 返回 0
     * @param customerCredit     客户信用等级：A/B/C/D，null 时按 B 计
     * @param hasHistoricalCoop  是否有历史合作
     * @param recentFollowCount  近30天跟进次数（0表示无跟进）
     * @param estimatedAmount    预估金额，null 时跳过金额因子
     * @return 赢单率（0-1）
     */
    public static BigDecimal evaluate(OpportunityDO opp, String customerCredit, boolean hasHistoricalCoop,
                                       int recentFollowCount, BigDecimal estimatedAmount) {
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

        // 1) 跟进阶段权重（25%）
        BigDecimal stageScore = stageScore(opp.getStatus());
        rate = rate.add(stageScore.multiply(new BigDecimal("0.25")));

        // 2) 分级权重（10%）
        BigDecimal levelScore = levelScore(opp.getLevel());
        rate = rate.add(levelScore.multiply(new BigDecimal("0.10")));

        // 3) 客户资质（15%）
        BigDecimal creditScoreVal = creditScore(customerCredit);
        rate = rate.add(creditScoreVal.multiply(new BigDecimal("0.15")));

        // 4) 竞争态势（10%）
        BigDecimal compScore = competitionScore(opp.getCompetitor());
        rate = rate.add(compScore.multiply(new BigDecimal("0.10")));

        // 5) 历史合作（15%）
        BigDecimal historyScore = hasHistoricalCoop ? new BigDecimal("1.0") : new BigDecimal("0.4");
        rate = rate.add(historyScore.multiply(new BigDecimal("0.15")));

        // 6) 跟进活跃度（15%）—— 近30天跟进次数加权
        BigDecimal activityScore = followActivityScore(recentFollowCount);
        rate = rate.add(activityScore.multiply(new BigDecimal("0.15")));

        // 7) 商机金额（10%）—— 适中金额得分高
        BigDecimal amountScore = amountScore(estimatedAmount);
        rate = rate.add(amountScore.multiply(new BigDecimal("0.10")));

        rate = rate.setScale(4, RoundingMode.HALF_UP);
        if (rate.compareTo(MAX) > 0) {
            return MAX;
        }
        if (rate.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        log.debug("[WinRate] 评估商机 {} (code={}) -> rate={}, followCount={}, amount={}",
                opp.getOpportunityName(), opp.getOpportunityCode(), rate, recentFollowCount, estimatedAmount);
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

    /**
     * 跟进活跃度得分：近30天跟进次数越多得分越高。
     * <ul>
     *   <li>0 次 → 0.2（活跃度极低，商机可能已停滞）</li>
     *   <li>1-2 次 → 0.5</li>
     *   <li>3-5 次 → 0.8</li>
     *   <li>6+ 次 → 1.0</li>
     * </ul>
     *
     * @param recentFollowCount 近30天跟进次数
     * @return 活跃度得分（0-1）
     */
    private static BigDecimal followActivityScore(int recentFollowCount) {
        if (recentFollowCount <= 0) return new BigDecimal("0.2");
        if (recentFollowCount <= 2) return new BigDecimal("0.5");
        if (recentFollowCount <= 5) return new BigDecimal("0.8");
        return new BigDecimal("1.0");
    }

    /**
     * 商机金额得分：适中金额（50万-500万）得分最高。
     * <ul>
     *   <li>null → 0.5（未知金额按中等计）</li>
     *   <li>50万-500万 → 1.0（适中金额，赢率最高）</li>
     *   <li>10万-50万 → 0.7（小金额，竞争可能激烈）</li>
     *   <li>500万-2000万 → 0.6（大金额，审批周期长）</li>
     *   <li>2000万+ → 0.3（超大金额，赢率显著降低）</li>
     *   <li>10万以下 → 0.4（微型金额，可能不值得投入）</li>
     * </ul>
     *
     * @param amount 预估金额
     * @return 金额得分（0-1）
     */
    private static BigDecimal amountScore(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return new BigDecimal("0.5");
        if (amount.compareTo(IDEAL_AMOUNT_MIN) >= 0 && amount.compareTo(IDEAL_AMOUNT_MAX) <= 0) {
            return new BigDecimal("1.0");
        }
        if (amount.compareTo(new BigDecimal("100000")) >= 0) {
            return new BigDecimal("0.7");
        }
        if (amount.compareTo(new BigDecimal("20000000")) >= 0) {
            return new BigDecimal("0.3");
        }
        if (amount.compareTo(IDEAL_AMOUNT_MAX) > 0) {
            return new BigDecimal("0.6");
        }
        return new BigDecimal("0.4");
    }
}
