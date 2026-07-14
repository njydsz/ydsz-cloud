package com.njydsz.pmis.common.core.retry;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 声明式重试模板
 *
 * <p>提供灵活的重试能力，支持：
 * <ul>
 *   <li>最大重试次数限制</li>
 *   <li>指数退避策略</li>
 *   <li>异常谓词过滤（仅对特定异常重试）</li>
 *   <li>重试回调通知</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * RetryTemplate template = RetryTemplate.builder()
 *     .maxRetries(3)
 *     .initialBackoff(Duration.ofMillis(100))
 *     .maxBackoff(Duration.ofSeconds(5))
 *     .retryOn(e -> e instanceof java.io.IOException)
 *     .build();
 *
 * String result = template.execute("remoteCall", () -> {
 *     return httpClient.call(url);
 * });
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 3.5.0
 */
public class RetryTemplate {

    private static final Logger log = LoggerFactory.getLogger(RetryTemplate.class);

    private final int maxRetries;
    private final Duration initialBackoff;
    private final double backoffMultiplier;
    private final Duration maxBackoff;
    private final Predicate<Throwable> retryPredicate;
    private final RetryListener listener;

    private RetryTemplate(Builder b) {
        this.maxRetries = b.maxRetries;
        this.initialBackoff = b.initialBackoff;
        this.backoffMultiplier = b.backoffMultiplier;
        this.maxBackoff = b.maxBackoff;
        this.retryPredicate = b.retryPredicate;
        this.listener = b.listener;
    }

    /**
     * 执行带重试的操作
     *
     * @param name   操作名称（日志用）
     * @param action 业务操作
     * @param <T>    返回值类型
     * @return 操作返回值
     * @throws Exception 操作异常（超过重试次数后抛出最后一次异常）
     */
    public <T> T execute(String name, Callable<T> action) throws Exception {
        Exception lastException = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                T result = action.call();
                if (attempt > 0) {
                    log.info("Retry succeeded: name={}, attempt={}", name, attempt);
                    if (listener != null) {
                        listener.onSuccess(name, attempt);
                    }
                }
                return result;
            } catch (Exception e) {
                lastException = e;
                if (attempt >= maxRetries || !retryPredicate.test(e)) {
                    throw e;
                }
                long backoffMs = calculateBackoff(attempt);
                log.warn("Retry scheduled: name={}, attempt={}, backoff={}ms, error={}",
                        name, attempt + 1, backoffMs, e.getMessage());
                if (listener != null) {
                    listener.onRetry(name, attempt + 1, e);
                }
                Thread.sleep(backoffMs);
            }
        }
        throw lastException;
    }

    private long calculateBackoff(int attempt) {
        long backoff = (long) (initialBackoff.toMillis() * Math.pow(backoffMultiplier, attempt));
        return Math.min(backoff, maxBackoff.toMillis());
    }

    /**
     * 创建 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 重试监听器
     */
    public interface RetryListener {
        void onRetry(String name, int attempt, Exception e);
        void onSuccess(String name, int attempts);
    }

    /**
     * Builder
     */
    public static class Builder {
        private int maxRetries = 3;
        private Duration initialBackoff = Duration.ofMillis(100);
        private double backoffMultiplier = 2.0;
        private Duration maxBackoff = Duration.ofSeconds(10);
        private Predicate<Throwable> retryPredicate = e -> true;
        private RetryListener listener = null;

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder initialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
            return this;
        }

        public Builder backoffMultiplier(double multiplier) {
            this.backoffMultiplier = multiplier;
            return this;
        }

        public Builder maxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
            return this;
        }

        public Builder retryOn(Predicate<Throwable> predicate) {
            this.retryPredicate = predicate;
            return this;
        }

        public Builder listener(RetryListener listener) {
            this.listener = listener;
            return this;
        }

        public RetryTemplate build() {
            return new RetryTemplate(this);
        }
    }
}
