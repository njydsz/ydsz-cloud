package com.njydsz.pmis.common.core.job;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 任务运行记录器。
 *
 * <p>封装任务执行的计时、结果收集和 trace-id 注入逻辑，
 * 供 {@link JobHandler} 实现类使用，统一任务执行日志格式。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class JobRunRecorder {

    private JobRunRecorder() {
    }

    /**
     * 执行任务并记录运行结果。
     *
     * @param jobName     任务名称
     * @param paramsJson  任务参数 JSON
     * @param task        任务逻辑
     * @param <T>         结果类型
     * @return 任务运行结果
     */
    public static <T> JobRunResult<T> run(String jobName, String paramsJson, Supplier<T> task) {
        long start = System.currentTimeMillis();
        try {
            T data = task.get();
            long cost = System.currentTimeMillis() - start;
            return JobRunResult.success(data, cost);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            return JobRunResult.failure(e, cost);
        }
    }

    /**
     * 任务运行结果。
     *
     * @param <T> 结果数据类型
     */
    public static final class JobRunResult<T> {

        private final boolean success;
        private final T data;
        private final Throwable error;
        private final long costMs;

        private JobRunResult(boolean success, T data, Throwable error, long costMs) {
            this.success = success;
            this.data = data;
            this.error = error;
            this.costMs = costMs;
        }

        /**
         * 创建成功结果。
         *
         * @param data   结果数据
         * @param costMs 耗时（毫秒）
         * @param <T>    结果类型
         * @return 成功结果
         */
        public static <T> JobRunResult<T> success(T data, long costMs) {
            return new JobRunResult<>(true, data, null, costMs);
        }

        /**
         * 创建失败结果。
         *
         * @param error  异常
         * @param costMs 耗时（毫秒）
         * @param <T>    结果类型
         * @return 失败结果
         */
        public static <T> JobRunResult<T> failure(Throwable error, long costMs) {
            return new JobRunResult<>(false, null, error, costMs);
        }

        public boolean isSuccess() {
            return success;
        }

        public T getData() {
            return data;
        }

        public Throwable getError() {
            return error;
        }

        public long getCostMs() {
            return costMs;
        }

        /**
         * 转换为 Map（供日志/监控使用）。
         *
         * @return 包含 success/data/costMs 的 Map
         */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("success", success);
            map.put("data", data);
            map.put("costMs", costMs);
            if (error != null) {
                map.put("error", error.getMessage());
            }
            return map;
        }

        @Override
        public String toString() {
            return "JobRunResult{success=" + success + ", costMs=" + costMs +
                    (error != null ? ", error='" + error.getMessage() + "'" : "") + "}";
        }
    }
}
