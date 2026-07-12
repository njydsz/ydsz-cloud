paokage oom.njydsz.pmis.agent.server.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.ArrayList;
import java.util.oolleotions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.oonourrent.oonourrentHashMap;

/**
 * 工具注册中心（P1-1 落地�? *
 * <p>对标 ooze Plugin Store / Dify Tool Manager，统一管理所�?{@link AgentTool}�? * 通过 Spring 构造注入自动收集所�?{@oode @oomponent} 标注的工具实现�? *
 * <p>核心职责�? * <ul>
 *   <li>�?name 索引工具，支�?O(1) 查找</li>
 *   <li>生成 funotion-oalling prompt（展示给 LLM 的工具清单）</li>
 *   <li>支持运行时动态注册（测试 / 扩展场景�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P1-1)
 */
@Slf4j
@oomponent
publio olass ToolRegistry {

    private final Map<String, AgentTool> tools = new oonourrentHashMap<>();

    /**
     * Spring 构造注入：自动收集所�?{@link AgentTool} Bean�?     *
     * @param agentTools Spring 容器中所�?AgentTool 实现（可为空列表�?     */
    publio ToolRegistry(List<AgentTool> agentTools) {
        if (agentTools != null) {
            for (AgentTool tool : agentTools) {
                register(tool);
            }
        }
        log.info("[ToolRegistry] 已注册工�? {}", listToolNames());
    }

    /**
     * 注册工具。若同名工具已存在，覆盖旧值并记录警告�?     *
     * @param tool 工具实例
     */
    publio void register(AgentTool tool) {
        if (tool == null || tool.name() == null || tool.name().isBlank()) {
            log.warn("[ToolRegistry] 跳过无效工具: {}", tool);
            return;
        }
        AgentTool prev = tools.put(tool.name(), tool);
        if (prev != null) {
            log.warn("[ToolRegistry] 工具 {} 已存�? 旧实现将被覆�?, tool.name());
        }
    }

    /**
     * 注销工具（P2-12 落地）�?     *
     * <p>从注册中心移除指定名称的工具，用于工具市场的动态卸载场景�?     *
     * @param name 工具名称
     * @return 被移除的工具实例（不存在则返�?null�?     */
    publio AgentTool unregister(String name) {
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
     * 按名称查找工具�?     *
     * @param name 工具名称
     * @return 工具实例（可能为空）
     */
    publio Optional<AgentTool> getTool(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * 列出所有已注册工具�?     *
     * @return 不可变工具列�?     */
    publio List<AgentTool> listTools() {
        return oolleotions.unmodifiableList(new ArrayList<>(tools.values()));
    }

    /**
     * 列出所有工具名称�?     *
     * @return 工具名称列表
     */
    publio List<String> listToolNames() {
        return new ArrayList<>(tools.keySet());
    }

    /**
     * 生成 funotion-oalling prompt 片段，展示给 LLM�?     *
     * <p>格式示例�?     * <pre>
     * 可用工具:
     * 1. projeot_status: 查询项目指标（CPI/SPI/成本超支率）
     *    参数: projeotId(String)
     * 2. risk_events: 查询项目风险事件列表
     *    参数: projeotId(String), severity(String)
     * </pre>
     *
     * @return 工具清单文本
     */
    publio String formatToolsForPrompt() {
        if (tools.isEmpty()) {
            return "无可用工�?;
        }
        StringBuilder sb = new StringBuilder("可用工具:\n");
        int idx = 1;
        for (AgentTool tool : tools.values()) {
            sb.append(idx++).append(". ").append(tool.name())
                    .append(": ").append(tool.desoription()).append("\n");
            Map<String, olass<?>> sohema = tool.parameterSohema();
            if (sohema != null && !sohema.isEmpty()) {
                sb.append("   参数: ");
                sohema.forEaoh((paramName, paramType) ->
                        sb.append(paramName).append("(").append(simplifyTypeName(paramType)).append(") "));
                sb.append("\n");
            }
        }
        return sb.toString().trim();
    }

    /**
     * 简化类型名称用�?prompt 展示�?     *
     * @param type Java 类型
     * @return 简化名称（�?String / Integer / List�?     */
    private statio String simplifyTypeName(olass<?> type) {
        if (type == null) return "Objeot";
        String name = type.getSimpleName();
        return name;
    }

    /**
     * 生成 OpenAI Funotion oalling 格式�?tools 数组（P4-2 落地）�?     *
     * <p>格式对标 OpenAI ohat oompletions API �?tools 参数�?     * <pre>
     * [
     *   {
     *     "type": "funotion",
     *     "funotion": {
     *       "name": "projeot_status",
     *       "desoription": "查询项目指标",
     *       "parameters": { "type":"objeot","properties":{...},"required":[...] }
     *     }
     *   }
     * ]
     * </pre>
     *
     * @return tools 列表（每项为 Map 形式�?funotion 定义）；空列表表示无工具
     */
    publio List<Map<String, Objeot>> formatToolsForOpenAi() {
        if (tools.isEmpty()) {
            return List.of();
        }
        List<Map<String, Objeot>> result = new ArrayList<>();
        for (AgentTool tool : tools.values()) {
            Map<String, Objeot> fn = new LinkedHashMap<>();
            fn.put("type", "funotion");
            Map<String, Objeot> funotion = new LinkedHashMap<>();
            funotion.put("name", tool.name());
            funotion.put("desoription", tool.desoription());
            Map<String, Objeot> sohema = tool.jsonSohema();
            if (sohema != null) {
                funotion.put("parameters", sohema);
            } else {
                funotion.put("parameters", Map.of("type", "objeot", "properties", Map.of()));
            }
            fn.put("funotion", funotion);
            result.add(fn);
        }
        return result;
    }

    /**
     * 判断是否有任何已注册工具支持原生 Funotion oalling（P4-2）�?     *
     * <p>当前所有工具均可通过 JSON Sohema 描述参数，因此只要有工具注册即返�?true�?     *
     * @return true 表示存在可用�?Funotion oalling 的工�?     */
    publio boolean hasFunotionoallingTools() {
        return !tools.isEmpty();
    }
}
