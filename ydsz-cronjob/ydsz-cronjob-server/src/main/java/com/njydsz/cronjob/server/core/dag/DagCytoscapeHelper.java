package com.njydsz.cronjob.server.core.dag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.njydsz.cronjob.domain.dag.DagNodeStatus;

/**
 * DAG Cytoscape.js 可视化辅助类（P1-E1）。
 *
 * <p>将 {@link DagDefinition} + 节点实例状态转换为 Cytoscape.js 兼容的 JSON 格式， 前端可直接用于渲染 DAG 执行拓扑图，包含实时状态颜色。
 *
 * <h3>输出格式</h3>
 *
 * <pre>{@code
 * {
 *   "nodes": [
 *     {
 *       "data": {
 *         "id": "a",
 *         "label": "抽取",
 *         "nodeType": "TASK",
 *         "color": "#28a745",
 *         "shape": "round-rectangle",
 *         "status": "SUCCESS",
 *         "durationMs": 1234
 *       }
 *     }
 *   ],
 *   "edges": [
 *     {
 *       "data": {
 *         "id": "edge_a_b",
 *         "source": "a",
 *         "target": "b",
 *         "condition": "${a.result=='success'}"
 *       }
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <h3>状态颜色映射</h3>
 *
 * <ul>
 *   <li>SUCCESS: 绿色 #28a745
 *   <li>RUNNING: 蓝色 #007bff
 *   <li>FAILED: 红色 #dc3545
 *   <li>PENDING: 灰色 #6c757d
 *   <li>CANCELED: 橙色 #fd7e14
 *   <li>PAUSED: 黄色 #ffc107
 * </ul>
 *
 * <h3>节点形状映射</h3>
 *
 * <ul>
 *   <li>TASK: round-rectangle（圆角矩形）
 *   <li>SUB_WORKFLOW: barrel（圆筒形）
 *   <li>APPROVAL: star（星形）
 * </ul>
 *
 * <p>P2-E2：CONDITION / LOOP / PARALLEL_GATEWAY 控制节点已于 26.09.01 废弃（反序列化时降级为 TASK），
 * 不再输出专用形状，统一按 TASK 渲染，避免前端展示与运行时能力不一致。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class DagCytoscapeHelper {

  private DagCytoscapeHelper() {
    // 工具类
  }

  /**
   * 将 DAG 定义 + 节点实例状态映射转换为 Cytoscape.js 兼容格式。
   *
   * @param definition DAG 定义（节点 + 边）
   * @param nodeStatusMap 节点状态映射: jobKey -> 状态字符串（PENDING/RUNNING/SUCCESS/FAILED/CANCELED/PAUSED）
   * @param durationMap 节点耗时映射: jobKey -> 耗时毫秒数（可为 null）
   * @return Cytoscape.js 兼容的 {nodes, edges} Map
   */
  public static Map<String, Object> toCytoscapeFormat(
      DagDefinition definition, Map<String, String> nodeStatusMap, Map<String, Long> durationMap) {

    List<Map<String, Object>> cytoscapeNodes =
        definition.nodes().stream()
            .map(
                node -> {
                  String jobKey = node.jobKey();
                  String status = nodeStatusMap.getOrDefault(jobKey, DagNodeStatus.PENDING.name());
                  Long duration = durationMap != null ? durationMap.get(jobKey) : null;

                  Map<String, Object> nodeData = new LinkedHashMap<>(16);
                  nodeData.put("id", jobKey);
                  nodeData.put("label", node.label() != null ? node.label() : jobKey);
                  nodeData.put("nodeType", node.nodeType());
                  nodeData.put("color", colorForStatus(status));
                  nodeData.put("shape", shapeForNodeType(node.nodeType()));
                  nodeData.put("status", status);
                  if (duration != null && duration > 0) {
                    nodeData.put("durationMs", duration);
                  }
                  // 保留前端画布坐标（Cytoscape.js preset layout 使用）
                  nodeData.put("x", node.x());
                  nodeData.put("y", node.y());

                  return Map.of("data", (Object) nodeData);
                })
            .toList();

    List<Map<String, Object>> cytoscapeEdges =
        definition.edges().stream()
            .map(
                edge -> {
                  Map<String, Object> edgeData = new LinkedHashMap<>(16);
                  edgeData.put("id", "edge_" + edge.from() + "_" + edge.to());
                  edgeData.put("source", edge.from());
                  edgeData.put("target", edge.to());
                  if (edge.condition() != null && !edge.condition().isBlank()) {
                    edgeData.put("condition", edge.condition());
                  }
                  if (edge.failStrategy() != null && !edge.failStrategy().isBlank()) {
                    edgeData.put("failStrategy", edge.failStrategy());
                  }
                  return Map.of("data", (Object) edgeData);
                })
            .toList();

    Map<String, Object> result = new LinkedHashMap<>(16);
    result.put("nodes", cytoscapeNodes);
    result.put("edges", cytoscapeEdges);
    return result;
  }

  /**
   * 根据节点状态返回对应的颜色 hex 值。
   *
   * @param status 状态字符串（PENDING / RUNNING / SUCCESS / FAILED / CANCELED / PAUSED）
   * @return 颜色 hex（如 #28a745），未知状态返回灰色
   */
  public static String colorForStatus(String status) {
    if (status == null) {
      return "#6c757d";
    }
    return switch (status.toUpperCase()) {
      case "SUCCESS" -> "#28a745";
      case "RUNNING" -> "#007bff";
      case "FAILED" -> "#dc3545";
      case "PENDING" -> "#6c757d";
      case "CANCELED" -> "#fd7e14";
      case "PAUSED" -> "#ffc107";
      default -> "#6c757d";
    };
  }

  /**
   * 根据节点类型返回对应的 Cytoscape.js 形状名称。
   *
   * @param nodeType 节点类型字符串（TASK / SUB_WORKFLOW / APPROVAL）
   * @return Cytoscape.js 形状名称，未知/废弃类型统一返回 round-rectangle
   */
  public static String shapeForNodeType(String nodeType) {
    if (nodeType == null) {
      return "round-rectangle";
    }
    return switch (nodeType.toUpperCase()) {
      // P2-E2: CONDITION/LOOP/PARALLEL_GATEWAY 已于 26.09.01 废弃（反序列化降级为 TASK），统一矩形渲染
      case "SUB_WORKFLOW" -> "barrel";
      case "APPROVAL" -> "star";
      default -> "round-rectangle";
    };
  }

  /**
   * 安全获取状态（处理 null 和空字符串）。
   *
   * @param status 原始状态字符串
   * @return 有效状态或 "PENDING"
   */
  public static String safeStatus(String status) {
    return (status == null || status.isBlank()) ? DagNodeStatus.PENDING.name() : status;
  }
}
