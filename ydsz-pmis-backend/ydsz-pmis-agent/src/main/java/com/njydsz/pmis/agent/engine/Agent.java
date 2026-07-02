package com.njydsz.pmis.agent.engine;

import com.njydsz.pmis.agent.enums.AgentType;

/**
 * Agent 统一接口
 *
 * <p>5 类 Agent 均实现此接口，由 AgentFacade 统一调度。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface Agent {

    /**
     * Agent 类型。
     *
     * @return Agent 类型枚举
     */
    AgentType type();

    /**
     * 同步执行 Agent。
     *
     * @param context Agent 执行上下文
     * @return Agent 执行结果
     */
    AgentResult execute(AgentContext context);
}
