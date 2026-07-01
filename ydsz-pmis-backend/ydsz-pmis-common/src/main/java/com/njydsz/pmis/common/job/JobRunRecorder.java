package com.njydsz.pmis.common.job;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Job 运行记录器（批次 21 / P2）
 *
 * <p>统一封装 Job 执行过程: 自动注入 provider_trace_id、记录开始/结束/异常、
 * 输出统一格式日志, 便于运维追踪 + 告警系统按 trace 聚合。</p>
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li>MDC 注入: 当前线程的 provider_trace_id 可被日志框架 (logback) 自动采集</li>
 *   <li>结果统一: 任意业务返回值自动包装为 {@link JobRunResult}</li>
 *   <li>失败兜底: 异常被记录但**不吞掉**, 由调度器决定重试策略</li>
 *   <li>零依赖: 只用 SLF4J, 不依赖 Spring / MyBatis / 数据库</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Component
 * public class MyJobHandler implements JobHandler {
 *     @Override
 *     public Object execute(String paramsJson) throws Exception {
 *         return JobRunRecorder.run("myJob", paramsJson, () -> {
 *             // 业务逻辑
 *             return Map.of("processed", 100);
 *         });
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public final class JobRunRecorder {

    public static final String MDC_TRACE_ID = "provider_trace_id";
    public static final String MDC_JOB_KEY  = "job_key";

    private JobRunRecorder() { /* 工具类, 不允许实例化 */ }

    /**
     * 同步执行 + 记录 (Supplier 版本, 不抛异常)
     *
     * @param jobKey     任务 key, 用于日志聚合
     * @param paramsJson 任务入参
     * @param biz        业务逻辑
     * @param <T>        业务返回值类型
     * @return {@link JobRunResult} 包装
     */
    public static <T> JobRunResult<T> run(String jobKey, String paramsJson, Supplier<T> biz) {
        try {
            return run(jobKey, paramsJson, () -> {
                T data = biz.get();
                return new JobRunResult<>(data, null);
            });
        } catch (Exception e) {
            // Supplier 不声明抛出异常，此处理论上不会走到，兜底转为运行时异常
            throw new RuntimeException(e);
        }
    }

    /**
     * 同步执行 + 记录 (Callable 版本, 可抛异常)
     *
     * @param jobKey     任务 key
     * @param paramsJson 任务入参
     * @param biz        业务逻辑
     * @param <T>        业务返回值类型
     * @return {@link JobRunResult} 包装
     * @throws Exception 业务异常透传
     */
    public static <T> JobRunResult<T> run(String jobKey, String paramsJson, Callable<JobRunResult<T>> biz)
            throws Exception {
        String traceId = ensureTraceId();
        long startMs = System.currentTimeMillis();

        // 注入 MDC (供日志框架自动采集)
        boolean traceMdcSet = tryPutMdc(MDC_TRACE_ID, traceId);
        boolean jobMdcSet   = tryPutMdc(MDC_JOB_KEY, jobKey);

        log.info("[JobRun] START job={} traceId={} params={}", jobKey, traceId, abbreviate(paramsJson));

        JobRunResult<T> result;
        try {
            result = biz.call();
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - startMs;
            log.error("[JobRun] FAIL job={} traceId={} costMs={} err={}: {}",
                    jobKey, traceId, cost, e.getClass().getSimpleName(), e.getMessage(), e);
            result = JobRunResult.failure(e, cost);
        } finally {
            // 清理 MDC
            if (traceMdcSet) MDC.remove(MDC_TRACE_ID);
            if (jobMdcSet)   MDC.remove(MDC_JOB_KEY);
        }

        if (result.isSuccess()) {
            log.info("[JobRun] SUCCESS job={} traceId={} costMs={} data={}",
                    jobKey, traceId, result.getCostMs(), abbreviate(String.valueOf(result.getData())));
        }
        return result;
    }

    /**
     * 生成或复用 provider_trace_id
     * <p>优先从 MDC 复用 (调度器已注入), 否则生成新的</p>
     */
    public static String ensureTraceId() {
        String existing = MDC.get(MDC_TRACE_ID);
        if (existing != null && !existing.isEmpty()) {
            return existing;
        }
        return "JOB-" + System.currentTimeMillis() + "-" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private static boolean tryPutMdc(String key, String val) {
        try {
            MDC.put(key, val);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String abbreviate(String s) {
        if (s == null) return "null";
        if (s.length() <= 200) return s;
        return s.substring(0, 200) + "...(truncated " + (s.length() - 200) + " chars)";
    }

    /**
     * Job 执行结果包装
     */
    public static class JobRunResult<T> {
        private final T data;
        private final Exception error;
        private final long costMs;
        private final String traceId;

        public JobRunResult(T data, Exception error) {
            this(data, error, 0, ensureTraceId());
        }

        public JobRunResult(T data, Exception error, long costMs, String traceId) {
            this.data = data;
            this.error = error;
            this.costMs = costMs;
            this.traceId = traceId;
        }

        public static <T> JobRunResult<T> failure(Exception e, long costMs) {
            return new JobRunResult<>(null, e, costMs, ensureTraceId());
        }

        public static <T> JobRunResult<T> success(T data, long costMs) {
            return new JobRunResult<>(data, null, costMs, ensureTraceId());
        }

        public boolean isSuccess() { return error == null; }
        public T getData() { return data; }
        public Exception getError() { return error; }
        public long getCostMs() { return costMs; }
        public String getTraceId() { return traceId; }

        /** 转 Map (供 JobHandler 返回给调度器序列化) */
        public Map<String, Object> toMap() {
            Map<String, Object> m = new HashMap<>();
            m.put("traceId", traceId);
            m.put("costMs", costMs);
            m.put("success", isSuccess());
            if (data != null) m.put("data", data);
            if (error != null) m.put("error", error.getMessage());
            return m;
        }
    }
}
