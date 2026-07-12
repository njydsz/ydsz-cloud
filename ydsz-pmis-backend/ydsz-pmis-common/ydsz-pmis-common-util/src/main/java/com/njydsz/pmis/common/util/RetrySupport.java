package com.njydsz.pmis.common.util;

import java.util.function.Supplier;

/**
 * 重试支持工具类
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class RetrySupport {

    private RetrySupport() {
    }

    /**
     * 执行带重试的操作
     *
     * @param action       操作
     * @param maxRetries   最大重试次数
     * @param retryDelayMs 重试间隔（毫秒）
     * @param <T>          返回类型
     * @return 操作结果
     */
    public static <T> T execute(Supplier<T> action, int maxRetries, long retryDelayMs) {
        return execute(action, maxRetries, retryDelayMs, null);
    }

    /**
     * 执行带重试的操作
     *
     * @param action       操作
     * @param maxRetries   最大重试次数
     * @param retryDelayMs 重试间隔（毫秒）
     * @param retryOn      需要重试的异常类型（null 表示所有异常都重试）
     * @param <T>          返回类型
     * @return 操作结果
     */
    public static <T> T execute(Supplier<T> action, int maxRetries, long retryDelayMs,
                                 Class<? extends Throwable> retryOn) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }
        Exception lastException = null;
        for (int i = 0; i <= maxRetries; i++) {
            try {
                return action.get();
            } catch (Exception e) {
                if (retryOn != null && !retryOn.isInstance(e)) {
                    throw e;
                }
                lastException = e;
                if (i < maxRetries && retryDelayMs > 0) {
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                }
            }
        }
        throw new RuntimeException("Operation failed after " + (maxRetries + 1) + " attempts", lastException);
    }

    /**
     * 执行带重试的无返回值操作
     *
     * @param action       操作
     * @param maxRetries   最大重试次数
     * @param retryDelayMs 重试间隔（毫秒）
     */
    public static void execute(Runnable action, int maxRetries, long retryDelayMs) {
        execute(() -> {
            action.run();
            return null;
        }, maxRetries, retryDelayMs);
    }
}
