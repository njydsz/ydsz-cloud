package com.njydsz.pmis.agent.orchestration.strategy;

import com.njydsz.pmis.agent.engine.Agent;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.AgentResult;
import com.njydsz.pmis.agent.orchestration.AgentBlackboard;
import com.njydsz.pmis.agent.orchestration.OrchestrationMode;
import com.njydsz.pmis.agent.orchestration.OrchestrationRequest;
import com.njydsz.pmis.agent.orchestration.OrchestrationResult;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 级联编排策略
 *
 * <p>按 agentTypes 声明顺序逐个执行，达标即停：
 * <ol>
 *   <li>第 1 个 Agent 执行 → 看 confidence 是否 ≥ threshold</li>
 *   <li>达标：finalResult 即其输出，停止</li>
 *   <li>未达标：把结果丢进黑板，下一 Agent 接手</li>
 *   <li>所有 Agent 都跑完仍不达标：取最后一个</li>
 * </ol>
 *
 * <p>适用场景：分级响应（先用便宜的规则 Agent 兜底，置信度低再调 AI Agent）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class CascadeStrategy implements OrchestrationStrategy {

    private static final double DEFAULT_THRESHOLD = 0.85d;

    @Override
    public OrchestrationResult apply(OrchestrationRequest req,
                                     Map<String, Agent> agents,
                                     AgentBlackboard blackboard) {
        long t0 = System.currentTimeMillis();
        OrchestrationResult result = new OrchestrationResult();
        result.setMode(OrchestrationMode.CASCADE);
        result.setAgentResults(new HashMap<>());
        result.setExecutedAgents(new ArrayList<>());

        double threshold = req.getConfidenceThreshold() == null ? DEFAULT_THRESHOLD : req.getConfidenceThreshold();
        List<String> types = req.getAgentTypes();
        if (types == null || types.isEmpty()) {
            result.setNote("未指定参与编排的 Agent");
            result.setTotalCostMs(System.currentTimeMillis() - t0);
            return result;
        }

        AgentResult lastResult = null;
        String lastType = null;
        boolean reached = false;
        for (int i = 0; i < types.size(); i++) {
            String agentType = types.get(i);
            Agent agent = agents.get(agentType);
            if (agent == null) {
                log.warn("[Cascade] 跳过未注册 Agent: type={}", agentType);
                continue;
            }
            Map<String, Object> params = new HashMap<>();
            if (req.getFacts() != null) params.putAll(req.getFacts());
            // 注入上游
            for (Map.Entry<String, Object> e : blackboard.getScratch().entrySet()) {
                params.put("upstream." + e.getKey(), e.getValue());
            }
            AgentContext ctx = new AgentContext(req.getBizType(), req.getBizId(), req.getBizRef(),
                    req.getCallerId(), req.getCallerName(), req.getSource(), params);
            try {
                AgentResult ar = agent.execute(ctx);
                result.getAgentResults().put(agentType, ar);
                result.getExecutedAgents().add(agentType);
                blackboard.putScratch(agentType, ar);
                lastResult = ar;
                lastType = agentType;
                double conf = ar.getConfidence() == null ? 0d : ar.getConfidence().doubleValue();
                blackboard.appendTrace(agentType, OrchestrationMode.CASCADE,
                        ar.getScore(), ar.getConfidence(),
                        "置信度=" + conf + (conf >= threshold ? " 达标，提前终止" : " 未达标，级联下一"));
                if (conf >= threshold) {
                    reached = true;
                    result.setNote("级联在第 " + (i + 1) + " 个 Agent 处达标提前终止: " + agentType);
                    break;
                }
            } catch (Exception e) {
                log.error("[Cascade] Agent 执行失败: type={} err={}", agentType, e.getMessage());
                blackboard.appendTrace(agentType, OrchestrationMode.CASCADE, null, null,
                        "执行异常: " + e.getMessage());
            }
        }

        result.setFinalResult(lastResult);
        result.setTrace(blackboard.getTrace());
        result.setAgentCount(result.getExecutedAgents().size());
        result.setTotalCostMs(System.currentTimeMillis() - t0);
        if (!reached) {
            result.setNote("级联跑完所有 Agent 仍不达标，最终结果来自: " + (lastType == null ? "无" : lastType));
        }
        return result;
    }
}
