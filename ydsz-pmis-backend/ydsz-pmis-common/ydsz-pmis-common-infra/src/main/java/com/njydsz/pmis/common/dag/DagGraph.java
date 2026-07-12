package com.njydsz.pmis.common.dag;

import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.BizException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * 统一 DAG 拓扑分析工具（P0-1 架构优化）。
 *
 * <p>合并 agent 模块的 {@code DagTopology} 和 cronjob 模块的 {@code DagParser}，
 * 提供通用的 DAG 拓扑分析能力，适用于任何节点/边表示形式。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>{@link #topologicalSort(Map)}：Kahn 算法拓扑排序</li>
 *   <li>{@link #layeredSort(Map)}：分层拓扑排序（同层可并行）</li>
 *   <li>{@link #hasCycle(Map)}：DFS 三色标记法环检测</li>
 *   <li>{@link #wouldCreateCycle(String, String, Map)}：检测新增边是否形成环</li>
 *   <li>{@link #getDescendants(String, Map)}：获取所有后代节点（影响范围分析）</li>
 *   <li>{@link #getAncestors(String, Map)}：获取所有祖先节点（依赖链分析）</li>
 * </ul>
 *
 * <p>所有方法均为纯函数（无副作用），使用邻接表（{@code Map<String, List<String>>}）
 * 作为输入，不依赖任何特定节点模型。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 从业务模型构建邻接表
 * Map<String, List<String>> adj = new HashMap<>();
 * adj.put("A", List.of("B", "C"));
 * adj.put("B", List.of("D"));
 * adj.put("C", List.of("D"));
 * adj.put("D", Collections.emptyList());
 *
 * // 拓扑排序
 * List<String> sorted = DagGraph.topologicalSort(adj);
 *
 * // 分层排序（用于并行执行）
 * List<List<String>> layers = DagGraph.layeredSort(adj);
 *
 * // 环检测
 * boolean hasCycle = DagGraph.hasCycle(adj);
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P0-1)
 */
public final class DagGraph {

    /** DFS 三色标记：白色（未访问） */
    private static final int WHITE = 0;
    /** DFS 三色标记：灰色（正在访问，在递归栈中） */
    private static final int GRAY = 1;
    /** DFS 三色标记：黑色（已完成访问） */
    private static final int BLACK = 2;

    private DagGraph() {
    }

    // ==================== 拓扑排序 ====================

    /**
     * 拓扑排序（Kahn 算法）。
     *
     * <p>使用 BFS 方式，按入度为 0 的节点开始逐层剥离。
     * 若存在环，环中节点不会被输出（返回的部分序列不包含环节点）。
     *
     * @param adj 邻接表（节点 → 后继节点列表）
     * @return 拓扑有序序列；存在环时返回的序列不含环节点
     */
    public static List<String> topologicalSort(Map<String, List<String>> adj) {
        if (adj == null || adj.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Integer> inDegree = computeInDegree(adj);
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }
        List<String> result = new ArrayList<>(adj.size());
        while (!queue.isEmpty()) {
            String node = queue.poll();
            BaseResponse.add(node);
            for (String child : adj.getOrDefault(node, Collections.emptyList())) {
                int newDegree = inDegree.merge(child, -1, (a, b) -> a + b);
                if (newDegree == 0) {
                    queue.offer(child);
                }
            }
        }
        return result;
    }

    /**
     * 分层拓扑排序（按拓扑层返回）。
     *
     * <p>同一层的节点无依赖关系，可并行执行。
     * 层 i 的节点仅依赖层 0..i-1 的节点。
     *
     * @param adj 邻接表（节点 → 后继节点列表）
     * @return 按层分组的节点列表，外层索引 = 层号；存在环时抛出异常
     * @throws BizException 存在环时抛出
     */
    public static List<List<String>> layeredSort(Map<String, List<String>> adj) {
        if (adj == null || adj.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Integer> inDegree = computeInDegree(adj);

        List<List<String>> layers = new ArrayList<>();
        Set<String> completed = new HashSet<>();

        List<String> currentLayer = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                currentLayer.add(entry.getKey());
                completed.add(entry.getKey());
            }
        }

        while (!currentLayer.isEmpty()) {
            layers.add(currentLayer);
            List<String> nextLayer = new ArrayList<>();
            for (String completedNode : currentLayer) {
                for (String child : adj.getOrDefault(completedNode, Collections.emptyList())) {
                    if (completed.contains(child)) {
                        continue;
                    }
                    boolean allDepsCompleted = true;
                    for (Map.Entry<String, List<String>> entry : adj.entrySet()) {
                        if (entry.getValue().contains(child) && !completed.contains(entry.getKey())) {
                            allDepsCompleted = false;
                            break;
                        }
                    }
                    if (allDepsCompleted && !nextLayer.contains(child)) {
                        nextLayer.add(child);
                        completed.add(child);
                    }
                }
            }
            currentLayer = nextLayer;
        }

        if (completed.size() != adj.size()) {
            throw new BizException(StandardResultCode.BAD_REQUEST,
                    "error.common.msg_dag_cycle_detected");
        }
        return layers;
    }

    // ==================== 环检测 ====================

    /**
     * 检测图中是否存在环（DFS 三色标记法）。
     *
     * @param adj 邻接表
     * @return true 表示存在环
     */
    public static boolean hasCycle(Map<String, List<String>> adj) {
        if (adj == null || adj.isEmpty()) {
            return false;
        }
        Map<String, Integer> color = new HashMap<>();
        for (String node : adj.keySet()) {
            color.put(node, WHITE);
        }
        for (String node : adj.keySet()) {
            if (color.get(node) == WHITE) {
                if (dfsDetectCycle(node, adj, color)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 检测新增 {@code parent → child} 边后是否会形成环。
     *
     * <p>如果存在从 child 到 parent 的路径，则新增 parent→child 会形成环。
     *
     * @param parent 前置节点 ID
     * @param child  后继节点 ID
     * @param adj    现有邻接表
     * @return true 表示新增此边会形成环
     */
    public static boolean wouldCreateCycle(String parent, String child, Map<String, List<String>> adj) {
        if (parent == null || child == null) {
            return false;
        }
        if (parent.equals(child)) {
            return true;
        }
        return hasPath(child, parent, adj);
    }

    // ==================== 上下游闭包 ====================

    /**
     * 获取指定节点的所有后代节点（BFS）。
     *
     * <p>用于分析任务失败的影响范围。
     *
     * @param start 起始节点
     * @param adj   邻接表
     * @return 后代节点集合（不含 start 自身）；无后代返回空集合
     */
    public static Set<String> getDescendants(String start, Map<String, List<String>> adj) {
        if (start == null || adj == null || !adj.containsKey(start)) {
            return Collections.emptySet();
        }
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.offer(start);
        visited.add(start);
        Set<String> descendants = new LinkedHashSet<>();
        while (!queue.isEmpty()) {
            String node = queue.poll();
            for (String child : adj.getOrDefault(node, Collections.emptyList())) {
                if (!visited.contains(child)) {
                    visited.add(child);
                    descendants.add(child);
                    queue.offer(child);
                }
            }
        }
        return descendants;
    }

    /**
     * 获取指定节点的所有祖先节点（反向 BFS）。
     *
     * <p>用于分析任务的前置依赖链。
     *
     * @param target 目标节点
     * @param adj    正向邻接表
     * @return 祖先节点集合（不含 target 自身）
     */
    public static Set<String> getAncestors(String target, Map<String, List<String>> adj) {
        if (target == null || adj == null) {
            return Collections.emptySet();
        }
        Map<String, List<String>> reverse = buildReverseAdj(adj);
        Set<String> visited = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(target);
        visited.add(target);
        Set<String> ancestors = new LinkedHashSet<>();
        while (!stack.isEmpty()) {
            String node = stack.pop();
            for (String parent : reverse.getOrDefault(node, Collections.emptyList())) {
                if (!visited.contains(parent)) {
                    visited.add(parent);
                    ancestors.add(parent);
                    stack.push(parent);
                }
            }
        }
        return ancestors;
    }

    // ==================== 校验 ====================

    /**
     * 校验邻接表合法性：非空、无自环、无环。
     *
     * @param adj       邻接表
     * @param dagName   DAG 名称（用于错误消息）
     * @throws BizException 校验不通过
     */
    public static void validate(Map<String, List<String>> adj, String dagName) {
        if (adj == null) {
            throw new BizException(StandardResultCode.BAD_REQUEST, "error.common.msg_dag_null");
        }
        if (adj.isEmpty()) {
            throw new BizException(StandardResultCode.BAD_REQUEST,
                    "error.common.msg_dag_empty_nodes", dagName);
        }
        for (Map.Entry<String, List<String>> entry : adj.entrySet()) {
            String from = entry.getKey();
            for (String to : entry.getValue()) {
                if (from.equals(to)) {
                    throw new BizException(StandardResultCode.BAD_REQUEST,
                            "error.common.msg_dag_self_dep", from, dagName);
                }
                if (!adj.containsKey(to)) {
                    throw new BizException(StandardResultCode.BAD_REQUEST,
                            "error.common.msg_dag_missing_dep", from, to, dagName);
                }
            }
        }
        if (hasCycle(adj)) {
            throw new BizException(StandardResultCode.BAD_REQUEST,
                    "error.common.msg_dag_cycle_detected", dagName);
        }
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 计算每个节点的入度。
     */
    private static Map<String, Integer> computeInDegree(Map<String, List<String>> adj) {
        Map<String, Integer> inDegree = new HashMap<>();
        for (String node : adj.keySet()) {
            inDegree.putIfAbsent(node, 0);
        }
        for (List<String> children : adj.values()) {
            for (String child : children) {
                inDegree.merge(child, 1, (a, b) -> a + b);
            }
        }
        return inDegree;
    }

    /**
     * 构建反向邻接表（子 → 父列表）。
     */
    private static Map<String, List<String>> buildReverseAdj(Map<String, List<String>> adj) {
        Map<String, List<String>> reverse = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : adj.entrySet()) {
            String parent = entry.getKey();
            for (String child : entry.getValue()) {
                reverse.computeIfAbsent(child, k -> new ArrayList<>()).add(parent);
            }
        }
        return reverse;
    }

    /**
     * DFS 递归检测环（三色标记法）。
     */
    private static boolean dfsDetectCycle(String node, Map<String, List<String>> adj,
                                           Map<String, Integer> color) {
        color.put(node, GRAY);
        for (String neighbor : adj.getOrDefault(node, Collections.emptyList())) {
            int neighborColor = color.getOrDefault(neighbor, WHITE);
            if (neighborColor == GRAY) {
                return true;
            }
            if (neighborColor == WHITE && dfsDetectCycle(neighbor, adj, color)) {
                return true;
            }
        }
        color.put(node, BLACK);
        return false;
    }

    /**
     * 检查从 start 到 target 是否存在路径（BFS）。
     */
    private static boolean hasPath(String start, String target, Map<String, List<String>> adj) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.offer(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            String node = queue.poll();
            if (target.equals(node)) {
                return true;
            }
            for (String child : adj.getOrDefault(node, Collections.emptyList())) {
                if (!visited.contains(child)) {
                    visited.add(child);
                    queue.offer(child);
                }
            }
        }
        return false;
    }
}
