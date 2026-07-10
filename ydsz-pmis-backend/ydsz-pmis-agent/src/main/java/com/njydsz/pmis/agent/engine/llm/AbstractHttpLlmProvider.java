package com.njydsz.pmis.agent.engine.llm;

import com.njydsz.pmis.agent.engine.AgentContext;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * LLM 调用守卫（批次 22 P1-4 落地）
 *
 * <p>对所有真实 LLM Provider 提供统一的：
 * <ul>
 *   <li>超时控制（默认 10s, 可通过 {@link #timeoutMillis} 覆盖）</li>
 *   <li>重试机制（指数退避, 默认 2 次）</li>
 *   <li>TraceId 透传（通过 SLF4J MDC, 便于 SkyWalking / Sentry 追踪）</li>
 *   <li>异常降级（失败时返回 mock 兜底）</li>
 * </ul>
 *
 * <p>注: 当前使用简易超时 + 重试实现, 生产环境可升级为 Sentinel 熔断 / Resilience4j.
 *      见 [docs/chaos-engineering.md] § 4.3 验证 FallbackFactory.
 *
 * <p><b>P0-4 修复</b>：原 {@code invokeWithTimeout} 每次调用都
 * {@code Executors.newSingleThreadExecutor()} 并在 finally 中 {@code shutdownNow()}，
 * 高并发下线程创建开销巨大且 {@code shutdownNow()} 不保证任务终止导致线程泄漏。
 * 现改为共享 {@link ExecutorService}（{@link Executors#newCachedThreadPool}），
 * 构造时创建，{@link #destroy()} 时优雅关闭，超时后显式 {@link Future#cancel(boolean)}。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (批次22)
 */
@Slf4j
public abstract class AbstractHttpLlmProvider implements LlmProvider {

    /** 默认超时时间: 10s */
    protected long timeoutMillis = 10_000L;

    /** 最大重试次数（不含首次） */
    protected int maxRetries = 2;

    /** 是否在失败时降级到 mock (false 时会抛错给上层) */
    protected boolean fallbackToMockOnError = true;

    /**
     * 重试退避基础间隔（毫秒，P2-8）。
     *
     * <p>实际退避 = min({@link #backoffMaxMillis},
     * {@code baseBackoffMillis * 3^attempt} + 随机抖动)。
     */
    protected long baseBackoffMillis = 200L;

    /**
     * 重试退避上限（毫秒，P2-8）。
     *
     * <p>无论重试到第几次，退避时间都不会超过此上限，防止指数爆炸。
     * 默认 5000ms（5 秒）。
     */
    protected long backoffMaxMillis = 5_000L;

    /**
     * 共享线程池（P0-4 修复）
     *
     * <p>使用 {@link Executors#newCachedThreadPool()}：
     * <ul>
     *   <li>线程按需创建，空闲 60s 自动回收，避免无界增长</li>
     *   <li>同一 Provider 实例的所有调用复用同一线程池，消除频繁创建/销毁开销</li>
     *   <li>守护线程，JVM 退出时不阻塞</li>
     * </ul>
     */
    private final ExecutorService sharedExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "llm-" + name() + "-worker");
        t.setDaemon(true);
        return t;
    });

    /**
     * 销毁时关闭共享线程池（P0-4 修复）
     *
     * <p>由 Spring 容器在 Bean 销毁时调用（{@link PreDestroy}）。
     * 测试中也可手动调用以验证线程池关闭行为。
     */
    @PreDestroy
    public void destroy() {
        if (sharedExecutor.isShutdown()) {
            return;
        }
        sharedExecutor.shutdown();
        try {
            if (!sharedExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                sharedExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sharedExecutor.shutdownNow();
        }
        log.info("[LLM:{}] 共享线程池已关闭", name());
    }

    /**
     * 同步执行 LLM 调用, 自动套上超时 + 重试 + TraceId
     *
     * <p><b>P2-1 修复</b>：MDC 恢复逻辑改为保存/恢复全部三个 key
     * （traceId / provider / providerTraceId）的旧值，避免嵌套调用时
     * 内层清除 外层已设置的 provider MDC 上下文。
     *
     * <p><b>P2-8 修复</b>：重试退避改为带上限的指数退避 + 随机抖动（jitter），
     * 避免高 maxRetries 配置下退避时间指数爆炸，以及多实例同步重试导致惊群。
     * 最后一次重试失败后不再 sleep。
     */
    protected String executeWithGuard(Callable<String> call, AgentContext context) {
        // 1. 透传 TraceId（P2-1：保存全部三个 key 的旧值，支持嵌套调用）
        String traceId = context != null && context.getTraceId() != null
                ? context.getTraceId() : "agent-" + System.currentTimeMillis();
        String previousTraceId = MDC.get("traceId");
        String previousProvider = MDC.get("provider");
        String previousProviderTraceId = MDC.get("providerTraceId");
        MDC.put("traceId", traceId);
        MDC.put("provider", name());
        MDC.put("providerTraceId", context != null ? safeStr(context.getProviderTraceId()) : "");

        try {
            Exception lastEx = null;
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    return invokeWithTimeout(call, timeoutMillis);
                } catch (TimeoutException te) {
                    lastEx = te;
                    log.warn("[LLM:{}] attempt {}/{} timeout after {}ms", name(), attempt + 1, maxRetries + 1, timeoutMillis);
                } catch (Exception e) {
                    lastEx = e;
                    log.warn("[LLM:{}] attempt {}/{} error: {}", name(), attempt + 1, maxRetries + 1, e.getMessage());
                }
                // P2-8：最后一次重试失败后不再 sleep，直接走降级/抛错
                if (attempt >= maxRetries) {
                    break;
                }
                // P2-8：带上限的指数退避 + 随机抖动（jitter），防止指数爆炸与惊群
                sleepWithCappedBackoff(attempt);
            }
            log.error("[LLM:{}] all {} attempts failed", name(), maxRetries + 1, lastEx);
            if (fallbackToMockOnError) {
                return new MockLlmProvider().chat("", "", context);
            }
            throw new RuntimeException("LLM " + name() + " failed after " + (maxRetries + 1) + " attempts", lastEx);
        } finally {
            // P2-1：恢复全部三个 key 的旧值（嵌套调用安全）
            restoreMdc("traceId", previousTraceId);
            restoreMdc("provider", previousProvider);
            restoreMdc("providerTraceId", previousProviderTraceId);
        }
    }

    /**
     * 带上限的指数退避 + 随机抖动（P2-8）。
     *
     * <p>退避时间 = min({@link #backoffMaxMillis},
     * {@code baseBackoffMillis * 3^attempt}) + jitter(0~baseBackoffMillis)。
     *
     * <p>设计要点：
     * <ul>
     *   <li>指数增长但有上限，防止高 maxRetries 配置下退避时间爆炸</li>
     *   <li>随机抖动（jitter）分散重试时间，避免多实例同步重试导致惊群效应</li>
     *   <li>响应中断，保持线程中断语义</li>
     * </ul>
     *
     * @param attempt 当前重试轮次（0 表示第一次重试前的失败）
     */
    private void sleepWithCappedBackoff(int attempt) {
        long exponential = (long) (baseBackoffMillis * Math.pow(3, attempt));
        long capped = Math.min(exponential, backoffMaxMillis);
        // jitter：[0, baseBackoffMillis) 随机抖动，但不超过上限
        long jitter = (long) (Math.random() * baseBackoffMillis);
        long sleepMs = Math.min(capped + jitter, backoffMaxMillis);
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 安全恢复 MDC 上下文（P2-1）。
     *
     * @param key      MDC key
     * @param previous 旧值；为 null 时移除该 key，否则恢复为旧值
     */
    private static void restoreMdc(String key, String previous) {
        if (previous != null) {
            MDC.put(key, previous);
        } else {
            MDC.remove(key);
        }
    }

    /**
     * 在共享线程池中执行调用并设置超时（P0-4 修复版）。
     *
     * <p>与原实现的差异：
     * <ul>
     *   <li>使用 {@link #sharedExecutor} 而非每次创建单线程池</li>
     *   <li>超时/异常后显式 {@link Future#cancel(boolean)} 中断子线程，避免任务继续运行占用资源</li>
     *   <li>不再在 finally 中 {@code shutdownNow()} 共享线程池</li>
     * </ul>
     *
     * @param call      可调用任务
     * @param timeoutMs 超时毫秒数
     * @return 调用结果
     * @throws Exception            调用异常
     * @throws TimeoutException     超时
     * @throws RejectedExecutionException 线程池已关闭时抛出
     */
    private String invokeWithTimeout(Callable<String> call, long timeoutMs) throws Exception {
        long start = System.currentTimeMillis();
        // 把当前线程的 MDC 复制到子线程, 避免跨线程上下文丢失
        final Map<String, String> mdcSnapshot = MDC.getCopyOfContextMap();
        Future<String> future = sharedExecutor.submit(() -> {
            // 子线程恢复 MDC
            if (mdcSnapshot != null) MDC.setContextMap(mdcSnapshot);
            try {
                return call.call();
            } finally {
                MDC.clear();
            }
        });
        try {
            String result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            log.debug("[LLM:{}] success in {}ms", name(), System.currentTimeMillis() - start);
            return result;
        } catch (ExecutionException ee) {
            // 包装原始异常便于排查
            Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
            if (cause instanceof Exception ex) throw ex;
            throw new RuntimeException(cause);
        } finally {
            // P0-4 修复：超时或异常后显式取消任务，避免子线程继续运行
            // 对已完成的 Future 调用 cancel 是 no-op，无副作用
            future.cancel(true);
        }
    }

    /**
     * 将对象安全转为字符串。
     *
     * @param o 输入对象，可空
     * @return 字符串表示；为空返回空字符串
     */
    private static String safeStr(Object o) {
        return o == null ? "" : o.toString();
    }
}
