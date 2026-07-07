package com.njydsz.pmis.agent.engine.llm;

import com.njydsz.pmis.agent.engine.AgentContext;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.Callable;
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
     * 同步执行 LLM 调用, 自动套上超时 + 重试 + TraceId
     */
    protected String executeWithGuard(Callable<String> call, AgentContext context) {
        // 1. 透传 TraceId
        String traceId = context != null && context.getTraceId() != null
                ? context.getTraceId() : "agent-" + System.currentTimeMillis();
        String previousTraceId = MDC.get("traceId");
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
                // 指数退避: 200ms, 600ms, 1800ms
                try {
                    Thread.sleep(200L * (long) Math.pow(3, attempt));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            log.error("[LLM:{}] all {} attempts failed", name(), maxRetries + 1, lastEx);
            if (fallbackToMockOnError) {
                return new MockLlmProvider().chat("", "", context);
            }
            throw new RuntimeException("LLM " + name() + " failed after " + (maxRetries + 1) + " attempts", lastEx);
        } finally {
            // 恢复 MDC
            if (previousTraceId != null) MDC.put("traceId", previousTraceId);
            else MDC.remove("traceId");
            MDC.remove("provider");
            MDC.remove("providerTraceId");
        }
    }

    /**
     * 在独立线程执行调用并设置超时。
     *
     * @param call      可调用任务
     * @param timeoutMs 超时毫秒数
     * @return 调用结果
     * @throws Exception 调用异常或超时
     */
    private String invokeWithTimeout(Callable<String> call, long timeoutMs) throws Exception {
        long start = System.currentTimeMillis();
        // 把当前线程的 MDC 复制到子线程, 避免跨线程上下文丢失
        final Map<String, String> mdcSnapshot = MDC.getCopyOfContextMap();
        java.util.concurrent.ExecutorService exec = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "llm-" + name());
            t.setDaemon(true);
            return t;
        });
        try {
            java.util.concurrent.Future<String> future = exec.submit(() -> {
                // 子线程恢复 MDC
                if (mdcSnapshot != null) MDC.setContextMap(mdcSnapshot);
                try {
                    return call.call();
                } finally {
                    MDC.clear();
                }
            });
            String result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            log.debug("[LLM:{}] success in {}ms", name(), System.currentTimeMillis() - start);
            return result;
        } catch (java.util.concurrent.ExecutionException ee) {
            // 包装原始异常便于排查
            Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
            if (cause instanceof Exception ex) throw ex;
            throw new RuntimeException(cause);
        } finally {
            exec.shutdownNow();
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
