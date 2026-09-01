package com.njydsz.literule.server.core;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 规则熔断器（基于 Resilience4j）。
 *
 * <p>每个规则编码独立维护一个熔断器，底层委托 Resilience4j {@link CircuitBreaker}，
 * 提供滑动窗口失败率统计、状态自动流转、半开探测等标准熔断能力。
 *
 * <h3>1.0.0 变更（2026-09-01）</h3>
 *
 * <p>底层实现改为 Resilience4j（{@code resilience4j-circuitbreaker}），移除自研引擎依赖：
 *
 * <ul>
 *   <li>使用 Resilience4j {@link CircuitBreakerRegistry} 与 {@link CircuitBreaker} 标准 API
 *   <li>使用 Resilience4j {@link CircuitBreakerConfig} 配置（阈值换算百分比语义）
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class RuleCircuitBreaker {

  /** 熔断状态（与 Resilience4j CircuitBreaker.State 兼容） */
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

  /** 每个规则一个独立熔断器（Resilience4j 实例） */
  private final ConcurrentMap<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

  /**
   * 构造熔断器
   *
   * @param errorRateThreshold 错误率阈值（0~1.0）
   * @param minEvaluations 最小评估次数（达到后才计算错误率；同时作为滑动窗口大小）
   * @param openStateMs OPEN 状态持续时间（毫秒）
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

    // 构建共享的 Resilience4j 配置（阈值 0-1 换算为百分比语义）
    CircuitBreakerConfig config =
        CircuitBreakerConfig.custom()
            .failureRateThreshold((float) (errorRateThreshold * 100))
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(this.minEvaluations)
            .waitDurationInOpenState(Duration.ofMillis(openStateMs))
            .minimumNumberOfCalls(this.minEvaluations)
            .permittedNumberOfCallsInHalfOpenState(1)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();

    this.sharedRegistry = CircuitBreakerRegistry.of(config);

    log.info(
        "[LiteRule-Breaker] 规则熔断器初始化完成: threshold={}, window={}, openStateMs={}",
        errorRateThreshold,
        minEvaluations,
        openStateMs);
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
    return breaker.getState() != CircuitBreaker.State.OPEN;
  }

  /**
   * 记录评估结果
   *
   * @param ruleCode 规则编码
   * @param success 是否成功（false 表示异常）
   */
  public void recordResult(String ruleCode, boolean success) {
    CircuitBreaker breaker =
        breakers.computeIfAbsent(
            ruleCode,
            k ->
                sharedRegistry.circuitBreaker(
                    "literule-" + k, buildBreakerConfig()));

    if (success) {
      breaker.onSuccess(0, TimeUnit.MILLISECONDS);
    } else {
      breaker.onError(
          0, TimeUnit.MILLISECONDS, new RuntimeException("Rule evaluation failure"));
    }

    // 记录状态变更日志（用于运维排查）
    State currentState = toLocalState(breaker.getState());
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
    return breaker == null ? State.CLOSED : toLocalState(breaker.getState());
  }

  /** 构建单规则熔断配置（复用全局阈值参数） */
  private CircuitBreakerConfig buildBreakerConfig() {
    return CircuitBreakerConfig.custom()
        .failureRateThreshold((float) (errorRateThreshold * 100))
        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
        .slidingWindowSize(minEvaluations)
        .waitDurationInOpenState(Duration.ofMillis(openStateMs))
        .minimumNumberOfCalls(minEvaluations)
        .permittedNumberOfCallsInHalfOpenState(1)
        .automaticTransitionFromOpenToHalfOpenEnabled(true)
        .build();
  }

  /** 将 Resilience4j CircuitBreaker.State 转换为本地 State */
  private static State toLocalState(CircuitBreaker.State engineState) {
    return switch (engineState) {
      case OPEN -> State.OPEN;
      case HALF_OPEN -> State.HALF_OPEN;
      default -> State.CLOSED;
    };
  }

  /**
   * 重置规则熔断器
   *
   * @param ruleCode 规则编码
   */
  public void reset(String ruleCode) {
    CircuitBreaker removed = breakers.remove(ruleCode);
    if (removed != null) {
      sharedRegistry.remove("literule-" + ruleCode);
    }
  }

  /** 重置全部熔断器 */
  public void resetAll() {
    breakers.keySet().forEach(this::reset);
    breakers.clear();
  }
}
