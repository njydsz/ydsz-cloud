package com.njydsz.pmis.agent.engine;

import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工时异常识别 Agent 测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("TimesheetAnomalyAgent 工时异常")
class TimesheetAnomalyAgentTest {

    private final TimesheetAnomalyAgent agent = new TimesheetAnomalyAgent();

    @Test
    @DisplayName("类型-TIMESHEET_ANOMALY")
    void type() {
        assertThat(agent.type()).isEqualTo(AgentType.TIMESHEET_ANOMALY);
    }

    @Test
    @DisplayName("空参数 提示")
    void empty() {
        AgentContext ctx = new AgentContext();
        ctx.setParams(new HashMap<>());
        AgentResult r = agent.execute(ctx);
        assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.INFO);
        assertThat(r.getSuggestion()).contains("未提供");
    }

    @Test
    @DisplayName("正常工时")
    void normal() {
        List<Map<String, Object>> timesheets = new ArrayList<>();
        timesheets.add(entry(1L, 100L, "2026-06-01", 8, 0));
        timesheets.add(entry(1L, 100L, "2026-06-02", 8, 0));
        timesheets.add(entry(1L, 100L, "2026-06-03", 8, 0));
        Map<String, Object> p = new HashMap<>();
        p.put("timesheets", timesheets);
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.NORMAL);
    }

    @Test
    @DisplayName("单日 > 24h 红色")
    void over24h() {
        List<Map<String, Object>> timesheets = new ArrayList<>();
        // 多个异常：>24h + 跨项目 + 补填
        timesheets.add(entry(1L, 100L, "2026-06-01", 30, 0));
        timesheets.add(entry(1L, 200L, "2026-06-01", 4, 0));
        timesheets.add(entry(1L, 100L, "2026-06-02", 8, 10));
        Map<String, Object> p = new HashMap<>();
        p.put("timesheets", timesheets);
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        // 多种异常类型 -> RED
        assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.RED);
        assertThat(r.getMatchedRules()).anyMatch(s -> s.contains("24h"));
    }

    @Test
    @DisplayName("跨项目同日填报")
    void crossProject() {
        List<Map<String, Object>> timesheets = new ArrayList<>();
        timesheets.add(entry(1L, 100L, "2026-06-01", 4, 0));
        timesheets.add(entry(1L, 200L, "2026-06-01", 4, 0));
        Map<String, Object> p = new HashMap<>();
        p.put("timesheets", timesheets);
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        assertThat(r.getMatchedRules()).anyMatch(s -> s.contains("跨项目"));
    }

    @Test
    @DisplayName("补填 > 7 天")
    void lateSubmit() {
        List<Map<String, Object>> timesheets = new ArrayList<>();
        timesheets.add(entry(1L, 100L, "2026-05-01", 8, 10));
        Map<String, Object> p = new HashMap<>();
        p.put("timesheets", timesheets);
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        assertThat(r.getMatchedRules()).anyMatch(s -> s.contains("补填"));
    }

    @Test
    @DisplayName("多条异常 红色")
    void multipleAnomalies() {
        List<Map<String, Object>> timesheets = new ArrayList<>();
        timesheets.add(entry(1L, 100L, "2026-06-01", 30, 10));
        timesheets.add(entry(1L, 200L, "2026-06-01", 8, 0));
        Map<String, Object> p = new HashMap<>();
        p.put("timesheets", timesheets);
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.RED);
    }

    @Test
    @DisplayName("payload 包含 totalHours")
    void payloadTotalHours() {
        List<Map<String, Object>> timesheets = new ArrayList<>();
        timesheets.add(entry(1L, 100L, "2026-06-01", 8, 0));
        timesheets.add(entry(1L, 100L, "2026-06-02", 8, 0));
        Map<String, Object> p = new HashMap<>();
        p.put("timesheets", timesheets);
        AgentContext ctx = new AgentContext();
        ctx.setParams(p);
        AgentResult r = agent.execute(ctx);
        assertThat(r.getPayload()).containsKey("totalHours");
        assertThat(((Number) r.getPayload().get("totalHours")).doubleValue()).isEqualTo(16.0);
    }

    private Map<String, Object> entry(Long empId, Long projId, String date, int hours, int lateDays) {
        Map<String, Object> t = new HashMap<>();
        t.put("employeeId", empId);
        t.put("projectId", projId);
        t.put("workDate", date);
        t.put("hours", hours);
        t.put("lateDays", lateDays);
        return t;
    }
}
