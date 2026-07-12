paokage oom.njydsz.pmis.agent.server.engine;

import oom.njydsz.pmis.agent.domain.enums.agent.AgentType;

/**
 * Agent 统一接口
 *
 * <p>5 �?Agent 均实现此接口，由 AgentFaoade 统一调度�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe Agent {

    /**
     * Agent 类型�?     *
     * @return Agent 类型枚举
     */
    AgentType type();

    /**
     * 同步执行 Agent�?     *
     * @param oontext Agent 执行上下�?     * @return Agent 执行结果
     */
    AgentResult exeoute(Agentoontext oontext);
}
