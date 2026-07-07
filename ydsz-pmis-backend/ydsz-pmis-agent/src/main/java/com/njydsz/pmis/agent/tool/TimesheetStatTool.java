package com.njydsz.pmis.agent.tool;

import com.njydsz.pmis.agent.engine.AgentContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工时异常统计工具（P1-1 落地）
 *
 * <p>查询指定项目在指定月份的工时异常情况，包括加班超时、漏报、异常打卡等，
 * 为项目周报 / 月报 / 风险预警等 Agent 推理场景提供数据支撑。
 *
 * <p>当前版本返回模拟数据，后续将对接工时模块真实查询接口。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-1)
 */
@Slf4j
@Component
public class TimesheetStatTool implements AgentTool {

    /** 月份格式（yyyy-MM） */
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @Override
    public String name() {
        return "timesheet_stat";
    }

    @Override
    public String description() {
        return "查询工时异常统计（超时/漏报/异常加班）";
    }

    @Override
    public Map<String, Class<?>> parameterSchema() {
        return Map.of("projectId", String.class, "month", String.class);
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, AgentContext ctx) {
        // 解析项目 ID
        String projectId = parameters == null ? null
                : (parameters.get("projectId") == null ? null : String.valueOf(parameters.get("projectId")));

        // 解析月份（可选，默认当前月）
        String month = resolveMonth(parameters);

        String traceId = ctx == null ? null : ctx.getTraceId();
        log.info("[timesheet_stat] 执行工时异常统计查询 projectId={}, month={}, traceId={}",
                projectId, month, traceId);

        // 模拟工时统计数据（后续替换为真实查询）
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projectId", projectId);
        data.put("month", month);
        data.put("overtimeCount", 8);   // 加班超时次数
        data.put("missingCount", 3);    // 漏报次数
        data.put("abnormalCount", 2);   // 异常打卡次数
        data.put("totalHours", 320);    // 总工时

        // 生成 LLM 可读的文本输出
        String output = String.format(
                "项目[%s] %s 工时异常统计：加班超时 %d 次、漏报 %d 次、异常打卡 %d 次，总工时 %d 小时。",
                projectId, month,
                data.get("overtimeCount"), data.get("missingCount"),
                data.get("abnormalCount"), data.get("totalHours"));

        return ToolResult.success(output, data);
    }

    /**
     * 解析月份参数。
     *
     * @param parameters 输入参数
     * @return 月份字符串（yyyy-MM），未指定时返回当前月
     */
    private String resolveMonth(Map<String, Object> parameters) {
        if (parameters == null) {
            return LocalDate.now().format(MONTH_FORMATTER);
        }
        Object monthObj = parameters.get("month");
        if (monthObj == null || String.valueOf(monthObj).isBlank()) {
            return LocalDate.now().format(MONTH_FORMATTER);
        }
        return String.valueOf(monthObj);
    }
}
