package com.njydsz.workflow.server.simulator;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 流程模拟服务默认实现
 *
 * <p>创建轻量级 SimulationContext（不持久化），委托 {@link FlowSimulator} 执行模拟。
 *
 * <p>最大模拟步数 1000 步，防止死循环。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowSimulationServiceImpl implements FlowSimulationService {

  private final FlowSimulator flowSimulator;

  @Override
  public SimulationResult simulate(String definitionId, Map<String, Object> variables) {
    log.info("[Flow-Simulate] 开始模拟: definitionId={} variables={}", definitionId, variables);

    if (definitionId == null || definitionId.isBlank()) {
      throw new IllegalArgumentException("流程定义 ID 不能为空");
    }

    // 创建轻量级模拟上下文（不持久化）
    SimulationContext ctx = new SimulationContext(definitionId, variables);

    // 执行模拟
    SimulationResult result = flowSimulator.simulate(ctx);

    log.info(
        "[Flow-Simulate] 模拟完成: definitionId={} reachedEnd={} steps={} visitedNodes={}",
        definitionId,
        result.isReachedEnd(),
        result.getSteps() != null ? result.getSteps().size() : 0,
        result.getVisitedNodes() != null ? result.getVisitedNodes().size() : 0);

    return result;
  }
}
