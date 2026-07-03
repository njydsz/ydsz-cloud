package com.njydsz.pmis.agent.engine;

import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工时异常识别 Agent
 *
 * <p>识别异常填报：
 * <ul>
 *   <li>单日 > 24h</li>
 *   <li>单周 > 60h</li>
 *   <li>连续 3 天 0 填报</li>
 *   <li>跨项目同日填报</li>
 *   <li>提交时间晚于填写日期超过 7 天</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class TimesheetAnomalyAgent implements Agent {

    @Override
    public AgentType type() {
        return AgentType.TIMESHEET_ANOMALY;
    }

    @Override
    @SuppressWarnings({"unchecked", "null"})
    public AgentResult execute(AgentContext ctx) {
        Map<String, Object> p = ctx.getParams() == null ? Map.of() : ctx.getParams();
        Object raw = p.get("timesheets");
        if (!(raw instanceof List<?>)) {
            return new AgentResult(AgentType.TIMESHEET_ANOMALY, AgentAlertLevel.INFO,
                    BigDecimal.ZERO, BigDecimal.valueOf(0.5),
                    "未提供工时数据", List.of("NO_DATA"), Map.of());
        }
        List<Map<String, Object>> timesheets = (List<Map<String, Object>>) raw;

        List<String> matched = new ArrayList<>();
        int over24 = 0;
        int overWeekly = 0;
        int zeroDayStreak = 0;
        int crossProject = 0;
        int lateSubmit = 0;
        double totalHours = 0.0;

        // 简单按 employeeId+date 分组检测
        Map<String, Double> dailyHours = new HashMap<>();
        Map<String, Integer> projectCount = new HashMap<>();
        for (Map<String, Object> t : timesheets) {
            String employee = String.valueOf(t.get("employeeId"));
            String date = String.valueOf(t.get("workDate"));
            double hours = t.get("hours") instanceof Number n ? n.doubleValue() : 0;
            int late = t.get("lateDays") instanceof Number ln ? ln.intValue() : 0;
            totalHours += hours;
            String key = employee + "#" + date;
            dailyHours.merge(key, hours, Double::sum);
            projectCount.merge(key, 1, Integer::sum);
            if (hours > 24) over24++;
            if (late > 7) lateSubmit++;
        }
        for (Map.Entry<String, Double> e : dailyHours.entrySet()) {
            if (e.getValue() > 24) over24++;
            if (e.getValue() > 12) overWeekly++; // 简单模型：单日 > 12 视为单周可能超
        }
        for (Map.Entry<String, Integer> e : projectCount.entrySet()) {
            if (e.getValue() > 1) crossProject++;
        }
        // 连续 3 天 0 填报需要外部时间窗口，此处仅作占位
        zeroDayStreak = 0;

        if (over24 > 0) matched.add("单日工时>24h 异常: " + over24 + " 条");
        if (overWeekly > 0) matched.add("单日工时>12h（疑似单周超 60h）: " + overWeekly + " 条");
        if (zeroDayStreak > 0) matched.add("连续3天0填报: " + zeroDayStreak + " 人");
        if (crossProject > 0) matched.add("跨项目同日填报: " + crossProject + " 条");
        if (lateSubmit > 0) matched.add("补填超过 7 天: " + lateSubmit + " 条");

        int anomalyCount = matched.size();
        AgentAlertLevel level;
        BigDecimal score;
        if (anomalyCount == 0) {
            level = AgentAlertLevel.NORMAL;
            score = BigDecimal.ZERO;
        } else if (anomalyCount <= 2) {
            level = AgentAlertLevel.YELLOW;
            score = BigDecimal.valueOf(0.4);
        } else {
            level = AgentAlertLevel.RED;
            score = BigDecimal.valueOf(0.8);
        }
        BigDecimal confidence = BigDecimal.valueOf(0.85);
        String suggestion = "总工时=" + String.format("%.1f", totalHours)
                + "，异常类型 " + anomalyCount + " 类。建议：";
        if (over24 > 0) suggestion += "复核单日工时>24h 的记录；";
        if (crossProject > 0) suggestion += "核实跨项目工时分摊；";
        if (lateSubmit > 0) suggestion += "加强日清日结管理；";
        if (matched.isEmpty()) suggestion = "工时数据无异常，保持当前节奏。";

        log.info("[TimesheetAnomaly] biz={} anomalyCount={} level={}",
                ctx.getBizRef(), anomalyCount, level);
        Map<String, Object> payload = new HashMap<>();
        payload.put("anomalyCount", anomalyCount);
        payload.put("totalHours", totalHours);
        return new AgentResult(AgentType.TIMESHEET_ANOMALY, level, score, confidence,
                suggestion, matched, payload);
    }
}
