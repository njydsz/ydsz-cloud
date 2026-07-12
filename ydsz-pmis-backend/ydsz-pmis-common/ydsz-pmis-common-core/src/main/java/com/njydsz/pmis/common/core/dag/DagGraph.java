package com.njydsz.pmis.common.core.dag;

import java.util.*;

/**
 * DAG 图结构定义与操作工具。
 *
 * <p>描述任务之间的有向无环图依赖关系，提供拓扑排序、环检测等静态方法。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class DagGraph {

    private final Map<String, List<String>> adjacency = new HashMap<>();
    private final Map<String, List<String>> reverseAdjacency = new HashMap<>();
    private final Set<String> nodes = new LinkedHashSet<>();

    public void addNode(String node) {
        nodes.add(node);
        adjacency.computeIfAbsent(node, k -> new ArrayList<>());
        reverseAdjacency.computeIfAbsent(node, k -> new ArrayList<>());
    }

    public void addEdge(String from, String to) {
        addNode(from);
        addNode(to);
        adjacency.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
        reverseAdjacency.computeIfAbsent(to, k -> new ArrayList<>()).add(from);
    }

    public Set<String> getNodes() {
        return Collections.unmodifiableSet(nodes);
    }

    public List<String> getSuccessors(String node) {
        return adjacency.getOrDefault(node, Collections.emptyList());
    }

    public List<String> getPredecessors(String node) {
        return reverseAdjacency.getOrDefault(node, Collections.emptyList());
    }

    public List<String> getRoots() {
        List<String> roots = new ArrayList<>();
        for (String node : nodes) {
            if (reverseAdjacency.getOrDefault(node, Collections.emptyList()).isEmpty()) {
                roots.add(node);
            }
        }
        return roots;
    }

    public boolean hasCycle() {
        return hasCycle(adjacency);
    }

    public List<String> topologicalSort() {
        return topologicalSort(adjacency);
    }

    // ==================== 静态方法 ====================

    /**
     * 对给定邻接表进行拓扑排序。
     *
     * @param adj 邻接表
     * @return 拓扑排序结果
     */
    public static List<String> topologicalSort(Map<String, List<String>> adj) {
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (String node : adj.keySet()) {
            topologicalSortDFS(node, adj, visited, result);
        }
        Collections.reverse(result);
        return result;
    }

    private static void topologicalSortDFS(String node, Map<String, List<String>> adj,
                                            Set<String> visited, List<String> result) {
        if (visited.contains(node)) {
            return;
        }
        visited.add(node);
        for (String successor : adj.getOrDefault(node, Collections.emptyList())) {
            topologicalSortDFS(successor, adj, visited, result);
        }
        result.add(node);
    }

    /**
     * 检测给定邻接表中是否存在环。
     *
     * @param adj 邻接表
     * @return 存在环返回 true
     */
    public static boolean hasCycle(Map<String, List<String>> adj) {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        for (String node : adj.keySet()) {
            if (hasCycleDFS(node, adj, visited, recursionStack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCycleDFS(String node, Map<String, List<String>> adj,
                                       Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(node)) {
            return true;
        }
        if (visited.contains(node)) {
            return false;
        }
        visited.add(node);
        recursionStack.add(node);
        for (String successor : adj.getOrDefault(node, Collections.emptyList())) {
            if (hasCycleDFS(successor, adj, visited, recursionStack)) {
                return true;
            }
        }
        recursionStack.remove(node);
        return false;
    }

    /**
     * 检测新增边是否会在图中形成环。
     *
     * @param parent 父节点
     * @param child  子节点
     * @param adj    现有邻接表
     * @return 形成环返回 true
     */
    public static boolean wouldCreateCycle(String parent, String child, Map<String, List<String>> adj) {
        if (parent == null || child == null) {
            return false;
        }
        if (parent.equals(child)) {
            return true;
        }
        // 检测从 child 是否能到达 parent（如果能，则添加 parent→child 会形成环）
        Set<String> visited = new HashSet<>();
        return canReach(child, parent, adj, visited);
    }

    private static boolean canReach(String from, String target, Map<String, List<String>> adj, Set<String> visited) {
        if (from.equals(target)) {
            return true;
        }
        if (visited.contains(from)) {
            return false;
        }
        visited.add(from);
        for (String successor : adj.getOrDefault(from, Collections.emptyList())) {
            if (canReach(successor, target, adj, visited)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取给定节点的所有后代节点（递归遍历邻接表）。
     *
     * @param start 起始节点
     * @param adj   邻接表
     * @return 所有后代节点集合（不含起始节点自身）
     */
    public static Set<String> getDescendants(String start, Map<String, List<String>> adj) {
        Set<String> result = new LinkedHashSet<>();
        if (start == null || adj == null) {
            return result;
        }
        collectDescendants(start, adj, result, new HashSet<>());
        result.remove(start);
        return result;
    }

    private static void collectDescendants(String node, Map<String, List<String>> adj,
                                            Set<String> result, Set<String> visited) {
        if (visited.contains(node)) {
            return;
        }
        visited.add(node);
        for (String successor : adj.getOrDefault(node, Collections.emptyList())) {
            result.add(successor);
            collectDescendants(successor, adj, result, visited);
        }
    }

    /**
     * 获取给定节点的所有祖先节点（递归遍历反向邻接表）。
     *
     * @param target 目标节点
     * @param adj    正向邻接表（自动构建反向）
     * @return 所有祖先节点集合（不含目标节点自身）
     */
    public static Set<String> getAncestors(String target, Map<String, List<String>> adj) {
        Set<String> result = new LinkedHashSet<>();
        if (target == null || adj == null) {
            return result;
        }
        // 构建反向邻接表
        Map<String, List<String>> reverseAdj = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : adj.entrySet()) {
            String from = entry.getKey();
            for (String to : entry.getValue()) {
                reverseAdj.computeIfAbsent(to, k -> new ArrayList<>()).add(from);
            }
        }
        collectDescendants(target, reverseAdj, result, new HashSet<>());
        result.remove(target);
        return result;
    }
}
