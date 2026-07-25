package com.njydsz.common.batch.retry;

import java.util.HashSet;
import java.util.Set;

/**
 * 默认重试策略
 *
 * <p>基于「最大次数 + 异常白名单」的简单重试策略。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SimpleRetryPolicy implements RetryPolicy {

    private final int maxAttempts;
    private final Set<Class<? extends Throwable>> retryableExceptions;
    private final long backoffBaseMillis;

    public SimpleRetryPolicy() {
        this(3, new HashSet<>(), 1000L);
    }

    public SimpleRetryPolicy(int maxAttempts,
                              Set<Class<? extends Throwable>> retryableExceptions,
                              long backoffBaseMillis) {
        this.maxAttempts = maxAttempts;
        this.retryableExceptions = retryableExceptions == null
                ? new HashSet<>() : retryableExceptions;
        this.backoffBaseMillis = backoffBaseMillis;
    }

    @Override
    public boolean canRetry(RetryContext context) {
        if (context.getRetryCount() >= maxAttempts) {
            return false;
        }
        if (retryableExceptions.isEmpty()) {
            return true;
        }
        Throwable t = context.getThrowable();
        while (t != null) {
            for (Class<? extends Throwable> exClass : retryableExceptions) {
                if (exClass.isInstance(t)) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    @Override
    public void registerRetry(RetryContext context) {
        context.setRetryCount(context.getRetryCount() + 1);
        context.setLastRetryTime(System.currentTimeMillis());
    }

    @Override
    public long backoffMillis(int retryCount) {
        // 指数退避：base * 2^(retryCount - 1)
        return Math.min(backoffBaseMillis * (1L << Math.min(retryCount - 1, 10)), 30_000L);
    }
}
