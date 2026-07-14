package com.njydsz.pmis.common.util.retry;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 统一重试工具类
 *
 * <p>提供通用的重试执行逻辑，支持多种退避策略。
 * 统一了 ydsz-pmis-common-redis、ydsz-pmis-common-job、ydsz-pmis-common-queue 等模块的重试逻辑。
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * // 使用指数退避重试
 * RetrySupport.withExponentialBackoff(3, 1000, 30000)
 *     .retryOn(e -> e instanceof TimeoutException)
 *     .execute(() -> remoteService.call());
 *
 * // 使用固定间隔重试
 * RetrySupport.withFixedInterval(5, 2000)
 *     .retryOn(e -> e instanceof IOException)
 *     .execute(() -> fileService.upload());
 *
 * // 带抖动因子的重试（避免惊群效应）
 * RetrySupport.withExponentialBackoff(3, 1000, 30000)
 *     .withJitter()
 *     .execute(() -> batchProcess());
 *
 * // 异步重试
 * RetrySupport.withExponentialBackoff(3, 1000, 30000)
 *     .retryOn(e -> e instanceof TimeoutException)
 *     .executeAsync(() -> remoteService.call())
 *     .thenAccept(result -> System.out.println("Success: " + result))
 *     .exceptionally(e -> { System.err.println("Failed: " + e); return null; });
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public final class RetrySupport {

    /**
     * 异步重试默认线程池（有界，防止 OOM）
     *
     * <p>核心线程数 = CPU 核心数，最大线程数 = CPU 核心数 * 4，
     * 队列容量 512，拒绝策略为 CallerRunsPolicy（回退到同步执行）。
     */
    private static final ExecutorService ASYNC_EXECUTOR = createAsyncExecutor();

    private static ExecutorService createAsyncExecutor() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        return new ThreadPoolExecutor(
                cpuCores,
                Math.max(cpuCores * 4, 16),
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(512),
                r -> {
                    Thread t = new Thread(r, "retry-async");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 关闭异步重试线程池（应用关闭时调用）
     *
     * <p>执行优雅关闭：先 shutdown，等待 5 秒，未完成则 shutdownNow。
     */
    public static void shutdown() {
        ASYNC_EXECUTOR.shutdown();
        try {
            if (!ASYNC_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                ASYNC_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            ASYNC_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private RetrySupport() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 创建指数退避重试构建器
     *
     * @param maxAttempts    最大重试次数（不含首次执行）
     * @param initialDelayMs 初始延迟（毫秒）
     * @param maxDelayMs     最大延迟（毫秒）
     * @return 重试构建器
     */
    public static RetryBuilder withExponentialBackoff(int maxAttempts, long initialDelayMs, long maxDelayMs) {
        return new RetryBuilder(RetryStrategy.EXPONENTIAL_BACKOFF, maxAttempts, initialDelayMs, maxDelayMs);
    }

    /**
     * 创建固定间隔重试构建器
     *
     * @param maxAttempts 最大重试次数（不含首次执行）
     * @param intervalMs  固定间隔（毫秒）
     * @return 重试构建器
     */
    public static RetryBuilder withFixedInterval(int maxAttempts, long intervalMs) {
        return new RetryBuilder(RetryStrategy.FIXED_INTERVAL, maxAttempts, intervalMs, intervalMs);
    }

    /**
     * 重试策略枚举
     */
    public enum RetryStrategy {
        /** 指数退避：delay = initialDelay * 2^attempt */
        EXPONENTIAL_BACKOFF,
        /** 固定间隔 */
        FIXED_INTERVAL
    }

    /**
     * 重试构建器
     *
     * <p>遵循统一构造器模式：构造器为 private，通过静态工厂方法
     * {@link #withExponentialBackoff(int, long, long)} 或 {@link #withFixedInterval(int, long)}
     * 创建实例，链式配置后通过 {@link #execute(Callable)} 或 {@link #executeAsync(Callable)} 终止链式调用。
     */
    public static class RetryBuilder {
        private final RetryStrategy strategy;
        private final int maxAttempts;
        private final long initialDelayMs;
        private final long maxDelayMs;
        private Predicate<Throwable> retryPredicate;
        private Predicate<Object> resultPredicate;
        private boolean withJitter = false;
        private Consumer<RetryEvent> retryCallback;

        private RetryBuilder(RetryStrategy strategy, int maxAttempts, long initialDelayMs, long maxDelayMs) {
            this.strategy = strategy;
            this.maxAttempts = maxAttempts;
            this.initialDelayMs = initialDelayMs;
            this.maxDelayMs = maxDelayMs;
        }

        /**
         * 设置重试条件（基于异常）
         *
         * @param predicate 判断异常是否可重试的谓词
         * @return 重试构建器
         */
        public RetryBuilder retryOn(Predicate<Throwable> predicate) {
            this.retryPredicate = predicate;
            return this;
        }

        /**
         * 设置重试条件（基于结果）
         *
         * <p>当任务执行成功但结果不满足条件时触发重试。
         * 常见场景：远程调用返回 null、空列表、状态码非 200 等。
         *
         * <pre>{@code
         * RetrySupport.withFixedInterval(3, 1000)
         *     .retryIfResult(r -> r == null || r.isEmpty())
         *     .execute(() -> cache.get(key));
         * }</pre>
         *
         * @param predicate 结果谓词，返回 true 表示需要重试
         * @return 重试构建器
         */
        public RetryBuilder retryIfResult(Predicate<Object> predicate) {
            this.resultPredicate = predicate;
            return this;
        }

        /**
         * 启用抖动因子（避免惊群效应）
         *
         * <p>抖动因子范围：[0.5, 1.0]，实际延迟 = delay * (0.5 + random * 0.5)
         *
         * @return 重试构建器
         */
        public RetryBuilder withJitter() {
            this.withJitter = true;
            return this;
        }

        /**
         * 设置重试回调（每次重试时触发）
         *
         * @param callback 重试回调
         * @return 重试构建器
         */
        public RetryBuilder onRetry(Consumer<RetryEvent> callback) {
            this.retryCallback = callback;
            return this;
        }

        /**
         * 执行无返回值的任务
         *
         * @param task 要执行的任务
         * @throws Exception 如果重试次数耗尽或遇到不可重试异常
         */
        public void execute(Runnable task) throws Exception {
            execute(() -> {
                task.run();
                return null;
            });
        }

        /**
         * 执行有返回值的任务
         *
         * @param task 要执行的任务
         * @param <T>  返回值类型
         * @return 任务执行结果
         * @throws Exception 如果重试次数耗尽或遇到不可重试异常
         */
        public <T> T execute(Callable<T> task) throws Exception {
            if (task == null) {
                throw new IllegalArgumentException("Task cannot be null");
            }

            int attempt = 0;
            Throwable lastException = null;

            while (attempt <= maxAttempts) {
                T result;
                try {
                    result = task.call();
                } catch (Throwable e) {
                    lastException = e;

                    // 检查异常是否可重试
                    if (retryPredicate != null && !retryPredicate.test(e)) {
                        throw e;
                    }

                    // 检查是否还有重试机会
                    if (attempt >= maxAttempts) {
                        break;
                    }

                    // 计算延迟
                    long delay = calculateDelay(attempt);

                    // 触发重试回调
                    if (retryCallback != null) {
                        retryCallback.accept(new RetryEvent(attempt + 1, delay, e));
                    }

                    // 等待
                    sleep(delay);

                    attempt++;
                    continue;
                }

                // 检查结果是否需要重试
                if (resultPredicate != null && resultPredicate.test(result)) {
                    if (attempt >= maxAttempts) {
                        // 重试次数耗尽，返回最后结果
                        return result;
                    }

                    long delay = calculateDelay(attempt);

                    if (retryCallback != null) {
                        retryCallback.accept(new RetryEvent(attempt + 1, delay, null));
                    }

                    sleep(delay);
                    attempt++;
                    continue;
                }

                return result;
            }

            // 重试耗尽，抛出最后异常
            if (lastException != null) {
                if (lastException instanceof Exception) {
                    throw (Exception) lastException;
                }
                throw new RuntimeException(lastException);
            }

            throw new RuntimeException("Retry exhausted without exception");
        }

        /**
         * 异步执行有返回值的任务
         *
         * @param task 要执行的任务
         * @param <T>  返回值类型
         * @return CompletableFuture
         */
        public <T> CompletableFuture<T> executeAsync(Callable<T> task) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return execute(task);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, ASYNC_EXECUTOR);
        }

        /**
         * 异步执行无返回值的任务
         *
         * @param task 要执行的任务
         * @return CompletableFuture
         */
        public CompletableFuture<Void> executeAsyncRunnable(Runnable task) {
            return CompletableFuture.runAsync(() -> {
                try {
                    execute(task);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, ASYNC_EXECUTOR);
        }

        /**
         * 计算延迟时间
         */
        private long calculateDelay(int attempt) {
            long delay;

            if (strategy == RetryStrategy.EXPONENTIAL_BACKOFF) {
                delay = initialDelayMs * (1L << attempt);
                delay = Math.min(delay, maxDelayMs);
            } else {
                // FIXED_INTERVAL
                delay = initialDelayMs;
            }

            // 应用抖动因子
            if (withJitter) {
                double jitterFactor = 0.5 + ThreadLocalRandom.current().nextDouble() * 0.5;
                delay = (long) (delay * jitterFactor);
            }

            return delay;
        }

        /**
         * 睡眠指定时间
         */
        private void sleep(long millis) {
            try {
                TimeUnit.MILLISECONDS.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Retry interrupted", e);
            }
        }
    }

    // ==================== 便捷方法 ====================

    /**
     * 重试事件
     *
     * <p>每次重试时通过回调传递给调用方，包含重试次数、延迟时间和异常信息。
     */
    public static class RetryEvent {
        private final int attempt;
        private final long delayMs;
        private final Throwable exception;

        RetryEvent(int attempt, long delayMs, Throwable exception) {
            this.attempt = attempt;
            this.delayMs = delayMs;
            this.exception = exception;
        }

        public int getAttempt() {
            return attempt;
        }

        public long getDelayMs() {
            return delayMs;
        }

        public Throwable getException() {
            return exception;
        }
    }

    /**
     * 计算指数退避延迟时间
     *
     * <p>公式：delay = min(initialDelay * 2^(attempt-1), maxDelay)
     *
     * @param attempt          当前重试次数（从1开始）
     * @param initialDelayMs   初始延迟（毫秒）
     * @param maxDelayMs       最大延迟（毫秒）
     * @return 延迟时间（毫秒）
     */
    public static long calculateExponentialBackoff(int attempt, long initialDelayMs, long maxDelayMs) {
        long delay = initialDelayMs * (1L << (attempt - 1));
        return Math.min(delay, maxDelayMs);
    }

    /**
     * 计算带抖动因子的指数退避延迟时间
     *
     * <p>公式：delay = min(initialDelay * 2^(attempt-1) * jitter, maxDelay)
     * <p>抖动因子范围：[0.5, 1.0]，避免多个任务同时重试导致的"惊群效应"
     *
     * @param attempt          当前重试次数（从1开始）
     * @param initialDelayMs   初始延迟（毫秒）
     * @param maxDelayMs       最大延迟（毫秒）
     * @return 延迟时间（毫秒）
     */
    public static long calculateExponentialBackoffWithJitter(int attempt, long initialDelayMs, long maxDelayMs) {
        long delay = initialDelayMs * (1L << (attempt - 1));
        double jitterFactor = 0.5 + ThreadLocalRandom.current().nextDouble() * 0.5;
        delay = (long) (delay * jitterFactor);
        return Math.min(delay, maxDelayMs);
    }

    /**
     * 计算固定间隔延迟时间
     *
     * @param intervalMs 固定间隔（毫秒）
     * @return 延迟时间（毫秒）
     */
    public static long calculateFixedInterval(long intervalMs) {
        return intervalMs;
    }
}
