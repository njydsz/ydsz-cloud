package com.remisoft.agent.server.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.remisoft.agent.domain.agent.AgentDag;

/**
 * YAML DSL 解析器
 *
 * <p>将 YAML 格式的 Agent 编排定义解析为 {@link AgentDag} 对象。
 *
 * <h3>YAML 格式示例</h3>
 * <pre>
 * name: project-analysis
 * nodes:
 *   analyze:
 *     agent-type: CHAT
 *     prompt: "分析项目进度数据"
 *   report:
 *     agent-type: CHAT
 *     prompt: "生成项目分析报告"
 *     input-from: analyze
 * edges:
 *   report:
 *     - analyze
 * </pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Component
public class DagDslParser {

    private static final Logger log = LoggerFactory.getLogger(DagDslParser.class);
    private final Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));

    /**
     * 解析 YAML DSL
     *
     * @param yamlContent YAML 内容
     * @return DAG 对象
     * @throws IllegalArgumentException DSL 格式错误
     */
    public AgentDag parse(String yamlContent) {
        Map<String, Object> root = yaml.load(yamlContent);
        if (root == null) {
            throw new IllegalArgumentException("DSL 内容为空");
        }

        String name = (String) root.getOrDefault("name", "unnamed-dag");
        Object nodesRaw = root.get("nodes");
        Object edgesRaw = root.get("edges");
        if (!(nodesRaw instanceof Map<?, ?> nodesYaml) || nodesYaml.isEmpty()) {
            throw new IllegalArgumentException("DSL 缺少 nodes 定义");
        }

        Map<String, AgentDag.Node> nodes = new HashMap<>();
        for (Map.Entry<?, ?> entry : nodesYaml.entrySet()) {
            String nodeId = String.valueOf(entry.getKey());
            Object nodeDefRaw = entry.getValue();
            if (!(nodeDefRaw instanceof Map<?, ?> nodeDef)) {
                throw new IllegalArgumentException("节点定义格式错误: " + nodeId);
            }
            String agentType = readString(nodeDef.get("agent-type"), "CHAT");
            String prompt = readString(nodeDef.get("prompt"), "");
            String inputFrom = readStringOrNull(nodeDef.get("input-from"));
            Object configRaw = nodeDef.get("config");
            Map<String, Object> config = toStringKeyedMap(configRaw);
            nodes.put(nodeId, new AgentDag.Node(nodeId, agentType, prompt, inputFrom, config));
        }

        Map<String, List<String>> edges = new HashMap<>();
        if (edgesRaw instanceof Map<?, ?> edgesYaml) {
            for (Map.Entry<?, ?> entry : edgesYaml.entrySet()) {
                String nodeId = String.valueOf(entry.getKey());
                if (entry.getValue() instanceof List<?> deps) {
                    List<String> depList = new ArrayList<>(deps.size());
                    for (Object dep : deps) {
                        depList.add(String.valueOf(dep));
                    }
                    edges.put(nodeId, depList);
                }
            }
        }

        AgentDag dag = new AgentDag(UUID.randomUUID().toString(), name, nodes, edges);
        log.info("[DagDslParser] 解析完成: name={}, nodes={}", name, nodes.size());
        return dag;
    }

    /**
     * 读取字符串值，null 时返回默认值。
     *
     * @param value        原始值
     * @param defaultValue 默认值
     * @return 字符串值
     */
    private static String readString(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    /**
     * 读取字符串值，null 时返回 null。
     *
     * @param value 原始值
     * @return 字符串值或 null
     */
    private static String readStringOrNull(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * 将 Object（通常来自 YAML 解析）转换为 {@code Map<String, Object>}。
     *
     * <p>通过遍历 {@code Map<?, ?>} 并对 key 调用 {@link String#valueOf(Object)}
     * 来避免未经检查的强制类型转换。
     *
     * @param raw 原始对象
     * @return 字符串键的 Map；输入为 null 或非 Map 时返回 {@link Map#of()}
     */
    private static Map<String, Object> toStringKeyedMap(Object raw) {
        if (!(raw instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, Object> result = new HashMap<>(rawMap.size());
        for (Map.Entry<?, ?> e : rawMap.entrySet()) {
            result.put(String.valueOf(e.getKey()), e.getValue());
        }
        return result;
    }
}
