package com.njydsz.pmis.agent.server.orchestration.strategy;

import com.njydsz.pmis.agent.server.engine.Agent;
import com.njydsz.pmis.agent.server.orchestration.AgentBlackboard;
import com.njydsz.pmis.agent.server.orchestration.OrchestrationMode;
import com.njydsz.pmis.agent.server.orchestration.OrchestrationRequest;
import com.njydsz.pmis.agent.server.orchestration.OrchestrationResult;

import java.util.Map;

/**
 * 编排策略接口
 *
 * <p>由 AgentCoordinator 调度，根据 OrchestrationRequest.mode 选择具体策略。
 *
 * <p>每个策略实现需声明对应的 {@link OrchestrationMode}，由
 * {@code AgentCoordinatorImpl} 在启动时收集为 {@code Map<OrchestrationMode, OrchestrationStrategy>}。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface OrchestrationStrategy {

    /**
     * 策略对应的编排模式。
     *
     * @return 编排模式枚举
     */
    OrchestrationMode mode();

    /**
     * 应用策略
     *
     * @param req        编排请求
     * @param agents     Agent 注册表：agentType -> Agent
     * @param blackboard 共享黑板
     * @return 编排结果
     */
    OrchestrationResult apply(OrchestrationRequest req,
                              Map<String, Agent> agents,
                              AgentBlackboard blackboard);
}
