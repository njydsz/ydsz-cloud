package com.njydsz.pmis.agent.orchestration.strategy;

import com.njydsz.pmis.agent.engine.Agent;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.AgentResult;
import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentType;
import com.njydsz.pmis.agent.orchestration.AgentBlackboard;
import com.njydsz.pmis.agent.orchestration.OrchestrationMode;
import com.njydsz.pmis.agent.orchestration.OrchestrationRequest;
import com.njydsz.pmis.agent.orchestration.OrchestrationResult;
import com.njydsz.pmis.common.constant.AsyncExecutorNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 投票融合编排策略
 *
 * <p>多 Agent 独立评分后按权重加权融合：
 * <ol>
 *   <li>每个 Agent 输出 score(0-100) + confidence(0-1)</li>
 *   <li>加权 score = Σ (agent.score × weight × confidence) / Σ (weight × confidence)</li>
 *   <li>加权 confidence = Σ (agent.confidence × weight) / Σ weight</li>
 *   <li>告警等级：RED > YELLOW > NORMAL，max level 决定</li>
 *   <li>suggestion 拼接所有 Agent 的建议</li>
 * </ol>
 *
 * <p>适用场景：多视角风险评估（如同时跑风险预警 + 利润预测 + 工时异常）。
 *
 * <p><b>P0-2 修复</b>：原实现使用串行 for 循环执行所有 Agent，违背"投票"语义（应并行）。
 * 现改为并行提交至共享 {@code agentExecutor}，与 {@link ParallelStrategy} 复用同一线程池。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class VotingStrategy implements OrchestrationStrategy {

    /** 共享 AI Agent 线程池（由 AsyncThreadPoolConfig 提供） */
    private final ThreadPoolTaskExecutor agentExecutor;

    /**
     * 构造投票策略，注入共享线程池。
     *
     * @param agentExecutor AI Agent 共享线程池
     */
    public VotingStrategy(@Qualifier(AsyncExecutorNames.AGENT) ThreadPoolTaskExecutor agentExecutor) {
        this.agentExecutor = agentExecutor;
    }

    @Override
    public OrchestrationMode mode() {
        return OrchestrationMode.VOTING;
    }

    @Override
    public OrchestrationResult apply(OrchestrationRequest req,
                                     Map<String, Agent> agents,
                                     AgentBlackboard blackboard) {
        long t0 = System.currentTimeMillis();
        OrchestrationResult result = new OrchestrationResult();
        result.setMode(OrchestrationMode.VOTING);
        result.setAgentResults(new HashMap<>());
        result.setExecutedAgents(new ArrayList<>());

        List<String> types = req.getAgentTypes();
        if (types == null || types.isEmpty()) {
            result.setNote("未指定参与编排的 Agent");
            result.setTotalCostMs(System.currentTimeMillis() - t0);
            return result;
        }

        Map<String, Double> weights = req.getWeights() == null ? new HashMap<>() : new HashMap<>(req.getWeights());
        // 归一化权重：缺省 1.0
        for (String t : types) {
            weights.putIfAbsent(t, 1.0);
        }

        // 并行执行所有 Agent（P0-2 修复：原串行 → 现并行）
        List<CompletableFuture<Map.Entry<String, AgentResult>>> futures = new ArrayList<>();
        for (String agentType : types) {
            Agent agent = agents.get(agentType);
            if (agent == null) {
                log.warn("[Voting] 跳过未注册 Agent: type={}", agentType);
                continue;
            }
            AgentContext ctx = new AgentContext(req.getBizType(), req.getBizId(), req.getBizRef(),
                    req.getCallerId(), req.getCallerName(), req.getSource(),
                    req.getFacts() == null ? new HashMap<>() : new HashMap<>(req.getFacts()));
            CompletableFuture<Map.Entry<String, AgentResult>> f = CompletableFuture.supplyAsync(() -> {
                try {
                    AgentResult ar = agent.execute(ctx);
                    return Map.entry(agentType, ar);
                } catch (Exception e) {
                    log.error("[Voting] Agent 执行失败: type={} err={}", agentType, e.getMessage());
                    return Map.entry(agentType, (AgentResult) null);
                }
            }, agentExecutor);
            futures.add(f);
        }

        // 等待全部完成并合并到黑板
        for (CompletableFuture<Map.Entry<String, AgentResult>> f : futures) {
            try {
                Map.Entry<String, AgentResult> e = f.join();
                String agentType = e.getKey();
                AgentResult ar = e.getValue();
                result.getExecutedAgents().add(agentType);
                if (ar != null) {
                    result.getAgentResults().put(agentType, ar);
                    blackboard.putScratch(agentType, ar);
                    blackboard.appendTrace(agentType, OrchestrationMode.VOTING,
                            ar.getScore(), ar.getConfidence(),
                            "权重=" + weights.getOrDefault(agentType, 1.0));
                } else {
                    blackboard.appendTrace(agentType, OrchestrationMode.VOTING, null, null, "并行执行失败");
                }
            } catch (Exception e) {
                log.error("[Voting] 等待 Agent 失败: err={}", e.getMessage());
            }
        }

        // 加权融合
        AgentResult fused = fuse(result.getAgentResults(), weights);
        result.setFinalResult(fused);
        result.setTrace(blackboard.getTrace());
        result.setAgentCount(result.getExecutedAgents().size());
        result.setTotalCostMs(System.currentTimeMillis() - t0);
        result.setNote("投票融合完成");
        return result;
    }

    /**
     * 等级严重度：RED=3 / YELLOW=2 / INFO=RECOMMEND=NORMAL=1，取最高
     */
    private int severity(AgentAlertLevel l) {
        if (l == null) return 0;
        return switch (l) {
            case RED -> 3;
            case YELLOW -> 2;
            case INFO, RECOMMEND, NORMAL -> 1;
        };
    }

    /**
     * 加权融合：score / confidence 按权重平均，level 取最高，suggestion 拼接。
     *
     * @param agentResults 各 Agent 的执行结果
     * @param weights      权重表（key=agentType value=权重 0-1）
     * @return 融合后的 AgentResult；无有效结果返回 null
     */
    public AgentResult fuse(Map<String, AgentResult> agentResults, Map<String, Double> weights) {
        if (agentResults == null || agentResults.isEmpty()) {
            return null;
        }
        double sumScoreWeighted = 0d;
        double sumWeight = 0d;
        double sumConfWeighted = 0d;
        AgentAlertLevel maxLevel = AgentAlertLevel.NORMAL;
        int maxSev = 0;
        StringBuilder sb = new StringBuilder();
        List<String> allRules = new ArrayList<>();
        int validCount = 0;

        for (Map.Entry<String, AgentResult> e : agentResults.entrySet()) {
            AgentResult ar = e.getValue();
            if (ar == null) continue;
            double w = weights.getOrDefault(e.getKey(), 1.0);
            double s = ar.getScore() == null ? 0d : ar.getScore().doubleValue();
            double c = ar.getConfidence() == null ? 0d : ar.getConfidence().doubleValue();
            sumScoreWeighted += s * w * (c > 0 ? c : 1.0);
            sumWeight += w * (c > 0 ? c : 1.0);
            sumConfWeighted += c * w;
            validCount++;
            // 等级按严重度取最高（RED > YELLOW > NORMAL/INFO/RECOMMEND）
            int sev = severity(ar.getAlertLevel());
            if (sev > maxSev) {
                maxSev = sev;
                maxLevel = ar.getAlertLevel();
            }
            if (ar.getSuggestion() != null && !ar.getSuggestion().isBlank()) {
                if (sb.length() > 0) sb.append("；");
                sb.append("[").append(e.getKey()).append("] ").append(ar.getSuggestion());
            }
            if (ar.getMatchedRules() != null) allRules.addAll(ar.getMatchedRules());
        }

        double fusedScore = sumWeight > 0 ? sumScoreWeighted / sumWeight : 0d;
        double totalW = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        double fusedConf = totalW > 0 ? sumConfWeighted / totalW : 0d;
        if (validCount == 0) return null;

        AgentResult out = new AgentResult();
        out.setAgentType(AgentType.RISK_WARNING); // 融合后无类型，置为 RISK_WARNING 占位
        out.setAlertLevel(maxLevel);
        out.setScore(BigDecimal.valueOf(fusedScore).setScale(2, RoundingMode.HALF_UP));
        out.setConfidence(BigDecimal.valueOf(fusedConf).setScale(4, RoundingMode.HALF_UP));
        out.setSuggestion(sb.length() == 0 ? null : sb.toString());
        out.setMatchedRules(allRules);
        out.setPayload(new HashMap<>());
        out.getPayload().put("fusionMode", "VOTING");
        out.getPayload().put("agentCount", validCount);
        out.getPayload().put("weights", weights);
        return out;
    }
}
