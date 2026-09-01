package com.njydsz.workflow.server.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.workflow.domain.enums.FlowNodeType;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowSkipVO;

/**
 * P2-1: 流程定义图校验器
 *
 * <p>在流程定义部署前，对节点和跳转关系进行结构校验，防止部署"坏流程"：
 *
 * <ul>
 *   <li><b>起始节点</b> — 必须存在且仅存在一个 START 类型节点
 *   <li><b>结束节点</b> — 必须至少存在一个 END 类型节点
 *   <li><b>连通性</b> — 所有节点从 START 可达（BFS 遍历）
 *   <li><b>可达终止</b> — 每个非 END 节点都能到达某个 END 节点（反向 BFS）
 *   <li><b>悬空边</b> — 跳转的 source/target 必须引用已定义的节点
 *   <li><b>孤立节点</b> — 非 START 节点必须有入边，非 END 节点必须有出边
 * </ul>
 *
 * <p>注意：BPMN 中的循环（rework loop）是合法的，本校验器不拒绝环， 仅在日志中记录检测到的环路。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
public class FlowGraphValidator {

  /**
   * 校验流程定义图结构
   *
   * @param nodes 节点列表
   * @param skips 跳转列表
   * @throws IllegalArgumentException 图结构不合法时抛出
   */
  public void validate(List<FlowNodeVO> nodes, List<FlowSkipVO> skips) {
    if (nodes == null || nodes.isEmpty()) {
      throw new IllegalArgumentException("流程定义节点列表为空");
    }

    // 1. 构建节点索引
    Map<String, FlowNodeVO> nodeMap = buildNodeIndex(nodes);

    // 2. 检查 START / END 节点
    String startCode = validateStartAndEndNodes(nodes);

    // 3. 构建邻接表（正向 + 反向）
    GraphEdges edges = buildAdjacencyLists(nodeMap, skips);

    // 4. 连通性检查：从 START 出发 BFS，所有节点应可达
    validateReachability(startCode, edges, nodes);

    // 5. 可达终止检查：每个非 END 节点都能到达 END
    validateCanReachEnd(nodes, edges);

    // 6. 孤立节点检查
    validateNoIsolatedNodes(nodes, edges);

    // 7. 环路检测（仅记录日志，不拒绝）
    detectCycles(nodeMap.keySet(), edges.outEdges);

    log.info("[Flow-Validate] 流程图校验通过: nodes={} skips={}", nodes.size(), edges.validSkipCount);
  }

  /** 构建节点索引映射（nodeCode -> FlowNode），同时校验 nodeCode 非空且唯一。 */
  private Map<String, FlowNodeVO> buildNodeIndex(List<FlowNodeVO> nodes) {
    Map<String, FlowNodeVO> nodeMap = new HashMap<>(nodes.size());
    for (FlowNodeVO node : nodes) {
      String code = node.getNodeCode();
      if (!StringUtils.hasText(code)) {
        throw new IllegalArgumentException("存在 nodeCode 为空的节点");
      }
      if (nodeMap.containsKey(code)) {
        throw new IllegalArgumentException("节点编码重复: " + code);
      }
      nodeMap.put(code, node);
    }
    return nodeMap;
  }

  /**
   * 校验 START / END 节点存在性，返回起始节点编码。
   *
   * @param nodes 节点列表
   * @return 起始节点编码
   */
  private String validateStartAndEndNodes(List<FlowNodeVO> nodes) {
    List<FlowNodeVO> startNodes = nodes.stream()
        .filter(n -> FlowNodeType.START.getCode() == n.getNodeType()).toList();
    if (startNodes.isEmpty()) {
      throw new IllegalArgumentException("流程定义缺少开始节点（nodeType=0）");
    }
    if (startNodes.size() > 1) {
      throw new IllegalArgumentException("流程定义存在多个开始节点（仅允许一个）");
    }
    boolean hasEnd = nodes.stream().anyMatch(n -> FlowNodeType.END.getCode() == n.getNodeType());
    if (!hasEnd) {
      throw new IllegalArgumentException("流程定义缺少结束节点（nodeType=2）");
    }
    return startNodes.get(0).getNodeCode();
  }

  /** 图边数据结构：包含正向边、反向边和有效跳转计数。 */
  private static class GraphEdges {
    final Map<String, List<String>> outEdges;
    final Map<String, List<String>> inEdges;
    int validSkipCount;

    GraphEdges(Map<String, List<String>> outEdges, Map<String, List<String>> inEdges, int validSkipCount) {
      this.outEdges = outEdges;
      this.inEdges = inEdges;
      this.validSkipCount = validSkipCount;
    }
  }

  /**
   * 构建邻接表（正向 + 反向），并校验跳转无悬空边。
   *
   * @param nodeMap 节点索引映射
   * @param skips 跳转列表
   * @return 构建好的正/反向图边
   */
  private GraphEdges buildAdjacencyLists(Map<String, FlowNodeVO> nodeMap, List<FlowSkipVO> skips) {
    Map<String, List<String>> outEdges = new HashMap<>(nodeMap.size());
    Map<String, List<String>> inEdges = new HashMap<>(nodeMap.size());
    for (String code : nodeMap.keySet()) {
      outEdges.put(code, new ArrayList<>());
      inEdges.put(code, new ArrayList<>());
    }

    int validSkipCount = 0;
    if (skips != null) {
      for (FlowSkipVO skip : skips) {
        String source = extractSourceRef(skip);
        String target = skip.getNextNodeCode();
        if (!StringUtils.hasText(source) || !StringUtils.hasText(target)) {
          log.warn("[Flow-Validate] 跳转缺少 sourceRef 或 nextNodeCode: skip={}", skip.getSkipName());
          continue;
        }
        if (!nodeMap.containsKey(source)) {
          throw new IllegalArgumentException("跳转 sourceRef 指向不存在的节点: " + source);
        }
        if (!nodeMap.containsKey(target)) {
          throw new IllegalArgumentException("跳转 nextNodeCode 指向不存在的节点: " + target);
        }
        outEdges.get(source).add(target);
        inEdges.get(target).add(source);
        validSkipCount++;
      }
    }
    return new GraphEdges(outEdges, inEdges, validSkipCount);
  }

  /**
   * 校验所有节点从 START 可达。
   *
   * @param startCode 起始节点编码
   * @param edges 图边结构
   * @param nodes 节点列表
   */
  private void validateReachability(String startCode, GraphEdges edges, List<FlowNodeVO> nodes) {
    Set<String> reachable = bfs(startCode, edges.outEdges);
    List<String> unreachable = nodes.stream()
        .map(FlowNodeVO::getNodeCode)
        .filter(code -> !reachable.contains(code))
        .toList();
    if (!unreachable.isEmpty()) {
      throw new IllegalArgumentException("以下节点从开始节点不可达: " + unreachable);
    }
  }

  /**
   * 校验每个非 END 节点都能到达 END 节点。
   *
   * @param nodes 节点列表
   * @param edges 图边结构
   */
  private void validateCanReachEnd(List<FlowNodeVO> nodes, GraphEdges edges) {
    List<String> endNodes = nodes.stream()
        .filter(n -> FlowNodeType.END.getCode() == n.getNodeType())
        .map(FlowNodeVO::getNodeCode)
        .toList();
    Set<String> canReachEnd = new HashSet<>(nodes.size());
    for (String endCode : endNodes) {
      canReachEnd.addAll(bfs(endCode, edges.inEdges));
    }
    List<String> cannotReachEnd = nodes.stream()
        .filter(n -> FlowNodeType.END.getCode() != n.getNodeType())
        .map(FlowNodeVO::getNodeCode)
        .filter(code -> !canReachEnd.contains(code))
        .toList();
    if (!cannotReachEnd.isEmpty()) {
      throw new IllegalArgumentException("以下节点无法到达结束节点（死胡同）: " + cannotReachEnd);
    }
  }

  /**
   * 校验无孤立节点：非 START 节点有入边，非 END 节点有出边。
   *
   * @param nodes 节点列表
   * @param edges 图边结构
   */
  private void validateNoIsolatedNodes(List<FlowNodeVO> nodes, GraphEdges edges) {
    for (FlowNodeVO node : nodes) {
      String code = node.getNodeCode();
      int type = node.getNodeType();
      if (type != FlowNodeType.START.getCode() && edges.inEdges.get(code).isEmpty()) {
        throw new IllegalArgumentException("节点 " + code + " 没有入边（非开始节点必须有入边）");
      }
      if (type != FlowNodeType.END.getCode() && edges.outEdges.get(code).isEmpty()) {
        throw new IllegalArgumentException("节点 " + code + " 没有出边（非结束节点必须有出边）");
      }
    }
  }

  /**
   * 从指定起点 BFS 遍历，返回所有可达节点
   *
   * @param start 遍历起始节点编码
   * @param edges 邻接表边映射
   * @return 从起点可达的节点编码集合
   */
  private Set<String> bfs(String start, Map<String, List<String>> edges) {
    Set<String> visited = new HashSet<>(edges.size());
    Queue<String> queue = new LinkedList<>();
    queue.add(start);
    visited.add(start);
    while (!queue.isEmpty()) {
      String current = queue.poll();
      List<String> neighbors = edges.getOrDefault(current, List.of());
      for (String next : neighbors) {
        if (!visited.contains(next)) {
          visited.add(next);
          queue.add(next);
        }
      }
    }
    return visited;
  }

  /**
   * 从 FlowSkip.ext 中提取 sourceRef
   *
   * <p>委托 {@link FlowSkipUtils#extractSourceNodeCode} 统一实现，避免三处重复。
   *
   * @param skip 跳转实体
   * @return 源节点编码
   */
  private String extractSourceRef(FlowSkipVO skip) {
    return FlowSkipUtils.extractSourceNodeCode(skip);
  }

  /**
   * 环路检测（DFS + 颜色标记法），仅记录日志不拒绝
   *
   * @param nodeCodes 全部节点编码集合
   * @param edges 邻接表边映射
   */
  private void detectCycles(Set<String> nodeCodes, Map<String, List<String>> edges) {
    Set<String> visited = new HashSet<>(nodeCodes.size());
    Set<String> inStack = new HashSet<>(nodeCodes.size());
    for (String node : nodeCodes) {
      if (!visited.contains(node)) {
        List<String> path = new ArrayList<>(nodeCodes.size());
        List<String> cyclePath = new ArrayList<>(nodeCodes.size());
        if (dfsCycle(node, edges, visited, inStack, path, cyclePath)) {
          log.warn("[Flow-Validate] 检测到环路: {}", String.join(" → ", cyclePath));
        }
      }
    }
  }

  /**
   * DFS 环路检测
   *
   * @param node 当前节点
   * @param edges 边映射
   * @param visited 已访问节点集合
   * @param inStack 当前 DFS 栈中节点集合
   * @param path 当前 DFS 路径（回溯时会移除节点）
   * @param cyclePath 发现环时保存的完整环路路径（独立于 path，不受回溯影响）
   * @return true 表示发现环
   */
  private boolean dfsCycle(
      String node,
      Map<String, List<String>> edges,
      Set<String> visited,
      Set<String> inStack,
      List<String> path,
      List<String> cyclePath) {
    visited.add(node);
    inStack.add(node);
    path.add(node);

    for (String neighbor : edges.getOrDefault(node, List.of())) {
      if (!visited.contains(neighbor)) {
        if (dfsCycle(neighbor, edges, visited, inStack, path, cyclePath)) {
          return true;
        }
      } else if (inStack.contains(neighbor)) {
        // 发现环：从 path 中提取完整环路（neighbor → ... → node → neighbor）
        int idx = path.indexOf(neighbor);
        if (idx >= 0) {
          cyclePath.addAll(path.subList(idx, path.size()));
          cyclePath.add(neighbor);
        } else {
          // 兜底：至少记录当前节点和邻居
          cyclePath.add(node);
          cyclePath.add(neighbor);
        }
        return true;
      }
    }

    inStack.remove(node);
    path.remove(path.size() - 1);
    return false;
  }
}
