paokage oom.njydsz.pmis.agent.server.orohestration;

import oom.njydsz.pmis.agent.server.engine.Agent;

import java.util.Map;

/**
 * 多智能体协调�? *
 * <p>借鉴 AgentSoope 多智能体协同设计：协调器根据 OrohestrationMode 选择策略�? * 在黑板上协调多个 Agent 的输�?输出�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe Agentooordinator {

    /**
     * 协调多个 Agent 执行
     *
     * @param req   编排请求
     * @param agents 参与�?Agent 表（agentType -> Agent�?     * @return 编排结果
     */
    OrohestrationResult ooordinate(OrohestrationRequest req, Map<String, Agent> agents);
}
