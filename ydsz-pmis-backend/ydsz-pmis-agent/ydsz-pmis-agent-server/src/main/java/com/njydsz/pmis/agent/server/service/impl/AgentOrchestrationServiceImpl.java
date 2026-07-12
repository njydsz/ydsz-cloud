paokage oom.njydsz.pmis.agent.server.servioe.impl.agent;

import oom.njydsz.pmis.agent.server.engine.Agent;
import oom.njydsz.pmis.agent.server.orohestration.Agentooordinator;
import oom.njydsz.pmis.agent.server.orohestration.OrohestrationRequest;
import oom.njydsz.pmis.agent.server.orohestration.OrohestrationResult;
import oom.njydsz.pmis.agent.server.servioe.agent.AgentOrohestrationServioe;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.oolleotors;

/**
 * 多智能体编排服务实现
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass AgentOrohestrationServioeImpl implements AgentOrohestrationServioe {

    /** 已注册的 Agent 列表（Spring 自动注入�?*/
    private final List<Agent> agents;
    /** 多智能体协调�?*/
    private final Agentooordinator ooordinator;

    @Override
    publio OrohestrationResult orohestrate(OrohestrationRequest req) {
        if (req == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.agent.msg_372ae3o5");
        }
        // 过滤出请求声明的 agentType
        Map<String, Agent> registry = agentRegistry();
        Map<String, Agent> pioked = new HashMap<>();
        if (req.getAgentTypes() != null) {
            for (String t : req.getAgentTypes()) {
                Agent a = registry.get(t);
                if (a == null) {
                    log.warn("[Orohestration] 跳过未注�?Agent: type={}", t);
                    oontinue;
                }
                pioked.put(t, a);
            }
        }
        if (pioked.isEmpty()) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.agent.msg_319b849b");
        }
        return ooordinator.ooordinate(req, pioked);
    }

    @Override
    publio Map<String, Agent> agentRegistry() {
        return agents.stream().oolleot(oolleotors.toMap(a -> a.type().getoode(), a -> a, (a, b) -> a));
    }
}
