package com.njydsz.common.notify.core;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.notify.audit.NotifyAuditService;
import com.njydsz.common.notify.channel.NotifyChannelStrategy;
import com.njydsz.common.notify.dedup.NotifyDedupService;
import com.njydsz.common.notify.enums.NotifyChannel;
import com.njydsz.common.notify.fallback.NotifyFallbackManager;
import com.njydsz.common.notify.metrics.NotifyMetrics;
import com.njydsz.common.notify.preference.NotifyPreferenceManager;
import com.njydsz.common.notify.ratelimit.NotifyRateLimiterManager;

/**
 * 通知发送处理链
 *
 * <p>封装单条通知发送的完整处理链路：去重 → 熔断 → 限流 → 执行 → 指标 → 审计 → 降级。 与直接在 {@link NotifyServiceImpl}
 * 中串联调用相比，本类将链路逻辑集中管理， 便于单元测试和步骤调整。
 *
 * <p>使用 {@link SendContext} 在步骤间传递状态，每个步骤可通过 {@link SendContext#hasResult()} 判断是否已短路返回。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class SendChain {

  private static final Logger LOG = LoggerFactory.getLogger(SendChain.class);

  private final Map<NotifyChannel, NotifyChannelStrategy> channelStrategies;
  private final NotifyRateLimiterManager rateLimiterManager;
  private final NotifyCircuitBreakerRegistry circuitBreakerRegistry;
  private final NotifyDedupService dedupService;
  private final NotifyPreferenceManager preferenceManager;
  private final NotifyMetrics metrics;
  private final NotifyAuditService auditService;
  private final NotifyFallbackManager fallbackManager;

  /**
   * 构造发送处理链
   *
   * @param channelStrategies 渠道策略映射
   * @param rateLimiterManager 限流管理器（可为 null）
   * @param circuitBreakerRegistry 熔断器注册中心（可为 null）
   * @param dedupService 去重服务（可为 null）
   * @param preferenceManager 偏好管理器（可为 null）
   * @param metrics 指标采集器（可为 null）
   * @param auditService 审计服务（可为 null）
   * @param fallbackManager 降级管理器（可为 null）
   */
  public SendChain(
      Map<NotifyChannel, NotifyChannelStrategy> channelStrategies,
      NotifyRateLimiterManager rateLimiterManager,
      NotifyCircuitBreakerRegistry circuitBreakerRegistry,
      NotifyDedupService dedupService,
      NotifyPreferenceManager preferenceManager,
      NotifyMetrics metrics,
      NotifyAuditService auditService,
      NotifyFallbackManager fallbackManager) {
    this.channelStrategies = channelStrategies;
    this.rateLimiterManager = rateLimiterManager;
    this.circuitBreakerRegistry = circuitBreakerRegistry;
    this.dedupService = dedupService;
    this.preferenceManager = preferenceManager;
    this.metrics = metrics;
    this.auditService = auditService;
    this.fallbackManager = fallbackManager;
  }

  /**
   * 执行文本发送链路
   *
   * @param ctx 发送上下文
   * @return 发送结果
   */
  public NotifySendResult executeSend(SendContext ctx) {
    SendContext context = ctx;

    // Step 1: 去重检查
    context = applyDedup(context);
    if (context.hasResult()) {
      return context.sendResult();
    }

    // Step 2: 熔断检查
    context = applyCircuitBreaker(context);
    if (context.hasResult()) {
      return context.sendResult();
    }

    // Step 3: 限流检查
    context = applyRateLimit(context);
    if (context.hasResult()) {
      return context.sendResult();
    }

    // Step 4: 执行发送
    context = executeTextSend(context);

    // Step 5: 后置处理（指标、审计、降级）
    context = applyPostProcess(context);

    return context.sendResult();
  }

  /**
   * 执行模板发送链路
   *
   * @param ctx 发送上下文
   * @return 发送结果
   */
  public NotifySendResult executeTemplate(SendContext ctx) {
    SendContext context = ctx;

    // Step 1: 熔断检查
    context = applyCircuitBreaker(context);
    if (context.hasResult()) {
      return context.sendResult();
    }

    // Step 2: 限流检查
    context = applyRateLimit(context);
    if (context.hasResult()) {
      return context.sendResult();
    }

    // Step 3: 执行模板发送
    context = executeTemplateSend(context);

    // Step 4: 后置处理
    context = applyPostProcess(context);

    return context.sendResult();
  }

  // ==================== 链路步骤 ====================

  /** 去重检查步骤 */
  private SendContext applyDedup(SendContext ctx) {
    if (dedupService == null) {
      return ctx;
    }
    if (dedupService.isDuplicate(ctx.receiver(), ctx.title(), ctx.content())) {
      LOG.debug("[SendChain] 去重命中，跳过发送: receiver={}, title={}", ctx.receiver(), ctx.title());
      return ctx.withResult(NotifySendResult.success("dedup-skipped", ctx.channel().getName()));
    }
    return ctx;
  }

  /** 熔断检查步骤 */
  private SendContext applyCircuitBreaker(SendContext ctx) {
    if (circuitBreakerRegistry == null || circuitBreakerRegistry.tryAcquire(ctx.channel())) {
      return ctx;
    }
    return ctx.withResult(
        NotifySendResult.failure(
            "通知渠道[" + ctx.channel().getName() + "]已熔断，请稍后重试", ctx.channel().getName()));
  }

  /** 限流检查步骤 */
  private SendContext applyRateLimit(SendContext ctx) {
    if (rateLimiterManager == null
        || rateLimiterManager.tryAcquire(ctx.channel(), ctx.tenantId())) {
      return ctx;
    }
    return ctx.withResult(
        NotifySendResult.failure(
            "通知渠道限流触发，请稍后重试: " + ctx.channel().getName(), ctx.channel().getName()));
  }

  /** 执行文本发送步骤 */
  private SendContext executeTextSend(SendContext ctx) {
    NotifyChannelStrategy strategy = channelStrategies.get(ctx.channel());
    if (strategy == null) {
      recordCircuitFailure(ctx.channel());
      return ctx.withResult(
          NotifySendResult.failure("通知渠道未配置: " + ctx.channel().getName(), ctx.channel().getName()));
    }

    long startTime = System.nanoTime();
    NotifySendResult result;
    try {
      result = strategy.send(ctx.receiver(), ctx.title(), ctx.content());
    } catch (Exception e) {
      result = NotifySendResult.failure(e.getMessage(), ctx.channel().getName());
    }
    long durationNanos = System.nanoTime() - startTime;

    return ctx.withResult(result).withTiming(startTime, durationNanos);
  }

  /** 执行模板发送步骤 */
  private SendContext executeTemplateSend(SendContext ctx) {
    NotifyChannelStrategy strategy = channelStrategies.get(ctx.channel());
    if (strategy == null) {
      recordCircuitFailure(ctx.channel());
      return ctx.withResult(
          NotifySendResult.failure("通知渠道未配置: " + ctx.channel().getName(), ctx.channel().getName()));
    }

    long startTime = System.nanoTime();
    NotifySendResult result;
    try {
      result = strategy.sendTemplate(ctx.receiver(), ctx.templateCode(), ctx.templateParams());
    } catch (Exception e) {
      result = NotifySendResult.failure(e.getMessage(), ctx.channel().getName());
    }
    long durationNanos = System.nanoTime() - startTime;

    return ctx.withResult(result).withTiming(startTime, durationNanos);
  }

  /** 后置处理步骤：熔断记录、指标、审计、降级 */
  private SendContext applyPostProcess(SendContext ctx) {
    if (ctx.sendResult() == null) {
      return ctx;
    }

    // 记录熔断结果
    recordCircuitResult(ctx.channel(), ctx.sendResult().isSuccess());

    // 指标采集
    if (metrics != null) {
      metrics.recordChannelSend(
          ctx.channel().getName(), ctx.sendResult().isSuccess(), ctx.templateCode());
      if (!ctx.sendResult().isSuccess()) {
        metrics.recordEmailFailure(ctx.channel().getName(), "send_error", "send_failure");
      }
    }

    // 审计日志
    if (auditService != null) {
      auditService.audit(
          ctx.channel(),
          ctx.receiver(),
          ctx.title(),
          ctx.sendResult(),
          ctx.durationNanos() / 1_000_000,
          ctx.templateCode());
    }

    // 失败降级
    if (!ctx.sendResult().isSuccess() && fallbackManager != null) {
      LOG.info("[SendChain] 主渠道[{}]发送失败，尝试降级", ctx.channel().getName());
      String fallbackTitle = ctx.title() != null ? ctx.title() : ctx.templateCode();
      String fallbackContent =
          ctx.content() != null
              ? ctx.content()
              : (ctx.templateParams() != null ? String.valueOf(ctx.templateParams()) : "");
      NotifySendResult fallbackResult =
          fallbackManager.fallbackSend(
              ctx.channel(), ctx.receiver(), fallbackTitle, fallbackContent);
      return ctx.withResult(fallbackResult);
    }

    return ctx;
  }

  // ==================== 辅助方法 ====================

  private void recordCircuitResult(NotifyChannel channel, boolean success) {
    if (circuitBreakerRegistry == null) {
      return;
    }
    if (success) {
      circuitBreakerRegistry.recordSuccess(channel);
    } else {
      circuitBreakerRegistry.recordFailure(channel);
    }
  }

  private void recordCircuitFailure(NotifyChannel channel) {
    recordCircuitResult(channel, false);
  }
}
