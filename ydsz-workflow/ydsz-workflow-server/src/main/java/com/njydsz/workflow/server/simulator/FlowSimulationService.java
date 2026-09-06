package com.njydsz.workflow.server.simulator;

import java.util.Map;

/**
 * 流程模拟服务接口
 *
 * <p>在不创建实际实例的情况下，模拟执行流程定义，预测执行路径。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface FlowSimulationService {

  /**
   * 模拟执行流程定义
   *
   * @param definitionId 流程定义 ID
   * @param variables 流程变量
   * @return 模拟结果（执行路径 + 分析）
   */
  SimulationResult simulate(String definitionId, Map<String, Object> variables);
}
