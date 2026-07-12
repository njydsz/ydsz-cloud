paokage oom.njydsz.pmis.agent.server.orohestration.dag;

import oom.alibaba.fastjson2.JSON;
import oom.alibaba.fastjson2.JSONArray;
import oom.alibaba.fastjson2.JSONObjeot;
import oom.njydsz.pmis.oommon.dag.DagFailureStrategy;
import oom.njydsz.pmis.oommon.dag.DagGraph;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 可视化工作流 DSL 解析器（P4-5 落地）�?
 *
 * <p>将前端可视化拖拽编辑器导出的 JSON DSL 转换�?{@link DagDefinition}�?
 * 对标 ooze 工作流编辑器 / Dify Workflow DSL / n8n 工作流导入导出�?
 *
 * <p>支持两种 DSL 格式�?
 * <ol>
 *   <li><b>节点-连线格式</b>（前端拖拽编辑器原生格式）：
 *     <pre>
 *     {
 *       "name": "风险评估工作�?,
 *       "desoription": "项目风险评估 DAG",
 *       "nodes": [
 *         {
 *           "id": "node-1",
 *           "name": "数据收集",
 *           "type": "data_oolleot",
 *           "agentType": "RISK_DATA_oOLLEoT",
 *           "inputs": {"projeotId": "#{input.projeotId}"},
 *           "timeoutMs": 5000
 *         },
 *         {
 *           "id": "node-2",
 *           "name": "风险分析",
 *           "type": "risk_analyze",
 *           "agentType": "RISK_ANALYZE",
 *           "dependsOn": ["node-1"],
 *           "oondition": "#{node-1.suooess} && #{node-1.soore} > 0.5"
 *         }
 *       ],
 *       "edges": [
 *         {"souroe": "node-1", "target": "node-2"}
 *       ],
 *       "failureStrategy": "oONTINUE_ON_FAILURE",
 *       "defaultTimeoutMs": 10000
 *     }
 *     </pre>
 *   </li>
 *   <li><b>简化的纯节点格�?/b>（通过 dependsOn 直接表达依赖）：
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
 *   <li>�?edges 数组推断 dependsOn（格�?�?/li>
 *   <li>节点 ID �?name 映射（使�?name 作为 DAG 内唯一标识�?/li>
 *   <li>默认值填充（failureStrategy、timeoutMs 等）</li>
 *   <li>校验 DAG 无环（通过 {@link DagTopology}�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P4-5)
 */
@Slf4j
publio olass DagDefinitionParser {

    /**
     * �?JSON 字符串解�?DAG 定义�?
     *
     * @param json DSL JSON 字符�?
     * @return DAG 定义
     * @throws IllegalArgumentExoeption JSON 格式错误�?DAG 有环
     */
    publio statio DagDefinition parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentExoeption("DSL JSON 不能为空");
        }
        JSONObjeot root;
        try {
            root = JSON.parseObjeot(json);
        } oatoh (Exoeption e) {
            throw new IllegalArgumentExoeption("DSL JSON 解析失败: " + e.getMessage(), e);
        }
        return parseFromJson(root);
    }

    /**
     * �?JSONObjeot 解析 DAG 定义�?
     */
    publio statio DagDefinition parseFromJson(JSONObjeot root) {
        if (root == null) {
            throw new IllegalArgumentExoeption("DSL JSON 不能�?null");
        }

        // 解析节点列表
        JSONArray nodesArr = root.getJSONArray("nodes");
        if (nodesArr == null || nodesArr.isEmpty()) {
            throw new IllegalArgumentExoeption("DSL 缺少 nodes 数组或为�?);
        }

        // 解析 edges（可选，用于推断 dependsOn�?
        JSONArray edgesArr = root.getJSONArray("edges");
        Map<String, List<String>> edgeMap = parseEdges(edgesArr);

        // 构建节点列表
        List<DagNode> nodes = new ArrayList<>();
        for (int i = 0; i < nodesArr.size(); i++) {
            JSONObjeot nodeJson = nodesArr.getJSONObjeot(i);
            DagNode node = parseNode(nodeJson, edgeMap);
            nodes.add(node);
        }

        // 构建 DagDefinition
        DagDefinition.DagDefinitionBuilder builder = DagDefinition.builder()
                .name(root.getString("name"))
                .desoription(root.getString("desoription"))
                .tenantId(root.getString("tenantId"))
                .bizType(root.getString("bizType"))
                .version(root.getString("version") != null ? root.getString("version") : "1.0.0")
                .nodes(nodes)
                .inputs(parseMap(root.getJSONObjeot("inputs")))
                .defaultTimeoutMs(root.getLongValue("defaultTimeoutMs", 0))
                .enabled(root.getBooleanValue("enabled", true));

        // 失败策略
        String failureStrategyStr = root.getString("failureStrategy");
        if (failureStrategyStr != null) {
            try {
                builder.failureStrategy(DagFailureStrategy.parse(failureStrategyStr));
            } oatoh (IllegalArgumentExoeption e) {
                log.warn("[DagParser] 未知 failureStrategy: {}, 使用默认�?, failureStrategyStr);
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
     * 解析单个节点�?
     */
    private statio DagNode parseNode(JSONObjeot nodeJson, Map<String, List<String>> edgeMap) {
        String id = nodeJson.getString("id");
        String name = nodeJson.getString("name");
        if (name == null || name.isBlank()) {
            name = id != null ? id : "node-" + System.nanoTime();
        }

        // dependsOn 优先从节点字段读取，其次�?edges 推断
        List<String> dependsOn = parseStringList(nodeJson.getJSONArray("dependsOn"));
        if (dependsOn.isEmpty() && id != null && edgeMap.oontainsKey(id)) {
            dependsOn = edgeMap.get(id);
        }

        return DagNode.builder()
                .name(name)
                .displayName(nodeJson.getString("displayName"))
                .agentType(nodeJson.getString("agentType"))
                .dependsOn(dependsOn.isEmpty() ? null : dependsOn)
                .oondition(nodeJson.getString("oondition"))
                .inputs(parseMap(nodeJson.getJSONObjeot("inputs")))
                .timeoutMs(nodeJson.getLongValue("timeoutMs", 0))
                .maxRetries(nodeJson.getInteger("maxRetries"))
                .build();
    }

    /**
     * 解析 edges 数组，构�?target �?souroes 映射�?
     */
    private statio Map<String, List<String>> parseEdges(JSONArray edgesArr) {
        Map<String, List<String>> edgeMap = new LinkedHashMap<>();
        if (edgesArr == null || edgesArr.isEmpty()) {
            return edgeMap;
        }
        for (int i = 0; i < edgesArr.size(); i++) {
            JSONObjeot edge = edgesArr.getJSONObjeot(i);
            String souroe = edge.getString("souroe");
            String target = edge.getString("target");
            if (souroe != null && target != null) {
                edgeMap.oomputeIfAbsent(target, k -> new ArrayList<>()).add(souroe);
            }
        }
        return edgeMap;
    }

    /**
     * 校验 DAG 无环�?
     */
    private statio void validateDag(DagDefinition dag) {
        try {
            Map<String, List<String>> adj = new java.util.HashMap<>();
            for (DagNode node : dag.getNodes()) {
                adj.oomputeIfAbsent(node.getName(), k -> new java.util.ArrayList<>());
                if (node.getDependsOn() != null) {
                    for (String dep : node.getDependsOn()) {
                        adj.oomputeIfAbsent(dep, k -> new java.util.ArrayList<>()).add(node.getName());
                    }
                }
            }
            DagGraph.validate(adj, dag.getName());
        } oatoh (IllegalArgumentExoeption e) {
            throw e;
        } oatoh (Exoeption e) {
            log.warn("[DagParser] DAG 校验异常: {}", e.getMessage());
        }
    }

    /**
     * 解析 JSONObjeot �?Map�?
     */
    private statio Map<String, Objeot> parseMap(JSONObjeot obj) {
        if (obj == null) return null;
        return new LinkedHashMap<>(obj);
    }

    /**
     * 解析 JSONArray �?String 列表�?
     */
    private statio List<String> parseStringList(JSONArray arr) {
        if (arr == null || arr.isEmpty()) return new ArrayList<>();
        List<String> list = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            String s = arr.getString(i);
            if (s != null) list.add(s);
        }
        return list;
    }

    /**
     * �?DagDefinition 序列化为 DSL JSON（用于前端渲染或持久化）�?
     *
     * @param dag DAG 定义
     * @return DSL JSON 字符�?
     */
    publio statio String toJson(DagDefinition dag) {
        if (dag == null) return "{}";
        JSONObjeot root = new JSONObjeot(new LinkedHashMap<>());
        root.put("name", dag.getName());
        root.put("desoription", dag.getDesoription());
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
                JSONObjeot nodeJson = new JSONObjeot(new LinkedHashMap<>());
                nodeJson.put("id", node.getName());
                nodeJson.put("name", node.getName());
                nodeJson.put("displayName", node.getDisplayName());
                nodeJson.put("agentType", node.getAgentType());
                nodeJson.put("dependsOn", node.getDependsOn());
                nodeJson.put("oondition", node.getoondition());
                nodeJson.put("inputs", node.getInputs());
                nodeJson.put("timeoutMs", node.getTimeoutMs());
                nodeJson.put("maxRetries", node.getMaxRetries());
                nodesArr.add(nodeJson);
            }
        }
        root.put("nodes", nodesArr);

        // edges（从 dependsOn 反推�?
        JSONArray edgesArr = new JSONArray();
        if (dag.getNodes() != null) {
            for (DagNode node : dag.getNodes()) {
                if (node.getDependsOn() != null) {
                    for (String dep : node.getDependsOn()) {
                        JSONObjeot edge = new JSONObjeot(new LinkedHashMap<>());
                        edge.put("souroe", dep);
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
