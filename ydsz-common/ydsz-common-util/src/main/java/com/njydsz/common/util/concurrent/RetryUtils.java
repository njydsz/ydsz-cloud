package com.njydsz.common.util.concurrent;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 重试工具类（Unchecked 风格）。
 *
 * <p>提供简洁易用的重试能力，支持固定间隔、指数退避、自定义异常判断等策略。
 * 适用于网络调用、消息发送、外部 API 调用等需要容错处理的场景。
 *
 * <p><b>异常模型：</b>所有方法均为 unchecked —— 重试耗尽后抛出
 * {@link RetryException}（包装最后一次异常），调用方无需强制捕获。
 * 这与 Spring Retry / Resilience4j 的异常模型一致。
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
     * 抖动随机数下限（保证 nextLong(0, upper) 的 upper 至少为 1，避免边界崩溃）。
     */
    private static final long JITTER_MIN_UPPER_BOUND = 1L;

    /**
     * 私有构造器，工具类不允许实例化。
     @return 处理结果
     */
    private RetryUtils() {
        throw new UnsupportedOperationException("RetryUtils is a utility class and cannot be instantiated");
    }

    /**
     * 固定间隔重试（所有异常均触发重试）。
     *
     * <p>在 action 失败时以固定的时间间隔进行重试，最多重试 maxRetries 次。
     * 重试耗尽后抛出 {@link RetryException}，其中包含最后一次失败的异常。
     *
     * @param action     待执行的操作（不可为 null）
     * @param maxRetries 最大重试次数（≥ 0，0 表示不重试）
     * @param delay      重试间隔（不可为 null，必须 ≥ 0）
     * @param <T>        返回值类型
     * @return 操作成功时的返回值
     * @throws RetryException 所有重试均失败（或执行被中断）时抛出
     */
    public static <T> T executeWithRetry(Callable<T> action, int maxRetries, Duration delay) {
        return executeWithRetry(action, maxRetries, delay, e -> true);
    }

    /**
     * 固定间隔重试（可自定义重试条件）。
     *
     * <p>仅当异常匹配 retryOn 时才触发重试，其他异常直接包装抛出。
     *
     * @param action     待执行的操作（不可为 null）
     * @param maxRetries 最大重试次数（≥ 0）
     * @param delay      重试间隔（不可为 null）
     * @param retryOn    重试条件（异常为 null 时不重试）
     * @param <T>        返回值类型
     * @return 操作成功时的返回值
     * @throws RetryException 所有重试均失败（或执行被中断）时抛出
     */
    public static <T> T executeWithRetry(Callable<T> action, int maxRetries, Duration delay,
                                         Predicate<Throwable> retryOn) {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        if (delay == null || delay.isNegative()) {
            throw new IllegalArgumentException("delay must not be null or negative");
        }
        if (retryOn == null) {
            throw new IllegalArgumentException("retryOn must not be null");
        }

        Throwable lastException = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return action.call();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RetryException("Retry interrupted", e);
            } catch (Throwable e) {
                lastException = e;
                if (attempt < maxRetries && retryOn.test(e)) {
                    log.debug("Retry attempt {}/{} after delay {} due to: {}",
                            attempt + 1, maxRetries, delay, e.getMessage());
                    sleepQuietly(delay.toMillis());
                } else {
                    break;
                }
            }
        }
        throw new RetryException("Retry exhausted after " + (maxRetries + 1) + " attempts", lastException);
    }

    /**
     * 指数退避重试。
     *
     * <p>每次重试的延迟时间按指数增长（initialDelay * multiplier^attempt），
     * 直到达到 maxDelay 上限，并叠加随机抖动避免"惊群效应"。
     *
     * @param action 待执行的操作（不可为 null）
     * @param config 重试配置（不可为 null）
     * @param <T>    返回值类型
     * @return 操作成功时的返回值
     * @throws RetryException 所有重试均失败（或执行被中断）时抛出
     */
    public static <T> T executeWithBackoff(Callable<T> action, RetryConfig config) {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }

        int maxRetries = config.getMaxRetries();
        Duration initialDelay = config.getInitialDelay();
        Duration maxDelay = config.getMaxDelay();
        Duration maxDuration = config.getMaxDuration();
        double multiplier = config.getMultiplier() > 0 ? config.getMultiplier() : DEFAULT_MULTIPLIER;
        Predicate<Throwable> retryOn = config.getRetryOn() != null ? config.getRetryOn() : e -> true;
        Consumer<Integer> onRetry = config.getOnRetry() != null ? config.getOnRetry() : attempt -> { };

        Throwable lastException = null;
        long currentDelayMs = initialDelay.toMillis();
        long startNanos = System.nanoTime();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return action.call();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RetryException("Retry interrupted", e);
            } catch (Throwable e) {
                lastException = e;
                if (attempt < maxRetries && retryOn.test(e)) {
                    // 总时长上限：超过 maxDuration 后停止重试
                    if (maxDuration != null && System.nanoTime() - startNanos >= maxDuration.toNanos()) {
                        break;
                    }
                    long jitterMs = ThreadLocalRandom.current().nextLong(0, Math.max(JITTER_MIN_UPPER_BOUND,
                            currentDelayMs / 2));
                    long sleepMs = Math.min(currentDelayMs + jitterMs, maxDelay.toMillis());
                    log.debug("Backoff retry attempt {}/{} after {}ms due to: {}",
                            attempt + 1, maxRetries, sleepMs, e.getMessage());
                    onRetry.accept(attempt + 1);
                    sleepQuietly(sleepMs);
                    currentDelayMs = Math.min((long) (currentDelayMs * multiplier), maxDelay.toMillis());
                } else {
                    break;
                }
            }
        }
        throw new RetryException("Retry exhausted after " + (maxRetries + 1) + " attempts", lastException);
    }

    /**
     * 静默睡眠，中断时恢复中断标志并抛出 {@link RetryException}。
     *
     * @param millis 睡眠毫秒数（≥ 0）
     * @throws RetryException 睡眠期间线程被中断时抛出
     */
    private static void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RetryException("Retry interrupted while sleeping", e);
        }
    }

    /**
     * 重试配置。
     *
     * <p>默认值：最大重试 3 次，初始延迟 500ms，最大延迟 30s，退避乘数 2.0，
     * 重试条件为所有异常。
     */
    @Getter
    @Builder
    public static class RetryConfig {
        /**
         * 最大重试次数（≥ 0），默认 3。
         */
        @Builder.Default
        private final int maxRetries = 3;

        /**
         * 初始延迟（第一次重试前的等待时间），默认 500ms。
         */
        @Builder.Default
        private final Duration initialDelay = Duration.ofMillis(500);

        /**
         * 最大延迟上限，默认 30s。
         */
        @Builder.Default
        private final Duration maxDelay = Duration.ofSeconds(30);

        /**
         * 退避乘数（每次延迟增长倍数），默认 2.0。
         */
        @Builder.Default
        private final double multiplier = DEFAULT_MULTIPLIER;

        /**
         * 重试条件（哪些异常触发重试），默认所有异常。
         */
        @Builder.Default
        private final Predicate<Throwable> retryOn = e -> true;

        /**
         * 总时长上限（null 表示不限），超过该时长后即使仍有重试次数也不再重试。
         */
        private final Duration maxDuration;

        /**
         * 每次重试前的回调，参数为已尝试次数（从 1 开始）。可用于埋点、告警等。
         */
        @Builder.Default
        private final Consumer<Integer> onRetry = attempt -> { };
    }
}
