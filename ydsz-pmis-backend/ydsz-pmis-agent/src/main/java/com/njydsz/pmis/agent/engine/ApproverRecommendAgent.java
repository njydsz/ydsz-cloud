package com.njydsz.pmis.agent.engine;

import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * P2-1: 审批人推荐 Agent（工作流场景）
 *
 * <p>输入参数（params）：
 * <ul>
 *   <li>candidates: List&lt;Map&gt; 候选审批人列表，每项字段：
 *     <ul>
 *       <li>userId: Long 用户 ID（必填）</li>
 *       <li>name: String 姓名</li>
 *       <li>department: String 部门（用于部门匹配）</li>
 *       <li>level: String 职级（L5/L6 等）</li>
 *       <li>role: String 角色（PM/部门经理/HRBP 等）</li>
 *       <li>activeTasks: Integer 当前在手任务数（越少越空闲）</li>
 *       <li>historicalApproveCount: Integer 历史审批量</li>
 *       <li>avgApprovalMs: Long 平均审批耗时（毫秒）</li>
 *     </ul>
 *   </li>
 *   <li>requiredLevel: String 期望职级（可空）</li>
 *   <li>requiredRole: String 期望角色（可空）</li>
 *   <li>requiredDepartment: String 期望部门（可空）</li>
 *   <li>topN: Integer 推荐 Top N，默认 3</li>
 * </ul>
 *
 * <p>评分模型（总分 1.0）：
 * <ul>
 *   <li>职级匹配 30%</li>
 *   <li>角色匹配 25%</li>
 *   <li>部门匹配 20%</li>
 *   <li>在手任务数（越少越好）15%</li>
 *   <li>历史审批耗时（越短越好）10%</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ApproverRecommendAgent implements Agent {

    @Override
    public AgentType type() {
        return AgentType.APPROVER_RECOMMEND;
    }

    @Override
    @SuppressWarnings("unchecked")
    public AgentResult execute(AgentContext ctx) {
        Map<String, Object> p = ctx.getParams() == null ? Map.of() : ctx.getParams();
        Object raw = p.get("candidates");
        if (!(raw instanceof List<?>)) {
            return new AgentResult(AgentType.APPROVER_RECOMMEND, AgentAlertLevel.INFO,
                    BigDecimal.ZERO, BigDecimal.valueOf(0.5),
                    "未提供候选审批人列表", List.of("NO_CANDIDATES"), Map.of());
        }
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) raw;
        if (candidates.isEmpty()) {
            return new AgentResult(AgentType.APPROVER_RECOMMEND, AgentAlertLevel.INFO,
                    BigDecimal.ZERO, BigDecimal.valueOf(0.5),
                    "无可推荐审批人", List.of("EMPTY"), Map.of());
        }

        Integer topN = p.get("topN") instanceof Number n ? n.intValue() : 3;
        if (topN <= 0) topN = 3;
        if (topN > 10) topN = 10;
        String requiredLevel = p.get("requiredLevel") == null ? null : p.get("requiredLevel").toString();
        String requiredRole = p.get("requiredRole") == null ? null : p.get("requiredRole").toString();
        String requiredDept = p.get("requiredDepartment") == null ? null : p.get("requiredDepartment").toString();

        // 1. 计算每个候选人的各项子得分
        List<Map<String, Object>> scored = new ArrayList<>();
        for (Map<String, Object> c : candidates) {
            BigDecimal levelScore = computeLevelMatch(
                    str(c.get("level")), requiredLevel);
            BigDecimal roleScore = computeStringMatch(
                    str(c.get("role")), requiredRole);
            BigDecimal deptScore = computeStringMatch(
                    str(c.get("department")), requiredDept);
            BigDecimal loadScore = computeLoadScore(toInt(c.get("activeTasks")));
            BigDecimal speedScore = computeSpeedScore(toLong(c.get("avgApprovalMs")));

            // 加权
            double total = levelScore.doubleValue() * 0.30
                    + roleScore.doubleValue() * 0.25
                    + deptScore.doubleValue() * 0.20
                    + loadScore.doubleValue() * 0.15
                    + speedScore.doubleValue() * 0.10;

            BigDecimal totalBd = BigDecimal.valueOf(total).setScale(4, RoundingMode.HALF_UP);
            Map<String, Object> out = new LinkedHashMap<>(c);
            out.put("_score", totalBd);
            out.put("_levelScore", levelScore);
            out.put("_roleScore", roleScore);
            out.put("_deptScore", deptScore);
            out.put("_loadScore", loadScore);
            out.put("_speedScore", speedScore);
            scored.add(out);
        }

        // 2. 排序取 Top N
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

        StringBuilder suggestion = new StringBuilder();
        suggestion.append("最佳审批人: ").append(nameOf(top.get(0)))
                .append("（综合得分 ").append(top1).append("）");
        if (top.size() > 1) {
            suggestion.append("；次选: ").append(nameOf(top.get(1)));
        }

        log.info("[ApproverRecommend] biz={} top1Score={} level={} candidates={}",
                ctx.getBizRef(), top1, level, candidates.size());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("top", top);
        payload.put("weights", Map.of(
                "level", 0.30, "role", 0.25, "department", 0.20,
                "load", 0.15, "speed", 0.10));
        return new AgentResult(AgentType.APPROVER_RECOMMEND, level, top1,
                BigDecimal.valueOf(0.78), suggestion.toString(), matched, payload);
    }

    // ========== 评分工具方法 ==========

    private static String nameOf(Map<String, Object> m) {
        Object n = m.get("name");
        if (n == null) n = m.get("userId");
        return Objects.toString(n, "?");
    }

    /**
     * 职级匹配度：完全匹配 1，相邻 0.5，相隔 2 0.25，否则 0；无要求时返回 1
     */
    static BigDecimal computeLevelMatch(String actual, String required) {
        if (required == null || required.isBlank()) return BigDecimal.ONE;
        if (actual == null || actual.isBlank()) return BigDecimal.ZERO;
        if (actual.equalsIgnoreCase(required)) return BigDecimal.ONE;
        try {
            int a = Integer.parseInt(actual.toUpperCase().replace("L", ""));
            int r = Integer.parseInt(required.toUpperCase().replace("L", ""));
            int diff = Math.abs(a - r);
            if (diff == 1) return new BigDecimal("0.5");
            if (diff == 2) return new BigDecimal("0.25");
            return BigDecimal.ZERO;
        } catch (Exception ignore) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 字符串匹配：完全匹配 1，包含匹配 0.6，否则 0；无要求时返回 1
     */
    static BigDecimal computeStringMatch(String actual, String required) {
        if (required == null || required.isBlank()) return BigDecimal.ONE;
        if (actual == null || actual.isBlank()) return BigDecimal.ZERO;
        if (actual.equalsIgnoreCase(required)) return BigDecimal.ONE;
        if (actual.toLowerCase().contains(required.toLowerCase())
                || required.toLowerCase().contains(actual.toLowerCase())) {
            return new BigDecimal("0.6");
        }
        return BigDecimal.ZERO;
    }

    /**
     * 负载得分：在手任务数越少得分越高
     * 0 个 = 1.0，1-3 个 = 0.8，4-6 个 = 0.5，7-10 个 = 0.2，超过 10 个 = 0
     */
    static BigDecimal computeLoadScore(int activeTasks) {
        if (activeTasks <= 0) return BigDecimal.ONE;
        if (activeTasks <= 3) return new BigDecimal("0.8");
        if (activeTasks <= 6) return new BigDecimal("0.5");
        if (activeTasks <= 10) return new BigDecimal("0.2");
        return BigDecimal.ZERO;
    }

    /**
     * 速度得分：平均审批耗时越短得分越高
     * 1 小时内 = 1.0，1 天内 = 0.8，3 天内 = 0.5，一周内 = 0.2，超过一周 = 0
     */
    static BigDecimal computeSpeedScore(long avgApprovalMs) {
        if (avgApprovalMs <= 0) return new BigDecimal("0.8"); // 无数据按中等算
        long oneHour = 60L * 60L * 1000L;
        long oneDay = 24L * oneHour;
        long threeDay = 3L * oneDay;
        long oneWeek = 7L * oneDay;
        if (avgApprovalMs <= oneHour) return BigDecimal.ONE;
        if (avgApprovalMs <= oneDay) return new BigDecimal("0.8");
        if (avgApprovalMs <= threeDay) return new BigDecimal("0.5");
        if (avgApprovalMs <= oneWeek) return new BigDecimal("0.2");
        return BigDecimal.ZERO;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(o.toString());
        } catch (Exception ignore) {
            return 0;
        }
    }

    private static long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(o.toString());
        } catch (Exception ignore) {
            return 0L;
        }
    }
}
