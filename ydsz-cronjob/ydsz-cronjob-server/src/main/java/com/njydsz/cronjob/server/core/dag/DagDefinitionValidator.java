package com.njydsz.cronjob.server.core.dag;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;

/**
 * P0-3: DAG 定义校验器（可视化编辑器后端校验）。
 *
 * <p>对 {@link DagDefinition} 进行全面的结构和语义校验，确保存入 DB 的 DAG 定义可在运行时正确执行。
 *
 * <h3>校验规则</h3>
 *
 * <ol>
 *   <li><b>节点完整性</b>：nodes 非空，jobKey 非空且唯一
 *   <li><b>边完整性</b>：from/to 非空且引用已存在的节点
 *   <li><b>无自环</b>：边的 from ≠ to
 *   <li><b>无环检测</b>：DAG 不允许存在环（DFS 三色标记法）
 *   <li><b>根节点</b>：至少存在一个无入边的根节点
 *   <li><b>节点类型校验</b>：
 *       <ul>
 *         <li>CONDITION: conditionExpression 非空，SpEL 表达式语法合法
 *         <li>PARALLEL_GATEWAY: 入边数和出边数符合 Fork/Join 模式
 *         <li>SUB_WORKFLOW: subWorkflowDagKey 非空
 *         <li>APPROVAL: approvalUsers 非空
 *       </ul>
 *   <li><b>规模限制</b>：节点数 ≤ 200，边数 ≤ 500（防止过大 DAG 拖慢调度）
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class DagDefinitionValidator {

  /** 最大节点数 */
  private static final int MAX_NODES = 200;

  /** 最大边数 */
  private static final int MAX_EDGES = 500;

  /**
   * 校验 DAG 定义，不通过时抛出 {@link SysException}。
   *
   * @param definition DAG 定义
   * @throws SysException 校验不通过
   */
  public void validate(DagDefinition definition) {
    if (definition == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.cronjob.msg_dag_definition_empty")
          .build();
    }
    validateNodes(definition);
    validateEdges(definition);
    validateNoCycles(definition);
    validateRootNodes(definition);
    validateNodeTypes(definition);
    validateScale(definition);
  }

  /** 校验节点完整性。 */
  private void validateNodes(DagDefinition definition) {
    if (definition.nodes() == null || definition.nodes().isEmpty()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.cronjob.msg_dag_no_nodes")
          .build();
    }
    Set<String> seen = new HashSet<>();
    for (DagNode node : definition.nodes()) {
      if (node.jobKey() == null || node.jobKey().isBlank()) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .message("error.cronjob.msg_dag_node_key_missing")
            .build();
      }
      if (!seen.add(node.jobKey())) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .message("error.cronjob.msg_dag_node_key_duplicate")
            .build();
      }
    }
  }

  /** 校验边完整性和无自环。 */
  private void validateEdges(DagDefinition definition) {
    Set<String> nodeKeys = new HashSet<>();
    for (DagNode node : definition.nodes()) {
      nodeKeys.add(node.jobKey());
    }
    Set<String> seenEdges = new HashSet<>();
    for (DagEdge edge : definition.edges()) {
      if (edge.from() == null || edge.to() == null) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .message("error.cronjob.msg_dag_edge_invalid")
            .build();
      }
      if (!nodeKeys.contains(edge.from())) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .key("error.cronjob.msg_dag_edge_node_not_found")
            .params(edge.from())
            .build();
      }
      if (!nodeKeys.contains(edge.to())) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .key("error.cronjob.msg_dag_edge_node_not_found")
            .params(edge.to())
            .build();
      }
      if (edge.from().equals(edge.to())) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .key("error.cronjob.msg_dag_self_loop")
            .params(edge.from())
            .build();
      }
      // 重复边检测
      String edgeKey = edge.from() + "->" + edge.to();
      if (!seenEdges.add(edgeKey)) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .key("error.cronjob.msg_dag_duplicate_edge")
            .params(edge.from(), edge.to())
            .build();
      }
    }
  }

  /**
   * 环检测：DFS 三色标记法。
   *
   * <p>颜色含义：
   *
   * <ul>
   *   <li>WHITE(0): 未访问
   *   <li>GRAY(1): 正在访问（在当前 DFS 路径上）
   *   <li>BLACK(2): 已完成（所有后代已访问）
   * </ul>
   *
   * <p>遇到 GRAY 节点表示存在环。
   */
  private void validateNoCycles(DagDefinition definition) {
    Map<String, Integer> color = new HashMap<>();
    for (DagNode node : definition.nodes()) {
      color.put(node.jobKey(), 0); // WHITE
    }
    for (DagNode node : definition.nodes()) {
      if (color.get(node.jobKey()) == 0) {
        if (hasCycleDFS(node.jobKey(), definition, color)) {
          throw SysException.builder()
              .resultCode(YdszResultCode.BAD_REQUEST)
              .key("error.cronjob.msg_dag_cycle_detected")
              .params(node.jobKey())
              .build();
        }
      }
    }
  }

  /**
   * DFS 递归检测环。
   *
   * @return true 发现环
   */
  private boolean hasCycleDFS(String jobKey, DagDefinition definition, Map<String, Integer> color) {
    color.put(jobKey, 1); // GRAY
    for (DagEdge edge : definition.outgoingEdges(jobKey)) {
      String neighbor = edge.to();
      if (color.get(neighbor) == 1) {
        // 遇到 GRAY 节点，存在环
        log.warn("[DagValidator] 检测到环: {} -> {}", jobKey, neighbor);
        return true;
      }
      if (color.get(neighbor) == 0 && hasCycleDFS(neighbor, definition, color)) {
        return true;
      }
    }
    color.put(jobKey, 2); // BLACK
    return false;
  }

  /** 校验至少存在一个根节点（无入边）。 */
  private void validateRootNodes(DagDefinition definition) {
    List<DagNode> roots = definition.rootNodes();
    if (roots.isEmpty()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.cronjob.msg_dag_no_root")
          .build();
    }
  }

  /**
   * 校验节点类型特定约束。
   *
   * <p>支持的节点类型校验规则：
   *
   * <ul>
   *   <li>TASK: jobId 非空</li>
   *   <li>CONDITION: conditionExpression 非空且 SpEL 语法合法</li>
   *   <li>PARALLEL_GATEWAY: 入边数和出边数符合 Fork/Join 模式</li>
   *   <li>SUB_WORKFLOW: subWorkflowDagKey 非空</li>
   *   <li>APPROVAL: approvalUsers 非空</li>
   * </ul>
   */
  private void validateNodeTypes(DagDefinition definition) {
    for (DagNode node : definition.nodes()) {
      DagNode.NodeType type = node.resolveNodeType();
      switch (type) {
        case TASK -> {
          // TASK 节点要求 jobId 非空（关联具体任务）
          if (node.jobId() == null || node.jobId().isBlank()) {
            throw SysException.builder()
                .resultCode(YdszResultCode.BAD_REQUEST)
                .key("error.cronjob.msg_dag_task_job_id_missing")
                .params(node.jobKey())
                .build();
          }
        }
        case CONDITION -> {
          // P1-1: 条件节点要求 conditionExpression 非空
          if (node.conditionExpression() == null || node.conditionExpression().isBlank()) {
            throw SysException.builder()
                .resultCode(YdszResultCode.BAD_REQUEST)
                .key("error.cronjob.msg_dag_condition_expression_missing")
                .params(node.jobKey())
                .build();
          }
          // P1-1: 校验 SpEL 表达式语法合法性
          validateSpelExpression(node.conditionExpression(), node.jobKey());
          // P1-1: 条件节点要求至少 2 个出边（true/false 分支）
          List<DagEdge> outgoing = definition.outgoingEdges(node.jobKey());
          if (outgoing.size() < 2) {
            throw SysException.builder()
                .resultCode(YdszResultCode.BAD_REQUEST)
                .key("error.cronjob.msg_dag_condition_branches_insufficient")
                .params(node.jobKey())
                .build();
          }
        }
        case PARALLEL_GATEWAY -> {
          // P1-1: 并行网关校验 Fork/Join 模式
          List<DagEdge> incoming = definition.incomingEdges(node.jobKey());
          List<DagEdge> outgoing = definition.outgoingEdges(node.jobKey());
          boolean isForkMode = incoming.size() <= 1 && outgoing.size() > 1;
          boolean isJoinMode = incoming.size() > 1 && outgoing.size() <= 1;
          boolean isPassThrough = incoming.size() <= 1 && outgoing.size() <= 1;
          if (!isForkMode && !isJoinMode && !isPassThrough) {
            throw SysException.builder()
                .resultCode(YdszResultCode.BAD_REQUEST)
                .key("error.cronjob.msg_dag_parallel_gateway_invalid")
                .params(node.jobKey())
                .build();
          }
        }
        case SUB_WORKFLOW -> {
          // P1-5: 子工作流节点要求 subWorkflowDagKey 非空
          if (node.subWorkflowDagKey() == null || node.subWorkflowDagKey().isBlank()) {
            throw SysException.builder()
                .resultCode(YdszResultCode.BAD_REQUEST)
                .key("error.cronjob.msg_dag_sub_workflow_key_missing")
                .params(node.jobKey())
                .build();
          }
        }
        case APPROVAL -> {
          // P1-6: 审批节点要求 approvalUsers 非空
          if (node.approvalUsers() == null || node.approvalUsers().isBlank()) {
            throw SysException.builder()
                .resultCode(YdszResultCode.BAD_REQUEST)
                .key("error.cronjob.msg_dag_approval_users_missing")
                .params(node.jobKey())
                .build();
          }
          if (node.approvalTimeoutMinutes() != null && node.approvalTimeoutMinutes() <= 0) {
            throw SysException.builder()
                .resultCode(YdszResultCode.BAD_REQUEST)
                .key("error.cronjob.msg_dag_approval_timeout_invalid")
                .params(node.jobKey())
                .build();
          }
        }
        default -> {
          // LOOP 已废弃，resolveNodeType() 已降级为 TASK，
          // 此分支理论上不会到达，保留作为防御性编程
        }
      }
    }
  }

  /**
   * P1-1: 校验 SpEL 表达式语法合法性。
   *
   * <p>尝试解析表达式，若抛出异常则说明语法不合法。
   *
   * @param expression SpEL 表达式
   * @param jobKey 节点 KEY（用于错误提示）
   * @throws SysException 表达式语法不合法
   */
  private void validateSpelExpression(String expression, String jobKey) {
    try {
      String expr = expression;
      // 支持 #{...} 格式
      if (expr.startsWith("#{") && expr.endsWith("}")) {
        expr = expr.substring(2, expr.length() - 1);
      }
      // 仅解析不求值，仅校验语法
      org.springframework.expression.ExpressionParser parser =
          new org.springframework.expression.spel.standard.SpelExpressionParser();
      parser.parseExpression(expr);
    } catch (Exception e) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.cronjob.msg_dag_condition_expression_invalid")
          .params(jobKey, e.getMessage())
          .build();
    }
  }

  /** 校验 DAG 规模限制。 */
  private void validateScale(DagDefinition definition) {
    if (definition.nodeCount() > MAX_NODES) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.cronjob.msg_dag_too_many_nodes")
          .params(MAX_NODES, definition.nodeCount())
          .build();
    }
    if (definition.edges().size() > MAX_EDGES) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.cronjob.msg_dag_too_many_edges")
          .params(MAX_EDGES, definition.edges().size())
          .build();
    }
  }
}
