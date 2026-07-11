package com.njydsz.pmis.agent.server.engine.llm;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM Provider 熔断器（P1-2 落地，P1-3 重构为 Resilience4j 实现）。
 *
 * <p>当某个 Provider 连续失败达到阈值时，熔断器开启（OPEN），
 * 在冷却期内跳过该 Provider 的调用，避免持续请求已宕机的 LLM 服务。
 *
 * <p>状态机：
 * <ul>
 *   <li><b>CLOSED</b>：正常调用，记录失败次数</li>
 *   <li><b>OPEN</b>：熔断中，拒绝调用，等待冷却期过后进入 HALF_OPEN</li>
 *   <li><b>HALF_OPEN</b>：放行有限次试探调用，成功则恢复 CLOSED，失败则重新 OPEN</li>
 * </ul>
 *
 * <p><b>P1-3 重构</b>：原自研实现使用 {@code synchronized} + {@code AtomicInteger}，
 * 存在锁粒度粗、无滑动窗口统计、无事件发布等缺陷。
 * 现替换为 Resilience4j {@link CircuitBreaker}，获得生产级能力：
 * <ul>
 *   <li>滑动窗口统计（基于计数或时间）</li>
 *   <li>自动 HALF_OPEN 探测</li>
 *   <li>事件发布（可对接 Micrometer 监控）</li>
 *   <li>与项目已有的 Resilience4j 生态统一管理</li>
 * </ul>
 *
 * <p>对外 API 保持兼容：{@link #allowCall} / {@link #recordSuccess} / {@link #recordFailure}
 * / {@link #getState} / {@link #reset} 签名不变，内部委托 Resilience4j 实现。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0 (P1-2), 1.3.1 (P1-3 Resilience4j 重构)
 */
@Slf4j
public class LlmCircuitBreaker {

    /** 默认失败阈值（连续失败次数达到此值时熔断） */
    private static final int DEFAULT_FAILURE_THRESHOLD = 5;

    /** 默认冷却时间（毫秒） */
    private static final long DEFAULT_COOLDOWN_MS = 30_000L;

    /** 默认半开试探次数 */
    private static final int DEFAULT_HALF_OPEN_TRIALS = 1;

    /** Resilience4j 熔断器注册表 */
    private final CircuitBreakerRegistry registry;

    /** 每个 Provider 的 Resilience4j CircuitBreaker 实例 */
    private final ConcurrentHashMap<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

    /** 失败阈值 */
    private final int failureThreshold;

    /** 冷却时间（毫秒） */
    private final long cooldownMs;

    /**
     * 使用默认配置构造熔断器。
     */
    public LlmCircuitBreaker() {
        this(DEFAULT_FAILURE_THRESHOLD, DEFAULT_COOLDOWN_MS);
    }

    /**
     * 自定义配置构造熔断器。
     *
     * @param failureThreshold 失败阈值
     * @param cooldownMs       冷却时间（毫秒）
     */
    public LlmCircuitBreaker(int failureThreshold, long cooldownMs) {
        this.failureThreshold = failureThreshold;
        this.cooldownMs = cooldownMs;
        this.registry = CircuitBreakerRegistry.of(buildConfig());
        log.info("[CircuitBreaker] 初始化 Resilience4j 熔断器, failureThreshold={}, cooldownMs={}",
                failureThreshold, cooldownMs);
    }

    /**
     * 构建 Resilience4j 熔断器配置。
     *
     * <p>配置映射：
     * <ul>
     *   <li>slidingWindowSize = failureThreshold（窗口大小等于失败阈值）</li>
     *   <li>minimumNumberOfCalls = failureThreshold（至少 N 次调用后才评估）</li>
     *   <li>failureRateThreshold = 100%（全部失败才熔断，等价于连续失败 N 次）</li>
     *   <li>waitDurationInOpenState = cooldownMs（OPEN 状态持续时间）</li>
     *   <li>permittedNumberOfCallsInHalfOpenState = DEFAULT_HALF_OPEN_TRIALS（半开试探次数）</li>
     *   <li>slidingWindowType = COUNT_BASED（基于计数的滑动窗口）</li>
     *   <li>automaticTransitionFromOpenToHalfOpenEnabled = true（自动从 OPEN 转 HALF_OPEN）</li>
     * </ul>
     *
     * @return Resilience4j 熔断器配置
     */
    private CircuitBreakerConfig buildConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(failureThreshold)
                .minimumNumberOfCalls(failureThreshold)
                .failureRateThreshold(100.0f)
                .waitDurationInOpenState(Duration.ofMillis(cooldownMs))
                .permittedNumberOfCallsInHalfOpenState(DEFAULT_HALF_OPEN_TRIALS)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
    }

    /**
     * 判断指定 Provider 是否允许调用（熔断器是否闭合或半开）。
     *
     * <p>委托 Resilience4j 的 {@link CircuitBreaker#tryAcquirePermission()} 实现：
     * <ul>
     *   <li>CLOSED → 返回 true</li>
     *   <li>OPEN → 返回 false（等待冷却期后自动转 HALF_OPEN）</li>
     *   <li>HALF_OPEN → 仅允许 limited 次试探</li>
     * </ul>
     *
     * @param providerName Provider 名称
     * @return true 表示允许调用（CLOSED 或 HALF_OPEN）；false 表示熔断中（OPEN）
     */
    public boolean allowCall(String providerName) {
        CircuitBreaker cb = getOrCreate(providerName);
        boolean allowed = cb.tryAcquirePermission();
        if (!allowed) {
            log.debug("[CircuitBreaker] {} 熔断中, 跳过 (state={})", providerName, cb.getState());
        }
        return allowed;
    }

    /**
     * 记录成功：重置失败计数，恢复 CLOSED 状态。
     *
     * @param providerName Provider 名称
     */
    public void recordSuccess(String providerName) {
        CircuitBreaker cb = breakers.get(providerName);
        if (cb == null) return;
        cb.onSuccess(0, java.util.concurrent.TimeUnit.NANOSECONDS);
        if (cb.getState() == CircuitBreaker.State.HALF_OPEN) {
            log.info("[CircuitBreaker] {} 熔断器恢复 CLOSED（半开试探成功）", providerName);
        }
    }

    /**
     * 记录失败：增加失败计数，达到阈值时熔断。
     *
     * @param providerName Provider 名称
     */
    public void recordFailure(String providerName) {
        CircuitBreaker cb = getOrCreate(providerName);
        cb.onError(0, java.util.concurrent.TimeUnit.NANOSECONDS, new RuntimeException("LLM provider call failed"));
        if (cb.getState() == CircuitBreaker.State.OPEN) {
            log.warn("[CircuitBreaker] {} 连续失败达到阈值，熔断器开启 (OPEN)，冷却 {}ms",
                    providerName, cooldownMs);
        }
    }

    /**
     * 获取指定 Provider 的当前状态（用于监控/健康检查）。
     *
     * @param providerName Provider 名称
     * @return 状态名称（CLOSED / OPEN / HALF_OPEN）
     */
    public String getState(String providerName) {
        CircuitBreaker cb = breakers.get(providerName);
        if (cb == null) return "CLOSED";
        return cb.getState().name();
    }

    /**
     * 重置指定 Provider 的熔断状态（用于手动恢复）。
     *
     * @param providerName Provider 名称
     */
    public void reset(String providerName) {
        CircuitBreaker cb = breakers.get(providerName);
        if (cb != null) {
            cb.reset();
            log.info("[CircuitBreaker] {} 熔断器已手动重置", providerName);
        }
    }

    /**
     * 获取或创建指定 Provider 的 CircuitBreaker（惰性初始化）。
     *
     * @param providerName Provider 名称
     * @return Resilience4j CircuitBreaker 实例
     */
    private CircuitBreaker getOrCreate(String providerName) {
        return breakers.computeIfAbsent(providerName,
                name -> registry.circuitBreaker(name, buildConfig()));
    }
}
