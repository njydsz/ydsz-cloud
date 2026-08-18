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

import com.njydsz.workflow.infra.entity.FlowNodeDO;
import com.njydsz.workflow.infra.entity.FlowSkipDO;
import com.njydsz.workflow.domain.enums.FlowNodeType;

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
  public void validate(List<FlowNodeDO> nodes, List<FlowSkipDO> skips) {
    if (nodes == null || nodes.isEmpty()) {
      throw new IllegalArgumentException("流程定义节点列表为空");
    }

    // 1. 构建节点索引
    Map<String, FlowNodeDO> nodeMap = new HashMap<>();
    for (FlowNodeDO node : nodes) {
      String code = node.getNodeCode();
      if (!StringUtils.hasText(code)) {
        throw new IllegalArgumentException("存在 nodeCode 为空的节点");
      }
      if (nodeMap.containsKey(code)) {
        throw new IllegalArgumentException("节点编码重复: " + code);
      }
      nodeMap.put(code, node);
    }

    // 2. 检查 START / END 节点
    List<FlowNodeDO> startNodes =
        nodes.stream().filter(n -> FlowNodeType.START.getCode() == n.getNodeType()).toList();
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

    String startCode = startNodes.get(0).getNodeCode();

    // 3. 构建邻接表（正向 + 反向）
    Map<String, List<String>> outEdges = new HashMap<>(); // source → [target...]
    Map<String, List<String>> inEdges = new HashMap<>(); // target → [source...]
    for (String code : nodeMap.keySet()) {
      outEdges.put(code, new ArrayList<>());
      inEdges.put(code, new ArrayList<>());
    }

    Set<String> validSkips = new HashSet<>();
    if (skips != null) {
      for (FlowSkipDO skip : skips) {
        String source = extractSourceRef(skip);
        String target = skip.getNextNodeCode();

        if (!StringUtils.hasText(source)) {
          log.warn("[Flow-Validate] 跳转缺少 sourceRef: skip={}", skip.getSkipName());
          continue;
        }
        if (!StringUtils.hasText(target)) {
          log.warn("[Flow-Validate] 跳转缺少 nextNodeCode: skip={}", skip.getSkipName());
          continue;
        }

        // 悬空边检查
        if (!nodeMap.containsKey(source)) {
          throw new IllegalArgumentException("跳转 sourceRef 指向不存在的节点: " + source);
        }
        if (!nodeMap.containsKey(target)) {
          throw new IllegalArgumentException("跳转 nextNodeCode 指向不存在的节点: " + target);
        }

        outEdges.get(source).add(target);
        inEdges.get(target).add(source);
        validSkips.add(source + "->" + target);
      }
    }

    // 4. 连通性检查：从 START 出发 BFS，所有节点应可达
    Set<String> reachable = bfs(startCode, outEdges);
    List<String> unreachable =
        nodes.stream()
            .map(FlowNodeDO::getNodeCode)
            .filter(code -> !reachable.contains(code))
            .toList();
    if (!unreachable.isEmpty()) {
      throw new IllegalArgumentException("以下节点从开始节点不可达: " + unreachable);
    }

    // 5. 可达终止检查：每个非 END 节点都能到达 END（反向 BFS 从所有 END 出发）
    List<String> endNodes =
        nodes.stream()
            .filter(n -> FlowNodeType.END.getCode() == n.getNodeType())
            .map(FlowNodeDO::getNodeCode)
            .toList();
    Set<String> canReachEnd = new HashSet<>();
    for (String endCode : endNodes) {
      canReachEnd.addAll(bfs(endCode, inEdges));
    }
    List<String> cannotReachEnd =
        nodes.stream()
            .filter(n -> FlowNodeType.END.getCode() != n.getNodeType())
            .map(FlowNodeDO::getNodeCode)
            .filter(code -> !canReachEnd.contains(code))
            .toList();
    if (!cannotReachEnd.isEmpty()) {
      throw new IllegalArgumentException("以下节点无法到达结束节点（死胡同）: " + cannotReachEnd);
    }

    // 6. 孤立节点检查
    for (FlowNodeDO node : nodes) {
      String code = node.getNodeCode();
      int type = node.getNodeType();
      if (type != FlowNodeType.START.getCode() && inEdges.get(code).isEmpty()) {
        throw new IllegalArgumentException("节点 " + code + " 没有入边（非开始节点必须有入边）");
      }
      if (type != FlowNodeType.END.getCode() && outEdges.get(code).isEmpty()) {
        throw new IllegalArgumentException("节点 " + code + " 没有出边（非结束节点必须有出边）");
      }
    }

    // 7. 环路检测（仅记录日志，不拒绝）
    detectCycles(nodeMap.keySet(), outEdges);

    log.info("[Flow-Validate] 流程图校验通过: nodes={} skips={}", nodes.size(), validSkips.size());
  }

  /** 从指定起点 BFS 遍历，返回所有可达节点 */
  private Set<String> bfs(String start, Map<String, List<String>> edges) {
    Set<String> visited = new HashSet<>();
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
   * 从 FlowSkipDO.ext 中提取 sourceRef
   *
   * <p>委托 {@link FlowSkipUtils#extractSourceNodeCode} 统一实现，避免三处重复。
   */
  private String extractSourceRef(FlowSkipDO skip) {
    return FlowSkipUtils.extractSourceNodeCode(skip);
  }

  /** 环路检测（DFS + 颜色标记法），仅记录日志不拒绝 */
  private void detectCycles(Set<String> nodeCodes, Map<String, List<String>> edges) {
    Set<String> visited = new HashSet<>();
    Set<String> inStack = new HashSet<>();
    for (String node : nodeCodes) {
      if (!visited.contains(node)) {
        List<String> path = new ArrayList<>();
        List<String> cyclePath = new ArrayList<>();
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
