package com.njydsz.pmis.agent.engine;

import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商机赢率预测 Agent
 *
 * <p>基于 5 因子评分：客户资质/历史合作/竞争对手/项目阶段/金额规模。
 *
 * <p>返回 0-1 的赢率预测值，附置信度。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class WinRatePredictAgent implements Agent {

    @Override
    public AgentType type() {
        return AgentType.WIN_RATE_PREDICT;
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        Map<String, Object> p = ctx.getParams() == null ? Map.of() : ctx.getParams();
        BigDecimal customerCredit = clamp01(toBd(p.get("customerCredit"), new BigDecimal("0.6")));
        BigDecimal historyScore = clamp01(toBd(p.get("historyScore"), new BigDecimal("0.5")));
        BigDecimal competition = clamp01(toBd(p.get("competitionScore"), new BigDecimal("0.5")));
        String stage = p.get("stage") == null ? "DISCOVERY" : p.get("stage").toString();
        BigDecimal amount = toBd(p.get("amount"), BigDecimal.ZERO);

        double stageWeight = switch (stage.toUpperCase()) {
            case "QUOTE", "NEGOTIATION" -> 0.30;
            case "PROPOSAL" -> 0.20;
            case "QUALIFICATION" -> 0.10;
            case "DISCOVERY" -> 0.05;
            default -> 0.15;
        };
        double amountScore = computeAmountScore(amount);

        double raw = customerCredit.doubleValue() * 0.20
                + historyScore.doubleValue() * 0.20
                + competition.doubleValue() * 0.20
                + stageWeight
                + amountScore * 0.10;
        BigDecimal winRate = BigDecimal.valueOf(Math.max(0.0, Math.min(1.0, raw)))
                .setScale(4, RoundingMode.HALF_UP);

        List<String> matched = new ArrayList<>();
        matched.add("客户资质=" + customerCredit);
        matched.add("历史合作=" + historyScore);
        matched.add("竞争=" + competition);
        matched.add("阶段=" + stage + "(权重 " + stageWeight + ")");
        matched.add("金额=" + amount + "(得分 " + amountScore + ")");

        AgentAlertLevel level;
        if (winRate.compareTo(new BigDecimal("0.7")) >= 0) {
            level = AgentAlertLevel.RECOMMEND;
        } else if (winRate.compareTo(new BigDecimal("0.4")) >= 0) {
            level = AgentAlertLevel.YELLOW;
        } else {
            level = AgentAlertLevel.RED;
        }

        String suggestion = "预测赢率=" + winRate.multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP) + "%（" + level.getDesc() + "）。";
        if (winRate.compareTo(new BigDecimal("0.4")) < 0) {
            suggestion += "建议重新评估项目优先级，避免资源浪费。";
        } else if (winRate.compareTo(new BigDecimal("0.7")) >= 0) {
            suggestion += "建议加大资源投入，争取尽快签约。";
        }

        BigDecimal confidence = BigDecimal.valueOf(0.6 + stageWeight).setScale(4, RoundingMode.HALF_UP);
        log.info("[WinRatePredict] biz={} winRate={} level={}", ctx.getBizRef(), winRate, level);
        Map<String, Object> payload = new HashMap<>();
        payload.put("winRate", winRate);
        payload.put("stage", stage);
        return new AgentResult(AgentType.WIN_RATE_PREDICT, level, winRate, confidence,
                suggestion, matched, payload);
    }

    /**
     * 根据合同金额计算规模得分。
     *
     * @param amount 合同金额，可空
     * @return 规模得分（0-1）；500万-5000万得分最高
     */
    private double computeAmountScore(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return 0.5;
        // 中型项目最易赢：500万-5000万得分最高
        if (amount.compareTo(new BigDecimal("5000000")) >= 0
                && amount.compareTo(new BigDecimal("50000000")) <= 0) {
            return 0.7;
        }
        if (amount.compareTo(new BigDecimal("500000")) >= 0) {
            return 0.5;
        }
        return 0.3;
    }

    /**
     * 将 BigDecimal 值限制在 [0, 1] 区间。
     *
     * @param v 输入值，可空
     * @return 限制后的值；为空返回 0
     */
    private static BigDecimal clamp01(BigDecimal v) {
        if (v == null) return BigDecimal.ZERO;
        if (v.signum() < 0) return BigDecimal.ZERO;
        if (v.compareTo(BigDecimal.ONE) > 0) return BigDecimal.ONE;
        return v;
    }

    /**
     * 将任意对象转换为 BigDecimal。
     *
     * @param o   输入对象（Number/BigDecimal/字符串），可空
     * @param def 默认值
     * @return 转换后的 BigDecimal；为空或转换失败返回 def
     */
    private static BigDecimal toBd(Object o, BigDecimal def) {
        if (o == null) return def;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(o.toString());
        } catch (Exception ignore) {
            return def;
        }
    }
}
