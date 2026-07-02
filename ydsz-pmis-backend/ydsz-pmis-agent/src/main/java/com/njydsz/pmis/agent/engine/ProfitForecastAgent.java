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
 * 利润预测 Agent
 *
 * <p>基于 CPI 趋势线预测 EAC（完工估算），给出健康度评分和优化建议。
 *
 * <p>预测公式：EAC = totalCost / progressPct
 * <p>健康度评分：0-100，综合毛利率 + CPI + SPI
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ProfitForecastAgent implements Agent {

    @Override
    public AgentType type() {
        return AgentType.PROFIT_FORECAST;
    }

    @Override
    public AgentResult execute(AgentContext ctx) {
        Map<String, Object> p = ctx.getParams() == null ? Map.of() : ctx.getParams();
        BigDecimal contractAmount = toBd(p.get("contractAmount"), BigDecimal.ZERO);
        BigDecimal totalCost = toBd(p.get("totalCost"), BigDecimal.ZERO);
        BigDecimal progressPct = toBd(p.get("progressPct"), BigDecimal.ZERO);
        BigDecimal cpi = toBd(p.get("cpi"), BigDecimal.ONE);
        BigDecimal spi = toBd(p.get("spi"), BigDecimal.ONE);

        List<String> matched = new ArrayList<>();

        // EAC
        BigDecimal eac = BigDecimal.ZERO;
        if (progressPct.signum() > 0) {
            BigDecimal pct = progressPct.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            eac = totalCost.divide(pct, 2, RoundingMode.HALF_UP);
        }

        // 预测毛利率
        BigDecimal forecastMargin = BigDecimal.ZERO;
        if (contractAmount.signum() > 0) {
            forecastMargin = contractAmount.subtract(eac)
                    .divide(contractAmount, 4, RoundingMode.HALF_UP);
        }

        // 健康度评分
        double health = 0.0;
        if (forecastMargin.compareTo(new BigDecimal("0.20")) >= 0) {
            health += 50.0;
        } else if (forecastMargin.signum() >= 0) {
            health += (forecastMargin.doubleValue() / 0.20) * 50.0;
        }
        if (cpi.compareTo(BigDecimal.ONE) >= 0) health += 25.0;
        else if (cpi.compareTo(new BigDecimal("0.85")) >= 0) {
            health += (cpi.doubleValue() - 0.85) / 0.15 * 25.0;
            matched.add("CPI<1.0（" + cpi.setScale(2, RoundingMode.HALF_UP) + "）");
        } else {
            matched.add("CPI<0.85（" + cpi.setScale(2, RoundingMode.HALF_UP) + "）");
        }
        if (spi.compareTo(BigDecimal.ONE) >= 0) health += 25.0;
        else if (spi.compareTo(new BigDecimal("0.85")) >= 0) {
            health += (spi.doubleValue() - 0.85) / 0.15 * 25.0;
        } else {
            matched.add("SPI<0.85（" + spi.setScale(2, RoundingMode.HALF_UP) + "）");
        }
        int healthScore = (int) Math.round(Math.max(0, Math.min(100, health)));

        AgentAlertLevel level;
        if (healthScore < 50) level = AgentAlertLevel.RED;
        else if (healthScore < 75) level = AgentAlertLevel.YELLOW;
        else level = AgentAlertLevel.NORMAL;

        StringBuilder suggestion = new StringBuilder();
        suggestion.append("预测EAC=").append(eac).append("，预测毛利率=")
                .append(forecastMargin.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP))
                .append("%，健康度评分=").append(healthScore).append("。");
        if (!matched.isEmpty()) {
            suggestion.append("优化建议：").append(String.join("；", matched));
        }

        BigDecimal scoreBd = BigDecimal.valueOf(healthScore).setScale(2, RoundingMode.HALF_UP);
        BigDecimal confidence = BigDecimal.valueOf(0.8);
        log.info("[ProfitForecast] biz={} EAC={} margin={} health={} level={}",
                ctx.getBizRef(), eac, forecastMargin, healthScore, level);
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("eac", eac);
        payload.put("forecastMargin", forecastMargin);
        payload.put("healthScore", healthScore);
        return new AgentResult(AgentType.PROFIT_FORECAST, level, scoreBd, confidence,
                suggestion.toString(), matched, payload);
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
