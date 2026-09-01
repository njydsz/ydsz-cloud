package com.njydsz.literule.server.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.safe.resilience.CircuitBreakerConfig;
import com.njydsz.common.safe.resilience.CircuitBreakerRegistry;
import com.njydsz.common.sentry.resilience.CircuitBreaker;

/**
 * 规则熔断器（基于 ydsz-common-sentry 统一熔断能力）。
 *
 * <p>每个规则编码独立维护一个熔断器，底层委托 sentry {@link CircuitBreaker}（平台自研弹性引擎），
 * 提供滑动窗口失败率统计、状态自动流转、半开探测等标准熔断能力。
 *
 * <h3>1.0.0 变更</h3>
 *
 * <p>底层实现委托 {@code ydsz-common-sentry} 的 {@link CircuitBreaker} 封装（平台自研弹性引擎，
 * 决策见 docs/ADR-0004-resilience-self-hosted.md），获得以下收益：
 *
 * <ul>
 *   <li>全仓统一的熔断状态机与事件总线
 *   <li>符合编码规范第 27.5 节"禁止自建熔断器"的要求
 *   <li>修复历史缺陷：错误率阈值（0-1）此前直传百分比语义 API，实际生效阈值缩小 100 倍
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

  /** 共享的自研引擎注册表（所有规则共用配置模板） */
  private final CircuitBreakerRegistry sharedRegistry;

  /** 每个规则一个独立熔断器（sentry 封装） */
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

    // 构建共享的自研引擎配置（阈值 0-1 换算为百分比语义）
    CircuitBreakerConfig config =
        CircuitBreakerConfig.custom()
            .failureRateThreshold((float) (errorRateThreshold * 100))
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(this.minEvaluations)
            .waitDurationInOpenState(java.time.Duration.ofMillis(openStateMs))
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
    // 使用 sentry CircuitBreaker 统一 canExecute API
    return breaker.canExecute();
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
                new CircuitBreaker(
                    "literule-" + k, buildBreakerConfig(), sharedRegistry));

    if (success) {
      // 使用 sentry CircuitBreaker 统一 recordSuccess API
      breaker.recordSuccess(0, TimeUnit.MILLISECONDS);
    } else {
      // 使用 sentry CircuitBreaker 统一 recordFailure API
      breaker.recordFailure(
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
        .waitDurationInOpenState(java.time.Duration.ofMillis(openStateMs))
        .minimumNumberOfCalls(minEvaluations)
        .permittedNumberOfCallsInHalfOpenState(1)
        .automaticTransitionFromOpenToHalfOpenEnabled(true)
        .build();
  }

  /** 将 sentry CircuitBreaker.State 转换为本地 State */
  private static State toLocalState(CircuitBreaker.State sentryState) {
    return switch (sentryState) {
      case OPEN -> State.OPEN;
      case HALF_OPEN -> State.HALF_OPEN;
      case CLOSED -> State.CLOSED;
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
      // 从注册表中移除，释放资源
      sharedRegistry.remove("literule-" + ruleCode);
    }
  }

  /** 重置全部熔断器 */
  public void resetAll() {
    breakers.keySet().forEach(this::reset);
    breakers.clear();
  }
}
