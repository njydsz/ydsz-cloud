package com.njydsz.common.util.concurrent;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import lombok.extern.slf4j.Slf4j;

/**
 * 重试工具类
 *
 * <p>提供简洁易用的重试能力，支持固定间隔、指数退避、自定义异常判断等策略。
 * 适用于网络调用、消息发送、外部 API 调用等需要容错处理的场景。
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 示例 1：固定间隔重试 3 次
 * String result = RetryUtils.executeWithRetry(() -> httpClient.call(), 3, Duration.ofSeconds(2));
 *
 * // 示例 2：指数退避重试
 * String result = RetryUtils.executeWithBackoff(() -> {
 *     return externalApi.fetchData();
 * }, RetryConfig.builder()
 *     .maxRetries(5)
 *     .initialDelay(Duration.ofMillis(100))
 *     .maxDelay(Duration.ofSeconds(10))
 *     .multiplier(2.0)
 *     .retryOn(e -> e instanceof java.net.SocketTimeoutException)
 *     .build());
 *
 * // 示例 3：无限重试直到成功（慎用）
 * RetryUtils.executeWithRetry(() -> initConnection(), Integer.MAX_VALUE, Duration.ofSeconds(5));
 * }</pre>
 *
 * @author ydsz-team
 * @since 3.1.0
 */
@Slf4j
public final class RetryUtils {

    /**
     * 默认指数退避乘数（每次延迟翻倍）。
     */
    private static final double DEFAULT_MULTIPLIER = 2.0;

    /**
     * 私有构造器，工具类不允许实例化。
     */
    private RetryUtils() {
        throw new UnsupportedOperationException("RetryUtils is a utility class and cannot be instantiated");
    }

    /**
     * 固定间隔重试。
     *
     * <p>在 action 失败时以固定的时间间隔进行重试，最多重试 maxRetries 次。
     * 所有异常都会触发重试。
     *
     * @param action     待执行的操作（不可为 null）
     * @param maxRetries 最大重试次数（≥ 0，0 表示不重试）
     * @param delay      重试间隔（不可为 null，必须 ≥ 0）
     * @param <T>        返回值类型
     * @return 操作成功时的返回值
     * @throws InterruptedException 等待重试时被中断
     * @throws Exception             所有重试均失败后抛出最后一次的异常
     */
    public static <T> T executeWithRetry(Callable<T> action, int maxRetries, Duration delay)
            throws InterruptedException, Exception {
        return executeWithRetry(action, maxRetries, delay, e -> true);
    }

    /**
     * 固定间隔重试（可自定义重试条件）。
     *
     * <p>仅当异常匹配 retryOn 时才触发重试，其他异常直接抛出。
     *
     * @param action     待执行的操作（不可为 null）
     * @param maxRetries 最大重试次数（≥ 0）
     * @param delay      重试间隔（不可为 null）
     * @param retryOn    重试条件（异常为 null 时不重试）
     * @param <T>        返回值类型
     * @return 操作成功时的返回值
     * @throws InterruptedException 等待重试时被中断
     * @throws Exception             所有重试均失败后抛出最后一次的异常
     */
    public static <T> T executeWithRetry(Callable<T> action, int maxRetries, Duration delay,
                                         Predicate<Throwable> retryOn)
            throws InterruptedException, Exception {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        if (delay == null || delay.isNegative()) {
            throw new IllegalArgumentException("delay must not be null or negative");
        }
        if (retryOn == null) {
            throw new IllegalArgumentException("retryOn must not be null");
        }

        Exception lastException = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return action.call();
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxRetries && retryOn.test(e)) {
                    log.debug("Retry attempt {}/{} after delay {} due to: {}",
                            attempt + 1, maxRetries, delay, e.getMessage());
                    Thread.sleep(delay.toMillis());
                } else {
                    break;
                }
            }
        }

        throw lastException;
    }

    /**
     * 指数退避重试。
     *
     * <p>每次重试的延迟时间按指数增长（initialDelay * multiplier^attempt），
     * 直到达到 maxDelay 上限。适用于需要避免"惊群效应"的场景。
     *
     * @param action  待执行的操作（不可为 null）
     * @param config  重试配置（不可为 null）
     * @param <T>     返回值类型
     * @return 操作成功时的返回值
     * @throws InterruptedException 等待重试时被中断
     * @throws Exception             所有重试均失败后抛出最后一次的异常
     */
    public static <T> T executeWithBackoff(Callable<T> action, RetryConfig config)
            throws InterruptedException, Exception {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }

        int maxRetries = config.getMaxRetries();
        Duration initialDelay = config.getInitialDelay();
        Duration maxDelay = config.getMaxDelay();
        double multiplier = config.getMultiplier() > 0 ? config.getMultiplier() : DEFAULT_MULTIPLIER;
        Predicate<Throwable> retryOn = config.getRetryOn() != null ? config.getRetryOn() : e -> true;

        Exception lastException = null;
        long currentDelayMs = initialDelay.toMillis();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return action.call();
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxRetries && retryOn.test(e)) {
                    long jitterMs = ThreadLocalRandom.current().nextLong(0, currentDelayMs / 2);
                    long sleepMs = Math.min(currentDelayMs + jitterMs, maxDelay.toMillis());
                    log.debug("Backoff retry attempt {}/{} after {}ms due to: {}",
                            attempt + 1, maxRetries, sleepMs, e.getMessage());
                    Thread.sleep(sleepMs);
                    currentDelayMs = Math.min((long) (currentDelayMs * multiplier), maxDelay.toMillis());
                } else {
                    break;
                }
            }
        }

        throw lastException;
    }

    /**
     * 重试配置。
     *
     * <p>使用 Builder 模式构建，支持 Lombok 的 {@code @Builder} 注解。
     */
    public static class RetryConfig {
        /** 最大重试次数（≥ 0） */
        private final int maxRetries;
        /** 初始延迟（第一次重试前的等待时间） */
        private final Duration initialDelay;
        /** 最大延迟上限 */
        private final Duration maxDelay;
        /** 退避乘数（每次延迟增长倍数） */
        private final double multiplier;
        /** 重试条件（哪些异常触发重试） */
        private final Predicate<Throwable> retryOn;

        /**
         * 构造器。
         *
         * @param maxRetries   最大重试次数
         * @param initialDelay 初始延迟
         * @param maxDelay     最大延迟上限
         * @param multiplier   退避乘数
         * @param retryOn      重试条件
         */
        public RetryConfig(int maxRetries, Duration initialDelay, Duration maxDelay,
                           double multiplier, Predicate<Throwable> retryOn) {
            this.maxRetries = maxRetries;
            this.initialDelay = initialDelay;
            this.maxDelay = maxDelay;
            this.multiplier = multiplier;
            this.retryOn = retryOn;
        }

        // ==================== Builder ====================

        /**
         * 创建 Builder 实例。
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * 配置构建器。
         */
        public static final class Builder {
            private int maxRetries = 3;
            private Duration initialDelay = Duration.ofMillis(500);
            private Duration maxDelay = Duration.ofSeconds(30);
            private double multiplier = DEFAULT_MULTIPLIER;
            private Predicate<Throwable> retryOn = e -> true;

            Builder() {
            }

            public Builder maxRetries(int maxRetries) {
                this.maxRetries = maxRetries;
                return this;
            }

            public Builder initialDelay(Duration initialDelay) {
                this.initialDelay = initialDelay;
                return this;
            }

            public Builder maxDelay(Duration maxDelay) {
                this.maxDelay = maxDelay;
                return this;
            }

            public Builder multiplier(double multiplier) {
                this.multiplier = multiplier;
                return this;
            }

            public Builder retryOn(Predicate<Throwable> retryOn) {
                this.retryOn = retryOn;
                return this;
            }

            /**
             * 构建配置。
             */
            public RetryConfig build() {
                return new RetryConfig(maxRetries, initialDelay, maxDelay, multiplier, retryOn);
            }
        }

        // ==================== Getters ====================

        public int getMaxRetries() {
            return maxRetries;
        }

        public Duration getInitialDelay() {
            return initialDelay;
        }

        public Duration getMaxDelay() {
            return maxDelay;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public Predicate<Throwable> getRetryOn() {
            return retryOn;
        }
    }
}
