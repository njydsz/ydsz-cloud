package com.njydsz.pmis.agent.tool;

import com.njydsz.pmis.agent.engine.AgentContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 项目指标查询工具（P1-1 落地）
 *
 * <p>内置 Agent 工具，供 ReAct 推理循环通过 function-calling 调用，
 * 用于查询项目的挣值管理核心指标（CPI/SPI）以及成本超支率、风险事件数、利润率等。
 *
 * <p>当前版本返回模拟数据，后续接入真实项目指标服务后替换实现即可。
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
        log.info("[ProjectStatusTool] 查询项目指标 projectId={}, traceId={}",
                projectId, ctx != null ? ctx.getTraceId() : null);

        // 组装模拟项目指标数据（后续接入真实项目指标服务后替换）
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projectId", projectId);
        data.put("cpi", 0.80);
        data.put("spi", 0.90);
        data.put("costOverrunRatio", 0.25);
        data.put("riskEventCount", 5);
        data.put("marginRatio", -0.10);

        // 生成 LLM 可读的文本输出（作为 ReAct 的 Observation）
        String output = String.format(
                "项目[%s]指标：CPI=%.2f（成本超支）, SPI=%.2f（进度滞后）, "
                        + "成本超支率=%.0f%%, 风险事件数=%d, 利润率=%.0f%%。",
                projectId,
                0.80, 0.90,
                0.25 * 100,
                5,
                -0.10 * 100);

        return ToolResult.success(output, data);
    }
}
