package com.njydsz.literule.server.core;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.domain.Rule;
import com.njydsz.literule.domain.vo.RuleContext;
import com.njydsz.literule.domain.vo.RuleResult;

/**
 * 规则超时执行器
 *
 * <p>用 {@link CompletableFuture} 包裹同步规则评估，超时则取消任务并返回未触发结果。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class RuleTimeoutExecutor {

  /** 默认单规则超时（毫秒） */
  private final long defaultTimeoutMs;

  /** 独立的执行器线程池（由 common-thread 管理，本实例不负责关闭） */
  private final Executor executor;

  /**
   * 构造超时执行器（强制使用外部线程池）
   *
   * @param defaultTimeoutMs 默认超时（毫秒）；0 表示不限制
   * @param executor 外部线程池（由 common-thread 管理，调用方不负责关闭）
   */
  public RuleTimeoutExecutor(long defaultTimeoutMs, Executor executor) {
    this.defaultTimeoutMs = defaultTimeoutMs;
    this.executor = Objects.requireNonNull(executor, "executor 不能为 null");
  }

  /**
   * 在超时控制下执行规则评估
   *
   * @param rule 规则
   * @param context 上下文
   * @param timeoutMs 本次评估超时（毫秒）；0 表示使用默认值；负数表示不限制
   * @return 评估结果；超时返回未触发结果（含 timeout 标记）
   */
  public RuleResult evaluateWithTimeout(Rule rule, RuleContext context, long timeoutMs) {
    long effectiveTimeout = timeoutMs > 0 ? timeoutMs : timeoutMs == 0 ? defaultTimeoutMs : 0;

    if (effectiveTimeout <= 0) {
      // 无超时限制：直接同步评估
      return rule.evaluate(context);
    }

    CompletableFuture<RuleResult> future =
        CompletableFuture.supplyAsync(() -> rule.evaluate(context), executor);

    try {
      return future.get(effectiveTimeout, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      log.warn("[LiteRule-Timeout] 规则 {} 评估超时（{}ms）", rule.getCode(), effectiveTimeout);
      return RuleResult.builder()
          .ruleCode(rule.getCode())
          .ruleName(rule.getName())
          .category(rule.getCategory())
          .triggered(false)
          .description("评估超时（" + effectiveTimeout + "ms）")
          .triggeredAt(LocalDateTime.now())
          .build();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("[LiteRule-Timeout] 规则 {} 评估被中断", rule.getCode());
      return RuleResult.builder()
          .ruleCode(rule.getCode())
          .triggered(false)
          .description("评估被中断")
          .triggeredAt(LocalDateTime.now())
          .build();
    } catch (ExecutionException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      if (cause instanceof RuntimeException re) {
        throw re;
      }
      throw RuleEvaluationException.timeout(rule.getCode(), effectiveTimeout);
    }
  }

  /** 关闭线程池（P0-3: 外部线程池由 common-thread 管理生命周期，此方法为 no-op） */
  public void shutdown() {
    // P0-3: ownsExecutor 始终为 false，无需手动关闭
    log.info("[LiteRule-Timeout] 超时执行器已关闭（委托 common-thread）");
  }
}
