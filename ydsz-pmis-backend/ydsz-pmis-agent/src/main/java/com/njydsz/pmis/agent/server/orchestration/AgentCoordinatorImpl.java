package com.njydsz.pmis.agent.server.orchestration;

import com.njydsz.pmis.agent.server.engine.Agent;
import com.njydsz.pmis.agent.server.orchestration.strategy.OrchestrationStrategy;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 多智能体协调器实现
 *
 * <p>根据 OrchestrationRequest.mode 选择对应策略 + 黑板协调。
 *
 * <p><b>P0-1 修复</b>：原实现于构造函数中 {@code new XxxStrategy()}，导致：
 * <ul>
 *   <li>{@code ParallelStrategy} 内部 {@code newFixedThreadPool} 永不 shutdown，线程池泄漏</li>
 *   <li>策略无法注入 Spring 容器管理的依赖（如共享线程池、LLM Router 等）</li>
 * </ul>
 * 现改为通过 Spring 注入 {@code List<OrchestrationStrategy>}，按 {@link OrchestrationStrategy#mode()}
 * 收集为 {@code EnumMap<OrchestrationMode, OrchestrationStrategy>}，策略实例由容器统一管理生命周期。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class AgentCoordinatorImpl implements AgentCoordinator {

    /** 策略表（模式 -> 策略实例，启动时一次性收集） */
    private final Map<OrchestrationMode, OrchestrationStrategy> strategyMap;

    /**
     * 构造协调器，注入所有策略 Bean 并按 {@link OrchestrationMode} 索引。
     *
     * <p>策略 Bean 由 Spring 容器统一管理，可在策略内注入线程池、LLM Router 等依赖。
     *
     * @param strategies Spring 自动收集的所有 {@link OrchestrationStrategy} 实现
     */
    public AgentCoordinatorImpl(List<OrchestrationStrategy> strategies) {
        this.strategyMap = new EnumMap<>(OrchestrationMode.class);
        for (OrchestrationStrategy s : strategies) {
            OrchestrationMode m = s.mode();
            if (strategyMap.put(m, s) != null) {
                log.warn("[Coordinator] 重复注册策略: mode={} 旧策略将被覆盖", m);
            }
        }
        log.info("[Coordinator] 已注册编排策略: {}", strategyMap.keySet());
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
