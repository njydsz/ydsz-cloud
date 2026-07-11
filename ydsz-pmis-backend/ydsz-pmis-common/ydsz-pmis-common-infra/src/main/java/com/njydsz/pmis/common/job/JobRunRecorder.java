package com.njydsz.pmis.common.job;

import com.njydsz.pmis.common.util.TraceIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Job 运行记录器（批次 21 / P2）
 *
 * <p>统一封装 Job 执行过程: 自动注入 traceId、记录开始/结束/异常、
 * 输出统一格式日志, 便于运维追踪 + 告警系统按 trace 聚合。</p>
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li>MDC 注入: 当前线程的 traceId 可被日志框架 (logback) 自动采集, 与请求链路统一</li>
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

    /** MDC 中 traceId 的键名（与全局 TraceIdFilter 统一），供日志框架 %X{traceId:-} 自动采集 */
    public static final String MDC_TRACE_ID = "traceId";
    /** MDC 中 job_key 的键名，用于按任务聚合日志 */
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
     * 生成或复用 traceId
     *
     * <p>P2-13 统一链路追踪：原生成 {@code "JOB-{ts}-{snowflake8}"} 格式与 Brave 16/32 位 hex
     * 格式不一致，无法与 Zipkin/SkyWalking 链路关联。现统一委托 {@link TraceIdUtil#ensureTraceId()}，
     * 优先复用 MDC 中 Brave 写入的 traceId，降级时生成 hex 格式雪花 ID。
     *
     * @return 当前线程有效的 traceId
     */
    public static String ensureTraceId() {
        return TraceIdUtil.ensureTraceId();
    }

    private static boolean tryPutMdc(String key, String val) {
        try {
            MDC.put(key, val);
            return true;
        } catch (Throwable t) {
            log.warn("[JobRunRecorder] MDC.put 失败 key={} val={}: {}", key, val, t.getMessage(), t);
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
     *
     * @param <T> 业务返回值类型
     */
    public static class JobRunResult<T> {
        /** 业务返回数据（失败时为 null） */
        private final T data;
        /** 业务异常（成功时为 null） */
        private final Exception error;
        /** 执行耗时（毫秒） */
        private final long costMs;
        /** 关联的 provider_trace_id */
        private final String traceId;

        /**
         * @param data  业务返回数据
         * @param error 业务异常（成功时为 null）
         */
        public JobRunResult(T data, Exception error) {
            this(data, error, 0, ensureTraceId());
        }

        /**
         * @param data    业务返回数据
         * @param error   业务异常（成功时为 null）
         * @param costMs  执行耗时（毫秒）
         * @param traceId 关联的 provider_trace_id
         */
        public JobRunResult(T data, Exception error, long costMs, String traceId) {
            this.data = data;
            this.error = error;
            this.costMs = costMs;
            this.traceId = traceId;
        }

        /**
         * 构造失败结果
         *
         * @param e      业务异常
         * @param costMs 执行耗时（毫秒）
         * @param <T>    业务返回值类型
         * @return 失败结果包装
         */
        public static <T> JobRunResult<T> failure(Exception e, long costMs) {
            return new JobRunResult<>(null, e, costMs, ensureTraceId());
        }

        /**
         * 构造成功结果
         *
         * @param data   业务返回数据
         * @param costMs 执行耗时（毫秒）
         * @param <T>    业务返回值类型
         * @return 成功结果包装
         */
        public static <T> JobRunResult<T> success(T data, long costMs) {
            return new JobRunResult<>(data, null, costMs, ensureTraceId());
        }

        /** @return true 表示执行成功（无异常） */
        public boolean isSuccess() { return error == null; }
        /** @return 业务返回数据（失败时为 null） */
        public T getData() { return data; }
        /** @return 业务异常（成功时为 null） */
        public Exception getError() { return error; }
        /** @return 执行耗时（毫秒） */
        public long getCostMs() { return costMs; }
        /** @return 关联的 provider_trace_id */
        public String getTraceId() { return traceId; }

        /**
         * 转 Map (供 JobHandler 返回给调度器序列化)
         *
         * @return 包含 traceId/costMs/success/data/error 的 Map
         */
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
