package com.njydsz.pmis.agent.server.tool;

import com.njydsz.pmis.agent.server.engine.AgentContext;
import com.njydsz.pmis.common.feign.ProjectServiceClient;
import com.njydsz.pmis.common.api.Result;
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
     * 构造器注入 {@link ProjectServiceClient}，由 Spring 容器调用。
     *
     * <p>显式声明构造器（模块未引入 Lombok），
     * 单元测试可直接 {@code new TimesheetStatTool(mock)} 注入。
     *
     * @param projectServiceClient 项目执行模块 Feign 客户端
     */
    public TimesheetStatTool(ProjectServiceClient projectServiceClient) {
        this.projectServiceClient = projectServiceClient;
    }

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

    /**
     * 项目执行模块 Feign 客户端（真实数据源模式使用）。
     *
     * <p>通过构造器注入，单元测试可直接 {@code new TimesheetStatTool(mock)}。
     */
    private final ProjectServiceClient projectServiceClient;

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
     * <p>通过 Feign 调用 project 模块的 {@code /execution/time-entry/abnormal-stat} 接口，
     * 获取指定项目在指定月份的工时异常统计（加班超时 / 漏报 / 异常 / 总工时）。
     * project 服务不可用时返回零值统计，避免 Agent 推理链路中断。
     *
     * @param projectId 项目 ID（对应 project 模块的 initiationId）
     * @param month     月份（yyyy-MM）
     * @param ctx       Agent 上下文
     * @return 工时统计数据 Map
     */
    protected Map<String, Object> fetchRealData(String projectId, String month, AgentContext ctx) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projectId", projectId);
        data.put("month", month);
        data.put("overtimeCount", 0);
        data.put("missingCount", 0);
        data.put("abnormalCount", 0);
        data.put("totalHours", 0);

        if (projectServiceClient == null) {
            log.warn("[TimesheetStatTool] projectServiceClient 未注入，返回零值统计 projectId={}", projectId);
            return data;
        }

        try {
            Result<Map<String, Object>> result = projectServiceClient.timeEntryAbnormalStat(projectId, month);
            if (result == null || !result.isSuccess() || result.getData() == null) {
                log.warn("[TimesheetStatTool] Feign 调用失败或返回空 projectId={}, result={}",
                        projectId, result == null ? "null" : result.getCode());
                return data;
            }
            Map<String, Object> remote = result.getData();
            data.put("overtimeCount", toInt(remote.get("overtimeCount")));
            data.put("missingCount", toInt(remote.get("missingCount")));
            data.put("abnormalCount", toInt(remote.get("abnormalCount")));
            data.put("totalHours", toInt(remote.get("totalHours")));
        } catch (Exception e) {
            log.warn("[TimesheetStatTool] Feign 调用异常 projectId={}: {}", projectId, e.getMessage());
        }
        return data;
    }

    /**
     * 安全转换 Object 为 int，处理 Number / String / null 等类型。
     *
     * @param value 原始值
     * @return int 值，null 或无法解析时返回 0
     */
    private static int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
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
