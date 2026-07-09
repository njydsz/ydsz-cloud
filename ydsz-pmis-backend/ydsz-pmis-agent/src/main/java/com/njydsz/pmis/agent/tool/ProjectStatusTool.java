package com.njydsz.pmis.agent.tool;

import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.feign.ProjectServiceClient;
import com.njydsz.pmis.common.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 项目指标查询工具（P1-1 落地，P1-5 改造支持真实数据源切换）
 *
 * <p>内置 Agent 工具，供 ReAct 推理循环通过 function-calling 调用，
 * 用于查询项目的挣值管理核心指标（CPI/SPI）以及成本超支率、风险事件数、利润率等。
 *
 * <p>数据源切换：通过 {@code pmis.agent.tool.mock-enabled} 配置控制
 * <ul>
 *   <li>{@code true}（默认）：返回模拟数据，适用于开发/测试环境</li>
 *   <li>{@code false}：调用 {@link #fetchRealData} 获取真实数据，需子类或后续实现覆盖</li>
 * </ul>
 *
 * <p>指标说明：
 * <ul>
 *   <li>CPI（成本绩效指数）= 挣值 EV / 实际成本 AC，&lt; 1 表示成本超支</li>
 *   <li>SPI（进度绩效指数）= 挣值 EV / 计划价值 PV，&lt; 1 表示进度滞后</li>
 *   <li>costOverrunRatio（成本超支率）= (AC - EV) / EV</li>
 *   <li>riskEventCount（风险事件数）= 当前未关闭的风险事件数量</li>
 *   <li>marginRatio（利润率）= (收入 - 成本) / 成本</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-1)
 */
@Slf4j
@Component
public class ProjectStatusTool implements AgentTool {

    /** 工具名称（function-calling 唯一标识） */
    private static final String TOOL_NAME = "project_status";

    /** 参数名：项目 ID */
    private static final String PARAM_PROJECT_ID = "projectId";

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
     * <p>使用 {@code @Autowired} 字段注入，保证单元测试直接 new 实例时该字段为 null。
     */
    @Autowired
    protected ProjectServiceClient projectServiceClient;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "查询项目指标（CPI/SPI/成本超支率/风险事件数）";
    }

    @Override
    public Map<String, Class<?>> parameterSchema() {
        return Map.of(PARAM_PROJECT_ID, String.class);
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, AgentContext ctx) {
        // 从参数中获取项目 ID（允许为空，空时使用上下文 bizRef 兜底）
        String projectId = parameters == null ? null
                : String.valueOf(parameters.get(PARAM_PROJECT_ID));
        if ("null".equals(projectId) || projectId == null || projectId.isBlank()) {
            projectId = ctx != null ? ctx.getBizRef() : null;
        }
        log.info("[ProjectStatusTool] 查询项目指标 projectId={}, traceId={}, mockEnabled={}",
                projectId, ctx != null ? ctx.getTraceId() : null, mockEnabled);

        Map<String, Object> data = mockEnabled ? fetchMockData(projectId) : fetchRealData(projectId, ctx);

        // 生成 LLM 可读的文本输出（作为 ReAct 的 Observation）
        double cpi = (double) data.getOrDefault("cpi", 0.0);
        double spi = (double) data.getOrDefault("spi", 0.0);
        double costOverrun = (double) data.getOrDefault("costOverrunRatio", 0.0);
        int riskCount = (int) data.getOrDefault("riskEventCount", 0);
        double margin = (double) data.getOrDefault("marginRatio", 0.0);

        String output = String.format(
                "项目[%s]指标：CPI=%.2f（成本超支）, SPI=%.2f（进度滞后）, "
                        + "成本超支率=%.0f%%, 风险事件数=%d, 利润率=%.0f%%。",
                projectId, cpi, spi, costOverrun * 100, riskCount, margin * 100);

        return ToolResult.success(output, data);
    }

    /**
     * 获取模拟项目指标数据（开发/测试环境使用）。
     *
     * @param projectId 项目 ID
     * @return 项目指标数据 Map
     */
    protected Map<String, Object> fetchMockData(String projectId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projectId", projectId);
        data.put("cpi", 0.80);
        data.put("spi", 0.90);
        data.put("costOverrunRatio", 0.25);
        data.put("riskEventCount", 5);
        data.put("marginRatio", -0.10);
        return data;
    }

    /**
     * 获取真实项目指标数据（生产环境使用）。
     *
     * <p>通过 Feign 调用 project 模块的两个接口聚合项目指标：
     * <ul>
     *   <li>{@code /execution/evm/dashboard}：获取 CPI / SPI / VAC 等挣值指标</li>
     *   <li>{@code /execution/risk/page}：获取风险事件总数（riskEventCount）</li>
     * </ul>
     *
     * <p>派生指标：
     * <ul>
     *   <li>{@code costOverrunRatio}：由 CPI 派生，= 1/CPI - 1（CPI &gt; 0 时），CPI ≤ 0 时返回 0</li>
     *   <li>{@code marginRatio}：利润率需要收入数据，EVM 仪表盘暂未提供，返回 0</li>
     * </ul>
     *
     * <p>project 服务不可用时返回零值指标，避免 Agent 推理链路中断。
     *
     * @param projectId 项目 ID（对应 project 模块的 initiationId）
     * @param ctx       Agent 上下文（可用于获取 tenantId、traceId 等）
     * @return 项目指标数据 Map
     */
    protected Map<String, Object> fetchRealData(String projectId, AgentContext ctx) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projectId", projectId);
        data.put("cpi", 0.0);
        data.put("spi", 0.0);
        data.put("costOverrunRatio", 0.0);
        data.put("riskEventCount", 0);
        data.put("marginRatio", 0.0);

        if (projectServiceClient == null) {
            log.warn("[ProjectStatusTool] projectServiceClient 未注入，返回零值指标 projectId={}", projectId);
            return data;
        }

        // 1. 调用 EVM 仪表盘获取 CPI / SPI
        try {
            Result<Map<String, Object>> dashResult = projectServiceClient.evmDashboard(projectId);
            if (dashResult != null && dashResult.isSuccess() && dashResult.getData() != null) {
                Map<String, Object> dash = dashResult.getData();
                double cpi = toDouble(dash.get("latestCpi"));
                double spi = toDouble(dash.get("latestSpi"));
                data.put("cpi", cpi);
                data.put("spi", spi);
                // 成本超支率 = 1/CPI - 1（CPI = EV/AC，超支率 = (AC-EV)/EV = 1/CPI - 1）
                data.put("costOverrunRatio", cpi > 0 ? (1.0 / cpi - 1.0) : 0.0);
            } else {
                log.warn("[ProjectStatusTool] EVM 仪表盘调用失败 projectId={}, result={}",
                        projectId, dashResult == null ? "null" : dashResult.getCode());
            }
        } catch (Exception e) {
            log.warn("[ProjectStatusTool] EVM 仪表盘调用异常 projectId={}: {}", projectId, e.getMessage());
        }

        // 2. 调用风险分页获取风险事件总数
        try {
            Result<Map<String, Object>> riskResult = projectServiceClient.riskPage(1, 1, projectId, null);
            if (riskResult != null && riskResult.isSuccess() && riskResult.getData() != null) {
                Object totalObj = riskResult.getData().get("total");
                data.put("riskEventCount", toInt(totalObj));
            } else {
                log.warn("[ProjectStatusTool] 风险分页调用失败 projectId={}, result={}",
                        projectId, riskResult == null ? "null" : riskResult.getCode());
            }
        } catch (Exception e) {
            log.warn("[ProjectStatusTool] 风险分页调用异常 projectId={}: {}", projectId, e.getMessage());
        }

        // marginRatio 需要收入数据，EVM 仪表盘暂未提供，保持 0.0
        return data;
    }

    /**
     * 安全转换 Object 为 double。
     *
     * @param value 原始值
     * @return double 值，null 或无法解析时返回 0.0
     */
    private static double toDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * 安全转换 Object 为 int。
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
}
