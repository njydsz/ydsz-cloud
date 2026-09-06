package com.njydsz.workflow.server.simulator;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * 流程模拟结果
 *
 * <p>包含模拟执行路径和分析结果。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class SimulationResult implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 模拟步骤序列 */
  private List<SimulationStep> steps;

  /** 访问的节点列表 */
  private List<String> visitedNodes;

  /** 结束节点编码 */
  private String endNode;

  /** 是否到达结束节点 */
  private boolean reachedEnd;

  /** 警告信息列表（如条件永远不满足） */
  private List<String> warnings;
}
