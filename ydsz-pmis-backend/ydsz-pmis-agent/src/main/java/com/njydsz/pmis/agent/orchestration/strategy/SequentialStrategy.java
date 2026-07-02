package com.njydsz.pmis.agent.orchestration.strategy;

import com.njydsz.pmis.agent.engine.Agent;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.AgentResult;
import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentType;
import com.njydsz.pmis.agent.orchestration.AgentBlackboard;
import com.njydsz.pmis.agent.orchestration.AgentMessage;
import com.njydsz.pmis.agent.orchestration.OrchestrationMode;
import com.njydsz.pmis.agent.orchestration.OrchestrationRequest;
import com.njydsz.pmis.agent.orchestration.OrchestrationResult;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 顺序编排策略
 *
 * <p>按 agentTypes 声明顺序逐个执行：
 * <ol>
 *   <li>每个 Agent 接收初始 facts + 上游 Agent 的 outputResult 拼装出的上下文</li>
 *   <li>执行结果立即写入黑板 scratch，下游 Agent 可见</li>
 *   <li>最后一个 Agent 的输出即 finalResult</li>
 * </ol>
 *
 * <p>适用场景：上下文逐步精炼（先粗后细）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class SequentialStrategy implements OrchestrationStrategy {

    @Override
    public OrchestrationResult apply(OrchestrationRequest req,
                                     Map<String, Agent> agents,
                                     AgentBlackboard blackboard) {
        long t0 = System.currentTimeMillis();
        OrchestrationResult result = new OrchestrationResult();
        result.setMode(OrchestrationMode.SEQUENTIAL);
        result.setAgentResults(new HashMap<>());
        result.setExecutedAgents(new ArrayList<>());

        List<String> types = req.getAgentTypes();
        if (types == null || types.isEmpty()) {
            result.setNote("未指定参与编排的 Agent");
            result.setTotalCostMs(System.currentTimeMillis() - t0);
            return result;
        }

        for (String agentType : types) {
            Agent agent = agents.get(agentType);
            if (agent == null) {
                log.warn("[Sequential] 跳过未注册 Agent: type={}", agentType);
                continue;
            }
            AgentContext ctx = buildContext(req, blackboard, agentType);
            try {
                AgentResult ar = agent.execute(ctx);
                blackboard.putScratch(agentType, ar);
                blackboard.appendTrace(agentType, OrchestrationMode.SEQUENTIAL,
                        ar.getScore(), ar.getConfidence(), "顺序执行");
                result.getAgentResults().put(agentType, ar);
                result.getExecutedAgents().add(agentType);
            } catch (Exception e) {
                log.error("[Sequential] Agent 执行失败: type={} err={}", agentType, e.getMessage());
                blackboard.appendTrace(agentType, OrchestrationMode.SEQUENTIAL,
                        null, null, "执行异常: " + e.getMessage());
            }
        }

        // finalResult = 最后一个成功 Agent 的输出
        if (!result.getExecutedAgents().isEmpty()) {
            String lastType = result.getExecutedAgents().get(result.getExecutedAgents().size() - 1);
            result.setFinalResult(result.getAgentResults().get(lastType));
        }
        result.setTrace(blackboard.getTrace());
        result.setAgentCount(result.getExecutedAgents().size());
        result.setTotalCostMs(System.currentTimeMillis() - t0);
        result.setNote("顺序执行完成");
        return result;
    }

    /**
     * 构造 Agent 上下文：facts + 上游 scratch + 当前 Agent 的上游提示
     */
    private AgentContext buildContext(OrchestrationRequest req, AgentBlackboard bb, String curType) {
        Map<String, Object> params = new HashMap<>();
        if (req.getFacts() != null) params.putAll(req.getFacts());
        // 注入上游 Agent 输出
        for (Map.Entry<String, Object> e : bb.getScratch().entrySet()) {
            params.put("upstream." + e.getKey(), e.getValue());
        }
        return new AgentContext(req.getBizType(), req.getBizId(), req.getBizRef(),
                req.getCallerId(), req.getCallerName(), req.getSource(), params);
    }

    /**
     * 工具：根据 agent code 取枚举（兼容大小写）
     */
    @SuppressWarnings("unused")
    private AgentType parseType(String code) {
        return AgentType.fromCode(code);
    }

    /**
     * 工具：根据 code 解析告警等级（兼容大小写）。
     *
     * @param code 等级码，可空
     * @return 告警等级；为空或未匹配返回 NORMAL
     */
    @SuppressWarnings("unused")
    private AgentAlertLevel parseLevel(String code) {
        if (code == null) return AgentAlertLevel.NORMAL;
        for (AgentAlertLevel l : AgentAlertLevel.values()) {
            if (l.getCode().equalsIgnoreCase(code)) return l;
        }
        return AgentAlertLevel.NORMAL;
    }

    /**
     * 构造输入消息（工具方法）。
     *
     * @param from 发送方
     * @return INPUT 类型消息
     */
    @SuppressWarnings("unused")
    private AgentMessage inputMessage(String from) {
        return AgentMessage.input(from, null);
    }
}
