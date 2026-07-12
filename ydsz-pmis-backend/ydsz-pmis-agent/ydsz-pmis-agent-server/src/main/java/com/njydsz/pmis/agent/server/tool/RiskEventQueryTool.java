package com.njydsz.pmis.agent.server.tool;

import com.njydsz.pmis.agent.server.engine.AgentContext;
import com.njydsz.pmis.project.api.client.ProjectServiceClient;
import com.njydsz.pmis.common.core.response.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 风险事件查询工具（P1-1 落地，P1-5 改造支持真实数据源切换）
 *
 * <p>内置 Agent 工具，用于按严重级别查询项目风险事件列表。
 * 由 {@link ToolRegistry} 自动收集，可被 LLM 通过 function-calling 调用。
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
public class RiskEventQueryTool implements AgentTool {

    /** 默认严重级别：全部 */
    private static final String DEFAULT_SEVERITY = "ALL";

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
     * <p>通过构造器注入，单元测试可直接 {@code new RiskEventQueryTool(mock)}。
     */
    private final ProjectServiceClient projectServiceClient;

    /**
     * 构造风险事件查询工具。
     *
     * @param projectServiceClient 项目 Feign 客户端（mock 模式下允许为 null，会在调用时再判断）
     */
    public RiskEventQueryTool(@Autowired(required = false) ProjectServiceClient projectServiceClient) {
        this.projectServiceClient = projectServiceClient;
    }

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
        Map<String, Object> safeParams = parameters == null ? Map.of() : parameters;
        String projectId = safeParams.get("projectId") == null
                ? null
                : String.valueOf(safeParams.get("projectId"));
        if (projectId == null || projectId.isBlank() || "null".equals(projectId)) {
            projectId = ctx == null ? null : ctx.getBizRef();
        }
        String severity = safeParams.get("severity") == null
                ? DEFAULT_SEVERITY
                : String.valueOf(safeParams.get("severity")).toUpperCase();

        log.info("[risk_events] 查询风险事件: projectId={}, severity={}, traceId={}, mockEnabled={}",
                projectId, severity, ctx == null ? null : ctx.getTraceId(), mockEnabled);

        List<Map<String, Object>> events = mockEnabled
                ? fetchMockData(projectId, severity)
                : fetchRealData(projectId, severity, ctx);

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

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projectId", projectId);
        data.put("severity", severity);
        data.put("total", events.size());
        data.put("events", events);

        return ToolResult.success(output.toString().trim(), data);
    }

    /**
     * 获取模拟风险事件数据（开发/测试环境使用）。
     *
     * @param projectId 项目 ID
     * @param severity  严重级别（HIGH / MEDIUM / LOW / ALL）
     * @return 风险事件列表
     */
    protected List<Map<String, Object>> fetchMockData(String projectId, String severity) {
        List<Map<String, Object>> events = new ArrayList<>();
        if ("HIGH".equals(severity) || "ALL".equals(severity)) {
            events.add(buildEvent("进度延期", "HIGH", "项目关键路径任务延期，可能影响里程碑达成"));
            events.add(buildEvent("成本超支", "HIGH", "项目实际成本超出预算 15%"));
        }
        if ("MEDIUM".equals(severity) || "ALL".equals(severity)) {
            events.add(buildEvent("资源不足", "MEDIUM", "核心研发人员配置不足，影响交付节奏"));
        }
        if ("LOW".equals(severity) || "ALL".equals(severity)) {
            events.add(buildEvent("需求变更", "LOW", "客户提出新增功能需求，需评估范围影响"));
        }
        return events;
    }

    /**
     * 获取真实风险事件数据（生产环境使用）。
     *
     * <p>通过 Feign 调用 project 模块的 {@code /execution/risk/page} 接口，
     * 按项目立项 ID 和风险等级查询风险列表。project 服务不可用时返回空列表。
     *
     * <p>字段映射：project 模块的 {@code riskTitle} → 工具输出 {@code name}，
     * {@code riskLevel} → {@code severity}，{@code description} 保持不变。
     *
     * @param projectId 项目 ID（对应 project 模块的 initiationId）
     * @param severity  严重级别（HIGH / MEDIUM / LOW / ALL）
     * @param ctx       Agent 上下文
     * @return 风险事件列表
     */
    protected List<Map<String, Object>> fetchRealData(String projectId, String severity, AgentContext ctx) {
        if (projectServiceClient == null) {
            log.warn("[RiskEventQueryTool] projectServiceClient 未注入，返回空列表 projectId={}", projectId);
            return List.of();
        }

        // ALL 时不传 riskLevel 过滤参数
        String riskLevelFilter = "ALL".equals(severity) ? null : severity;
        try {
            BaseResponse<Map<String, Object>> result = projectServiceClient.riskPage(1, 100, projectId, riskLevelFilter);
            if (result == null || !BaseResponse.isSuccess() || BaseResponse.getData() == null) {
                log.warn("[RiskEventQueryTool] Feign 调用失败或返回空 projectId={}, result={}",
                        projectId, result == null ? "null" : BaseResponse.getCode());
                return List.of();
            }
            Map<String, Object> pageData = BaseResponse.getData();
            Object recordsObj = pageData.get("records");
            if (!(recordsObj instanceof List<?> rawRecords)) {
                return List.of();
            }

            List<Map<String, Object>> events = new ArrayList<>();
            for (Object record : rawRecords) {
                if (!(record instanceof Map<?, ?> r)) continue;
                Map<String, Object> event = new LinkedHashMap<>();
                // 字段映射：riskTitle → name, riskLevel → severity, description → description
                event.put("name", r.get("riskTitle"));
                event.put("severity", r.get("riskLevel"));
                event.put("description", r.get("description"));
                events.add(event);
            }
            return events;
        } catch (Exception e) {
            log.warn("[RiskEventQueryTool] Feign 调用异常 projectId={}: {}", projectId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 构建单个风险事件数据。
     */
    private Map<String, Object> buildEvent(String name, String severity, String description) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("name", name);
        event.put("severity", severity);
        event.put("description", description);
        return event;
    }
}
