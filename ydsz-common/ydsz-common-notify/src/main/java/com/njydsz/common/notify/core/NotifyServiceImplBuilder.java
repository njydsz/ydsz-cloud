package com.njydsz.common.notify.core;

import java.util.List;
import java.util.concurrent.ExecutorService;

import com.njydsz.common.notify.aggregate.NotificationAggregator;
import com.njydsz.common.notify.audit.NotifyAuditService;
import com.njydsz.common.notify.channel.NotifyChannelStrategy;
import com.njydsz.common.notify.dedup.NotifyDedupService;
import com.njydsz.common.notify.fallback.NotifyFallbackManager;
import com.njydsz.common.notify.metrics.NotifyMetrics;
import com.njydsz.common.notify.preference.NotifyPreferenceManager;
import com.njydsz.common.notify.ratelimit.NotifyRateLimiterManager;

/**
 * {@link NotifyServiceImpl} 构建器
 *
 * <p>提供流式 API 构建 {@link NotifyServiceImpl}，避免直接使用 10 参数构造函数。 必填参数通过构造器传入，可选参数通过 fluent 方法设置。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * NotifyServiceImpl service = NotifyServiceImpl.builder(strategyList)
 *     .rateLimiterManager(rateLimiterManager)
 *     .parallelExecutor(executorService)
 *     .circuitBreakerRegistry(circuitBreakerRegistry)
 *     .fallbackManager(fallbackManager)
 *     .auditService(auditService)
 *     .metrics(metrics)
 *     .build();
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class NotifyServiceImplBuilder {

  /** 必填：渠道策略列表 */
  private final List<NotifyChannelStrategy> strategyList;

  /** 可选：限流管理器 */
  private NotifyRateLimiterManager rateLimiterManager;

  /** 可选：并行线程池 */
  private ExecutorService parallelExecutor;

  /** 可选：熔断器注册中心 */
  private NotifyCircuitBreakerRegistry circuitBreakerRegistry;

  /** 可选：降级管理器 */
  private NotifyFallbackManager fallbackManager;

  /** 可选：审计服务 */
  private NotifyAuditService auditService;

  /** 可选：指标服务 */
  private NotifyMetrics metrics;

  /** 可选：偏好管理器 */
  private NotifyPreferenceManager preferenceManager;

  /** 可选：去重服务 */
  private NotifyDedupService dedupService;

  /** 可选：消息聚合器 */
  private NotificationAggregator aggregator;

  /** 可选：回执追踪器 */
  private NotifyReceiptTracker receiptTracker;

  /**
   * 创建构建器（必填参数：渠道策略列表）
   *
   * @param strategyList 渠道策略列表
   */
  private NotifyServiceImplBuilder(List<NotifyChannelStrategy> strategyList) {
    this.strategyList = strategyList;
  }

  /**
   * 创建构建器实例（入口方法）
   *
   * @param strategyList 渠道策略列表
   * @return 构建器实例
   */
  public static NotifyServiceImplBuilder builder(List<NotifyChannelStrategy> strategyList) {
    return new NotifyServiceImplBuilder(strategyList);
  }

  /**
   * 设置限流管理器。
   *
   * @param rateLimiterManager 限流管理器
   * @return 当前 Builder
   */
  public NotifyServiceImplBuilder rateLimiterManager(NotifyRateLimiterManager rateLimiterManager) {
    this.rateLimiterManager = rateLimiterManager;
    return this;
  }

  /**
   * 设置并行线程池。
   *
   * @param parallelExecutor 并行线程池
   * @return 当前 Builder
   */
  public NotifyServiceImplBuilder parallelExecutor(ExecutorService parallelExecutor) {
    this.parallelExecutor = parallelExecutor;
    return this;
  }

  /**
   * 设置熔断器注册中心。
   *
   * @param circuitBreakerRegistry 熔断器注册中心
   * @return 当前 Builder
   */
  public NotifyServiceImplBuilder circuitBreakerRegistry(
      NotifyCircuitBreakerRegistry circuitBreakerRegistry) {
    this.circuitBreakerRegistry = circuitBreakerRegistry;
    return this;
  }

  /**
   * 设置降级管理器。
   *
   * @param fallbackManager 降级管理器
   * @return 当前 Builder
   */
  public NotifyServiceImplBuilder fallbackManager(NotifyFallbackManager fallbackManager) {
    this.fallbackManager = fallbackManager;
    return this;
  }

  /**
   * 设置审计服务。
   *
   * @param auditService 审计服务
   * @return 当前 Builder
   */
  public NotifyServiceImplBuilder auditService(NotifyAuditService auditService) {
    this.auditService = auditService;
    return this;
  }

  /**
   * 设置指标服务。
   *
   * @param metrics 指标服务
   * @return 当前 Builder
   */
  public NotifyServiceImplBuilder metrics(NotifyMetrics metrics) {
    this.metrics = metrics;
    return this;
  }

  /**
   * 设置偏好管理器。
   *
   * @param preferenceManager 偏好管理器
   * @return 当前 Builder
   */
  public NotifyServiceImplBuilder preferenceManager(NotifyPreferenceManager preferenceManager) {
    this.preferenceManager = preferenceManager;
    return this;
  }

  /**
   * 设置去重服务。
   *
   * @param dedupService 去重服务
   * @return 当前 Builder
   */
  public NotifyServiceImplBuilder dedupService(NotifyDedupService dedupService) {
    this.dedupService = dedupService;
    return this;
  }

  /**
   * 设置消息聚合器。
   *
   * @param aggregator 消息聚合器
   * @return 当前 Builder
   */
  public NotifyServiceImplBuilder aggregator(NotificationAggregator aggregator) {
    this.aggregator = aggregator;
    return this;
  }

  /**
   * 设置回执追踪器。
   *
   * @param receiptTracker 回执追踪器
   * @return 当前 Builder
   */
  public NotifyServiceImplBuilder receiptTracker(NotifyReceiptTracker receiptTracker) {
    this.receiptTracker = receiptTracker;
    return this;
  }

  /**
   * 构建 {@link NotifyServiceImpl} 实例
   *
   * @return 构建完成的通知服务实现
   */
  public NotifyServiceImpl build() {
    NotifyServiceImpl service =
        new NotifyServiceImpl(
            strategyList,
            rateLimiterManager,
            parallelExecutor,
            circuitBreakerRegistry,
            fallbackManager,
            auditService,
            metrics,
            preferenceManager,
            dedupService,
            aggregator);
    if (receiptTracker != null) {
      service.setReceiptTracker(receiptTracker);
    }
    return service;
  }
}
