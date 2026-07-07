package com.njydsz.pmis.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心（P1-1 落地）
 *
 * <p>对标 Coze Plugin Store / Dify Tool Manager，统一管理所有 {@link AgentTool}。
 * 通过 Spring 构造注入自动收集所有 {@code @Component} 标注的工具实现。
 *
 * <p>核心职责：
 * <ul>
 *   <li>按 name 索引工具，支持 O(1) 查找</li>
 *   <li>生成 function-calling prompt（展示给 LLM 的工具清单）</li>
 *   <li>支持运行时动态注册（测试 / 扩展场景）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-1)
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new ConcurrentHashMap<>();

    /**
     * Spring 构造注入：自动收集所有 {@link AgentTool} Bean。
     *
     * @param agentTools Spring 容器中所有 AgentTool 实现（可为空列表）
     */
    public ToolRegistry(List<AgentTool> agentTools) {
        if (agentTools != null) {
            for (AgentTool tool : agentTools) {
                register(tool);
            }
        }
        log.info("[ToolRegistry] 已注册工具: {}", listToolNames());
    }

    /**
     * 注册工具。若同名工具已存在，覆盖旧值并记录警告。
     *
     * @param tool 工具实例
     */
    public void register(AgentTool tool) {
        if (tool == null || tool.name() == null || tool.name().isBlank()) {
            log.warn("[ToolRegistry] 跳过无效工具: {}", tool);
            return;
        }
        AgentTool prev = tools.put(tool.name(), tool);
        if (prev != null) {
            log.warn("[ToolRegistry] 工具 {} 已存在, 旧实现将被覆盖", tool.name());
        }
    }

    /**
     * 按名称查找工具。
     *
     * @param name 工具名称
     * @return 工具实例（可能为空）
     */
    public Optional<AgentTool> getTool(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * 列出所有已注册工具。
     *
     * @return 不可变工具列表
     */
    public List<AgentTool> listTools() {
        return Collections.unmodifiableList(new ArrayList<>(tools.values()));
    }

    /**
     * 列出所有工具名称。
     *
     * @return 工具名称列表
     */
    public List<String> listToolNames() {
        return new ArrayList<>(tools.keySet());
    }

    /**
     * 生成 function-calling prompt 片段，展示给 LLM。
     *
     * <p>格式示例：
     * <pre>
     * 可用工具:
     * 1. project_status: 查询项目指标（CPI/SPI/成本超支率）
     *    参数: projectId(String)
     * 2. risk_events: 查询项目风险事件列表
     *    参数: projectId(String), severity(String)
     * </pre>
     *
     * @return 工具清单文本
     */
    public String formatToolsForPrompt() {
        if (tools.isEmpty()) {
            return "无可用工具";
        }
        StringBuilder sb = new StringBuilder("可用工具:\n");
        int idx = 1;
        for (AgentTool tool : tools.values()) {
            sb.append(idx++).append(". ").append(tool.name())
                    .append(": ").append(tool.description()).append("\n");
            Map<String, Class<?>> schema = tool.parameterSchema();
            if (schema != null && !schema.isEmpty()) {
                sb.append("   参数: ");
                schema.forEach((paramName, paramType) ->
                        sb.append(paramName).append("(").append(simplifyTypeName(paramType)).append(") "));
                sb.append("\n");
            }
        }
        return sb.toString().trim();
    }

    /**
     * 简化类型名称用于 prompt 展示。
     *
     * @param type Java 类型
     * @return 简化名称（如 String / Integer / List）
     */
    private static String simplifyTypeName(Class<?> type) {
        if (type == null) return "Object";
        String name = type.getSimpleName();
        return name;
    }
}
