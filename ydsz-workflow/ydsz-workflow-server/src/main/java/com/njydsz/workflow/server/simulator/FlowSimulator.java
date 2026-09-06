package com.njydsz.workflow.server.simulator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.enums.FlowNodeType;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowSkipVO;
import com.njydsz.workflow.server.engine.FlowDefinitionCacheService;
import com.njydsz.workflow.server.engine.impl.DefaultFlowAdvancer;

/**
 * 流程模拟执行器
 *
 * <p>在不创建实际实例的情况下模拟执行流程定义，预测执行路径。
 *
 * <p>模拟逻辑：
 * <ul>
 *   <li>从开始节点出发，按条件逐个推进
 *   <li>遇到并行网关时模拟所有分支（不合并）
 *   <li>遇到用户任务记录但不等待
 *   <li>遇到排他/包容网关时计算条件表达式，选择一个或多个分支
 *   <li>遇到服务节点时执行脚本（可选 mock）
 *   <li>最大模拟步数 1000 步，防止死循环
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowSimulator {

  /** 最大模拟步数安全阈值 */
  private static final int MAX_SIMULATION_STEPS = 1000;

  private final FlowDefinitionCacheService definitionCacheService;

  private final DefaultFlowAdvancer flowAdvancer;

  /**
   * 执行流程模拟
   *
   * @param ctx 模拟上下文
   * @return 模拟结果（步骤序列 + 访问节点 + 警告）
   */
  public SimulationResult simulate(SimulationContext ctx) {
    String definitionId = ctx.getDefinitionId();

    // 1. 定位开始节点
    FlowNodeVO startNode = definitionCacheService.getStartNode(definitionId);
    if (startNode == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.INTERNAL_ERROR)
          .key("error.workflow.simulate.start.node.missing")
          .params(definitionId)
          .build();
    }

    ctx.advanceTo(startNode.getNodeCode(), "START");
    ctx.addStep(new SimulationStep(0, startNode.getNodeCode(), startNode.getNodeName(),
        FlowNodeType.START.name(), "开始节点", null));

    // 2. 模拟推进
    simulateFromNode(ctx, startNode);

    // 3. 组装结果
    SimulationResult result = new SimulationResult();
    result.setSteps(ctx.getSteps());
    result.setVisitedNodes(ctx.getVisitedNodes());
    result.setEndNode(ctx.getEndNode());
    result.setReachedEnd(ctx.isReachedEnd());
    result.setWarnings(ctx.getWarnings());

    return result;
  }

  /**
   * 从指定节点开始模拟推进
   *
   * @param ctx 模拟上下文
   * @param currentNode 当前节点
   */
  private void simulateFromNode(SimulationContext ctx, FlowNodeVO currentNode) {
    if (ctx.isMaxStepsReached()) {
      ctx.addWarning("达到最大模拟步数 " + MAX_SIMULATION_STEPS + "，模拟终止以防死循环");
      log.warn("[Flow-Simulate] 达到最大步数: definitionId={}", ctx.getDefinitionId());
      return;
    }

    String definitionId = ctx.getDefinitionId();
    String nodeCode = currentNode.getNodeCode();
    Integer nodeType = currentNode.getNodeType();

    // 结束节点 — 模拟完成
    if (nodeType != null && nodeType == FlowNodeType.END.getCode()) {
      ctx.markReachedEnd(nodeCode);
      log.info("[Flow-Simulate] 模拟到达结束节点: {}", nodeCode);
      return;
    }

    // 解析出边（复用 FlowAdvancer 的条件评估逻辑）
    List<FlowSkipVO> matchedSkips = resolveMatchingSkips(ctx, currentNode);

    if (matchedSkips.isEmpty()) {
      // 无出边 — 流程异常结束
      ctx.addWarning("节点 " + nodeCode + " 无出边，模拟终止");
      ctx.markReachedEnd(nodeCode);
      return;
    }

    // 判定是否为网关节点
    boolean isExclusive = nodeType != null && nodeType == FlowNodeType.CONDITION.getCode();
    boolean isInclusive = nodeType != null && nodeType == FlowNodeType.INCLUSIVE.getCode();
    boolean isParallel = nodeType != null && nodeType == FlowNodeType.PARALLEL.getCode();

    if (isParallel) {
      // 并行网关：模拟所有分支
      for (FlowSkipVO skip : matchedSkips) {
        simulateSkip(ctx, skip, "PARALLEL_BRANCH");
      }
    } else if (isExclusive) {
      // 排他网关：取第一个匹配
      simulateSkip(ctx, matchedSkips.get(0), "EXCLUSIVE_PASS");
      if (matchedSkips.size() > 1) {
        for (int i = 1; i < matchedSkips.size(); i++) {
          String skippedNode = matchedSkips.get(i).getNextNodeCode();
          ctx.addStep(new SimulationStep(ctx.getStepCount(), skippedNode, null,
              "SKIPPED", "排他网关未选中的分支", null));
        }
      }
    } else if (isInclusive) {
      // 包容网关：取所有匹配
      for (FlowSkipVO skip : matchedSkips) {
        simulateSkip(ctx, skip, "INCLUSIVE_PASS");
      }
    } else {
      // 普通节点：逐一推进
      for (FlowSkipVO skip : matchedSkips) {
        simulateSkip(ctx, skip, "PASS");
      }
    }
  }

  /**
   * 模拟单个跳转
   *
   * @param ctx 模拟上下文
   * @param skip 跳转
   * @param action 动作类型描述
   */
  private void simulateSkip(SimulationContext ctx, FlowSkipVO skip, String action) {
    String nextNodeCode = skip.getNextNodeCode();
    FlowNodeVO nextNode =
        definitionCacheService.getNodeByCode(ctx.getDefinitionId(), nextNodeCode);
    if (nextNode == null) {
      ctx.addStep(new SimulationStep(ctx.getStepCount(), nextNodeCode, null,
          "MISSING", "节点不存在", skip.getSkipCondition()));
      return;
    }

    ctx.advanceTo(nextNodeCode, action);
    String nodeTypeName = FlowNodeType.of(nextNode.getNodeType()).name();

    // 根据节点类型记录步骤
    String stepDetail = switch (FlowNodeType.of(nextNode.getNodeType())) {
      case APPROVAL, LEVEL_APPROVAL, AI_AGENT ->
          "用户任务节点（模拟：记录但不等待）";
      case SERVICE ->
          "服务节点（模拟：自动执行）";
      case CC ->
          "抄送节点（模拟：自动通过）";
      case CONDITION ->
          "排他网关（模拟：计算条件取分支）";
      case PARALLEL ->
          "并行网关（模拟：展开所有分支）";
      case INCLUSIVE ->
          "包容网关（模拟：取所有匹配分支）";
      case SUBPROCESS ->
          "子流程节点（模拟：跳过子流程内部）";
      case FOREACH ->
          "循环节点（模拟：单迭代）";
      case END ->
          "结束节点（模拟完成）";
      default ->
          "节点";
    };

    ctx.addStep(new SimulationStep(ctx.getStepCount(), nextNodeCode,
        nextNode.getNodeName(), nodeTypeName, stepDetail, skip.getSkipCondition()));

    // 递归推进（除了结束节点和子流程节点）    Integer nextType = nextNode.getNodeType();
    if (nextType != null && nextType != FlowNodeType.END.getCode()
        && nextType != FlowNodeType.SUBPROCESS.getCode()) {
      simulateFromNode(ctx, nextNode);
    } else if (nextType != null && nextType == FlowNodeType.SUBPROCESS.getCode()) {
      // 子流程节点：跳过内部，继续推进
      List<FlowSkipVO> subSkips = definitionCacheService.getAllSkips(ctx.getDefinitionId())
          .stream()
          .filter(s -> nextNodeCode.equals(s.getSourceRef()) && "PASS".equalsIgnoreCase(s.getSkipType()))
          .toList();
      for (FlowSkipVO subSkip : subSkips) {
        simulateSkip(ctx, subSkip, "SUBPROCESS_PASS");
      }
    }
  }

  /**
   * 解析匹配的出边
   *
   * <p>复用 FlowAdvancer 的条件评估逻辑，但不需要连接持久化层。
   *
   * @param ctx 模拟上下文
   * @param currentNode 当前节点
   * @return 匹配的出边列表
   */
  private List<FlowSkipVO> resolveMatchingSkips(SimulationContext ctx, FlowNodeVO currentNode) {
    String definitionId = ctx.getDefinitionId();
    String nodeCode = currentNode.getNodeCode();
    Integer nodeType = currentNode.getNodeType();

    // 获取当前节点的所有 PASS 出边
    List<FlowSkipVO> allSkips = definitionCacheService.getAllSkips(definitionId).stream()
        .filter(s -> nodeCode.equals(s.getSourceRef()) && "PASS".equalsIgnoreCase(s.getSkipType()))
        .toList();

    if (allSkips.isEmpty()) {
      return Collections.emptyList();
    }

    // 网关节点按条件筛选
    boolean isExclusive = nodeType != null && nodeType == FlowNodeType.CONDITION.getCode();
    boolean isInclusive = nodeType != null && nodeType == FlowNodeType.INCLUSIVE.getCode();

    if (!isExclusive && !isInclusive) {
      // 非网关节点：返回所有出边
      return new ArrayList<>(allSkips);
    }

    // 网关节点：按条件表达式筛选
    List<FlowSkipVO> matched = new ArrayList<>();
    boolean hasCondition = false;
    for (FlowSkipVO skip : allSkips) {
      String cond = skip.getSkipCondition();
      if (cond != null && !cond.isBlank()) {
        hasCondition = true;
        boolean result = flowAdvancer.evaluateSkipCondition(cond, ctx.getVariables());
        if (result) {
          matched.add(skip);
          if (isExclusive) {
            break; // 排他网关取第一个
          }
        }
      } else {
        // 无条件出边作为兜底
        if (matched.isEmpty() || !isExclusive) {
          matched.add(skip);
        }
      }
    }

    // 排他/包容网关无匹配条件时的告警
    if (matched.isEmpty() && hasCondition) {
      ctx.addWarning("网关 " + nodeCode + " 所有条件均不满足，取第一条无条件出边");
      // 取第一条无条件出边
      allSkips.stream()
          .filter(s -> s.getSkipCondition() == null || s.getSkipCondition().isBlank())
          .findFirst()
          .ifPresent(matched::add);
    }

    if (matched.isEmpty() && !allSkips.isEmpty()) {
      matched.add(allSkips.get(0));
    }

    return matched;
  }
}
