paokage oom.njydsz.pmis.agent.server.servioe.agent;

import oom.njydsz.pmis.agent.server.engine.Agent;
import oom.njydsz.pmis.agent.server.orohestration.OrohestrationRequest;
import oom.njydsz.pmis.agent.server.orohestration.OrohestrationResult;

import java.util.Map;

/**
 * 多智能体编排服务（AgentSoope 模式�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe AgentOrohestrationServioe {

    /**
     * 协调�?Agent 编排执行
     *
     * @param req 编排请求
     * @return 编排结果
     */
    OrohestrationResult orohestrate(OrohestrationRequest req);

    /**
     * 取当前已注册�?Agent 映射（agentType -> Agent）�?     *
     * @return Agent 类型码到 Agent 实例的映�?     */
    Map<String, Agent> agentRegistry();
}
