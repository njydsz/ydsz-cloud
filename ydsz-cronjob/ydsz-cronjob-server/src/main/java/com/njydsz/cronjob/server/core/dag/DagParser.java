package com.njydsz.cronjob.server.core.dag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * DAG 解析器。
 *
 * <p>提供 DAG 拓扑算法：拓扑排序、环检测、后代/祖先节点查询。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>{@link #topologicalSort(Map)}：Kahn 算法拓扑排序</li>
 *   <li>{@link #hasCycle(Map)}：基于入度的环检测</li>
 *   <li>{@link #getDescendants(String, Map)}：BFS 遍历后代节点</li>
 *   <li>{@link #getAncestors(String, Map)}：BFS 遍历祖先节点</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
public class DagParser {

    /**
     * 拓扑排序（Kahn 算法）。
     *
     * @param adj 邻接表
     * @return 拓扑排序结果；存在环时返回空列表
     */
    public List<String> topologicalSort(Map<String, List<String>> adj) {
        if (adj == null || adj.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Integer> inDegree = new HashMap<>();
        for (String node : adj.keySet()) {
            inDegree.putIfAbsent(node, 0);
        }
        for (List<String> children : adj.values()) {
            if (children != null) {
                for (String child : children) {
                    inDegree.merge(child, 1, Integer::sum);
                }
            }
        }
        Deque<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }
        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String node = queue.poll();
            result.add(node);
            List<String> children = adj.get(node);
            if (children != null) {
                for (String child : children) {
                    int newDeg = inDegree.merge(child, -1, Integer::sum);
                    if (newDeg == 0) {
                        queue.add(child);
                    }
                }
            }
        }
        if (result.size() != inDegree.size()) {
            return Collections.emptyList();
        }
        return result;
    }

    /**
     * 检测邻接表中是否存在环。
     *
     * @param adj 邻接表
     * @return true 表示存在环
     */
    public boolean hasCycle(Map<String, List<String>> adj) {
        if (adj == null || adj.isEmpty()) {
            return false;
        }
        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();
        for (String node : adj.keySet()) {
            if (dfsHasCycle(node, adj, visited, inStack)) {
                return true;
            }
        }
        return false;
    }

    private boolean dfsHasCycle(String node, Map<String, List<String>> adj,
                                 Set<String> visited, Set<String> inStack) {
        if (inStack.contains(node)) {
            return true;
        }
        if (visited.contains(node)) {
            return false;
        }
        visited.add(node);
        inStack.add(node);
        List<String> children = adj.get(node);
        if (children != null) {
            for (String child : children) {
                if (dfsHasCycle(child, adj, visited, inStack)) {
                    return true;
                }
            }
        }
        inStack.remove(node);
        return false;
    }

    /**
     * 获取起始节点的所有后代节点（BFS）。
     *
     * @param start 起始节点
     * @param adj   邻接表
     * @return 后代节点集合
     */
    public Set<String> getDescendants(String start, Map<String, List<String>> adj) {
        if (start == null || adj == null) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String node = queue.poll();
            List<String> children = adj.get(node);
            if (children != null) {
                for (String child : children) {
                    if (result.add(child)) {
                        queue.add(child);
                    }
                }
            }
        }
        return result;
    }

    /**
     * 获取目标节点的所有祖先节点（BFS，需构建反向邻接表）。
     *
     * @param target 目标节点
     * @param adj    邻接表
     * @return 祖先节点集合
     */
    public Set<String> getAncestors(String target, Map<String, List<String>> adj) {
        if (target == null || adj == null) {
            return Collections.emptySet();
        }
        Map<String, List<String>> reverse = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : adj.entrySet()) {
            String parent = entry.getKey();
            if (entry.getValue() != null) {
                for (String child : entry.getValue()) {
                    reverse.computeIfAbsent(child, k -> new ArrayList<>()).add(parent);
                }
            }
        }
        Set<String> result = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(target);
        while (!queue.isEmpty()) {
            String node = queue.poll();
            List<String> parents = reverse.get(node);
            if (parents != null) {
                for (String parent : parents) {
                    if (result.add(parent)) {
                        queue.add(parent);
                    }
                }
            }
        }
        return result;
    }
}
