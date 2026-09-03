package com.njydsz.cronjob.server.core.dag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
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
}
}