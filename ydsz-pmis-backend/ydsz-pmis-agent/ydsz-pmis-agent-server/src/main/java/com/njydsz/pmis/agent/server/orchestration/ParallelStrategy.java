package com.njydsz.pmis.agent.server.orchestration.strategy;

import com.njydsz.pmis.agent.server.engine.Agent;
import com.njydsz.pmis.agent.server.engine.AgentContext;
import com.njydsz.pmis.agent.server.engine.AgentResult;
import com.njydsz.pmis.agent.server.orchestration.AgentBlackboard;
import com.njydsz.pmis.agent.server.orchestration.OrchestrationMode;
import com.njydsz.pmis.agent.server.orchestration.OrchestrationRequest;
import com.njydsz.pmis.agent.server.orchestration.OrchestrationResult;
import com.njydsz.pmis.common.constant.AsyncExecutorNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 并行编排策略
 *
 * <p>所有 Agent 同时跑（共享 {@code agentExecutor} 线程池），最后合并到黑板。
 * <ul>
 *   <li>finalResult 取 score 最高的 Agent 输出（兼顾置信度）</li>
 * </ul>
 *
 * <p><b>P0-1 修复</b>：原实现内部 {@code newFixedThreadPool(5)} 永不 shutdown，
 * 且 {@code AgentCoordinatorImpl} 单例化时被多次构造导致线程池泄漏。
 * 现统一改为 Spring Bean 注入共享 {@link AsyncExecutorNames#AGENT}，由容器统一管理生命周期
 * （core=2 / max=8 / queue=100 / CallerRunsPolicy / 优雅关闭 60s）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ParallelStrategy implements OrchestrationStrategy {

    /** 共享 AI Agent 线程池（由 AsyncThreadPoolConfig 提供，避免泄漏） */
    private final ThreadPoolTaskExecutor agentExecutor;

    /**
     * 构造并行策略，注入共享线程池。
     *
     * @param agentExecutor AI Agent 共享线程池（Bean name = {@link AsyncExecutorNames#AGENT}）
     */
    public ParallelStrategy(@Qualifier(AsyncExecutorNames.AGENT) ThreadPoolTaskExecutor agentExecutor) {
        this.agentExecutor = agentExecutor;
    }

    @Override
    public OrchestrationMode mode() {
        return OrchestrationMode.PARALLEL;
    }

    @Override
    public OrchestrationResult apply(OrchestrationRequest req,
                                     Map<String, Agent> agents,
                                     AgentBlackboard blackboard) {
        long t0 = System.currentTimeMillis();
        OrchestrationResult result = new OrchestrationResult();
        result.setMode(OrchestrationMode.PARALLEL);
        result.setAgentResults(new HashMap<>());
        result.setExecutedAgents(new ArrayList<>());

        List<String> types = req.getAgentTypes();
        if (types == null || types.isEmpty()) {
            result.setNote("未指定参与编排的 Agent");
            result.setTotalCostMs(System.currentTimeMillis() - t0);
            return result;
        }

        // 提交所有 Agent 到共享线程池
        List<CompletableFuture<Map.Entry<String, AgentResult>>> futures = new ArrayList<>();
        for (String agentType : types) {
            Agent agent = agents.get(agentType);
            if (agent == null) {
                log.warn("[Parallel] 跳过未注册 Agent: type={}", agentType);
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
                    log.error("[Parallel] Agent 执行失败: type={} err={}", agentType, e.getMessage());
                    return Map.entry(agentType, (AgentResult) null);
                }
            }, agentExecutor);
            futures.add(f);
        }

        // 等待全部完成
        AgentResult best = null;
        String bestType = null;
        for (CompletableFuture<Map.Entry<String, AgentResult>> f : futures) {
            try {
                Map.Entry<String, AgentResult> e = f.join();
                String type = e.getKey();
                AgentResult ar = e.getValue();
                result.getExecutedAgents().add(type);
                if (ar != null) {
                    result.getAgentResults().put(type, ar);
                    blackboard.putScratch(type, ar);
                    blackboard.appendTrace(type, OrchestrationMode.PARALLEL,
                            ar.getScore(), ar.getConfidence(), "并行执行");
                    if (best == null || compareScore(ar, best) > 0) {
                        best = ar;
                        bestType = type;
                    }
                } else {
                    blackboard.appendTrace(type, OrchestrationMode.PARALLEL, null, null, "并行执行失败");
                }
            } catch (Exception e) {
                log.error("[Parallel] 等待 Agent 失败: err={}", e.getMessage());
            }
        }

        result.setFinalResult(best);
        result.setTrace(blackboard.getTrace());
        result.setAgentCount(result.getExecutedAgents().size());
        result.setTotalCostMs(System.currentTimeMillis() - t0);
        result.setNote("并行执行完成，最优 Agent: " + (bestType == null ? "无" : bestType));
        return result;
    }

    /**
     * 比较 score，相等时比较 confidence
     */
    private int compareScore(AgentResult a, AgentResult b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        int c = a.getScore() == null ? 0 : a.getScore().compareTo(b.getScore() == null ? BigDecimal.ZERO : b.getScore());
        if (c != 0) return c;
        return a.getConfidence() == null ? 0 : a.getConfidence().compareTo(b.getConfidence() == null ? BigDecimal.ZERO : b.getConfidence());
    }
}
