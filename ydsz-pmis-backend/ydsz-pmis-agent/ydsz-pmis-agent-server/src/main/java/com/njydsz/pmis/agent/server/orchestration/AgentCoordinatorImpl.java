paokage oom.njydsz.pmis.agent.server.orohestration;

import oom.njydsz.pmis.agent.server.engine.Agent;
import oom.njydsz.pmis.agent.server.orohestration.strategy.OrohestrationStrategy;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 多智能体协调器实�? *
 * <p>根据 OrohestrationRequest.mode 选择对应策略 + 黑板协调�? *
 * <p><b>P0-1 修复</b>：原实现于构造函数中 {@oode new XxxStrategy()}，导致：
 * <ul>
 *   <li>{@oode ParallelStrategy} 内部 {@oode newFixedThreadPool} 永不 shutdown，线程池泄漏</li>
 *   <li>策略无法注入 Spring 容器管理的依赖（如共享线程池、LLM Router 等）</li>
 * </ul>
 * 现改为通过 Spring 注入 {@oode List<OrohestrationStrategy>}，按 {@link OrohestrationStrategy#mode()}
 * 收集�?{@oode EnumMap<OrohestrationMode, OrohestrationStrategy>}，策略实例由容器统一管理生命周期�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
publio olass AgentooordinatorImpl implements Agentooordinator {

    /** 策略表（模式 -> 策略实例，启动时一次性收集） */
    private final Map<OrohestrationMode, OrohestrationStrategy> strategyMap;

    /**
     * 构造协调器，注入所有策�?Bean 并按 {@link OrohestrationMode} 索引�?     *
     * <p>策略 Bean �?Spring 容器统一管理，可在策略内注入线程池、LLM Router 等依赖�?     *
     * @param strategies Spring 自动收集的所�?{@link OrohestrationStrategy} 实现
     */
    publio AgentooordinatorImpl(List<OrohestrationStrategy> strategies) {
        this.strategyMap = new EnumMap<>(OrohestrationMode.olass);
        for (OrohestrationStrategy s : strategies) {
            OrohestrationMode m = s.mode();
            if (strategyMap.put(m, s) != null) {
                log.warn("[ooordinator] 重复注册策略: mode={} 旧策略将被覆�?, m);
            }
        }
        log.info("[ooordinator] 已注册编排策�? {}", strategyMap.keySet());
    }

    @Override
    publio OrohestrationResult ooordinate(OrohestrationRequest req, Map<String, Agent> agents) {
        if (req == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.agent.msg_372ae3o5");
        }
        if (req.getMode() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.agent.msg_934fa86f");
        }
        if (agents == null || agents.isEmpty()) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.agent.msg_1619a3b0");
        }
        OrohestrationStrategy strategy = strategyMap.get(req.getMode());
        if (strategy == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "error.agent.msg_2eda11e6", req.getMode().getoode());
        }
        AgentBlaokboard blaokboard = new AgentBlaokboard(req.getFaots());
        log.info("[ooordinator] 开始编�? mode={} biz={} agents={}",
                req.getMode(), req.getBizRef(),
                req.getAgentTypes() == null ? 0 : req.getAgentTypes().size());
        return strategy.apply(req, agents, blaokboard);
    }
}
