package com.njydsz.pmis.literule.core;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 单规则超时与熔断单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
class RuleTimeoutAndBreakerTest {

    @Test
    void shouldTimeoutLongRunningRule() throws Exception {
        RuleTimeoutExecutor executor = new RuleTimeoutExecutor(50, 2);
        try {
            Rule slowRule = new Rule() {
                @Override public String getCode() { return "SLOW_RULE"; }
                @Override public String getName() { return "慢规则"; }
                @Override public String getCategory() { return "TEST"; }
                @Override public RuleResult evaluate(RuleContext context) {
                    try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    return RuleResult.builder().ruleCode("SLOW_RULE").triggered(true).build();
                }
            };

            RuleContext ctx = RuleContext.of(new HashMap<>());
            RuleResult result = executor.evaluateWithTimeout(slowRule, ctx, 50);
            assertFalse(result.isTriggered());
            assertTrue(result.getDescription().contains("评估超时"));
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void shouldNotTimeoutFastRule() {
        RuleTimeoutExecutor executor = new RuleTimeoutExecutor(1000, 2);
        try {
            Rule fastRule = new Rule() {
                @Override public String getCode() { return "FAST_RULE"; }
                @Override public String getName() { return "快规则"; }
                @Override public String getCategory() { return "TEST"; }
                @Override public RuleResult evaluate(RuleContext context) {
                    return RuleResult.builder().ruleCode("FAST_RULE").triggered(true).triggeredAt(LocalDateTime.now()).build();
                }
            };

            RuleContext ctx = RuleContext.of(new HashMap<>());
            RuleResult result = executor.evaluateWithTimeout(fastRule, ctx, 1000);
            assertTrue(result.isTriggered());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void shouldOpenCircuitOnHighErrorRate() {
        RuleCircuitBreaker breaker = new RuleCircuitBreaker(0.5, 4, 1000);

        // 4 次评估，3 次失败 → 错误率 75% > 50% → 熔断 OPEN
        breaker.recordResult("R1", true);
        assertEquals(RuleCircuitBreaker.State.CLOSED, breaker.getState("R1"));

        breaker.recordResult("R1", false);
        breaker.recordResult("R1", false);
        breaker.recordResult("R1", false);

        assertEquals(RuleCircuitBreaker.State.OPEN, breaker.getState("R1"));
        assertFalse(breaker.allowEvaluate("R1"));
    }

    @Test
    void shouldKeepClosedBelowThreshold() {
        RuleCircuitBreaker breaker = new RuleCircuitBreaker(0.5, 4, 1000);

        breaker.recordResult("R2", true);
        breaker.recordResult("R2", true);
        breaker.recordResult("R2", true);
        breaker.recordResult("R2", false);

        // 4 次评估 1 次失败 → 25% < 50% → 保持 CLOSED
        assertEquals(RuleCircuitBreaker.State.CLOSED, breaker.getState("R2"));
        assertTrue(breaker.allowEvaluate("R2"));
    }

    @Test
    void shouldRecoverAfterOpenPeriod() throws InterruptedException {
        RuleCircuitBreaker breaker = new RuleCircuitBreaker(0.5, 2, 200);

        breaker.recordResult("R3", false);
        breaker.recordResult("R3", false);

        assertEquals(RuleCircuitBreaker.State.OPEN, breaker.getState("R3"));
        assertFalse(breaker.allowEvaluate("R3"));

        // 等待 OPEN 持续时间结束 → HALF_OPEN
        Thread.sleep(250);

        assertTrue(breaker.allowEvaluate("R3"));
        assertEquals(RuleCircuitBreaker.State.HALF_OPEN, breaker.getState("R3"));

        // HALF_OPEN 下成功 → CLOSED
        breaker.recordResult("R3", true);
        assertEquals(RuleCircuitBreaker.State.CLOSED, breaker.getState("R3"));
    }
}
