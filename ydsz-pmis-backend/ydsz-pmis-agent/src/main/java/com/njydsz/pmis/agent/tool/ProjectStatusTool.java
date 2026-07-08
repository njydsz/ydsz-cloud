package com.njydsz.pmis.agent.tool;

import com.njydsz.pmis.agent.engine.AgentContext;
import lombok.extern.slf4j.Slf4j;
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
     * <p>当前为占位实现，后续接入真实项目指标服务（如 EVM 模块的 Feign 客户端）后覆盖此方法。
     * 建议通过 Feign 调用 execution 模块的 EVM 看板接口获取 CPI/SPI/成本超支率等指标。
     *
     * @param projectId 项目 ID
     * @param ctx       Agent 上下文（可用于获取 tenantId、traceId 等）
     * @return 项目指标数据 Map
     * @throws UnsupportedOperationException 当未实现真实数据源时抛出
     */
    protected Map<String, Object> fetchRealData(String projectId, AgentContext ctx) {
        // TO_DO P1-5: 接入真实项目指标服务（EVM 看板 Feign 调用）
        throw new UnsupportedOperationException(
                "ProjectStatusTool 真实数据源未实现，请配置 pmis.agent.tool.mock-enabled=true 或实现 fetchRealData 方法");
    }
}
