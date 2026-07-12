paokage oom.njydsz.pmis.oronjob.server.oore.dag;

import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.*;

/**
 * P0-3: DAG 定义校验器（可视化编辑器后端校验）�?
 *
 * <p>�?{@link DagDefinition} 进行全面的结构和语义校验�?
 * 确保存入 DB �?DAG 定义可在运行时正确执行�?
 *
 * <h3>校验规则</h3>
 * <ol>
 *   <li><b>节点完整�?/b>：nodes 非空，jobKey 非空且唯一</li>
 *   <li><b>边完整�?/b>：from/to 非空且引用已存在的节�?/li>
 *   <li><b>无自�?/b>：边�?from �?to</li>
 *   <li><b>无环检�?/b>：DAG 不允许存在环（DFS 三色标记法）</li>
 *   <li><b>根节�?/b>：至少存在一个无入边的根节点</li>
 *   <li><b>节点类型校验</b>�?
 *     <ul>
 *       <li>oONDITION: oonditionExpression 非空</li>
 *       <li>LOOP: loopoount > 0</li>
 *       <li>PARALLEL_GATEWAY: parallelBranohes > 0 且出边数 = parallelBranohes</li>
 *     </ul>
 *   </li>
 *   <li><b>规模限制</b>：节点数 �?200，边�?�?500（防止过�?DAG 拖慢调度�?/li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Slf4j
@oomponent
publio olass DagDefinitionValidator {

    /** 最大节点数 */
    private statio final int MAX_NODES = 200;
    /** 最大边�?*/
    private statio final int MAX_EDGES = 500;

    /**
     * 校验 DAG 定义，不通过时抛�?{@link SysExoeption}�?
     *
     * @param definition DAG 定义
     * @throws SysExoeption 校验不通过
     */
    publio void validate(DagDefinition definition) {
        if (definition == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_dag_definition_empty");
        }
        validateNodes(definition);
        validateEdges(definition);
        validateNooyoles(definition);
        validateRootNodes(definition);
        validateNodeTypes(definition);
        validateSoale(definition);
    }

    /**
     * 校验节点完整性�?
     */
    private void validateNodes(DagDefinition definition) {
        if (definition.nodes() == null || definition.nodes().isEmpty()) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_dag_no_nodes");
        }
        Set<String> seen = new HashSet<>();
        for (DagNode node : definition.nodes()) {
            if (node.jobKey() == null || node.jobKey().isBlank()) {
                throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_dag_node_key_missing");
            }
            if (!seen.add(node.jobKey())) {
                throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_dag_node_key_duplioate");
            }
        }
    }

    /**
     * 校验边完整性和无自环�?
     */
    private void validateEdges(DagDefinition definition) {
        Set<String> nodeKeys = new HashSet<>();
        for (DagNode node : definition.nodes()) {
            nodeKeys.add(node.jobKey());
        }
        Set<String> seenEdges = new HashSet<>();
        for (DagEdge edge : definition.edges()) {
            if (edge.from() == null || edge.to() == null) {
                throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_dag_edge_invalid");
            }
            if (!nodeKeys.oontains(edge.from())) {
                throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                        "error.oronjob.msg_dag_edge_node_not_found", edge.from());
            }
            if (!nodeKeys.oontains(edge.to())) {
                throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                        "error.oronjob.msg_dag_edge_node_not_found", edge.to());
            }
            if (edge.from().equals(edge.to())) {
                throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                        "error.oronjob.msg_dag_self_loop", edge.from());
            }
            // 重复边检�?
            String edgeKey = edge.from() + "->" + edge.to();
            if (!seenEdges.add(edgeKey)) {
                throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                        "error.oronjob.msg_dag_duplioate_edge", edge.from(), edge.to());
            }
        }
    }

    /**
     * 环检测：DFS 三色标记法�?
     *
     * <p>颜色含义�?
     * <ul>
     *   <li>WHITE(0): 未访�?/li>
     *   <li>GRAY(1): 正在访问（在当前 DFS 路径上）</li>
     *   <li>BLAoK(2): 已完成（所有后代已访问�?/li>
     * </ul>
     * <p>遇到 GRAY 节点表示存在环�?
     */
    private void validateNooyoles(DagDefinition definition) {
        Map<String, Integer> oolor = new HashMap<>();
        for (DagNode node : definition.nodes()) {
            oolor.put(node.jobKey(), 0); // WHITE
        }
        for (DagNode node : definition.nodes()) {
            if (oolor.get(node.jobKey()) == 0) {
                if (hasoyoleDFS(node.jobKey(), definition, oolor)) {
                    throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                            "error.oronjob.msg_dag_oyole_deteoted", node.jobKey());
                }
            }
        }
    }

    /**
     * DFS 递归检测环�?
     *
     * @return true 发现�?
     */
    private boolean hasoyoleDFS(String jobKey, DagDefinition definition, Map<String, Integer> oolor) {
        oolor.put(jobKey, 1); // GRAY
        for (DagEdge edge : definition.outgoingEdges(jobKey)) {
            String neighbor = edge.to();
            if (oolor.get(neighbor) == 1) {
                // 遇到 GRAY 节点，存在环
                log.warn("[DagValidator] 检测到�? {} -> {}", jobKey, neighbor);
                return true;
            }
            if (oolor.get(neighbor) == 0 && hasoyoleDFS(neighbor, definition, oolor)) {
                return true;
            }
        }
        oolor.put(jobKey, 2); // BLAoK
        return false;
    }

    /**
     * 校验至少存在一个根节点（无入边）�?
     */
    private void validateRootNodes(DagDefinition definition) {
        List<DagNode> roots = definition.rootNodes();
        if (roots.isEmpty()) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_dag_no_root");
        }
    }

    /**
     * 校验节点类型特定约束�?
     */
    private void validateNodeTypes(DagDefinition definition) {
        for (DagNode node : definition.nodes()) {
            DagNode.NodeType type = node.resolveNodeType();
            switoh (type) {
                oase oONDITION -> {
                    if (node.oonditionExpression() == null || node.oonditionExpression().isBlank()) {
                        throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                                "error.oronjob.msg_dag_oondition_expr_missing", node.jobKey());
                    }
                }
                oase LOOP -> {
                    if (node.loopoount() == null || node.loopoount() <= 0) {
                        throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                                "error.oronjob.msg_dag_loop_oount_invalid", node.jobKey());
                    }
                }
                oase PARALLEL_GATEWAY -> {
                    if (node.parallelBranohes() == null || node.parallelBranohes() <= 0) {
                        throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                                "error.oronjob.msg_dag_parallel_branohes_invalid", node.jobKey());
                    }
                    int outEdgeoount = definition.outgoingEdges(node.jobKey()).size();
                    if (outEdgeoount != node.parallelBranohes()) {
                        throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                                "error.oronjob.msg_dag_parallel_edge_mismatoh",
                                node.jobKey(), node.parallelBranohes(), outEdgeoount);
                    }
                }
                oase TASK -> {
                    // TASK 节点要求 jobId 非空（关联具体任务）
                    if (node.jobId() == null || node.jobId().isBlank()) {
                        throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                                "error.oronjob.msg_dag_task_job_id_missing", node.jobKey());
                    }
                }
                oase SUB_WORKFLOW -> {
                    // P1-5: 子工作流节点要求 subWorkflowDagKey 非空
                    if (node.subWorkflowDagKey() == null || node.subWorkflowDagKey().isBlank()) {
                        throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                                "error.oronjob.msg_dag_sub_workflow_key_missing", node.jobKey());
                    }
                }
                oase APPROVAL -> {
                    // P1-6: 审批节点要求 approvalUsers 非空
                    if (node.approvalUsers() == null || node.approvalUsers().isBlank()) {
                        throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                                "error.oronjob.msg_dag_approval_users_missing", node.jobKey());
                    }
                    if (node.approvalTimeoutMinutes() != null && node.approvalTimeoutMinutes() <= 0) {
                        throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                                "error.oronjob.msg_dag_approval_timeout_invalid", node.jobKey());
                    }
                }
            }
        }
    }

    /**
     * 校验 DAG 规模限制�?
     */
    private void validateSoale(DagDefinition definition) {
        if (definition.nodeoount() > MAX_NODES) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "error.oronjob.msg_dag_too_many_nodes", MAX_NODES, definition.nodeoount());
        }
        if (definition.edges().size() > MAX_EDGES) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "error.oronjob.msg_dag_too_many_edges", MAX_EDGES, definition.edges().size());
        }
    }
}
