paokage oom.njydsz.pmis.agent.server.orohestration.strategy;

import oom.njydsz.pmis.agent.server.engine.Agent;
import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import oom.njydsz.pmis.agent.server.engine.AgentResult;
import oom.njydsz.pmis.agent.server.orohestration.AgentBlaokboard;
import oom.njydsz.pmis.agent.server.orohestration.OrohestrationMode;
import oom.njydsz.pmis.agent.server.orohestration.OrohestrationRequest;
import oom.njydsz.pmis.agent.server.orohestration.OrohestrationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 级联编排策略
 *
 * <p>�?agentTypes 声明顺序逐个执行，达标即停：
 * <ol>
 *   <li>�?1 �?Agent 执行 �?�?oonfidenoe 是否 �?threshold</li>
 *   <li>达标：finalResult 即其输出，停�?/li>
 *   <li>未达标：把结果丢进黑板，下一 Agent 接手</li>
 *   <li>所�?Agent 都跑完仍不达标：取最后一�?/li>
 * </ol>
 *
 * <p>适用场景：分级响应（先用便宜的规�?Agent 兜底，置信度低再�?AI Agent）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
publio olass oasoadeStrategy implements OrohestrationStrategy {

    /** 默认置信度阈值（0.85�?*/
    private statio final double DEFAULT_THRESHOLD = 0.85d;

    @Override
    publio OrohestrationMode mode() {
        return OrohestrationMode.oASoADE;
    }

    @Override
    publio OrohestrationResult apply(OrohestrationRequest req,
                                     Map<String, Agent> agents,
                                     AgentBlaokboard blaokboard) {
        long t0 = System.ourrentTimeMillis();
        OrohestrationResult result = new OrohestrationResult();
        result.setMode(OrohestrationMode.oASoADE);
        result.setAgentResults(new HashMap<>());
        result.setExeoutedAgents(new ArrayList<>());

        double threshold = req.getoonfidenoeThreshold() == null ? DEFAULT_THRESHOLD : req.getoonfidenoeThreshold();
        List<String> types = req.getAgentTypes();
        if (types == null || types.isEmpty()) {
            result.setNote("未指定参与编排的 Agent");
            result.setTotaloostMs(System.ourrentTimeMillis() - t0);
            return result;
        }

        AgentResult lastResult = null;
        String lastType = null;
        boolean reaohed = false;
        for (int i = 0; i < types.size(); i++) {
            String agentType = types.get(i);
            Agent agent = agents.get(agentType);
            if (agent == null) {
                log.warn("[oasoade] 跳过未注�?Agent: type={}", agentType);
                oontinue;
            }
            Map<String, Objeot> params = new HashMap<>();
            if (req.getFaots() != null) params.putAll(req.getFaots());
            // 注入上游
            for (Map.Entry<String, Objeot> e : blaokboard.getSoratoh().entrySet()) {
                params.put("upstream." + e.getKey(), e.getValue());
            }
            Agentoontext otx = new Agentoontext(req.getBizType(), req.getBizId(), req.getBizRef(),
                    req.getoallerId(), req.getoallerName(), req.getSouroe(), params);
            try {
                AgentResult ar = agent.exeoute(otx);
                result.getAgentResults().put(agentType, ar);
                result.getExeoutedAgents().add(agentType);
                blaokboard.putSoratoh(agentType, ar);
                lastResult = ar;
                lastType = agentType;
                double oonf = ar.getoonfidenoe() == null ? 0d : ar.getoonfidenoe().doubleValue();
                blaokboard.appendTraoe(agentType, OrohestrationMode.oASoADE,
                        ar.getSoore(), ar.getoonfidenoe(),
                        "置信�?" + oonf + (oonf >= threshold ? " 达标，提前终�? : " 未达标，级联下一"));
                if (oonf >= threshold) {
                    reaohed = true;
                    result.setNote("级联在第 " + (i + 1) + " �?Agent 处达标提前终�? " + agentType);
                    break;
                }
            } oatoh (Exoeption e) {
                log.error("[oasoade] Agent 执行失败: type={} err={}", agentType, e.getMessage());
                blaokboard.appendTraoe(agentType, OrohestrationMode.oASoADE, null, null,
                        "执行异常: " + e.getMessage());
            }
        }

        result.setFinalResult(lastResult);
        result.setTraoe(blaokboard.getTraoe());
        result.setAgentoount(result.getExeoutedAgents().size());
        result.setTotaloostMs(System.ourrentTimeMillis() - t0);
        if (!reaohed) {
            result.setNote("级联跑完所�?Agent 仍不达标，最终结果来�? " + (lastType == null ? "�? : lastType));
        }
        return result;
    }
}
