package com.njydsz.common.safe.ratelimit.core;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.safe.ratelimit.algorithm.RateLimiter;
import com.njydsz.common.safe.ratelimit.circuitbreaker.CircuitBreaker;
import com.njydsz.common.safe.ratelimit.cluster.ClusterRateLimiter;
import com.njydsz.common.safe.ratelimit.enums.RateLimitMode;
import com.njydsz.common.safe.ratelimit.enums.RateLimitResult;
import com.njydsz.common.safe.ratelimit.model.RateLimitContext;
import com.njydsz.common.safe.ratelimit.model.RateLimitDecision;
import com.njydsz.common.safe.ratelimit.model.RateLimitRule;
import com.njydsz.common.safe.ratelimit.properties.RateLimitProperties;
import com.njydsz.common.safe.ratelimit.spi.RateLimitRuleProvider;

/**
 * 限流管理器
 *
 * <p>统一入口：根据规则模式（LOCAL / CLUSTER / ADAPTIVE）分发到不同的限流器。
 *
 * <p><b>熔断保护：</b>集群模式下的 Redis 调用通过 {@link CircuitBreaker} 进行保护， 当 Redis
 * 连续失败时自动熔断，避免级联故障。熔断期间直接降级为本地限流或放行。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class RateLimitManager {

  /** 熔断器资源标识（用于区分不同熔断目标） */
  private static final String CIRCUIT_BREAKER_RESOURCE = "redis-cluster-limiter";

  private final RateLimitRuleProvider ruleProvider;
  private final RateLimitRuleCache ruleCache;
  private final RateLimitProperties properties;
  private final ClusterRateLimiter clusterLimiter;

  /** Redis 集群调用的熔断器 */
  private final CircuitBreaker circuitBreaker;

  /** 决策监听器（用于埋点） */
  private final List<DecisionListener> listeners = new CopyOnWriteArrayList<>();

  /**
   * 构造函数（推荐用法，由 Spring 注入 {@link ClusterRateLimiter} Bean）
   *
   * @param ruleProvider 规则提供器
   * @param properties 限流配置
   * @param clusterLimiter 集群限流器（可由 {@code RateLimitAutoConfiguration} 注入； 为 {@code null} 时集群模式将降级为
   *     PASS/BLOCK）
   */
  public RateLimitManager(
      RateLimitRuleProvider ruleProvider,
      RateLimitProperties properties,
      ClusterRateLimiter clusterLimiter) {
    this(ruleProvider, properties, clusterLimiter, createDefaultCircuitBreaker(properties));
  }

  /**
   * 构造函数（允许注入自定义熔断器，便于测试）
   *
   * @param ruleProvider 规则提供器
   * @param properties 限流配置
   * @param clusterLimiter 集群限流器
   * @param circuitBreaker 熔断器实例
   */
  public RateLimitManager(
      RateLimitRuleProvider ruleProvider,
      RateLimitProperties properties,
      ClusterRateLimiter clusterLimiter,
      CircuitBreaker circuitBreaker) {
    this.ruleProvider = ruleProvider;
    this.properties = properties;
    this.ruleCache = new RateLimitRuleCache(ruleProvider);
    this.clusterLimiter = clusterLimiter;
    this.circuitBreaker = circuitBreaker;
    if (clusterLimiter == null) {
      log.warn(
          "RateLimitManager initialized without ClusterRateLimiter; CLUSTER mode will fall back to {}",
          properties.getFallbackOnError());
    }
    if (circuitBreaker != null) {
      log.info("RateLimitManager initialized with CircuitBreaker for Redis cluster protection");
    }
  }

  /**
   * 创建默认熔断器配置
   *
   * <p>默认策略：
   *
   * <ul>
   *   <li>失败率阈值 50%
   *   <li>最小调用数 5（快速熔断）
   *   <li>OPEN 状态等待 10s 后进入 HALF_OPEN
   * </ul>
    * @param properties properties 参数
   */
  private static CircuitBreaker createDefaultCircuitBreaker(RateLimitProperties properties) {
    boolean cbEnabled =
        properties.getCircuitBreaker() != null && properties.getCircuitBreaker().isEnabled();
    if (!cbEnabled) {
      return null;
    }
    CircuitBreaker.CircuitBreakerConfig config =
        CircuitBreaker.CircuitBreakerConfig.builder()
            .failureRateThreshold(properties.getCircuitBreaker().getFailureRateThreshold() / 100.0)
            .minimumNumberOfCalls(properties.getCircuitBreaker().getMinimumNumberOfCalls())
            .waitDurationInOpenState(
                Duration.ofSeconds(properties.getCircuitBreaker().getWaitDurationSeconds()))
            .permittedNumberOfCallsInHalfOpenState(
                properties.getCircuitBreaker().getPermittedHalfOpenCalls())
            .slidingWindowSize(properties.getCircuitBreaker().getSlidingWindowSize())
            .build();
    return new CircuitBreaker(config);
  }

  /**
   * 核心限流决策入口。
   *
   * @param context 限流上下文
   * @return 限流决策
   */
  public RateLimitDecision decide(RateLimitContext context) {
    if (!properties.isEnabled()) {
      return passThrough(context, "ratelimit disabled");
    }
    Optional<RateLimiter> limiterOpt = ruleCache.getLimiter(context.getResource());
    if (limiterOpt.isEmpty()) {
      return passThrough(context, "no rule matched");
    }
    RateLimiter limiter = limiterOpt.get();
    RateLimitRule rule = limiter.getRule();
    if (!rule.isEnabled()) {
      return passThrough(context, "rule disabled");
    }

    RateLimitDecision decision;
    try {
      if (rule.getMode() == RateLimitMode.LOCAL) {
        decision = limiter.tryAcquire(context);
      } else if (rule.getMode() == RateLimitMode.CLUSTER) {
        decision = decideClusterMode(context, limiter, rule);
      } else {
        // ADAPTIVE / HYBRID：简化处理，按 LOCAL 处理
        decision = limiter.tryAcquire(context);
      }
    } catch (Exception ex) {
      log.error("Rate limit decision failed for resource={}", context.getResource(), ex);
      decision = handleFallback(context, rule, ex);
    }
    decision.setResource(context.getResource());
    notifyListeners(decision);
    return decision;
  }

  /**
   * 集群模式决策（带熔断保护）
   *
   * <p>通过熔断器保护 Redis 调用，熔断开启时直接降级为本地限流。
   */
  private RateLimitDecision decideClusterMode(
      RateLimitContext context, RateLimiter limiter, RateLimitRule rule) {
    if (clusterLimiter == null) {
      // ClusterRateLimiter 未注入（如 Redis 未启用），降级为本地限流
      log.debug(
          "ClusterRateLimiter not available, fall back to local limiter for resource={}",
          context.getResource());
      return limiter.tryAcquire(context);
    }

    // 熔断器未启用或未配置，直接调用集群限流器
    if (circuitBreaker == null) {
      return clusterLimiter.tryAcquire(rule, context);
    }

    // 检查熔断器状态，OPEN 状态直接降级
    if (circuitBreaker.getState(CIRCUIT_BREAKER_RESOURCE) == CircuitBreaker.State.OPEN) {
      log.debug(
          "Circuit breaker OPEN, fall back to local limiter for resource={}",
          context.getResource());
      return limiter.tryAcquire(context);
    }

    // 通过熔断器执行 Redis 调用
    return circuitBreaker.tryAcquire(
        CIRCUIT_BREAKER_RESOURCE, () -> clusterLimiter.tryAcquire(rule, context));
  }

  /** 放行 */
  private RateLimitDecision passThrough(RateLimitContext context, String reason) {
    return RateLimitDecision.builder()
        .resource(context.getResource())
        .result(RateLimitResult.PASS)
        .remaining(Double.MAX_VALUE)
        .threshold(-1)
        .timestamp(Instant.now())
        .reason(reason)
        .build();
  }

  /** 失败降级 */
  private RateLimitDecision handleFallback(
      RateLimitContext context, RateLimitRule rule, Throwable ex) {
    if ("BLOCK".equalsIgnoreCase(properties.getFallbackOnError())) {
      return RateLimitDecision.builder()
          .resource(context.getResource())
          .rule(rule)
          .result(RateLimitResult.BLOCKED)
          .remaining(0)
          .timestamp(Instant.now())
          .reason("error fallback: " + ex.getMessage())
          .build();
    }
    return passThrough(context, "error fallback: " + ex.getMessage());
  }

  /**
   * 添加决策监听器。
   *
   * @param listener 决策监听器
   */
  public void addListener(DecisionListener listener) {
    listeners.add(listener);
  }

  private void notifyListeners(RateLimitDecision decision) {
    for (DecisionListener listener : listeners) {
      try {
        listener.onDecision(decision);
      } catch (Exception ex) {
        log.warn("Decision listener failed", ex);
      }
    }
  }

  /**
   * 重新加载规则。
   *
   * <p>清空规则缓存并重新加载最新配置，用于规则变更热更新。
   */
  public void reload() {
    ruleCache.reload();
  }

  /**
   * 获取规则提供器。
   *
   * @return 规则提供器实例
   */
  public RateLimitRuleProvider getRuleProvider() {
    return ruleProvider;
  }

  /**
   * 获取规则缓存。
   *
   * @return 规则缓存实例
   */
  public RateLimitRuleCache getRuleCache() {
    return ruleCache;
  }

  /**
   * 获取熔断器（用于监控/管理）。
   *
   * @return 熔断器实例（未配置时为 {@code Optional.empty()}
   */
  public Optional<CircuitBreaker> getCircuitBreaker() {
    return Optional.ofNullable(circuitBreaker);
  }

  /** 决策监听器（用于指标埋点/告警） */
  @FunctionalInterface
  public interface DecisionListener {
    void onDecision(RateLimitDecision decision);
  }
}
