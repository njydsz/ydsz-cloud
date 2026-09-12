package com.njydsz.workflow.server.simulator;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 流程模拟上下文
 *
 * <p>轻量级模拟执行上下文，不持久化。
 * 模拟执行过程中跟踪当前节点、已访问节点、步骤序列等状态。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class SimulationContext implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 流程定义 ID */
  private final String definitionId;

  /** 流程变量 */
  private final Map<String, Object> variables;

  /** 当前节点编码 */
  private String currentNodeCode;

  /** 已访问节点列表（按访问顺序） */
  private final List<String> visitedNodes = new ArrayList<>(16);

  /** 模拟步骤序列 */
  private final List<SimulationStep> steps = new ArrayList<>(16);

  /** 警告信息（如条件永远不满足） */
  private final List<String> warnings = new ArrayList<>(8);

  /** 当前模拟步数 */
  private int stepCount = 0;

  /** 最大模拟步数 */
  private static final int MAX_STEPS = 1000;

  /** 是否到达结束节点 */
  private boolean reachedEnd = false;

  /** 结束节点编码 */
  private String endNode;

  
  /**
   * 构造模拟执行上下文。
   *
   * @param definitionId 流程定义 ID
   * @param variables 初始流程变量
   */
  public SimulationContext(String definitionId, Map<String, Object> variables) {
    this.definitionId = definitionId;
    this.variables = variables != null ? new HashMap<>(variables) : new HashMap<>();
  }

  /**
   * 推进到下一节点
   *
   * @param nodeCode 下一节点编码
   * @param actionType 动作类型（PASS / REJECT / SIMULATE）
   */
  public void advanceTo(String nodeCode, String actionType) {
    this.currentNodeCode = nodeCode;
    if (!visitedNodes.contains(nodeCode)) {
      visitedNodes.add(nodeCode);
    }
    stepCount++;
  }

  /**
   * 添加模拟步骤
   *
   * @param step 步骤
   */
  public void addStep(SimulationStep step) {
    steps.add(step);
  }

  /**
   * 添加警告信息
   *
   * @param warning 警告消息
   */
  public void addWarning(String warning) {
    warnings.add(warning);
  }

  /**
   * 检查是否超过最大模拟步数
   *
   * @return true=已超过最大步数
   */
  public boolean isMaxStepsReached() {
    return stepCount >= MAX_STEPS;
  }

  /**
   * 标记到达结束节点
   *
   * @param endNodeCode 结束节点编码
   */
  public void markReachedEnd(String endNodeCode) {
    this.reachedEnd = true;
    this.endNode = endNodeCode;
  }
}
