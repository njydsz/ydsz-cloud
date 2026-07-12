package com.njydsz.pmis.cronjob.server.core.dag;

import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.SysException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * P0-3: DAG 定义校验器（可视化编辑器后端校验）。
 *
 * <p>对 {@link DagDefinition} 进行全面的结构和语义校验，
 * 确保存入 DB 的 DAG 定义可在运行时正确执行。
 *
 * <h3>校验规则</h3>
 * <ol>
 *   <li><b>节点完整性</b>：nodes 非空，jobKey 非空且唯一</li>
 *   <li><b>边完整性</b>：from/to 非空且引用已存在的节点</li>
 *   <li><b>无自环</b>：边的 from ≠ to</li>
 *   <li><b>无环检测</b>：DAG 不允许存在环（DFS 三色标记法）</li>
 *   <li><b>根节点</b>：至少存在一个无入边的根节点</li>
 *   <li><b>节点类型校验</b>：
 *     <ul>
 *       <li>CONDITION: conditionExpression 非空</li>
 *       <li>LOOP: loopCount > 0</li>
 *       <li>PARALLEL_GATEWAY: parallelBranches > 0 且出边数 = parallelBranches</li>
 *     </ul>
 *   </li>
 *   <li><b>规模限制</b>：节点数 ≤ 200，边数 ≤ 500（防止过大 DAG 拖慢调度）</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Component
public class DagDefinitionValidator {

    /** 最大节点数 */
    private static final int MAX_NODES = 200;
    /** 最大边数 */
    private static final int MAX_EDGES = 500;

    /**
     * 校验 DAG 定义，不通过时抛出 {@link SysException}。
     *
     * @param definition DAG 定义
     * @throws SysException 校验不通过
     */
    public void validate(DagDefinition definition) {
        if (definition == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.cronjob.msg_dag_definition_empty");
        }
        validateNodes(definition);
        validateEdges(definition);
        validateNoCycles(definition);
        validateRootNodes(definition);
        validateNodeTypes(definition);
        validateScale(definition);
    }

    /**
     * 校验节点完整性。
     */
    private void validateNodes(DagDefinition definition) {
        if (definition.nodes() == null || definition.nodes().isEmpty()) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.cronjob.msg_dag_no_nodes");
        }
        Set<String> seen = new HashSet<>();
        for (DagNode node : definition.nodes()) {
            if (node.jobKey() == null || node.jobKey().isBlank()) {
                throw new SysException(StandardResultCode.BAD_REQUEST, "error.cronjob.msg_dag_node_key_missing");
            }
            if (!seen.add(node.jobKey())) {
                throw new SysException(StandardResultCode.BAD_REQUEST, "error.cronjob.msg_dag_node_key_duplicate");
            }
        }
    }

    /**
     * 校验边完整性和无自环。
     */
    private void validateEdges(DagDefinition definition) {
        Set<String> nodeKeys = new HashSet<>();
        for (DagNode node : definition.nodes()) {
            nodeKeys.add(node.jobKey());
        }
        Set<String> seenEdges = new HashSet<>();
        for (DagEdge edge : definition.edges()) {
            if (edge.from() == null || edge.to() == null) {
                throw new SysException(StandardResultCode.BAD_REQUEST, "error.cronjob.msg_dag_edge_invalid");
            }
            if (!nodeKeys.contains(edge.from())) {
                throw new SysException(StandardResultCode.BAD_REQUEST,
                        "error.cronjob.msg_dag_edge_node_not_found", edge.from());
            }
            if (!nodeKeys.contains(edge.to())) {
                throw new SysException(StandardResultCode.BAD_REQUEST,
                        "error.cronjob.msg_dag_edge_node_not_found", edge.to());
            }
            if (edge.from().equals(edge.to())) {
                throw new SysException(StandardResultCode.BAD_REQUEST,
                        "error.cronjob.msg_dag_self_loop", edge.from());
            }
            // 重复边检测
            String edgeKey = edge.from() + "->" + edge.to();
            if (!seenEdges.add(edgeKey)) {
                throw new SysException(StandardResultCode.BAD_REQUEST,
                        "error.cronjob.msg_dag_duplicate_edge", edge.from(), edge.to());
            }
        }
    }

    /**
     * 环检测：DFS 三色标记法。
     *
     * <p>颜色含义：
     * <ul>
     *   <li>WHITE(0): 未访问</li>
     *   <li>GRAY(1): 正在访问（在当前 DFS 路径上）</li>
     *   <li>BLACK(2): 已完成（所有后代已访问）</li>
     * </ul>
     * <p>遇到 GRAY 节点表示存在环。
     */
    private void validateNoCycles(DagDefinition definition) {
        Map<String, Integer> color = new HashMap<>();
        for (DagNode node : definition.nodes()) {
            color.put(node.jobKey(), 0); // WHITE
        }
        for (DagNode node : definition.nodes()) {
            if (color.get(node.jobKey()) == 0) {
                if (hasCycleDFS(node.jobKey(), definition, color)) {
                    throw new SysException(StandardResultCode.BAD_REQUEST,
                            "error.cronjob.msg_dag_cycle_detected", node.jobKey());
                }
            }
        }
    }

    /**
     * DFS 递归检测环。
     *
     * @return true 发现环
     */
    private boolean hasCycleDFS(String jobKey, DagDefinition definition, Map<String, Integer> color) {
        color.put(jobKey, 1); // GRAY
        for (DagEdge edge : definition.outgoingEdges(jobKey)) {
            String neighbor = edge.to();
            if (color.get(neighbor) == 1) {
                // 遇到 GRAY 节点，存在环
                log.warn("[DagValidator] 检测到环: {} -> {}", jobKey, neighbor);
                return true;
            }
            if (color.get(neighbor) == 0 && hasCycleDFS(neighbor, definition, color)) {
                return true;
            }
        }
        color.put(jobKey, 2); // BLACK
        return false;
    }

    /**
     * 校验至少存在一个根节点（无入边）。
     */
    private void validateRootNodes(DagDefinition definition) {
        List<DagNode> roots = definition.rootNodes();
        if (roots.isEmpty()) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.cronjob.msg_dag_no_root");
        }
    }

    /**
     * 校验节点类型特定约束。
     */
    private void validateNodeTypes(DagDefinition definition) {
        for (DagNode node : definition.nodes()) {
            DagNode.NodeType type = node.resolveNodeType();
            switch (type) {
                case CONDITION -> {
                    if (node.conditionExpression() == null || node.conditionExpression().isBlank()) {
                        throw new SysException(StandardResultCode.BAD_REQUEST,
                                "error.cronjob.msg_dag_condition_expr_missing", node.jobKey());
                    }
                }
                case LOOP -> {
                    if (node.loopCount() == null || node.loopCount() <= 0) {
                        throw new SysException(StandardResultCode.BAD_REQUEST,
                                "error.cronjob.msg_dag_loop_count_invalid", node.jobKey());
                    }
                }
                case PARALLEL_GATEWAY -> {
                    if (node.parallelBranches() == null || node.parallelBranches() <= 0) {
                        throw new SysException(StandardResultCode.BAD_REQUEST,
                                "error.cronjob.msg_dag_parallel_branches_invalid", node.jobKey());
                    }
                    int outEdgeCount = definition.outgoingEdges(node.jobKey()).size();
                    if (outEdgeCount != node.parallelBranches()) {
                        throw new SysException(StandardResultCode.BAD_REQUEST,
                                "error.cronjob.msg_dag_parallel_edge_mismatch",
                                node.jobKey(), node.parallelBranches(), outEdgeCount);
                    }
                }
                case TASK -> {
                    // TASK 节点要求 jobId 非空（关联具体任务）
                    if (node.jobId() == null || node.jobId().isBlank()) {
                        throw new SysException(StandardResultCode.BAD_REQUEST,
                                "error.cronjob.msg_dag_task_job_id_missing", node.jobKey());
                    }
                }
                case SUB_WORKFLOW -> {
                    // P1-5: 子工作流节点要求 subWorkflowDagKey 非空
                    if (node.subWorkflowDagKey() == null || node.subWorkflowDagKey().isBlank()) {
                        throw new SysException(StandardResultCode.BAD_REQUEST,
                                "error.cronjob.msg_dag_sub_workflow_key_missing", node.jobKey());
                    }
                }
                case APPROVAL -> {
                    // P1-6: 审批节点要求 approvalUsers 非空
                    if (node.approvalUsers() == null || node.approvalUsers().isBlank()) {
                        throw new SysException(StandardResultCode.BAD_REQUEST,
                                "error.cronjob.msg_dag_approval_users_missing", node.jobKey());
                    }
                    if (node.approvalTimeoutMinutes() != null && node.approvalTimeoutMinutes() <= 0) {
                        throw new SysException(StandardResultCode.BAD_REQUEST,
                                "error.cronjob.msg_dag_approval_timeout_invalid", node.jobKey());
                    }
                }
            }
        }
    }

    /**
     * 校验 DAG 规模限制。
     */
    private void validateScale(DagDefinition definition) {
        if (definition.nodeCount() > MAX_NODES) {
            throw new SysException(StandardResultCode.BAD_REQUEST,
                    "error.cronjob.msg_dag_too_many_nodes", MAX_NODES, definition.nodeCount());
        }
        if (definition.edges().size() > MAX_EDGES) {
            throw new SysException(StandardResultCode.BAD_REQUEST,
                    "error.cronjob.msg_dag_too_many_edges", MAX_EDGES, definition.edges().size());
        }
    }
}
