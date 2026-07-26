package com.njydsz.agent.server.agent;

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

import com.njydsz.agent.domain.agent.AgentDag;

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
 * @author ydsz-team
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
        if (!(nodesRaw instanceof Map<?, ?>) || ((Map<?, ?>) nodesRaw).isEmpty()) {
            throw new IllegalArgumentException("DSL 缺少 nodes 定义");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> nodesYaml = (Map<String, Object>) nodesRaw;

        Map<String, AgentDag.Node> nodes = new HashMap<>();
        for (Map.Entry<String, Object> entry : nodesYaml.entrySet()) {
            String nodeId = entry.getKey();
            Object nodeDefRaw = entry.getValue();
            if (!(nodeDefRaw instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("节点定义格式错误: " + nodeId);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> nodeDef = (Map<String, Object>) nodeDefRaw;
            String agentType = (String) nodeDef.getOrDefault("agent-type", "CHAT");
            String prompt = (String) nodeDef.getOrDefault("prompt", "");
            String inputFrom = (String) nodeDef.get("input-from");
            Object configRaw = nodeDef.get("config");
            @SuppressWarnings("unchecked")
            Map<String, Object> config = configRaw instanceof Map ? (Map<String, Object>) configRaw : null;
            nodes.put(nodeId, new AgentDag.Node(nodeId, agentType, prompt, inputFrom, config));
        }

        Map<String, List<String>> edges = new HashMap<>();
        if (edgesRaw instanceof Map<?, ?> edgesYaml) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) edgesYaml).entrySet()) {
                String nodeId = entry.getKey();
                if (entry.getValue() instanceof List<?> deps) {
                    @SuppressWarnings("unchecked")
                    List<String> depList = (List<String>) deps;
                    edges.put(nodeId, depList);
                }
            }
        }

        AgentDag dag = new AgentDag(UUID.randomUUID().toString(), name, nodes, edges);
        log.info("[DagDslParser] 解析完成: name={}, nodes={}", name, nodes.size());
        return dag;
    }
}
