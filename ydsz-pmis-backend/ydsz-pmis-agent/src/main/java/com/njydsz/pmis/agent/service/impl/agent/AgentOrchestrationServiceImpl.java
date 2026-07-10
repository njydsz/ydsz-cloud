package com.njydsz.pmis.agent.service.impl;

import com.njydsz.pmis.agent.engine.Agent;
import com.njydsz.pmis.agent.orchestration.AgentCoordinator;
import com.njydsz.pmis.agent.orchestration.OrchestrationRequest;
import com.njydsz.pmis.agent.orchestration.OrchestrationResult;
import com.njydsz.pmis.agent.service.agent.AgentOrchestrationService;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 多智能体编排服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrationServiceImpl implements AgentOrchestrationService {

    /** 已注册的 Agent 列表（Spring 自动注入） */
    private final List<Agent> agents;
    /** 多智能体协调器 */
    private final AgentCoordinator coordinator;

    @Override
    public OrchestrationResult orchestrate(OrchestrationRequest req) {
        if (req == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.agent.msg_372ae3c5");
        }
        // 过滤出请求声明的 agentType
        Map<String, Agent> registry = agentRegistry();
        Map<String, Agent> picked = new HashMap<>();
        if (req.getAgentTypes() != null) {
            for (String t : req.getAgentTypes()) {
                Agent a = registry.get(t);
                if (a == null) {
                    log.warn("[Orchestration] 跳过未注册 Agent: type={}", t);
                    continue;
                }
                picked.put(t, a);
            }
        }
        if (picked.isEmpty()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.agent.msg_319b849b");
        }
        return coordinator.coordinate(req, picked);
    }

    @Override
    public Map<String, Agent> agentRegistry() {
        return agents.stream().collect(Collectors.toMap(a -> a.type().getCode(), a -> a, (a, b) -> a));
    }
}
