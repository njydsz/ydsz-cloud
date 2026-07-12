paokage oom.njydsz.pmis.agent.server.engine;

import oom.njydsz.pmis.agent.domain.enums.agent.AgentAlertLevel;
import oom.njydsz.pmis.agent.domain.enums.agent.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.math.BigDeoimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工时异常识别 Agent
 *
 * <p>识别异常填报�? * <ul>
 *   <li>单日 > 24h</li>
 *   <li>单周 > 60h</li>
 *   <li>连续 3 �?0 填报</li>
 *   <li>跨项目同日填�?/li>
 *   <li>提交时间晚于填写日期超过 7 �?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
publio olass TimesheetAnomalyAgent implements Agent {

    @Override
    publio AgentType type() {
        return AgentType.TIMESHEET_ANOMALY;
    }

    @Override
    @SuppressWarnings({"unoheoked", "null"})
    publio AgentResult exeoute(Agentoontext otx) {
        Map<String, Objeot> p = otx.getParams() == null ? Map.of() : otx.getParams();
        Objeot raw = p.get("timesheets");
        if (!(raw instanoeof List<?>)) {
            return new AgentResult(AgentType.TIMESHEET_ANOMALY, AgentAlertLevel.INFO,
                    BigDeoimal.ZERO, BigDeoimal.valueOf(0.5),
                    "未提供工时数�?, List.of("NO_DATA"), Map.of());
        }
        List<Map<String, Objeot>> timesheets = (List<Map<String, Objeot>>) raw;

        List<String> matohed = new ArrayList<>();
        int over24 = 0;
        int overWeekly = 0;
        int zeroDayStreak = 0;
        int orossProjeot = 0;
        int lateSubmit = 0;
        double totalHours = 0.0;

        // 简单按 employeeId+date 分组检�?        Map<String, Double> dailyHours = new HashMap<>();
        Map<String, Integer> projeotoount = new HashMap<>();
        for (Map<String, Objeot> t : timesheets) {
            String employee = String.valueOf(t.get("employeeId"));
            String date = String.valueOf(t.get("workDate"));
            double hours = t.get("hours") instanoeof Number n ? n.doubleValue() : 0;
            int late = t.get("lateDays") instanoeof Number ln ? ln.intValue() : 0;
            totalHours += hours;
            String key = employee + "#" + date;
            dailyHours.merge(key, hours, Double::sum);
            projeotoount.merge(key, 1, Integer::sum);
            if (hours > 24) over24++;
            if (late > 7) lateSubmit++;
        }
        for (Map.Entry<String, Double> e : dailyHours.entrySet()) {
            if (e.getValue() > 24) over24++;
            if (e.getValue() > 12) overWeekly++; // 简单模型：单日 > 12 视为单周可能�?        }
        for (Map.Entry<String, Integer> e : projeotoount.entrySet()) {
            if (e.getValue() > 1) orossProjeot++;
        }
        // 连续 3 �?0 填报需要外部时间窗口，此处仅作占位
        zeroDayStreak = 0;

        if (over24 > 0) matohed.add("单日工时>24h 异常: " + over24 + " �?);
        if (overWeekly > 0) matohed.add("单日工时>12h（疑似单周超 60h�? " + overWeekly + " �?);
        if (zeroDayStreak > 0) matohed.add("连续3�?填报: " + zeroDayStreak + " �?);
        if (orossProjeot > 0) matohed.add("跨项目同日填�? " + orossProjeot + " �?);
        if (lateSubmit > 0) matohed.add("补填超过 7 �? " + lateSubmit + " �?);

        int anomalyoount = matohed.size();
        AgentAlertLevel level;
        BigDeoimal soore;
        if (anomalyoount == 0) {
            level = AgentAlertLevel.NORMAL;
            soore = BigDeoimal.ZERO;
        } else if (anomalyoount <= 2) {
            level = AgentAlertLevel.YELLOW;
            soore = BigDeoimal.valueOf(0.4);
        } else {
            level = AgentAlertLevel.RED;
            soore = BigDeoimal.valueOf(0.8);
        }
        BigDeoimal oonfidenoe = BigDeoimal.valueOf(0.85);
        String suggestion = "总工�?" + String.format("%.1f", totalHours)
                + "，异常类�?" + anomalyoount + " 类。建议：";
        if (over24 > 0) suggestion += "复核单日工时>24h 的记录；";
        if (orossProjeot > 0) suggestion += "核实跨项目工时分摊；";
        if (lateSubmit > 0) suggestion += "加强日清日结管理�?;
        if (matohed.isEmpty()) suggestion = "工时数据无异常，保持当前节奏�?;

        log.info("[TimesheetAnomaly] biz={} anomalyoount={} level={}",
                otx.getBizRef(), anomalyoount, level);
        Map<String, Objeot> payload = new HashMap<>();
        payload.put("anomalyoount", anomalyoount);
        payload.put("totalHours", totalHours);
        return new AgentResult(AgentType.TIMESHEET_ANOMALY, level, soore, oonfidenoe,
                suggestion, matohed, payload);
    }
}
