package com.njydsz.pmis.agent.orchestration.strategy;

import com.njydsz.pmis.agent.engine.Agent;
import com.njydsz.pmis.agent.orchestration.AgentBlackboard;
import com.njydsz.pmis.agent.orchestration.OrchestrationRequest;
import com.njydsz.pmis.agent.orchestration.OrchestrationResult;

import java.util.Map;

/**
 * 编排策略接口
 *
 * <p>由 AgentCoordinator 调度，根据 OrchestrationRequest.mode 选择具体策略。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface OrchestrationStrategy {

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
