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
 *
 * <ul>
 *   <li>{@link #topologicalSort(Map)}：Kahn 算法拓扑排序
 *   <li>{@link #hasCycle(Map)}：基于入度的环检测
 *   <li>{@link #getDescendants(String, Map)}：BFS 遍历后代节点
 *   <li>{@link #getAncestors(String, Map)}：BFS 遍历祖先节点
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Component
public class DagParser {
  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;


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
    Map<String, Integer> inDegree = new HashMap<>(COLLECTION_CAPACITY);
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
    List<String> result = new ArrayList<>(COLLECTION_CAPACITY);
    while (!queue.isEmpty()) {
      String node = queue.poll();
      result.add(node);
      List<String> children = adj.get(node);
      if (children != null) {
        for (String child : children) {
          int newDegree = inDegree.get(child) - 1;
          inDegree.put(child, newDegree);
          if (newDegree == 0) {
            queue.add(child);
          }
        }
      }
    }
    // 如果结果节点数不等于邻接表节点数，说明存在环，返回空列表
    return result.size() == adj.size() ? result : Collections.emptyList();
  }

  /**
   * 检测图中是否存在环（基于入度）。
   *
   * <p>使用 Kahn 算法：移除所有入度为 0 的节点后，若仍有节点剩余，则存在环。
   *
   * @param adj 邻接表
   * @return true 存在环
   */
  public boolean hasCycle(Map<String, List<String>> adj) {
    if (adj == null || adj.isEmpty()) {
      return false;
    }
    Map<String, Integer> inDegree = new HashMap<>(COLLECTION_CAPACITY);
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
    int visited = 0;
    while (!queue.isEmpty()) {
      String node = queue.poll();
      visited++;
      List<String> children = adj.get(node);
      if (children != null) {
        for (String child : children) {
          int newDegree = inDegree.get(child) - 1;
          inDegree.put(child, newDegree);
          if (newDegree == 0) {
            queue.add(child);
          }
        }
      }
    }
    return visited != adj.size();
  }

  /**
   * BFS 遍历获取所有后代节点。
   *
   * @param start 起始节点
   * @param adj 邻接表
   * @return 后代节点列表
   */
  public List<String> getDescendants(String start, Map<String, List<String>> adj) {
    if (start == null || adj == null || !adj.containsKey(start)) {
      return Collections.emptyList();
    }
    List<String> descendants = new ArrayList<>();
    Deque<String> queue = new ArrayDeque<>();
    queue.add(start);
    Set<String> visited = new HashSet<>();
    visited.add(start);
    while (!queue.isEmpty()) {
      String node = queue.poll();
      List<String> children = adj.get(node);
      if (children != null) {
        for (String child : children) {
          if (visited.add(child)) {
            descendants.add(child);
            queue.add(child);
          }
        }
      }
    }
    return descendants;
  }

  /**
   * BFS 遍历获取所有祖先节点（反向邻接表）。
   *
   * @param start 起始节点
   * @param adj 邻接表（正向）
   * @return 祖先节点列表
   */
  public List<String> getAncestors(String start, Map<String, List<String>> adj) {
    if (start == null || adj == null) {
      return Collections.emptyList();
    }
    // 构建反向邻接表
    Map<String, List<String>> reverseAdj = new HashMap<>(COLLECTION_CAPACITY);
    for (Map.Entry<String, List<String>> entry : adj.entrySet()) {
      for (String child : entry.getValue()) {
        reverseAdj.computeIfAbsent(child, k -> new ArrayList<>()).add(entry.getKey());
      }
    }
    if (!reverseAdj.containsKey(start)) {
      return Collections.emptyList();
    }
    List<String> ancestors = new ArrayList<>();
    Deque<String> queue = new ArrayDeque<>();
    queue.add(start);
    Set<String> visited = new HashSet<>();
    visited.add(start);
    while (!queue.isEmpty()) {
      String node = queue.poll();
      List<String> parents = reverseAdj.get(node);
      if (parents != null) {
        for (String parent : parents) {
          if (visited.add(parent)) {
            ancestors.add(parent);
            queue.add(parent);
          }
        }
      }
    }
    return ancestors;
  }
}
