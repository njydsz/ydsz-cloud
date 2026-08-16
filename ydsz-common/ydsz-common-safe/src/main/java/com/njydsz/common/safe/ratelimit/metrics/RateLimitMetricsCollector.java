package com.njydsz.common.safe.ratelimit.metrics;

import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.safe.ratelimit.core.RateLimitManager;
import com.njydsz.common.safe.ratelimit.enums.RateLimitResult;
import com.njydsz.common.safe.ratelimit.model.RateLimitDecision;

/**
 * 限流指标收集器
 *
 * <p>基于 Micrometer 暴露限流相关指标：
 *
 * <ul>
 *   <li>{@code ydsz_ratelimit_decisions_total{resource, result}} - 限流决策总数
 *   <li>{@code ydsz_ratelimit_block_total{resource, algorithm}} - 限流阻塞数
 *   <li>{@code ydsz_ratelimit_pass_total{resource, algorithm}} - 限流通过数
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class RateLimitMetricsCollector {

  private final MeterRegistry meterRegistry;
  private final AtomicLong totalDecisions = new AtomicLong(0);
  private final AtomicLong totalBlocked = new AtomicLong(0);
  private final AtomicLong totalPassed = new AtomicLong(0);

  public RateLimitMetricsCollector(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    registerGauges();
  }

  private void registerGauges() {
    if (meterRegistry == null) return;
    meterRegistry.gauge("ydsz_ratelimit_decisions_total", totalDecisions);
    meterRegistry.gauge("ydsz_ratelimit_block_total", totalBlocked);
    meterRegistry.gauge("ydsz_ratelimit_pass_total", totalPassed);
  }

  /** 记录一次决策 */
  public void record(RateLimitDecision decision) {
    if (decision == null || meterRegistry == null) return;
    totalDecisions.incrementAndGet();
    Tags tags =
        Tags.of(
            "resource", decision.getResource() == null ? "unknown" : decision.getResource(),
            "algorithm",
                decision.getRule() == null || decision.getRule().getAlgorithm() == null
                    ? "unknown"
                    : decision.getRule().getAlgorithm().name(),
            "mode",
                decision.getRule() == null || decision.getRule().getMode() == null
                    ? "unknown"
                    : decision.getRule().getMode().name());
    Counter.builder("ydsz_ratelimit_decisions")
        .tags(tags)
        .description("Rate limit decisions")
        .register(meterRegistry)
        .increment();
    if (decision.getResult() == RateLimitResult.BLOCKED) {
      totalBlocked.incrementAndGet();
      Counter.builder("ydsz_ratelimit_blocked")
          .tags(tags)
          .description("Rate limit blocked count")
          .register(meterRegistry)
          .increment();
    } else {
      totalPassed.incrementAndGet();
      Counter.builder("ydsz_ratelimit_passed")
          .tags(tags)
          .description("Rate limit passed count")
          .register(meterRegistry)
          .increment();
    }
  }

  /** 创建 Micrometer 监听器 */
  public RateLimitManager.DecisionListener asListener() {
    return this::record;
  }
}
