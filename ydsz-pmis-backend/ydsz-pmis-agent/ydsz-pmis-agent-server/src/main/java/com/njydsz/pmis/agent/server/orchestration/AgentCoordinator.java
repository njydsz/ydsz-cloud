package com.njydsz.pmis.agent.server.orchestration;

import com.njydsz.pmis.agent.server.engine.Agent;

import java.util.Map;

/**
 * 多智能体协调器
 *
 * <p>借鉴 AgentScope 多智能体协同设计：协调器根据 OrchestrationMode 选择策略，
 * 在黑板上协调多个 Agent 的输入/输出。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface AgentCoordinator {

    /**
     * 协调多个 Agent 执行
     *
     * @param req   编排请求
     * @param agents 参与的 Agent 表（agentType -> Agent）
     * @return 编排结果
     */
    OrchestrationResult coordinate(OrchestrationRequest req, Map<String, Agent> agents);
}
