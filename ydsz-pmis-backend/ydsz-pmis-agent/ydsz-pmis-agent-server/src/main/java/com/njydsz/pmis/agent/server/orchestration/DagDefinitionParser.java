package com.njydsz.pmis.agent.server.orchestration.dag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.dag.DagFailureStrategy;
import com.njydsz.pmis.common.dag.DagGraph;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 可视化工作流 DSL 解析器（P4-5 落地）。
 *
 * <p>将前端可视化拖拽编辑器导出的 JSON DSL 转换为 {@link DagDefinition}，
 * 对标 Coze 工作流编辑器 / Dify Workflow DSL / n8n 工作流导入导出。
 *
 * <p>支持两种 DSL 格式：
 * <ol>
 *   <li><b>节点-连线格式</b>（前端拖拽编辑器原生格式）：
 *     <pre>
 *     {
 *       "name": "风险评估工作流",
 *       "description": "项目风险评估 DAG",
 *       "nodes": [
 *         {
 *           "id": "node-1",
 *           "name": "数据收集",
 *           "type": "data_collect",
 *           "agentType": "RISK_DATA_COLLECT",
 *           "inputs": {"projectId": "#{input.projectId}"},
 *           "timeoutMs": 5000
 *         },
 *         {
 *           "id": "node-2",
 *           "name": "风险分析",
 *           "type": "risk_analyze",
 *           "agentType": "RISK_ANALYZE",
 *           "dependsOn": ["node-1"],
 *           "condition": "#{node-1.success} && #{node-1.score} > 0.5"
 *         }
 *       ],
 *       "edges": [
 *         {"source": "node-1", "target": "node-2"}
 *       ],
 *       "failureStrategy": "CONTINUE_ON_FAILURE",
 *       "defaultTimeoutMs": 10000
 *     }
 *     </pre>
 *   </li>
 *   <li><b>简化的纯节点格式</b>（通过 dependsOn 直接表达依赖）：
 *     <pre>
 *     {
 *       "name": "简化工作流",
 *       "nodes": [
 *         {"name": "A", "agentType": "AGENT_A"},
 *         {"name": "B", "agentType": "AGENT_B", "dependsOn": ["A"]}
 *       ]
 *     }
 *     </pre>
 *   </li>
 * </ol>
 *
 * <p>解析器自动处理：
 * <ul>
 *   <li>从 edges 数组推断 dependsOn（格式1）</li>
 *   <li>节点 ID → name 映射（使用 name 作为 DAG 内唯一标识）</li>
 *   <li>默认值填充（failureStrategy、timeoutMs 等）</li>
 *   <li>校验 DAG 无环（通过 {@link DagTopology}）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P4-5)
 */
@Slf4j
public class DagDefinitionParser {

    /**
     * 从 JSON 字符串解析 DAG 定义。
     *
     * @param json DSL JSON 字符串
     * @return DAG 定义
     * @throws IllegalArgumentException JSON 格式错误或 DAG 有环
     */
    public static DagDefinition parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("DSL JSON 不能为空");
        }
        JSONObject root;
        try {
            root = JSON.parseObject(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("DSL JSON 解析失败: " + e.getMessage(), e);
        }
        return parseFromJson(root);
    }

    /**
     * 从 JSONObject 解析 DAG 定义。
     */
    public static DagDefinition parseFromJson(JSONObject root) {
        if (root == null) {
            throw new IllegalArgumentException("DSL JSON 不能为 null");
        }

        // 解析节点列表
        JSONArray nodesArr = root.getJSONArray("nodes");
        if (nodesArr == null || nodesArr.isEmpty()) {
            throw new IllegalArgumentException("DSL 缺少 nodes 数组或为空");
        }

        // 解析 edges（可选，用于推断 dependsOn）
        JSONArray edgesArr = root.getJSONArray("edges");
        Map<String, List<String>> edgeMap = parseEdges(edgesArr);

        // 构建节点列表
        List<DagNode> nodes = new ArrayList<>();
        for (int i = 0; i < nodesArr.size(); i++) {
            JSONObject nodeJson = nodesArr.getJSONObject(i);
            DagNode node = parseNode(nodeJson, edgeMap);
            nodes.add(node);
        }

        // 构建 DagDefinition
        DagDefinition.DagDefinitionBuilder builder = DagDefinition.builder()
                .name(root.getString("name"))
                .description(root.getString("description"))
                .tenantId(root.getString("tenantId"))
                .bizType(root.getString("bizType"))
                .version(root.getString("version") != null ? root.getString("version") : "1.0.0")
                .nodes(nodes)
                .inputs(parseMap(root.getJSONObject("inputs")))
                .defaultTimeoutMs(root.getLongValue("defaultTimeoutMs", 0))
                .enabled(root.getBooleanValue("enabled", true));

        // 失败策略
        String failureStrategyStr = root.getString("failureStrategy");
        if (failureStrategyStr != null) {
            try {
                builder.failureStrategy(DagFailureStrategy.parse(failureStrategyStr));
            } catch (IllegalArgumentException e) {
                log.warn("[DagParser] 未知 failureStrategy: {}, 使用默认值", failureStrategyStr);
            }
        }
        builder.maxRetries(root.getIntValue("maxRetries", 3));

        DagDefinition dag = builder.build();

        // 校验 DAG 无环
        validateDag(dag);

        log.info("[DagParser] 解析成功: name={}, nodes={}", dag.getName(), dag.getNodes().size());
        return dag;
    }

    /**
     * 解析单个节点。
     */
    private static DagNode parseNode(JSONObject nodeJson, Map<String, List<String>> edgeMap) {
        String id = nodeJson.getString("id");
        String name = nodeJson.getString("name");
        if (name == null || name.isBlank()) {
            name = id != null ? id : "node-" + System.nanoTime();
        }

        // dependsOn 优先从节点字段读取，其次从 edges 推断
        List<String> dependsOn = parseStringList(nodeJson.getJSONArray("dependsOn"));
        if (dependsOn.isEmpty() && id != null && edgeMap.containsKey(id)) {
            dependsOn = edgeMap.get(id);
        }

        return DagNode.builder()
                .name(name)
                .displayName(nodeJson.getString("displayName"))
                .agentType(nodeJson.getString("agentType"))
                .dependsOn(dependsOn.isEmpty() ? null : dependsOn)
                .condition(nodeJson.getString("condition"))
                .inputs(parseMap(nodeJson.getJSONObject("inputs")))
                .timeoutMs(nodeJson.getLongValue("timeoutMs", 0))
                .maxRetries(nodeJson.getInteger("maxRetries"))
                .build();
    }

    /**
     * 解析 edges 数组，构建 target → sources 映射。
     */
    private static Map<String, List<String>> parseEdges(JSONArray edgesArr) {
        Map<String, List<String>> edgeMap = new LinkedHashMap<>();
        if (edgesArr == null || edgesArr.isEmpty()) {
            return edgeMap;
        }
        for (int i = 0; i < edgesArr.size(); i++) {
            JSONObject edge = edgesArr.getJSONObject(i);
            String source = edge.getString("source");
            String target = edge.getString("target");
            if (source != null && target != null) {
                edgeMap.computeIfAbsent(target, k -> new ArrayList<>()).add(source);
            }
        }
        return edgeMap;
    }

    /**
     * 校验 DAG 无环。
     */
    private static void validateDag(DagDefinition dag) {
        try {
            Map<String, List<String>> adj = new java.util.HashMap<>();
            for (DagNode node : dag.getNodes()) {
                adj.computeIfAbsent(node.getName(), k -> new java.util.ArrayList<>());
                if (node.getDependsOn() != null) {
                    for (String dep : node.getDependsOn()) {
                        adj.computeIfAbsent(dep, k -> new java.util.ArrayList<>()).add(node.getName());
                    }
                }
            }
            DagGraph.validate(adj, dag.getName());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[DagParser] DAG 校验异常: {}", e.getMessage());
        }
    }

    /**
     * 解析 JSONObject 为 Map。
     */
    private static Map<String, Object> parseMap(JSONObject obj) {
        if (obj == null) return null;
        return new LinkedHashMap<>(obj);
    }

    /**
     * 解析 JSONArray 为 String 列表。
     */
    private static List<String> parseStringList(JSONArray arr) {
        if (arr == null || arr.isEmpty()) return new ArrayList<>();
        List<String> list = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            String s = arr.getString(i);
            if (s != null) list.add(s);
        }
        return list;
    }

    /**
     * 将 DagDefinition 序列化为 DSL JSON（用于前端渲染或持久化）。
     *
     * @param dag DAG 定义
     * @return DSL JSON 字符串
     */
    public static String toJson(DagDefinition dag) {
        if (dag == null) return "{}";
        JSONObject root = new JSONObject(new LinkedHashMap<>());
        root.put("name", dag.getName());
        root.put("description", dag.getDescription());
        root.put("tenantId", dag.getTenantId());
        root.put("bizType", dag.getBizType());
        root.put("version", dag.getVersion());
        root.put("failureStrategy", dag.getFailureStrategy() != null ? dag.getFailureStrategy().name() : null);
        root.put("maxRetries", dag.getMaxRetries());
        root.put("defaultTimeoutMs", dag.getDefaultTimeoutMs());
        root.put("enabled", dag.getEnabled());

        // 节点
        JSONArray nodesArr = new JSONArray();
        if (dag.getNodes() != null) {
            for (DagNode node : dag.getNodes()) {
                JSONObject nodeJson = new JSONObject(new LinkedHashMap<>());
                nodeJson.put("id", node.getName());
                nodeJson.put("name", node.getName());
                nodeJson.put("displayName", node.getDisplayName());
                nodeJson.put("agentType", node.getAgentType());
                nodeJson.put("dependsOn", node.getDependsOn());
                nodeJson.put("condition", node.getCondition());
                nodeJson.put("inputs", node.getInputs());
                nodeJson.put("timeoutMs", node.getTimeoutMs());
                nodeJson.put("maxRetries", node.getMaxRetries());
                nodesArr.add(nodeJson);
            }
        }
        root.put("nodes", nodesArr);

        // edges（从 dependsOn 反推）
        JSONArray edgesArr = new JSONArray();
        if (dag.getNodes() != null) {
            for (DagNode node : dag.getNodes()) {
                if (node.getDependsOn() != null) {
                    for (String dep : node.getDependsOn()) {
                        JSONObject edge = new JSONObject(new LinkedHashMap<>());
                        edge.put("source", dep);
                        edge.put("target", node.getName());
                        edgesArr.add(edge);
                    }
                }
            }
        }
        root.put("edges", edgesArr);

        return root.toJSONString();
    }
}
