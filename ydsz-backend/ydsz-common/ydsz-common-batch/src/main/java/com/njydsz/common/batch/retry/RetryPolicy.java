package com.njydsz.common.batch.retry;

/**
 * 重试策略
 *
 * <p>定义异常发生时是否重试、重试次数、重试间隔、退避算法等。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface RetryPolicy {

    /**
     * 是否可以重试
     */
    boolean canRetry(RetryContext context);

    /**
     * 注册一次重试
     */
    void registerRetry(RetryContext context);

    /**
     * 退避策略
     */
    default long backoffMillis(int retryCount) {
        return Math.min(1000L * (1L << Math.min(retryCount, 10)), 30_000L);
    }
}
