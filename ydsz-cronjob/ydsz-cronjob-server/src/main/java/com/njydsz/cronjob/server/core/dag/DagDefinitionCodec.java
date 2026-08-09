package com.njydsz.cronjob.server.core.dag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.tree.ArrayNode;
import com.njydsz.common.json.tree.ObjectNode;

import org.springframework.stereotype.Component;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;

import lombok.extern.slf4j.Slf4j;

/**
 * DAG 定义 JSON 编解码器（P2 DAG 增强）。
 *
 * <p>负责 {@link DagDefinition} 与 JSON 字符串之间的转换，
 * 存储/读取 {@code JobDag.dagDefinition} 字段。
 *
 * <p>序列化使用 YdszJson POJO 自动序列化（record 字段已标记 @JsonProperty），
 * 反序列化保留手工解析以支持结构校验和向后兼容，避免 record 构造函数兼容性问题。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class DagDefinitionCodec {

    /**
     * 序列化 DAG 定义为 JSON 字符串（使用 POJO 自动序列化）。
     *
     * <p>1.1.0 重构：DagNode/DagEdge 已添加 @JsonProperty 和 @JsonClass 注解，
     * 直接委托 YdszJson 引擎完成序列化，消除 50+ 行手工树构建代码。</p>
     *
     * @param definition DAG 定义
     * @return JSON 字符串
     */
    public String toJson(DagDefinition definition) {
        if (definition == null) {
            return null;
        }
        return YdszJson.toJson(definition);
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
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.cronjob.msg_dag_definition_empty")
                .build();
        }
        ObjectNode root;
        try {
            root = YdszJson.parseObject(json);
        } catch (Exception e) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.cronjob.msg_dag_definition_invalid")
                .build();
        }
        if (root == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.cronjob.msg_dag_definition_empty")
                .build();
        }

        // 解析 nodes
        List<DagNode> nodes = new ArrayList<>();
        ArrayNode nodesArr = root.getArrayNode("nodes");
        if (nodesArr == null || nodesArr.isEmpty()) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.cronjob.msg_dag_no_nodes")
                .build();
        }
        for (int i = 0; i < nodesArr.size(); i++) {
            ObjectNode n = nodesArr.getObjectNode(i);
            String jobKey = n.getString("jobKey");
            if (jobKey == null || jobKey.isBlank()) {
                throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.cronjob.msg_dag_node_key_missing")
                .build();
            }
            nodes.add(new DagNode(
                    jobKey,
                    n.getString("jobId"),
                    n.getString("label"),
                    n.getIntValue("x"),
                    n.getIntValue("y"),
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
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.cronjob.msg_dag_node_key_duplicate")
                .build();
        }

        // 解析 edges（可为空）
        List<DagEdge> edges = new ArrayList<>();
        ArrayNode edgesArr = root.getArrayNode("edges");
        if (edgesArr != null) {
            for (int i = 0; i < edgesArr.size(); i++) {
                ObjectNode e = edgesArr.getObjectNode(i);
                String from = e.getString("from");
                String to = e.getString("to");
                if (from == null || to == null) {
                    throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.cronjob.msg_dag_edge_invalid")
                .build();
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
                throw SysException.builder()
                    .resultCode(BaseResultCode.BAD_REQUEST)
                    .key("error.cronjob.msg_dag_edge_node_not_found").params(edge.from())
                    .build();
            }
            if (!nodeKeys.contains(edge.to())) {
                throw SysException.builder()
                    .resultCode(BaseResultCode.BAD_REQUEST)
                    .key("error.cronjob.msg_dag_edge_node_not_found").params(edge.to())
                    .build();
            }
        }

        return new DagDefinition(nodes, edges);
    }
}
