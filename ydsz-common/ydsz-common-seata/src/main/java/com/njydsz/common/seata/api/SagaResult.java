package com.njydsz.common.seata.api;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SAGA 事务执行结果
 *
 * <p>封装 SAGA 事务的完整执行结果，包含：
 * <ul>
 *   <li>执行状态（成功/失败/补偿中/补偿失败）</li>
 *   <li>各步骤执行详情（名称、耗时、结果）</li>
 *   <li>补偿失败明细（步骤名、错误信息、重试次数）</li>
 *   <li>总耗时</li>
 * </ul>
 *
 * <p><b>P2-4 新增</b>：解决 SAGA 执行结果信息不足的问题，
 * 便于调用方根据结果做后续处理（如人工介入、告警通知）。
 *
 * <p>使用示例：
 * <pre>{@code
 * SagaResult&lt;OrderResult&gt; result = sagaOrchestrator.executeWithResult("create-order", steps);
 * if (result.isSuccess()) {
 *     OrderResult order = result.getResult();
 * } else if (result.isCompensationFailed()) {
 *     // 补偿失败，需要人工介入
 *     for (SagaResult.CompensationFailure failure : result.getCompensationFailures()) {
 *         alertService.send("SAGA compensation failed: " + failure.getStepName());
 *     }
 * }
 * }</pre>
 *
 * @param <T> 正向操作返回值类型
 * @author ydsz-team
 * @since 1.3.0
 */
public class SagaResult<T> {

    /**
     * SAGA 执行状态
     */
    public enum Status {
        /** 全部步骤执行成功 */
        SUCCESS,
        /** 某步骤失败但补偿成功 */
        COMPENSATED,
        /** 某步骤失败且补偿失败（需要人工介入） */
        COMPENSATION_FAILED,
        /** 执行中（仅异步场景） */
        EXECUTING
    }

    private final String transactionName;
    private final String xid;
    private final Status status;
    private final T result;
    private final List<StepExecution> stepExecutions;
    private final List<CompensationFailure> compensationFailures;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final long totalDurationMs;
    private final String errorMessage;

    private SagaResult(Builder<T> builder) {
        this.transactionName = builder.transactionName;
        this.xid = builder.xid;
        this.status = builder.status;
        this.result = builder.result;
        this.stepExecutions = Collections.unmodifiableList(new ArrayList<>(builder.stepExecutions));
        this.compensationFailures = Collections.unmodifiableList(new ArrayList<>(builder.compensationFailures));
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.totalDurationMs = builder.totalDurationMs;
        this.errorMessage = builder.errorMessage;
    }

    /**
     * 判断是否全部成功
     */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    /**
     * 判断是否已补偿（正向失败但补偿成功）
     */
    public boolean isCompensated() {
        return status == Status.COMPENSATED;
    }

    /**
     * 判断是否补偿失败
     */
    public boolean isCompensationFailed() {
        return status == Status.COMPENSATION_FAILED;
    }

    /**
     * 判断是否执行失败（无论补偿结果）
     */
    public boolean isFailed() {
        return status != Status.SUCCESS;
    }

    // ============= Getter 方法 =============

    public String getTransactionName() {
        return transactionName;
    }

    public String getXid() {
        return xid;
    }

    public Status getStatus() {
        return status;
    }

    public T getResult() {
        return result;
    }

    public List<StepExecution> getStepExecutions() {
        return stepExecutions;
    }

    public List<CompensationFailure> getCompensationFailures() {
        return compensationFailures;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * 创建 Builder
     */
    public static <T> Builder<T> builder(String transactionName, String xid) {
        return new Builder<>(transactionName, xid);
    }

    // ============= 内部类 =============

    /**
     * 步骤执行详情
     */
    public static class StepExecution {
        private final int index;
        private final String stepName;
        private final boolean success;
        private final long durationMs;
        private final String errorMessage;

        public StepExecution(int index, String stepName, boolean success, long durationMs, String errorMessage) {
            this.index = index;
            this.stepName = stepName;
            this.success = success;
            this.durationMs = durationMs;
            this.errorMessage = errorMessage;
        }

        public int getIndex() {
            return index;
        }

        public String getStepName() {
            return stepName;
        }

        public boolean isSuccess() {
            return success;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * 补偿失败详情
     */
    public static class CompensationFailure {
        private final String stepName;
        private final String errorMessage;
        private final int retryAttempts;

        public CompensationFailure(String stepName, String errorMessage, int retryAttempts) {
            this.stepName = stepName;
            this.errorMessage = errorMessage;
            this.retryAttempts = retryAttempts;
        }

        public String getStepName() {
            return stepName;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public int getRetryAttempts() {
            return retryAttempts;
        }
    }

    /**
     * SagaResult 构建器
     */
    public static class Builder<T> {
        private final String transactionName;
        private final String xid;
        private Status status;
        private T result;
        private final List<StepExecution> stepExecutions = new ArrayList<>();
        private final List<CompensationFailure> compensationFailures = new ArrayList<>();
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private long totalDurationMs;
        private String errorMessage;

        private Builder(String transactionName, String xid) {
            this.transactionName = transactionName;
            this.xid = xid;
            this.startTime = LocalDateTime.now();
        }

        /**
         * 设置执行状态
         *
         * @param status 执行状态
         * @return this
         */
        public Builder<T> status(Status status) {
            this.status = status;
            return this;
        }

        /**
         * 设置正向操作返回值
         *
         * @param result 正向操作返回值
         * @return this
         */
        public Builder<T> result(T result) {
            this.result = result;
            return this;
        }

        /**
         * 添加步骤执行详情
         *
         * @param execution 步骤执行详情
         * @return this
         */
        public Builder<T> addStepExecution(StepExecution execution) {
            this.stepExecutions.add(execution);
            return this;
        }

        /**
         * 添加补偿失败详情
         *
         * @param failure 补偿失败详情
         * @return this
         */
        public Builder<T> addCompensationFailure(CompensationFailure failure) {
            this.compensationFailures.add(failure);
            return this;
        }

        /**
         * 批量添加补偿失败详情
         *
         * @param failures 补偿失败详情列表
         * @return this
         */
        public Builder<T> addCompensationFailures(List<CompensationFailure> failures) {
            this.compensationFailures.addAll(failures);
            return this;
        }

        /**
         * 设置事务开始时间
         *
         * @param startTime 事务开始时间
         * @return this
         */
        public Builder<T> startTime(LocalDateTime startTime) {
            this.startTime = startTime;
            return this;
        }

        /**
         * 设置事务结束时间
         *
         * @param endTime 事务结束时间
         * @return this
         */
        public Builder<T> endTime(LocalDateTime endTime) {
            this.endTime = endTime;
            return this;
        }

        /**
         * 设置事务总耗时
         *
         * @param totalDurationMs 事务总耗时（毫秒）
         * @return this
         */
        public Builder<T> totalDurationMs(long totalDurationMs) {
            this.totalDurationMs = totalDurationMs;
            return this;
        }

        /**
         * 设置错误信息
         *
         * @param errorMessage 错误信息
         * @return this
         */
        public Builder<T> errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        /**
         * 构建 SagaResult 实例
         *
         * @return SagaResult 实例
         */
        public SagaResult<T> build() {
            if (endTime == null) {
                endTime = LocalDateTime.now();
            }
            if (totalDurationMs == 0 && startTime != null && endTime != null) {
                totalDurationMs = java.time.Duration.between(startTime, endTime).toMillis();
            }
            return new SagaResult<>(this);
        }
    }
}
