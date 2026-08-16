package com.njydsz.literule.server.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.sentry.resilience.CircuitBreaker;

/**
 * 规则熔断器（基于 ydsz-common-sentry 统一熔断能力）。
 *
 * <p>每个规则编码独立维护一个熔断器，底层委托 sentry {@link CircuitBreaker}（Resilience4j），
 * 提供滑动窗口失败率统计、状态自动流转、半开探测等标准熔断能力。
 *
 * <h3>v2.8 变更</h3>
 * <p>自 v2.8 起，底层实现从自研滑动窗口改为委托 {@code ydsz-common-sentry} 的
 * {@link CircuitBreaker} 封装（基于 Resilience4j），获得以下收益：
 * <ul>
 *   <li>经过 10+ 年生产验证的稳定性</li>
 *   <li>原生支持 Micrometer 指标导出</li>
 *   <li>支持事件总线（状态变更 / 错误 / 成功事件）</li>
 *   <li>符合编码规范第 27.5 节"禁止自建熔断器"的要求</li>
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 * @see com.njydsz.common.sentry.resilience.CircuitBreaker
 */
@Slf4j
public class RuleCircuitBreaker {

    /** 熔断状态（与 sentry CircuitBreaker.State 一一对应） */
    public enum State {
        /** 关闭：正常评估 */
        CLOSED,
        /** 打开：拒绝评估 */
        OPEN,
        /** 半开：试探性评估 */
        HALF_OPEN
    }

    private final double errorRateThreshold;
    private final int minEvaluations;
    private final long openStateMs;

    /** 共享的 Resilience4j 注册表（所有规则共用配置模板） */
    private final CircuitBreakerRegistry sharedRegistry;

    /** 每个规则一个独立熔断器（sentry 封装） */
    private final ConcurrentMap<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

    /**
     * 构造熔断器
     *
     * @param errorRateThreshold 错误率阈值（0~1.0）
     * @param minEvaluations     最小评估次数（达到后才计算错误率；同时作为滑动窗口大小）
     * @param openStateMs        OPEN 状态持续时间（毫秒）
     */
    public RuleCircuitBreaker(double errorRateThreshold, int minEvaluations, long openStateMs) {
        if (errorRateThreshold <= 0 || errorRateThreshold > 1) {
            throw new IllegalArgumentException("errorRateThreshold 必须在 (0, 1] 区间");
        }
        if (openStateMs <= 0) {
            throw new IllegalArgumentException("openStateMs 必须大于 0");
        }
        this.errorRateThreshold = errorRateThreshold;
        this.minEvaluations = Math.max(1, minEvaluations);
        this.openStateMs = openStateMs;

        // 构建共享的 Resilience4j 配置
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold((float) errorRateThreshold)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(this.minEvaluations)
                .waitDurationInOpenState(java.time.Duration.ofMillis(openStateMs))
                .minimumNumberOfCalls(this.minEvaluations)
                .permittedNumberOfCallsInHalfOpenState(1)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordException(e -> true)
                .build();

        this.sharedRegistry = CircuitBreakerRegistry.of(config);

        log.info("[LiteRule-Breaker] 规则熔断器初始化完成: threshold={}, window={}, openStateMs={}",
                errorRateThreshold, minEvaluations, openStateMs);
    }

    /**
     * 判断规则是否允许评估（未被熔断）
     *
     * @param ruleCode 规则编码
     * @return true=允许评估；false=已被熔断
     */
    public boolean allowEvaluate(String ruleCode) {
        CircuitBreaker breaker = breakers.get(ruleCode);
        if (breaker == null) {
            return true;
        }
        // 使用 sentry CircuitBreaker 统一 canExecute API
        return breaker.canExecute();
    }

    /**
     * 记录评估结果
     *
     * @param ruleCode  规则编码
     * @param success   是否成功（false 表示异常）
     */
    public void recordResult(String ruleCode, boolean success) {
        CircuitBreaker breaker = breakers.computeIfAbsent(ruleCode,
                k -> new CircuitBreaker("literule-" + k, sharedRegistry));

        if (success) {
            // 使用 sentry CircuitBreaker 统一 recordSuccess API
            breaker.recordSuccess(0, TimeUnit.MILLISECONDS);
        } else {
            // 使用 sentry CircuitBreaker 统一 recordFailure API
            breaker.recordFailure(0, TimeUnit.MILLISECONDS,
                    new RuntimeException("Rule evaluation failure"));
        }

        // 记录状态变更日志（用于运维排查）
        State currentState = breaker.getState();
        if (currentState == State.OPEN) {
            log.warn("[LiteRule-Breaker] 规则 {} 熔断器 OPEN", ruleCode);
        } else if (currentState == State.HALF_OPEN) {
            log.info("[LiteRule-Breaker] 规则 {} 熔断器 HALF_OPEN", ruleCode);
        }
    }

    /**
     * 查询规则当前熔断状态
     *
     * @param ruleCode 规则编码
     * @return 状态；规则未被评估过返回 CLOSED
     */
    public State getState(String ruleCode) {
        CircuitBreaker breaker = breakers.get(ruleCode);
        return breaker == null ? State.CLOSED : breaker.getState();
    }

    /**
     * 重置规则熔断器
     *
     * @param ruleCode 规则编码
     */
    public void reset(String ruleCode) {
        CircuitBreaker removed = breakers.remove(ruleCode);
        if (removed != null) {
            // 从 Resilience4j 注册表中移除，释放资源
            sharedRegistry.remove("literule-" + ruleCode);
        }
    }

    /**
     * 重置全部熔断器
     */
    public void resetAll() {
        breakers.keySet().forEach(this::reset);
        breakers.clear();
    }
}
