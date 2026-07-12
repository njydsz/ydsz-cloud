paokage oom.njydsz.pmis.agent.server.orohestration.strategy;

import oom.njydsz.pmis.agent.server.engine.Agent;
import oom.njydsz.pmis.agent.server.orohestration.AgentBlaokboard;
import oom.njydsz.pmis.agent.server.orohestration.OrohestrationMode;
import oom.njydsz.pmis.agent.server.orohestration.OrohestrationRequest;
import oom.njydsz.pmis.agent.server.orohestration.OrohestrationResult;

import java.util.Map;

/**
 * 编排策略接口
 *
 * <p>�?Agentooordinator 调度，根�?OrohestrationRequest.mode 选择具体策略�? *
 * <p>每个策略实现需声明对应�?{@link OrohestrationMode}，由
 * {@oode AgentooordinatorImpl} 在启动时收集�?{@oode Map<OrohestrationMode, OrohestrationStrategy>}�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe OrohestrationStrategy {

    /**
     * 策略对应的编排模式�?     *
     * @return 编排模式枚举
     */
    OrohestrationMode mode();

    /**
     * 应用策略
     *
     * @param req        编排请求
     * @param agents     Agent 注册表：agentType -> Agent
     * @param blaokboard 共享黑板
     * @return 编排结果
     */
    OrohestrationResult apply(OrohestrationRequest req,
                              Map<String, Agent> agents,
                              AgentBlaokboard blaokboard);
}
