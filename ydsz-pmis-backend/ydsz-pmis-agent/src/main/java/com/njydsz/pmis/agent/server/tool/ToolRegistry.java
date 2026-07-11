package com.njydsz.pmis.agent.server.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
     * 注销工具（P2-12 落地）。
     *
     * <p>从注册中心移除指定名称的工具，用于工具市场的动态卸载场景。
     *
     * @param name 工具名称
     * @return 被移除的工具实例（不存在则返回 null）
     */
    public AgentTool unregister(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        AgentTool removed = tools.remove(name);
        if (removed != null) {
            log.info("[ToolRegistry] 工具已注销: {}", name);
        }
        return removed;
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

    /**
     * 生成 OpenAI Function Calling 格式的 tools 数组（P4-2 落地）。
     *
     * <p>格式对标 OpenAI Chat Completions API 的 tools 参数：
     * <pre>
     * [
     *   {
     *     "type": "function",
     *     "function": {
     *       "name": "project_status",
     *       "description": "查询项目指标",
     *       "parameters": { "type":"object","properties":{...},"required":[...] }
     *     }
     *   }
     * ]
     * </pre>
     *
     * @return tools 列表（每项为 Map 形式的 function 定义）；空列表表示无工具
     */
    public List<Map<String, Object>> formatToolsForOpenAi() {
        if (tools.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (AgentTool tool : tools.values()) {
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("type", "function");
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.name());
            function.put("description", tool.description());
            Map<String, Object> schema = tool.jsonSchema();
            if (schema != null) {
                function.put("parameters", schema);
            } else {
                function.put("parameters", Map.of("type", "object", "properties", Map.of()));
            }
            fn.put("function", function);
            result.add(fn);
        }
        return result;
    }

    /**
     * 判断是否有任何已注册工具支持原生 Function Calling（P4-2）。
     *
     * <p>当前所有工具均可通过 JSON Schema 描述参数，因此只要有工具注册即返回 true。
     *
     * @return true 表示存在可用于 Function Calling 的工具
     */
    public boolean hasFunctionCallingTools() {
        return !tools.isEmpty();
    }
}
