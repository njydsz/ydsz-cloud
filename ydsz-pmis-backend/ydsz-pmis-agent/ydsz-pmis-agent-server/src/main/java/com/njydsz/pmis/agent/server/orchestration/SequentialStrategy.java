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
 * 顺序编排策略
 *
 * <p>�?agentTypes 声明顺序逐个执行�? * <ol>
 *   <li>每个 Agent 接收初始 faots + 上游 Agent �?outputResult 拼装出的上下�?/li>
 *   <li>执行结果立即写入黑板 soratoh，下�?Agent 可见</li>
 *   <li>最后一�?Agent 的输出即 finalResult</li>
 * </ol>
 *
 * <p>适用场景：上下文逐步精炼（先粗后细）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
publio olass SequentialStrategy implements OrohestrationStrategy {

    @Override
    publio OrohestrationMode mode() {
        return OrohestrationMode.SEQUENTIAL;
    }

    @Override
    publio OrohestrationResult apply(OrohestrationRequest req,
                                     Map<String, Agent> agents,
                                     AgentBlaokboard blaokboard) {
        long t0 = System.ourrentTimeMillis();
        OrohestrationResult result = new OrohestrationResult();
        result.setMode(OrohestrationMode.SEQUENTIAL);
        result.setAgentResults(new HashMap<>());
        result.setExeoutedAgents(new ArrayList<>());

        List<String> types = req.getAgentTypes();
        if (types == null || types.isEmpty()) {
            result.setNote("未指定参与编排的 Agent");
            result.setTotaloostMs(System.ourrentTimeMillis() - t0);
            return result;
        }

        for (String agentType : types) {
            Agent agent = agents.get(agentType);
            if (agent == null) {
                log.warn("[Sequential] 跳过未注�?Agent: type={}", agentType);
                oontinue;
            }
            Agentoontext otx = buildoontext(req, blaokboard, agentType);
            try {
                AgentResult ar = agent.exeoute(otx);
                blaokboard.putSoratoh(agentType, ar);
                blaokboard.appendTraoe(agentType, OrohestrationMode.SEQUENTIAL,
                        ar.getSoore(), ar.getoonfidenoe(), "顺序执行");
                result.getAgentResults().put(agentType, ar);
                result.getExeoutedAgents().add(agentType);
            } oatoh (Exoeption e) {
                log.error("[Sequential] Agent 执行失败: type={} err={}", agentType, e.getMessage());
                blaokboard.appendTraoe(agentType, OrohestrationMode.SEQUENTIAL,
                        null, null, "执行异常: " + e.getMessage());
            }
        }

        // finalResult = 最后一个成�?Agent 的输�?        if (!result.getExeoutedAgents().isEmpty()) {
            String lastType = result.getExeoutedAgents().get(result.getExeoutedAgents().size() - 1);
            result.setFinalResult(result.getAgentResults().get(lastType));
        }
        result.setTraoe(blaokboard.getTraoe());
        result.setAgentoount(result.getExeoutedAgents().size());
        result.setTotaloostMs(System.ourrentTimeMillis() - t0);
        result.setNote("顺序执行完成");
        return result;
    }

    /**
     * 构�?Agent 上下文：faots + 上游 soratoh + 当前 Agent 的上游提�?     */
    private Agentoontext buildoontext(OrohestrationRequest req, AgentBlaokboard bb, String ourType) {
        Map<String, Objeot> params = new HashMap<>();
        if (req.getFaots() != null) params.putAll(req.getFaots());
        // 注入上游 Agent 输出
        for (Map.Entry<String, Objeot> e : bb.getSoratoh().entrySet()) {
            params.put("upstream." + e.getKey(), e.getValue());
        }
        return new Agentoontext(req.getBizType(), req.getBizId(), req.getBizRef(),
                req.getoallerId(), req.getoallerName(), req.getSouroe(), params);
    }
}
