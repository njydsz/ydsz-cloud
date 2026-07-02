package com.njydsz.pmis.agent.engine;

import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 资源调度推荐 Agent
 *
 * <p>输入：候选人员列表（含 level/cost/availability/skillMatch），
 * 输出：按综合得分排序的 Top N 推荐。
 *
 * <p>评分模型：
 * <ul>
 *   <li>技能匹配度 40%</li>
 *   <li>可用度 30%</li>
 *   <li>成本最优 20%</li>
 *   <li>职级匹配 10%</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ResourceRecommendAgent implements Agent {

    @Override
    public AgentType type() {
        return AgentType.RESOURCE_RECOMMEND;
    }

    @Override
    @SuppressWarnings("unchecked")
    public AgentResult execute(AgentContext ctx) {
        Map<String, Object> p = ctx.getParams() == null ? Map.of() : ctx.getParams();
        Object raw = p.get("candidates");
        if (!(raw instanceof List<?>)) {
            return new AgentResult(AgentType.RESOURCE_RECOMMEND, AgentAlertLevel.INFO,
                    BigDecimal.ZERO, BigDecimal.valueOf(0.5),
                    "未提供候选人员列表", List.of("NO_CANDIDATES"), Map.of());
        }
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) raw;
        if (candidates.isEmpty()) {
            return new AgentResult(AgentType.RESOURCE_RECOMMEND, AgentAlertLevel.INFO,
                    BigDecimal.ZERO, BigDecimal.valueOf(0.5),
                    "无可推荐人员", List.of("EMPTY"), Map.of());
        }
        Integer topN = p.get("topN") instanceof Number n ? n.intValue() : 3;
        String requiredLevel = p.get("requiredLevel") == null ? null : p.get("requiredLevel").toString();

        // 归一化成本
        List<BigDecimal> costs = candidates.stream()
                .map(c -> toBd(c.get("dailyCost"), BigDecimal.ZERO))
                .toList();
        BigDecimal minCost = costs.stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ONE);
        BigDecimal maxCost = costs.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ONE);
        BigDecimal costRange = maxCost.subtract(minCost);

        List<Map<String, Object>> scored = new ArrayList<>();
        for (Map<String, Object> c : candidates) {
            BigDecimal skill = clamp01(toBd(c.get("skillMatch"), BigDecimal.ZERO));
            BigDecimal avail = clamp01(toBd(c.get("availability"), BigDecimal.ZERO));
            BigDecimal cost = toBd(c.get("dailyCost"), BigDecimal.ZERO);
            String level = c.get("level") == null ? "" : c.get("level").toString();
            BigDecimal levelMatch = computeLevelMatch(level, requiredLevel);

            // 成本得分：minCost 得 1，maxCost 得 0
            BigDecimal costScore = BigDecimal.ONE;
            if (costRange.signum() > 0 && maxCost.signum() > 0) {
                costScore = maxCost.subtract(cost).divide(costRange, 4, RoundingMode.HALF_UP);
                if (costScore.signum() < 0) costScore = BigDecimal.ZERO;
                if (costScore.compareTo(BigDecimal.ONE) > 0) costScore = BigDecimal.ONE;
            }

            double total = skill.doubleValue() * 0.40
                    + avail.doubleValue() * 0.30
                    + costScore.doubleValue() * 0.20
                    + levelMatch.doubleValue() * 0.10;
            BigDecimal totalBd = BigDecimal.valueOf(total).setScale(4, RoundingMode.HALF_UP);
            c.put("_score", totalBd);
            c.put("_costScore", costScore);
            c.put("_levelMatch", levelMatch);
            c.put("_skillScore", skill);
            c.put("_availScore", avail);
            scored.add(c);
        }
        List<Map<String, Object>> top = scored.stream()
                .sorted((a, b) -> ((BigDecimal) b.get("_score"))
                        .compareTo((BigDecimal) a.get("_score")))
                .limit(topN)
                .collect(Collectors.toList());

        List<String> matched = new ArrayList<>();
        matched.add("候选数=" + candidates.size() + ", 推荐Top" + top.size());

        BigDecimal top1 = (BigDecimal) top.get(0).get("_score");
        AgentAlertLevel level;
        if (top1.compareTo(new BigDecimal("0.7")) >= 0) {
            level = AgentAlertLevel.RECOMMEND;
        } else if (top1.compareTo(new BigDecimal("0.4")) >= 0) {
            level = AgentAlertLevel.YELLOW;
        } else {
            level = AgentAlertLevel.RED;
        }

        String suggestion = "最佳候选: " + top.get(0).get("name") + "（综合得分 "
                + top1 + "）；如需降本可考虑第 " + (top.size() > 1 ? "2" : "1") + " 名";

        log.info("[ResourceRecommend] biz={} top1Score={} level={}",
                ctx.getBizRef(), top1, level);
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("top", top);
        return new AgentResult(AgentType.RESOURCE_RECOMMEND, level, top1,
                BigDecimal.valueOf(0.75), suggestion, matched, payload);
    }

    /**
     * 计算职级匹配度。
     *
     * @param actual   实际职级（如 "L5"），可空
     * @param required 要求职级（如 "L5"），可空
     * @return 匹配度（0-1）；完全匹配返回 1，相邻级别返回 0.5，相隔 2 级返回 0.25
     */
    private BigDecimal computeLevelMatch(String actual, String required) {
        if (required == null || required.isBlank()) return BigDecimal.ONE;
        if (actual == null) return BigDecimal.ZERO;
        if (actual.equalsIgnoreCase(required)) return BigDecimal.ONE;
        // 相邻级别算 0.5
        try {
            int a = Integer.parseInt(actual.toUpperCase().replace("L", ""));
            int r = Integer.parseInt(required.toUpperCase().replace("L", ""));
            if (Math.abs(a - r) == 1) return new BigDecimal("0.5");
            if (Math.abs(a - r) == 2) return new BigDecimal("0.25");
            return BigDecimal.ZERO;
        } catch (Exception ignore) {
            return BigDecimal.ZERO;
        }
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
