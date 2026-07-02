package com.njydsz.pmis.agent.engine;

import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 项目风险预警 Agent
 *
 * <p>基于多维度评分（EVM/CPI/SPI/进度偏差/成本超支/风险事件数），
 * 输出风险等级（INFO/YELLOW/RED/NORMAL）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class RiskWarningAgent implements Agent {

    @Override
    public AgentType type() {
        return AgentType.RISK_WARNING;
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        Map<String, Object> p = ctx.getParams() == null ? Map.of() : ctx.getParams();
        BigDecimal cpi = toBd(p.get("cpi"), BigDecimal.ONE);
        BigDecimal spi = toBd(p.get("spi"), BigDecimal.ONE);
        BigDecimal grossMargin = toBd(p.get("grossMargin"), BigDecimal.ZERO);
        BigDecimal progressPct = toBd(p.get("progressPct"), BigDecimal.ZERO);
        BigDecimal costOverrun = toBd(p.get("costOverrun"), BigDecimal.ZERO);
        Integer riskCount = p.get("riskCount") instanceof Number n ? n.intValue() : 0;
        Integer highRiskCount = p.get("highRiskCount") instanceof Number n2 ? n2.intValue() : 0;

        double score = 0.0;
        List<String> matched = new ArrayList<>();

        if (cpi.compareTo(new BigDecimal("0.85")) < 0) {
            score += 0.35;
            matched.add("CPI<0.85（" + cpi.setScale(2, RoundingMode.HALF_UP) + "）");
        } else if (cpi.compareTo(new BigDecimal("0.95")) < 0) {
            score += 0.18;
            matched.add("CPI<0.95（" + cpi.setScale(2, RoundingMode.HALF_UP) + "）");
        }
        if (spi.compareTo(new BigDecimal("0.85")) < 0) {
            score += 0.20;
            matched.add("SPI<0.85（" + spi.setScale(2, RoundingMode.HALF_UP) + "）");
        } else if (spi.compareTo(new BigDecimal("0.95")) < 0) {
            score += 0.10;
            matched.add("SPI<0.95（" + spi.setScale(2, RoundingMode.HALF_UP) + "）");
        }
        if (costOverrun.signum() > 0) {
            double pct = costOverrun.doubleValue();
            if (pct >= 0.20) {
                score += 0.20;
                matched.add("成本超支≥20%（" + (pct * 100) + "%）");
            } else if (pct >= 0.10) {
                score += 0.10;
                matched.add("成本超支≥10%（" + (pct * 100) + "%）");
            }
        }
        if (grossMargin.signum() < 0) {
            score += 0.15;
            matched.add("毛利率为负（" + grossMargin.setScale(4, RoundingMode.HALF_UP) + "）");
        }
        if (highRiskCount >= 2) {
            score += 0.10;
            matched.add("高风险事件≥2");
        } else if (highRiskCount >= 1) {
            score += 0.05;
            matched.add("存在高风险事件");
        }
        if (riskCount >= 5) {
            score += 0.05;
            matched.add("风险事件数≥5");
        }

        AgentAlertLevel level;
        if (score >= 0.55) level = AgentAlertLevel.RED;
        else if (score >= 0.25) level = AgentAlertLevel.YELLOW;
        else level = AgentAlertLevel.NORMAL;

        BigDecimal scoreBd = BigDecimal.valueOf(Math.min(1.0, score)).setScale(4, RoundingMode.HALF_UP);
        BigDecimal confidence = BigDecimal.valueOf(0.7 + Math.min(0.25, matched.size() * 0.05))
                .setScale(4, RoundingMode.HALF_UP);
        String suggestion = buildSuggestion(level, matched);

        log.info("[RiskWarning] biz={} score={} level={} matched={}",
                ctx.getBizRef(), scoreBd, level, matched);
        Map<String, Object> payload = new HashMap<>();
        payload.put("score", score);
        payload.put("cpi", cpi);
        payload.put("spi", spi);
        payload.put("progressPct", progressPct);
        payload.put("grossMargin", grossMargin);
        return new AgentResult(AgentType.RISK_WARNING, level, scoreBd, confidence,
                suggestion, matched, payload);
    }

    /**
     * 根据告警等级与命中规则构建建议文本。
     *
     * @param level   告警等级
     * @param matched 命中规则列表
     * @return 建议文本
     */
    private String buildSuggestion(AgentAlertLevel level, List<String> matched) {
        if (matched.isEmpty()) {
            return "项目各项指标正常，请保持当前节奏。";
        }
        StringBuilder sb = new StringBuilder();
        if (level == AgentAlertLevel.RED) {
            sb.append("【红色预警】建议立即召集 PMO + 事业部总经理 + 财务总监评审。");
        } else if (level == AgentAlertLevel.YELLOW) {
            sb.append("【黄色预警】建议加强关注并制定纠偏计划。");
        } else {
            sb.append("请关注以下指标：");
        }
        sb.append("命中规则: ").append(String.join("；", matched));
        return sb.toString();
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
