package com.njydsz.literule.server.orchestrator;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 规则链画布图验证器（P0-1）
 *
 * <p>对可视化编排画布进行结构合法性检查，校验项：
 *
 * <ul>
 *   <li>自环：禁止边的 source 与 target 指向同一节点
 *   <li>重复边：禁止 source-target-edgeType 三个字段都相同的边重复出现
 *   <li>未连接节点：禁止除根节点外的孤立节点（无边相连）
 *   <li>悬空引用：边的 source/target 必须指向已存在的节点
 *   <li>根节点：必须有且仅有一个根节点（CHAIN 类型 + parentNodeId 为空）
 *   <li>SINGLE 节点必须设置 ruleCode
 * </ul>
 *
 * <p>典型用法：
 *
 * <pre>
 *   List&lt;GraphValidationIssue&gt; issues = RuleGraphValidator.validate(graph);
 *   if (!issues.isEmpty()) {
 *       // 提示用户修复后再保存
 *   }
 * </pre>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
public final class RuleGraphValidator {

  private RuleGraphValidator() {}

  /**
   * 验证画布图，返回所有问题
   *
   * @param graph 画布图
   * @return 问题列表；为空表示无问题
   */
  public static List<GraphValidationIssue> validate(RuleChainGraph graph) {
    List<GraphValidationIssue> issues = new ArrayList<>(16);
    if (graph == null) {
      issues.add(new GraphValidationIssue("GRAPH_NULL", "画布图对象为空", null));
      return issues;
    }

    List<ChainNodeDTO> nodes = graph.getNodes();
    List<ChainEdgeDTO> edges = graph.getEdges();

    if (nodes == null || nodes.isEmpty()) {
      issues.add(new GraphValidationIssue("NO_NODES", "画布中没有任何节点", null));
      return issues;
    }

    // 建立 nodeId -> node 的索引
    Map<String, ChainNodeDTO> nodeIndex = new HashMap<>(nodes.size());
    for (ChainNodeDTO node : nodes) {
      if (node.getNodeId() != null) {
        nodeIndex.put(node.getNodeId(), node);
      }
    }

    // 1. 自环检测 + 2. 重复边检测 + 悬空引用检测
    Set<String> edgeFingerprints = new HashSet<>(edges != null ? edges.size() : 0);
    Set<String> connectedNodeIds = new HashSet<>();

    if (edges != null) {
      for (ChainEdgeDTO edge : edges) {
        String sourceId = edge.getSourceNodeId();
        String targetId = edge.getTargetNodeId();
        String edgeType = edge.getEdgeType();

        // 悬空引用检测
        if (sourceId == null || !nodeIndex.containsKey(sourceId)) {
          issues.add(new GraphValidationIssue(
              "DANGLING_SOURCE",
              String.format("边[%s]的起点节点[%s]不存在", edge.getEdgeId(), sourceId),
              edge.getEdgeId()));
        }
        if (targetId == null || !nodeIndex.containsKey(targetId)) {
          issues.add(new GraphValidationIssue(
              "DANGLING_TARGET",
              String.format("边[%s]的终点节点[%s]不存在", edge.getEdgeId(), targetId),
              edge.getEdgeId()));
        }

        // 自环检测
        if (sourceId != null && sourceId.equals(targetId)) {
          issues.add(new GraphValidationIssue(
              "SELF_LOOP",
              String.format("节点[%s]存在自环边", sourceId),
              sourceId));
        }

        // 重复边检测
        if (sourceId != null && targetId != null) {
          String fingerprint = sourceId + "|" + targetId + "|" + (edgeType != null ? edgeType : "");
          if (!edgeFingerprints.add(fingerprint)) {
            issues.add(new GraphValidationIssue(
                "DUPLICATE_EDGE",
                String.format("重复边: %s -> %s (%s)", sourceId, targetId, edgeType),
                edge.getEdgeId()));
          }
        }

        // 收集已连接的节点
        if (sourceId != null) {
          connectedNodeIds.add(sourceId);
        }
        if (targetId != null) {
          connectedNodeIds.add(targetId);
        }
      }
    }

    // 3. 未连接节点检测（排除根节点：CHAIN 类型 + parentNodeId 为空）
    for (ChainNodeDTO node : nodes) {
      String nodeId = node.getNodeId();
      if (nodeId == null) {
        continue;
      }
      boolean isRoot = "CHAIN".equals(node.getNodeType())
          && (node.getParentNodeId() == null || node.getParentNodeId().isBlank());
      if (!isRoot && !connectedNodeIds.contains(nodeId)) {
        issues.add(new GraphValidationIssue(
            "DISCONNECTED_NODE",
            String.format("节点[%s]孤立无连接", nodeId),
            nodeId));
      }
    }

    // 4. 根节点检测：必须有且仅有一个根节点
    List<String> rootNodeIds = new ArrayList<>();
    for (ChainNodeDTO node : nodes) {
      if ("CHAIN".equals(node.getNodeType())
          && (node.getParentNodeId() == null || node.getParentNodeId().isBlank())) {
        rootNodeIds.add(node.getNodeId());
      }
    }
    if (rootNodeIds.isEmpty()) {
      issues.add(new GraphValidationIssue("NO_ROOT", "画布缺少根节点（CHAIN 类型且 parentNodeId 为空的节点）", null));
    } else if (rootNodeIds.size() > 1) {
      issues.add(new GraphValidationIssue(
          "MULTIPLE_ROOTS",
          String.format("画布存在多个根节点: %s", rootNodeIds),
          null));
    }

    // 5. SINGLE 节点必须设置 ruleCode
    for (ChainNodeDTO node : nodes) {
      if ("SINGLE".equals(node.getNodeType())
          && (node.getRuleCode() == null || node.getRuleCode().isBlank())) {
        issues.add(new GraphValidationIssue(
            "SINGLE_NO_RULECODE",
            String.format("SINGLE 类型节点[%s]未设置 ruleCode", node.getNodeId()),
            node.getNodeId()));
      }
    }

    return issues;
  }

  /**
   * 判断验证问题列表是否整体有效（无任何 ERROR 级别问题）
   *
   * <p>仅当存在 {@link Level#ERROR} 级别问题时视为无效（不可执行）。
   * WARN 级别问题不影响有效性，仅作提醒。
   *
   * @param issues 验证问题列表
   * @return true 表示有效（无 ERROR 级别问题），false 表示无效
   */
  public static boolean isValid(List<GraphValidationIssue> issues) {
    if (issues == null || issues.isEmpty()) {
      return true;
    }
    for (GraphValidationIssue issue : issues) {
      if (issue.getLevel() == Level.ERROR) {
        return false;
      }
    }
    return true;
  }

  /**
   * 验证问题严重级别枚举
   */
  public enum Level {
    /** 警告（不影响执行） */
    WARN,
    /** 错误（阻断执行） */
    ERROR
  }

  /**
   * 图验证问题描述
   *
   * @since 26.09.01
   * @author ydsz-team
   */
  @Data
  @AllArgsConstructor
  public static class GraphValidationIssue implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 问题代码（如 SELF_LOOP / DUPLICATE_EDGE / DISCONNECTED_NODE） */
    private final String code;

    /** 问题描述 */
    private final String message;

    /** 相关节点/边 ID（可能为 null） */
    private final String relatedId;

    /** 问题严重级别 */
    private Level level;

    public GraphValidationIssue(String code, String message, String relatedId) {
      this(code, message, relatedId, Level.ERROR);
    }
  }
}
