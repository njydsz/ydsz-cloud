package com.njydsz.pmis.literule.server.core;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;

import lombok.extern.slf4j.Slf4j;

/**
 * 规则超时执行器
 *
 * <p>用 {@link CompletableFuture} 包裹同步规则评估，超时则取消任务并返回未触发结果。
 *
 * @since 1.4.0
 */
@Slf4j
public class RuleTimeoutExecutor {

    /** 默认单规则超时（毫秒） */
    private final long defaultTimeoutMs;

    /** 独立的执行器线程池（避免拖垮主线程池） */
    private final ExecutorService executor;

    /**
     * 构造超时执行器
     *
     * @param defaultTimeoutMs 默认超时（毫秒）；0 表示不限制
     * @param threadPoolSize   线程池大小
     */
    public RuleTimeoutExecutor(long defaultTimeoutMs, int threadPoolSize) {
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.executor = Executors.newFixedThreadPool(Math.max(2, threadPoolSize), r -> {
            Thread t = new Thread(r, "literule-timeout-exec");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 在超时控制下执行规则评估
     *
     * @param rule       规则
     * @param context    上下文
     * @param timeoutMs  本次评估超时（毫秒）；0 表示使用默认值；负数表示不限制
     * @return 评估结果；超时返回未触发结果（含 timeout 标记）
     */
    public RuleResult evaluateWithTimeout(Rule rule, RuleContext context, long timeoutMs) {
        long effectiveTimeout = timeoutMs > 0 ? timeoutMs
                : timeoutMs == 0 ? defaultTimeoutMs : 0;

        if (effectiveTimeout <= 0) {
            // 无超时限制：直接同步评估
            return rule.evaluate(context);
        }

        CompletableFuture<RuleResult> future = CompletableFuture.supplyAsync(
                () -> rule.evaluate(context), executor);

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
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("规则评估异常: " + rule.getCode(), cause);
        }
    }

    /**
     * 关闭线程池
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        log.info("[LiteRule-Timeout] 超时执行器已关闭");
    }
}
