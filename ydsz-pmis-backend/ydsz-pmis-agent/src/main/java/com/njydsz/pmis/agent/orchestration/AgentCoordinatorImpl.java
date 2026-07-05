package com.njydsz.pmis.agent.orchestration;

import com.njydsz.pmis.agent.engine.Agent;
import com.njydsz.pmis.agent.orchestration.strategy.CascadeStrategy;
import com.njydsz.pmis.agent.orchestration.strategy.OrchestrationStrategy;
import com.njydsz.pmis.agent.orchestration.strategy.ParallelStrategy;
import com.njydsz.pmis.agent.orchestration.strategy.SequentialStrategy;
import com.njydsz.pmis.agent.orchestration.strategy.VotingStrategy;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * 多智能体协调器实现
 *
 * <p>根据 OrchestrationRequest.mode 选择对应策略 + 黑板协调。
 * 策略实例复用，避免每次 new。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class AgentCoordinatorImpl implements AgentCoordinator {

    /** 策略表（模式 -> 策略实例） */
    private final Map<OrchestrationMode, OrchestrationStrategy> strategyMap;

    /**
     * 构造协调器，初始化 4 种编排策略。
     */
    public AgentCoordinatorImpl() {
        this.strategyMap = new EnumMap<>(OrchestrationMode.class);
        this.strategyMap.put(OrchestrationMode.SEQUENTIAL, new SequentialStrategy());
        this.strategyMap.put(OrchestrationMode.PARALLEL, new ParallelStrategy());
        this.strategyMap.put(OrchestrationMode.VOTING, new VotingStrategy());
        this.strategyMap.put(OrchestrationMode.CASCADE, new CascadeStrategy());
    }

    @Override
    public OrchestrationResult coordinate(OrchestrationRequest req, Map<String, Agent> agents) {
        if (req == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.agent.msg_372ae3c5");
        }
        if (req.getMode() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.agent.msg_934fa86f");
        }
        if (agents == null || agents.isEmpty()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.agent.msg_1619a3b0");
        }
        OrchestrationStrategy strategy = strategyMap.get(req.getMode());
        if (strategy == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "error.agent.msg_2eda11e6", req.getMode().getCode());
        }
        AgentBlackboard blackboard = new AgentBlackboard(req.getFacts());
        log.info("[Coordinator] 开始编排: mode={} biz={} agents={}",
                req.getMode(), req.getBizRef(),
                req.getAgentTypes() == null ? 0 : req.getAgentTypes().size());
        return strategy.apply(req, agents, blackboard);
    }
}
