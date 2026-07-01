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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 并行编排策略
 *
 * <p>所有 Agent 同时跑（独立线程），最后合并到黑板。
 * <ul>
 *   <li>finalResult 取第一个完成且 score 最高的 Agent 输出（竞速）</li>
 *   <li>或者：finalResult 取最先完成的 Agent 输出（更快 → 可配置）</li>
 * </ul>
 *
 * <p>线程池：固定 5 线程守护线程，Bean 由协调器持有以便复用。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class ParallelStrategy implements OrchestrationStrategy {

    private final ExecutorService executor;

    public ParallelStrategy() {
        this(5);
    }

    public ParallelStrategy(int parallelism) {
        AtomicInteger seq = new AtomicInteger(0);
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "agent-orch-parallel-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        this.executor = Executors.newFixedThreadPool(parallelism, tf);
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

        // 提交所有 Agent 到线程池
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
            }, executor);
            futures.add(f);
        }

        // 等待全部完成
        AgentResult best = null;
        String bestType = null;
        for (CompletableFuture<Map.Entry<String, AgentResult>> f : futures) {
            try {
                Map.Entry<String, AgentResult> e = f.get();
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
        int c = a.getScore() == null ? 0 : a.getScore().compareTo(b.getScore() == null ? java.math.BigDecimal.ZERO : b.getScore());
        if (c != 0) return c;
        return a.getConfidence() == null ? 0 : a.getConfidence().compareTo(b.getConfidence() == null ? java.math.BigDecimal.ZERO : b.getConfidence());
    }

    public void shutdown() {
        executor.shutdown();
    }
}
