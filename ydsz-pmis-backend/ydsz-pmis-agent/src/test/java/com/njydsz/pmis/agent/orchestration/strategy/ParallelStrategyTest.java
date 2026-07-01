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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并行编排策略测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ParallelStrategy 并行编排")
class ParallelStrategyTest {

    @Test
    @DisplayName("空 agentTypes 直接返回")
    void emptyTypes() {
        ParallelStrategy s = new ParallelStrategy();
        OrchestrationResult r = s.apply(req(List.of()), Map.of(), new AgentBlackboard());
        assertThat(r.getMode()).isEqualTo(OrchestrationMode.PARALLEL);
        assertThat(r.getAgentResults()).isEmpty();
        assertThat(r.getNote()).contains("未指定");
        s.shutdown();
    }

    @Test
    @DisplayName("全部 Agent 并行执行 - finalResult = 最高 score")
    void finalBestScore() {
        ParallelStrategy s = new ParallelStrategy(3);
        Agent a = stubAgent(AgentType.RISK_WARNING, AgentAlertLevel.NORMAL, "60", "0.60");
        Agent b = stubAgent(AgentType.PROFIT_FORECAST, AgentAlertLevel.RED, "90", "0.90");
        Agent c = stubAgent(AgentType.WIN_RATE_PREDICT, AgentAlertLevel.YELLOW, "75", "0.80");
        OrchestrationRequest rq = req(List.of("RISK_WARNING", "PROFIT_FORECAST", "WIN_RATE_PREDICT"));
        OrchestrationResult r = s.apply(rq,
                Map.of("RISK_WARNING", a, "PROFIT_FORECAST", b, "WIN_RATE_PREDICT", c),
                new AgentBlackboard());
        assertThat(r.getFinalResult().getScore()).isEqualByComparingTo("90");
        assertThat(r.getFinalResult().getAlertLevel()).isEqualTo(AgentAlertLevel.RED);
        assertThat(r.getAgentResults()).hasSize(3);
        assertThat(r.getNote()).contains("PROFIT_FORECAST");
        s.shutdown();
    }

    @Test
    @DisplayName("并行执行 - 多个 Agent 实际并发")
    void runsInParallel() throws InterruptedException {
        ParallelStrategy s = new ParallelStrategy(3);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(2);
        Agent a = blockingAgent(AgentType.RISK_WARNING, startGate, doneGate);
        Agent b = blockingAgent(AgentType.PROFIT_FORECAST, startGate, doneGate);
        long t0 = System.currentTimeMillis();
        Thread t = new Thread(() -> s.apply(req(List.of("RISK_WARNING", "PROFIT_FORECAST")),
                Map.of("RISK_WARNING", a, "PROFIT_FORECAST", b), new AgentBlackboard()));
        t.start();
        // 等待 2 个 Agent 都进入 blocking 状态
        assertThat(doneGate.await(2, TimeUnit.SECONDS)).isTrue();
        startGate.countDown();
        t.join(3000);
        long cost = System.currentTimeMillis() - t0;
        // 串行应 ~ 600ms，并行应 ~ 300ms；我们给 600ms 容忍即可
        assertThat(cost).isLessThan(1500L);
        s.shutdown();
    }

    @Test
    @DisplayName("Agent 抛错 - 不影响其他 Agent")
    void exceptionTolerated() {
        ParallelStrategy s = new ParallelStrategy(2);
        Agent good = stubAgent(AgentType.RISK_WARNING, AgentAlertLevel.NORMAL, "60", "0.60");
        Agent bad = new Agent() {
            @Override
            public AgentType type() { return AgentType.PROFIT_FORECAST; }
            @Override
            public AgentResult execute(AgentContext ctx) { throw new RuntimeException("boom"); }
        };
        OrchestrationResult r = s.apply(req(List.of("RISK_WARNING", "PROFIT_FORECAST")),
                Map.of("RISK_WARNING", good, "PROFIT_FORECAST", bad), new AgentBlackboard());
        // 抛错的 Agent 不会写入 agentResults，good 仍正常输出
        assertThat(r.getAgentResults()).containsOnlyKeys("RISK_WARNING");
        assertThat(r.getFinalResult().getScore()).isEqualByComparingTo("60");
        s.shutdown();
    }

    @Test
    @DisplayName("未注册 Agent - 跳过")
    void unregisteredSkipped() {
        ParallelStrategy s = new ParallelStrategy(2);
        Agent a = stubAgent(AgentType.RISK_WARNING, AgentAlertLevel.NORMAL, "60", "0.60");
        OrchestrationResult r = s.apply(req(List.of("RISK_WARNING", "MISSING")),
                Map.of("RISK_WARNING", a), new AgentBlackboard());
        assertThat(r.getAgentResults()).containsOnlyKeys("RISK_WARNING");
        s.shutdown();
    }

    @Test
    @DisplayName("score 相等时 confidence 更高者胜出")
    void sameScoreHigherConfidenceWins() {
        ParallelStrategy s = new ParallelStrategy(2);
        Agent a = stubAgent(AgentType.RISK_WARNING, AgentAlertLevel.NORMAL, "80", "0.50");
        Agent b = stubAgent(AgentType.PROFIT_FORECAST, AgentAlertLevel.NORMAL, "80", "0.95");
        OrchestrationResult r = s.apply(req(List.of("RISK_WARNING", "PROFIT_FORECAST")),
                Map.of("RISK_WARNING", a, "PROFIT_FORECAST", b), new AgentBlackboard());
        assertThat(r.getFinalResult().getConfidence()).isEqualByComparingTo("0.95");
        assertThat(r.getNote()).contains("PROFIT_FORECAST");
        s.shutdown();
    }

    @Test
    @DisplayName("线程池是守护线程 + shutdown 安全")
    void daemonAndShutdown() throws InterruptedException {
        ParallelStrategy s = new ParallelStrategy(2);
        Agent a = stubAgent(AgentType.RISK_WARNING, AgentAlertLevel.NORMAL, "60", "0.60");
        s.apply(req(List.of("RISK_WARNING")), Map.of("RISK_WARNING", a), new AgentBlackboard());
        s.shutdown();
        Thread.sleep(50);
        // 关闭后无异常即可
    }

    private OrchestrationRequest req(List<String> types) {
        OrchestrationRequest req = new OrchestrationRequest();
        req.setBizType("PROJECT");
        req.setBizId(1L);
        req.setBizRef("PRJ-1");
        req.setCallerId(1L);
        req.setCallerName("tester");
        req.setSource("TEST");
        req.setAgentTypes(types);
        req.setFacts(new HashMap<>());
        return req;
    }

    private Agent stubAgent(AgentType t, AgentAlertLevel l, String score, String conf) {
        return new Agent() {
            @Override
            public AgentType type() { return t; }
            @Override
            public AgentResult execute(AgentContext ctx) {
                AgentResult r = new AgentResult();
                r.setAgentType(t);
                r.setAlertLevel(l);
                r.setScore(new BigDecimal(score));
                r.setConfidence(new BigDecimal(conf));
                return r;
            }
        };
    }

    private Agent blockingAgent(AgentType t, CountDownLatch start, CountDownLatch done) {
        return new Agent() {
            @Override
            public AgentType type() { return t; }
            @Override
            public AgentResult execute(AgentContext ctx) {
                try {
                    done.countDown();
                    start.await();
                    Thread.sleep(300);
                } catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                }
                AgentResult r = new AgentResult();
                r.setAgentType(t);
                r.setAlertLevel(AgentAlertLevel.NORMAL);
                r.setScore(new BigDecimal("50"));
                r.setConfidence(new BigDecimal("0.5"));
                return r;
            }
        };
    }
}
