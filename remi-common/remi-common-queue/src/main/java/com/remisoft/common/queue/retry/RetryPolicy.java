package com.remisoft.common.queue.retry;

/**
 * 统一重试策略接口
 *
 * <p>定义消息队列消费失败时的重试策略。
 * 支持指数退避、固定间隔、线性增长等多种重试策略。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 指数退避重试（最多 3 次，初始 1s，最大 30s）
 * RetryPolicy policy = RetryPolicy.exponentialBackoff(3, 1000, 30000);
 *
 * // 固定间隔重试（最多 5 次，每次间隔 2s）
 * RetryPolicy policy = RetryPolicy.fixedInterval(5, 2000);
 *
 * // 检查是否可以重试
 * if (policy.canRetry(attempt)) {
 *     long delay = policy.getDelayMillis(attempt);
 *     Thread.sleep(delay);
 * }
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
public interface RetryPolicy {

    /**
     * 判断是否可以继续重试
     *
     * @param attempt 当前重试次数（从 0 开始，0 表示首次调用）
     * @return true 表示可以继续重试，false 表示已达最大重试次数
     */
    boolean canRetry(int attempt);

    /**
     * 获取下次重试的延迟时间（毫秒）
     *
     * @param attempt 当前重试次数（从 0 开始）
     * @return 延迟时间（毫秒）
     */
    long getDelayMillis(int attempt);

    /**
     * 获取最大重试次数
     *
     * @return 最大重试次数
     */
    int getMaxAttempts();

    /**
     * 创建指数退避重试策略
     *
     * @param maxAttempts     最大重试次数
     * @param initialDelayMs  初始延迟（毫秒）
     * @param maxDelayMs      最大延迟（毫秒）
     * @return 指数退避重试策略
     */
    static RetryPolicy exponentialBackoff(int maxAttempts, long initialDelayMs, long maxDelayMs) {
        return new ExponentialBackoffRetryPolicy(maxAttempts, initialDelayMs, maxDelayMs);
    }

    /**
     * 创建固定间隔重试策略
     *
     * @param maxAttempts 最大重试次数
     * @param intervalMs  固定间隔（毫秒）
     * @return 固定间隔重试策略
     */
    static RetryPolicy fixedInterval(int maxAttempts, long intervalMs) {
        return new FixedIntervalRetryPolicy(maxAttempts, intervalMs);
    }

    /**
     * 指数退避重试策略实现
     */
    class ExponentialBackoffRetryPolicy implements RetryPolicy {
        private final int maxAttempts;
        private final long initialDelayMs;
        private final long maxDelayMs;

        ExponentialBackoffRetryPolicy(int maxAttempts, long initialDelayMs, long maxDelayMs) {
            if (maxAttempts < 0) throw new IllegalArgumentException("最大重试次数必须 >= 0");
            if (initialDelayMs <= 0) throw new IllegalArgumentException("初始延迟必须 > 0");
            if (maxDelayMs < initialDelayMs) throw new IllegalArgumentException("最大延迟必须 >= 初始延迟");
            this.maxAttempts = maxAttempts;
            this.initialDelayMs = initialDelayMs;
            this.maxDelayMs = maxDelayMs;
        }

        @Override
        public boolean canRetry(int attempt) {
            return attempt >= 0 && attempt < maxAttempts;
        }

        @Override
        public long getDelayMillis(int attempt) {
            long delay = initialDelayMs * (1L << attempt);
            return Math.min(delay, maxDelayMs);
        }

        @Override
        public int getMaxAttempts() {
            return maxAttempts;
        }
    }

    /**
     * 固定间隔重试策略实现
     */
    class FixedIntervalRetryPolicy implements RetryPolicy {
        private final int maxAttempts;
        private final long intervalMs;

        FixedIntervalRetryPolicy(int maxAttempts, long intervalMs) {
            if (maxAttempts < 0) throw new IllegalArgumentException("最大重试次数必须 >= 0");
            if (intervalMs <= 0) throw new IllegalArgumentException("间隔时间必须 > 0");
            this.maxAttempts = maxAttempts;
            this.intervalMs = intervalMs;
        }

        @Override
        public boolean canRetry(int attempt) {
            return attempt >= 0 && attempt < maxAttempts;
        }

        @Override
        public long getDelayMillis(int attempt) {
            return intervalMs;
        }

        @Override
        public int getMaxAttempts() {
            return maxAttempts;
        }
    }

    /**
     * 每条消息的重试状态跟踪器
     *
     * 封装了消息级别的重试计数器，避免调用方手动管理重试次数。
     * 线程安全，支持并发场景下的原子操作。
     *
     * <b>使用示例：</b>
     * <pre>{@code
     * RetryPolicy policy = RetryPolicy.exponentialBackoff(3, 1000, 30000);
     * RetryState state = policy.createState();
     *
     * while (state.canRetry()) {
     *     try {
     *         processMessage(msg);
     *         state.markSuccess();
     *         break;
     *     } catch (Exception e) {
     *         if (!state.tryIncrement()) {
     *             log.error("消息重试次数已用尽");
     *             break;
     *         }
     *         Thread.sleep(state.getDelayMillis());
     *     }
     * }
     * }</pre>
     */
    class RetryState {
        private final RetryPolicy policy;
        private final int maxRetries;
        private volatile int attemptCount;
        private volatile boolean success;

        private RetryState(RetryPolicy policy, int maxRetries) {
            this.policy = policy;
            this.maxRetries = maxRetries;
            this.attemptCount = 0;
            this.success = false;
        }

        /**
         * 判断是否可以继续重试
         *
         * @return true 表示可以重试，false 表示已达最大重试次数
         */
        public boolean canRetry() {
            return !success && attemptCount < maxRetries;
        }

        /**
         * 原子性地增加重试计数
         *
         * @return true 表示成功递增（未超限），false 表示已达最大重试次数
         */
        public synchronized boolean tryIncrement() {
            if (attemptCount >= maxRetries) {
                return false;
            }
            attemptCount++;
            return true;
        }

        /**
         * 获取当前重试次数
         *
         * @return 当前重试次数（从 0 开始）
         */
        public int getAttemptCount() {
            return attemptCount;
        }

        /**
         * 获取下次重试的延迟时间（毫秒）
         *
         * @return 延迟时间
         */
        public long getDelayMillis() {
            return policy.getDelayMillis(attemptCount);
        }

        /**
         * 标记消息处理成功
         */
        public void markSuccess() {
            this.success = true;
        }

        /**
         * 是否已处理成功
         *
         * @return true 表示成功，false 表示仍在重试或已耗尽
         */
        public boolean isSuccess() {
            return success;
        }

        /**
         * 是否已耗尽所有重试次数
         *
         * @return true 表示已耗尽，false 表示仍有重试机会
         */
        public boolean isExhausted() {
            return attemptCount >= maxRetries && !success;
        }

        /**
         * 获取最大重试次数
         *
         * @return 最大重试次数
         */
        public int getMaxRetries() {
            return maxRetries;
        }
    }

    /**
     * 创建消息级别的重试状态跟踪器
     *
     * @return 重试状态实例
     */
    default RetryState createState() {
        return new RetryState(this, getMaxAttempts());
    }
}
