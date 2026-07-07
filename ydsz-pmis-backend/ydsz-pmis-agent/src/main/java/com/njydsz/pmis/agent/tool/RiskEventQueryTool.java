package com.njydsz.pmis.agent.tool;

import com.njydsz.pmis.agent.engine.AgentContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 风险事件查询工具（P1-1 落地）
 *
 * <p>内置 Agent 工具，用于按严重级别查询项目风险事件列表。
 * 由 {@link ToolRegistry} 自动收集，可被 LLM 通过 function-calling 调用。
 *
 * <p>当前为模拟数据实现，后续将接入真实数据源。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-1)
 */
@Slf4j
@Component
public class RiskEventQueryTool implements AgentTool {

    /** 默认严重级别：全部 */
    private static final String DEFAULT_SEVERITY = "ALL";

    @Override
    public String name() {
        return "risk_events";
    }

    @Override
    public String description() {
        return "查询项目风险事件列表（按严重级别筛选）";
    }

    @Override
    public Map<String, Class<?>> parameterSchema() {
        return Map.of(
                "projectId", String.class,
                "severity", String.class
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, AgentContext ctx) {
        // 解析参数：projectId 必填，severity 可选（默认 ALL）
        String projectId = parameters.get("projectId") == null
                ? null
                : String.valueOf(parameters.get("projectId"));
        String severity = parameters.get("severity") == null
                ? DEFAULT_SEVERITY
                : String.valueOf(parameters.get("severity")).toUpperCase();

        log.info("[risk_events] 查询风险事件: projectId={}, severity={}, traceId={}",
                projectId, severity, ctx == null ? null : ctx.getTraceId());

        // 按严重级别收集风险事件（模拟数据）
        List<Map<String, Object>> events = new ArrayList<>();

        // 高风险事件：进度延期、成本超支
        if ("HIGH".equals(severity) || "ALL".equals(severity)) {
            events.add(buildEvent("进度延期", "HIGH", "项目关键路径任务延期，可能影响里程碑达成"));
            events.add(buildEvent("成本超支", "HIGH", "项目实际成本超出预算 15%"));
        }

        // 中风险事件：资源不足
        if ("MEDIUM".equals(severity) || "ALL".equals(severity)) {
            events.add(buildEvent("资源不足", "MEDIUM", "核心研发人员配置不足，影响交付节奏"));
        }

        // 低风险事件：需求变更
        if ("LOW".equals(severity) || "ALL".equals(severity)) {
            events.add(buildEvent("需求变更", "LOW", "客户提出新增功能需求，需评估范围影响"));
        }

        // 构建文本输出（LLM 可读的观察结果）
        StringBuilder output = new StringBuilder();
        output.append("项目 ").append(projectId)
                .append(" 风险事件列表（severity=").append(severity).append("）:\n");
        if (events.isEmpty()) {
            output.append("  无匹配风险事件");
        } else {
            for (int i = 0; i < events.size(); i++) {
                Map<String, Object> e = events.get(i);
                output.append(i + 1).append(". [").append(e.get("severity")).append("] ")
                        .append(e.get("name")).append(" - ").append(e.get("description")).append("\n");
            }
        }

        // 构建结构化数据
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projectId", projectId);
        data.put("severity", severity);
        data.put("total", events.size());
        data.put("events", events);

        return ToolResult.success(output.toString().trim(), data);
    }

    /**
     * 构建单个风险事件数据。
     *
     * @param name        事件名称
     * @param severity    严重级别（HIGH / MEDIUM / LOW）
     * @param description 事件描述
     * @return 风险事件数据 Map
     */
    private Map<String, Object> buildEvent(String name, String severity, String description) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("name", name);
        event.put("severity", severity);
        event.put("description", description);
        return event;
    }
}
