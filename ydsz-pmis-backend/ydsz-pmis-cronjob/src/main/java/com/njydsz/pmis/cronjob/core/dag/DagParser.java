package com.njydsz.pmis.cronjob.core.dag;

import com.njydsz.pmis.cronjob.entity.JobRelationDO;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * DAG 解析器（P4-2 DAG 工作流）。
 *
 * <p>负责从任务依赖边列表构建邻接表、执行拓扑排序、检测环依赖。
 * 对标 PowerJob 的 {@code WorkflowDAG} / XXL-Job 的子任务依赖解析。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>{@link #buildAdjacencyList(List)}：构建邻接表（parent → children）</li>
 *   <li>{@link #topologicalSort(Map)}：Kahn 算法拓扑排序，存在环时返回 null</li>
 *   <li>{@link #hasCycle(Map)}：DFS 三色标记法环检测</li>
 *   <li>{@link #wouldCreateCycle(String, String, List)}：检测新增边是否形成环</li>
 *   <li>{@link #getDescendants(String, Map)}：获取所有后代节点（用于影响范围分析）</li>
 * </ul>
 *
 * <p>所有方法均为纯函数（无副作用），便于单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Component
public class DagParser {

    /** DFS 三色标记：白色（未访问） */
    private static final int WHITE = 0;
    /** DFS 三色标记：灰色（正在访问，在递归栈中） */
    private static final int GRAY = 1;
    /** DFS 三色标记：黑色（已完成访问） */
    private static final int BLACK = 2;

    /**
     * 从依赖边列表构建邻接表（parent → children list）。
     *
     * @param edges 依赖边列表
     * @return 邻接表；空列表返回空 Map
     */
    public Map<String, List<String>> buildAdjacencyList(List<JobRelationDO> edges) {
        if (edges == null || edges.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> adj = new HashMap<>();
        for (JobRelationDO edge : edges) {
            adj.computeIfAbsent(edge.getParentJobId(), k -> new ArrayList<>())
                    .add(edge.getChildJobId());
            // 确保子节点也在图中（即使它没有后继）
            adj.computeIfAbsent(edge.getChildJobId(), k -> new ArrayList<>());
        }
        return adj;
    }

    /**
     * 拓扑排序（Kahn 算法）。
     *
     * <p>使用 BFS 方式，按入度为 0 的节点开始逐层剥离。
     * 若存在环，环中节点不会被输出（返回的部分序列不包含环节点）。
     *
     * @param adj 邻接表
     * @return 拓扑有序序列；存在环时返回的序列不含环节点
     */
    public List<String> topologicalSort(Map<String, List<String>> adj) {
        if (adj == null || adj.isEmpty()) {
            return Collections.emptyList();
        }
        // 计算入度
        Map<String, Integer> inDegree = new HashMap<>();
        for (String node : adj.keySet()) {
            inDegree.putIfAbsent(node, 0);
        }
        for (List<String> children : adj.values()) {
            for (String child : children) {
                inDegree.merge(child, 1, (a, b) -> a + b);
            }
        }
        // 入度为 0 的节点入队
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }
        List<String> result = new ArrayList<>(adj.size());
        while (!queue.isEmpty()) {
            String node = queue.poll();
            result.add(node);
            List<String> children = adj.getOrDefault(node, Collections.emptyList());
            for (String child : children) {
                int newDegree = inDegree.merge(child, -1, (a, b) -> a + b);
                if (newDegree == 0) {
                    queue.offer(child);
                }
            }
        }
        return result;
    }

    /**
     * 检测图中是否存在环（DFS 三色标记法）。
     *
     * @param adj 邻接表
     * @return true 表示存在环
     */
    public boolean hasCycle(Map<String, List<String>> adj) {
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
     * @param parent       前置任务 ID
     * @param child        后继任务 ID
     * @param existingEdges 现有依赖边列表
     * @return true 表示新增此边会形成环
     */
    public boolean wouldCreateCycle(String parent, String child, List<JobRelationDO> existingEdges) {
        if (parent == null || child == null) {
            return false;
        }
        if (parent.equals(child)) {
            return true; // 自环
        }
        Map<String, List<String>> adj = buildAdjacencyList(existingEdges);
        // 检查是否存在从 child 到 parent 的路径
        return hasPath(child, parent, adj);
    }

    /**
     * 获取指定节点的所有后代节点（BFS）。
     *
     * <p>用于分析任务失败的影响范围。
     *
     * @param start 起始节点
     * @param adj   邻接表
     * @return 后代节点集合（不含 start 自身）；无后代返回空集合
     */
    public Set<String> getDescendants(String start, Map<String, List<String>> adj) {
        if (start == null || adj == null || !adj.containsKey(start)) {
            return Collections.emptySet();
        }
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.offer(start);
        visited.add(start);
        Set<String> descendants = new HashSet<>();
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
    public Set<String> getAncestors(String target, Map<String, List<String>> adj) {
        if (target == null || adj == null) {
            return Collections.emptySet();
        }
        // 构建反向邻接表
        Map<String, List<String>> reverse = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : adj.entrySet()) {
            String parent = entry.getKey();
            for (String child : entry.getValue()) {
                reverse.computeIfAbsent(child, k -> new ArrayList<>()).add(parent);
            }
        }
        // BFS 反向遍历
        Set<String> visited = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(target);
        visited.add(target);
        Set<String> ancestors = new HashSet<>();
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

    // ==================== 内部辅助方法 ====================

    /**
     * DFS 递归检测环（三色标记法）。
     *
     * @param node  当前节点
     * @param adj   邻接表
     * @param color 颜色标记
     * @return true 表示在当前 DFS 分支中发现环
     */
    private boolean dfsDetectCycle(String node, Map<String, List<String>> adj, Map<String, Integer> color) {
        color.put(node, GRAY);
        for (String neighbor : adj.getOrDefault(node, Collections.emptyList())) {
            int neighborColor = color.getOrDefault(neighbor, WHITE);
            if (neighborColor == GRAY) {
                return true; // 遇到灰色节点，存在环
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
    private boolean hasPath(String start, String target, Map<String, List<String>> adj) {
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
