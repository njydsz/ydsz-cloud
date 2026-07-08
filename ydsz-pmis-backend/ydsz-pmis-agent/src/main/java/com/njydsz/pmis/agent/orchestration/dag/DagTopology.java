package com.njydsz.pmis.agent.orchestration.dag;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DAG 拓扑分析工具（P3-2 落地）。
 *
 * <p>基于 Kahn 算法实现拓扑排序、环检测、分层（拓扑层）、依赖闭包计算。
 * 所有方法均为纯函数式（无副作用），输入 {@link DagDefinition}，输出分析结果。
 *
 * <p>对标 cronjob 模块 {@code DagInstanceExecutor} 的拓扑分析能力，但面向 Agent 编排场景。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-2)
 */
public final class DagTopology {

    private DagTopology() {
    }

    /**
     * 拓扑排序（Kahn 算法）。
     *
     * <p>若存在环，抛出 {@link BizException}。
     *
     * @param dag DAG 定义
     * @return 拓扑序节点名列表（入度为 0 的节点优先）
     */
    public static List<String> topologicalSort(DagDefinition dag) {
        validate(dag);
        Map<String, Set<String>> graph = buildGraph(dag);
        Map<String, Integer> inDegree = computeInDegree(graph);

        Deque<String> queue = new ArrayDeque<>();
        // 入度为 0 的节点按定义顺序入队（保证稳定排序）
        for (DagNode node : dag.getNodes()) {
            if (inDegree.getOrDefault(node.getName(), 0) == 0) {
                queue.add(node.getName());
            }
        }

        List<String> result = new ArrayList<>(dag.getNodes().size());
        while (!queue.isEmpty()) {
            String current = queue.poll();
            result.add(current);
            // 遍历所有节点，找出以 current 为前置的节点
            for (DagNode node : dag.getNodes()) {
                if (node.getDependsOn() != null && node.getDependsOn().contains(current)) {
                    int newDegree = inDegree.get(node.getName()) - 1;
                    inDegree.put(node.getName(), newDegree);
                    if (newDegree == 0) {
                        queue.add(node.getName());
                    }
                }
            }
        }

        if (result.size() != dag.getNodes().size()) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "error.agent.msg_dag_cycle_detected", dag.getName());
        }
        return result;
    }

    /**
     * 分层拓扑排序（按拓扑层返回）。
     *
     * <p>同一层的节点无依赖关系，可并行执行。
     * 层 i 的节点仅依赖层 0..i-1 的节点。
     *
     * @param dag DAG 定义
     * @return 按层分组的节点名列表，外层索引 = 层号
     */
    public static List<List<String>> layeredSort(DagDefinition dag) {
        validate(dag);
        Map<String, Set<String>> graph = buildGraph(dag);
        Map<String, Integer> inDegree = computeInDegree(graph);

        List<List<String>> layers = new ArrayList<>();
        Set<String> completed = new HashSet<>();

        List<String> currentLayer = new ArrayList<>();
        for (DagNode node : dag.getNodes()) {
            if (inDegree.getOrDefault(node.getName(), 0) == 0) {
                currentLayer.add(node.getName());
                completed.add(node.getName());
            }
        }

        while (!currentLayer.isEmpty()) {
            layers.add(currentLayer);
            List<String> nextLayer = new ArrayList<>();
            for (String completedNode : currentLayer) {
                for (DagNode node : dag.getNodes()) {
                    if (completed.contains(node.getName())) {
                        continue;
                    }
                    if (node.getDependsOn() != null && node.getDependsOn().contains(completedNode)) {
                        // 检查该节点的所有依赖是否都已完成
                        boolean allDepsCompleted = true;
                        if (node.getDependsOn() != null) {
                            for (String dep : node.getDependsOn()) {
                                if (!completed.contains(dep)) {
                                    allDepsCompleted = false;
                                    break;
                                }
                            }
                        }
                        if (allDepsCompleted && !nextLayer.contains(node.getName())) {
                            nextLayer.add(node.getName());
                            completed.add(node.getName());
                        }
                    }
                }
            }
            currentLayer = nextLayer;
        }

        if (completed.size() != dag.getNodes().size()) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "error.agent.msg_dag_cycle_detected", dag.getName());
        }
        return layers;
    }

    /**
     * 计算节点的所有下游节点（传递闭包）。
     *
     * @param dag       DAG 定义
     * @param nodeName  起始节点名
     * @return 所有直接/间接依赖 nodeName 的节点名集合
     */
    public static Set<String> downstreamClosure(DagDefinition dag, String nodeName) {
        validate(dag);
        Set<String> result = new LinkedHashSet<>();
        collectDownstream(dag, nodeName, result);
        result.remove(nodeName);
        return result;
    }

    /**
     * 计算节点的所有上游节点（传递闭包）。
     *
     * @param dag       DAG 定义
     * @param nodeName  起始节点名
     * @return 所有直接/间接被 nodeName 依赖的节点名集合
     */
    public static Set<String> upstreamClosure(DagDefinition dag, String nodeName) {
        validate(dag);
        Set<String> result = new LinkedHashSet<>();
        collectUpstream(dag, nodeName, result);
        result.remove(nodeName);
        return result;
    }

    /**
     * 校验 DAG 定义合法性：节点非空、名称唯一、依赖存在。
     *
     * @param dag DAG 定义
     */
    public static void validate(DagDefinition dag) {
        if (dag == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.agent.msg_dag_null");
        }
        if (dag.getNodes() == null || dag.getNodes().isEmpty()) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "error.agent.msg_dag_empty_nodes", dag.getName());
        }
        Set<String> names = new HashSet<>();
        for (DagNode node : dag.getNodes()) {
            if (node.getName() == null || node.getName().isBlank()) {
                throw new BizException(BizErrorCode.BAD_REQUEST,
                        "error.agent.msg_dag_node_no_name", dag.getName());
            }
            if (!names.add(node.getName())) {
                throw new BizException(BizErrorCode.BAD_REQUEST,
                        "error.agent.msg_dag_dup_node", node.getName(), dag.getName());
            }
        }
        // 依赖存在性校验
        for (DagNode node : dag.getNodes()) {
            if (node.getDependsOn() != null) {
                for (String dep : node.getDependsOn()) {
                    if (!names.contains(dep)) {
                        throw new BizException(BizErrorCode.BAD_REQUEST,
                                "error.agent.msg_dag_missing_dep", node.getName(), dep, dag.getName());
                    }
                    if (dep.equals(node.getName())) {
                        throw new BizException(BizErrorCode.BAD_REQUEST,
                                "error.agent.msg_dag_self_dep", node.getName(), dag.getName());
                    }
                }
            }
        }
    }

    /**
     * 构建邻接表（节点 -> 其前置节点集合）。
     */
    private static Map<String, Set<String>> buildGraph(DagDefinition dag) {
        Map<String, Set<String>> graph = new HashMap<>();
        for (DagNode node : dag.getNodes()) {
            graph.put(node.getName(), new LinkedHashSet<>());
            if (node.getDependsOn() != null) {
                graph.get(node.getName()).addAll(node.getDependsOn());
            }
        }
        return graph;
    }

    /**
     * 计算入度（每个节点依赖的前置节点数）。
     */
    private static Map<String, Integer> computeInDegree(Map<String, Set<String>> graph) {
        Map<String, Integer> inDegree = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : graph.entrySet()) {
            inDegree.put(entry.getKey(), entry.getValue().size());
        }
        return inDegree;
    }

    /**
     * 递归收集下游节点。
     */
    private static void collectDownstream(DagDefinition dag, String nodeName, Set<String> result) {
        for (DagNode node : dag.getNodes()) {
            if (node.getDependsOn() != null && node.getDependsOn().contains(nodeName)) {
                if (result.add(node.getName())) {
                    collectDownstream(dag, node.getName(), result);
                }
            }
        }
    }

    /**
     * 递归收集上游节点。
     */
    private static void collectUpstream(DagDefinition dag, String nodeName, Set<String> result) {
        DagNode node = dag.findNode(nodeName);
        if (node == null || node.getDependsOn() == null) {
            return;
        }
        for (String dep : node.getDependsOn()) {
            if (result.add(dep)) {
                collectUpstream(dag, dep, result);
            }
        }
    }
}
