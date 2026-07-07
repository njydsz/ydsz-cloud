package com.njydsz.pmis.agent.tool;

import com.njydsz.pmis.agent.engine.AgentContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工时异常统计工具（P1-1 落地，P1-5 改造支持真实数据源切换）
 *
 * <p>查询指定项目在指定月份的工时异常情况，包括加班超时、漏报、异常打卡等，
 * 为项目周报 / 月报 / 风险预警等 Agent 推理场景提供数据支撑。
 *
 * <p>数据源切换：通过 {@code pmis.agent.tool.mock-enabled} 配置控制
 * <ul>
 *   <li>{@code true}（默认）：返回模拟数据，适用于开发/测试环境</li>
 *   <li>{@code false}：调用 {@link #fetchRealData} 获取真实数据，需子类或后续实现覆盖</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-1)
 */
@Slf4j
@Component
public class TimesheetStatTool implements AgentTool {

    /** 月份格式（yyyy-MM） */
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * 是否使用模拟数据（true=模拟，false=真实数据源）。
     *
     * <p>字段默认值为 {@code true}，保证在以下场景都安全降级到 mock 数据：
     * <ul>
     *   <li>单元测试直接 new 实例（无 Spring 容器，@Value 不生效）</li>
     *   <li>配置文件未配置该项（@Value 默认值也是 true）</li>
     * </ul>
     * Spring 环境下 {@code @Value} 注入的值会覆盖此默认值。
     */
    @Value("${pmis.agent.tool.mock-enabled:true}")
    protected boolean mockEnabled = true;

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
        String projectId = parameters == null ? null
                : (parameters.get("projectId") == null ? null : String.valueOf(parameters.get("projectId")));
        String month = resolveMonth(parameters);

        String traceId = ctx == null ? null : ctx.getTraceId();
        log.info("[timesheet_stat] 执行工时异常统计查询 projectId={}, month={}, traceId={}, mockEnabled={}",
                projectId, month, traceId, mockEnabled);

        Map<String, Object> data = mockEnabled
                ? fetchMockData(projectId, month)
                : fetchRealData(projectId, month, ctx);

        String output = String.format(
                "项目[%s] %s 工时异常统计：加班超时 %d 次、漏报 %d 次、异常打卡 %d 次，总工时 %d 小时。",
                projectId, month,
                data.get("overtimeCount"), data.get("missingCount"),
                data.get("abnormalCount"), data.get("totalHours"));

        return ToolResult.success(output, data);
    }

    /**
     * 获取模拟工时统计数据（开发/测试环境使用）。
     *
     * @param projectId 项目 ID
     * @param month     月份（yyyy-MM）
     * @return 工时统计数据 Map
     */
    protected Map<String, Object> fetchMockData(String projectId, String month) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projectId", projectId);
        data.put("month", month);
        data.put("overtimeCount", 8);
        data.put("missingCount", 3);
        data.put("abnormalCount", 2);
        data.put("totalHours", 320);
        return data;
    }

    /**
     * 获取真实工时统计数据（生产环境使用）。
     *
     * <p>当前为占位实现，后续接入真实工时数据源后覆盖此方法。
     * 建议通过 Feign 调用 execution 模块的工时查询接口。
     *
     * @param projectId 项目 ID
     * @param month     月份（yyyy-MM）
     * @param ctx       Agent 上下文
     * @return 工时统计数据 Map
     * @throws UnsupportedOperationException 当未实现真实数据源时抛出
     */
    protected Map<String, Object> fetchRealData(String projectId, String month, AgentContext ctx) {
        // TODO P1-5: 接入真实工时数据源（execution 模块 Feign 调用）
        throw new UnsupportedOperationException(
                "TimesheetStatTool 真实数据源未实现，请配置 pmis.agent.tool.mock-enabled=true 或实现 fetchRealData 方法");
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
