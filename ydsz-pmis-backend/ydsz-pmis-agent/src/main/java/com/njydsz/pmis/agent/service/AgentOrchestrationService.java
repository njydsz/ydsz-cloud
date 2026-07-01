package com.njydsz.pmis.agent.service;

import com.njydsz.pmis.agent.engine.Agent;
import com.njydsz.pmis.agent.orchestration.OrchestrationRequest;
import com.njydsz.pmis.agent.orchestration.OrchestrationResult;

import java.util.Map;

/**
 * 多智能体编排服务（AgentScope 模式）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface AgentOrchestrationService {

    /**
     * 协调多 Agent 编排执行
     *
     * @param req 编排请求
     * @return 编排结果
     */
    OrchestrationResult orchestrate(OrchestrationRequest req);

    /**
     * 取当前已注册的 Agent 映射（agentType -> Agent）
     */
    Map<String, Agent> agentRegistry();
}
