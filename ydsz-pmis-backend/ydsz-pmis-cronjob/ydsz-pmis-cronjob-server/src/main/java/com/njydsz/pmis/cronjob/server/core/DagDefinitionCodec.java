package com.njydsz.pmis.cronjob.server.core.dag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

import com.njydsz.pmis.common.json.YdszJson;

import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.core.response.BaseResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;

import lombok.extern.slf4j.Slf4j;

/**
 * DAG 定义 JSON 编解码器（P2 DAG 增强）。
 *
 * <p>负责 {@link DagDefinition} 与 JSON 字符串之间的转换，
 * 存储/读取 {@code JobDagDO.dagDefinition} 字段。
 *
 * <p>使用 fastjson2 手动解析，避免 record 反序列化兼容性问题，
 * 并提供校验（节点 jobKey 唯一、边的 from/to 必须存在于节点列表）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class DagDefinitionCodec {

    /**
     * 序列化 DAG 定义为 JSON 字符串。
     *
     * @param definition DAG 定义
     * @return JSON 字符串
     */
    public String toJson(DagDefinition definition) {
        if (definition == null) {
            return null;
        }
        Map<String, Object> root = new JSONObject();
        List<Object> nodesArr = new JSONArray();
        for (DagNode node : definition.nodes()) {
            Map<String, Object> n = new JSONObject();
            n.put("jobKey", node.jobKey());
            n.put("jobId", node.jobId());
            n.put("label", node.label());
            n.put("x", node.x());
            n.put("y", node.y());
            n.put("paramsJson", node.paramsJson());
            // P2-1: 节点类型扩展字段
            n.put("nodeType", node.nodeType());
            n.put("conditionExpression", node.conditionExpression());
            n.put("loopCount", node.loopCount());
            n.put("parallelBranches", node.parallelBranches());
            // P1-5/P1-6: 子工作流/审批节点扩展字段
            n.put("subWorkflowDagKey", node.subWorkflowDagKey());
            n.put("approvalUsers", node.approvalUsers());
            n.put("approvalTimeoutMinutes", node.approvalTimeoutMinutes());
            nodesArr.add(n);
        }
        List<Object> edgesArr = new JSONArray();
        for (DagEdge edge : definition.edges()) {
            Map<String, Object> e = new JSONObject();
            e.put("from", edge.from());
            e.put("to", edge.to());
            e.put("failStrategy", edge.failStrategy());
            e.put("condition", edge.condition());
            edgesArr.add(e);
        }
        root.put("nodes", nodesArr);
        root.put("edges", edgesArr);
        return root.toJSONString();
    }

    /**
     * 反序列化 JSON 字符串为 DAG 定义，并执行结构校验。
     *
     * @param json JSON 字符串
     * @return DAG 定义
     * @throws SysException 解析失败或校验不通过
     */
    public DagDefinition fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.cronjob.msg_dag_definition_empty");
        }
        JSONObject root;
        try {
            root = YdszJson.parseMap(json);
        } catch (Exception e) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.cronjob.msg_dag_definition_invalid");
        }
        if (root == null) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.cronjob.msg_dag_definition_empty");
        }

        // 解析 nodes
        List<DagNode> nodes = new ArrayList<>();
        List<Object> nodesArr = root.getJSONArray("nodes");
        if (nodesArr == null || nodesArr.isEmpty()) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.cronjob.msg_dag_no_nodes");
        }
        for (int i = 0; i < nodesArr.size(); i++) {
            Map<String, Object> n = nodesArr.getJSONObject(i);
            String jobKey = n.getString("jobKey");
            if (jobKey == null || jobKey.isBlank()) {
                throw new SysException(BaseResultCode.BAD_REQUEST, "error.cronjob.msg_dag_node_key_missing");
            }
            nodes.add(new DagNode(
                    jobKey,
                    n.getString("jobId"),
                    n.getString("label"),
                    n.getIntValue("x", 0),
                    n.getIntValue("y", 0),
                    n.getString("paramsJson"),
                    // P2-1: 节点类型扩展字段（缺失时默认 TASK）
                    n.getString("nodeType"),
                    n.getString("conditionExpression"),
                    n.getInteger("loopCount"),
                    n.getInteger("parallelBranches"),
                    // P1-5/P1-6: 子工作流/审批节点扩展字段
                    n.getString("subWorkflowDagKey"),
                    n.getString("approvalUsers"),
                    n.getInteger("approvalTimeoutMinutes")));
        }

        // 校验节点 jobKey 唯一
        long distinctCount = nodes.stream().map(DagNode::jobKey).distinct().count();
        if (distinctCount != nodes.size()) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.cronjob.msg_dag_node_key_duplicate");
        }

        // 解析 edges（可为空）
        List<DagEdge> edges = new ArrayList<>();
        List<Object> edgesArr = root.getJSONArray("edges");
        if (edgesArr != null) {
            for (int i = 0; i < edgesArr.size(); i++) {
                Map<String, Object> e = edgesArr.getJSONObject(i);
                String from = e.getString("from");
                String to = e.getString("to");
                if (from == null || to == null) {
                    throw new SysException(BaseResultCode.BAD_REQUEST, "error.cronjob.msg_dag_edge_invalid");
                }
                edges.add(new DagEdge(from, to,
                        e.getString("failStrategy"),
                        e.getString("condition")));
            }
        }

        // 校验边的 from/to 必须存在于节点列表
        Set<String> nodeKeys = new HashSet<>();
        for (DagNode node : nodes) {
            nodeKeys.add(node.jobKey());
        }
        for (DagEdge edge : edges) {
            if (!nodeKeys.contains(edge.from())) {
                throw new SysException(BaseResultCode.BAD_REQUEST,
                        "error.cronjob.msg_dag_edge_node_not_found", edge.from());
            }
            if (!nodeKeys.contains(edge.to())) {
                throw new SysException(BaseResultCode.BAD_REQUEST,
                        "error.cronjob.msg_dag_edge_node_not_found", edge.to());
            }
        }

        return new DagDefinition(nodes, edges);
    }
}
