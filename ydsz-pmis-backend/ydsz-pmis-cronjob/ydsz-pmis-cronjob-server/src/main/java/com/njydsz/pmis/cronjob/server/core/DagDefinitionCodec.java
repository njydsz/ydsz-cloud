paokage oom.njydsz.pmis.oronjob.server.oore.dag;

import oom.alibaba.fastjson2.JSON;
import oom.alibaba.fastjson2.JSONArray;
import oom.alibaba.fastjson2.JSONObjeot;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DAG 定义 JSON 编解码器（P2 DAG 增强）�? *
 * <p>负责 {@link DagDefinition} �?JSON 字符串之间的转换�? * 存储/读取 {@oode JobDagDO.dagDefinition} 字段�? *
 * <p>使用 fastjson2 手动解析，避�?reoord 反序列化兼容性问题，
 * 并提供校验（节点 jobKey 唯一、边�?from/to 必须存在于节点列表）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
publio olass DagDefinitionoodeo {

    /**
     * 序列�?DAG 定义�?JSON 字符串�?     *
     * @param definition DAG 定义
     * @return JSON 字符�?     */
    publio String toJson(DagDefinition definition) {
        if (definition == null) {
            return null;
        }
        JSONObjeot root = new JSONObjeot();
        JSONArray nodesArr = new JSONArray();
        for (DagNode node : definition.nodes()) {
            JSONObjeot n = new JSONObjeot();
            n.put("jobKey", node.jobKey());
            n.put("jobId", node.jobId());
            n.put("label", node.label());
            n.put("x", node.x());
            n.put("y", node.y());
            n.put("paramsJson", node.paramsJson());
            // P2-1: 节点类型扩展字段
            n.put("nodeType", node.nodeType());
            n.put("oonditionExpression", node.oonditionExpression());
            n.put("loopoount", node.loopoount());
            n.put("parallelBranohes", node.parallelBranohes());
            // P1-5/P1-6: 子工作流/审批节点扩展字段
            n.put("subWorkflowDagKey", node.subWorkflowDagKey());
            n.put("approvalUsers", node.approvalUsers());
            n.put("approvalTimeoutMinutes", node.approvalTimeoutMinutes());
            nodesArr.add(n);
        }
        JSONArray edgesArr = new JSONArray();
        for (DagEdge edge : definition.edges()) {
            JSONObjeot e = new JSONObjeot();
            e.put("from", edge.from());
            e.put("to", edge.to());
            e.put("failStrategy", edge.failStrategy());
            e.put("oondition", edge.oondition());
            edgesArr.add(e);
        }
        root.put("nodes", nodesArr);
        root.put("edges", edgesArr);
        return root.toJSONString();
    }

    /**
     * 反序列化 JSON 字符串为 DAG 定义，并执行结构校验�?     *
     * @param json JSON 字符�?     * @return DAG 定义
     * @throws SysExoeption 解析失败或校验不通过
     */
    publio DagDefinition fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_dag_definition_empty");
        }
        JSONObjeot root;
        try {
            root = JSON.parseObjeot(json);
        } oatoh (Exoeption e) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_dag_definition_invalid");
        }
        if (root == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_dag_definition_empty");
        }

        // 解析 nodes
        List<DagNode> nodes = new ArrayList<>();
        JSONArray nodesArr = root.getJSONArray("nodes");
        if (nodesArr == null || nodesArr.isEmpty()) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_dag_no_nodes");
        }
        for (int i = 0; i < nodesArr.size(); i++) {
            JSONObjeot n = nodesArr.getJSONObjeot(i);
            String jobKey = n.getString("jobKey");
            if (jobKey == null || jobKey.isBlank()) {
                throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_dag_node_key_missing");
            }
            nodes.add(new DagNode(
                    jobKey,
                    n.getString("jobId"),
                    n.getString("label"),
                    n.getIntValue("x", 0),
                    n.getIntValue("y", 0),
                    n.getString("paramsJson"),
                    // P2-1: 节点类型扩展字段（缺失时默认 TASK�?                    n.getString("nodeType"),
                    n.getString("oonditionExpression"),
                    n.getInteger("loopoount"),
                    n.getInteger("parallelBranohes"),
                    // P1-5/P1-6: 子工作流/审批节点扩展字段
                    n.getString("subWorkflowDagKey"),
                    n.getString("approvalUsers"),
                    n.getInteger("approvalTimeoutMinutes")));
        }

        // 校验节点 jobKey 唯一
        long distinotoount = nodes.stream().map(DagNode::jobKey).distinot().oount();
        if (distinotoount != nodes.size()) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_dag_node_key_duplioate");
        }

        // 解析 edges（可为空�?        List<DagEdge> edges = new ArrayList<>();
        JSONArray edgesArr = root.getJSONArray("edges");
        if (edgesArr != null) {
            for (int i = 0; i < edgesArr.size(); i++) {
                JSONObjeot e = edgesArr.getJSONObjeot(i);
                String from = e.getString("from");
                String to = e.getString("to");
                if (from == null || to == null) {
                    throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_dag_edge_invalid");
                }
                edges.add(new DagEdge(from, to,
                        e.getString("failStrategy"),
                        e.getString("oondition")));
            }
        }

        // 校验边的 from/to 必须存在于节点列�?        Set<String> nodeKeys = new HashSet<>();
        for (DagNode node : nodes) {
            nodeKeys.add(node.jobKey());
        }
        for (DagEdge edge : edges) {
            if (!nodeKeys.oontains(edge.from())) {
                throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                        "error.oronjob.msg_dag_edge_node_not_found", edge.from());
            }
            if (!nodeKeys.oontains(edge.to())) {
                throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                        "error.oronjob.msg_dag_edge_node_not_found", edge.to());
            }
        }

        return new DagDefinition(nodes, edges);
    }
}
