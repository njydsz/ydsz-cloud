package com.njydsz.common.util.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link RetrySupport} 单元测试 — 覆盖指数退避/固定间隔/抖动/重试谓词/异步重试等关键路径。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("RetrySupport 重试工具测试")
class RetrySupportTest {

    @Test
    @DisplayName("首次执行成功不触发重试")
    void successOnFirstAttempt() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        String result = RetrySupport.withFixedInterval(3, 10)
                .execute(() -> {
                    counter.incrementAndGet();
                    return "ok";
                });
        assertThat(result).isEqualTo("ok");
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("异常重试 — 第 3 次成功，共执行 3 次")
    void retryOnExceptionAndSucceedAtThirdAttempt() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        String result = RetrySupport.withFixedInterval(3, 10)
                .retryOn(e -> e instanceof IllegalStateException)
                .execute(() -> {
                    int attempt = counter.incrementAndGet();
                    if (attempt < 3) {
                        throw new IllegalStateException("transient error");
                    }
                    return "ok-" + attempt;
                });
        assertThat(result).isEqualTo("ok-3");
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("重试次数耗尽抛出最后异常")
    void retryExhaustedThrowsLastException() {
        AtomicInteger counter = new AtomicInteger();
        assertThatThrownBy(() ->
                RetrySupport.withFixedInterval(2, 10)
                        .retryOn(e -> e instanceof IllegalStateException)
                        .execute(() -> {
                            counter.incrementAndGet();
                            throw new IllegalStateException("always fail");
                        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("always fail");
        assertThat(counter.get()).isEqualTo(3); // 1 首次 + 2 重试
    }

    @Test
    @DisplayName("不可重试异常直接抛出不重试")
    void nonRetryableExceptionThrowsImmediately() {
        AtomicInteger counter = new AtomicInteger();
        assertThatThrownBy(() ->
                RetrySupport.withFixedInterval(3, 10)
                        .retryOn(e -> e instanceof java.io.IOException)
                        .execute(() -> {
                            counter.incrementAndGet();
                            throw new IllegalStateException("not retryable");
                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("retryIfResult — 结果谓词触发重试直到满足条件")
    void retryIfResultTriggersRetry() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        Predicate<Object> isNull = r -> r == null;
        String result = RetrySupport.withFixedInterval(5, 10)
                .retryIfResult(isNull)
                .execute(() -> {
                    int attempt = counter.incrementAndGet();
                    return attempt < 3 ? null : "got-it";
                });
        assertThat(result).isEqualTo("got-it");
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("指数退避 — 实际延迟随次数翻倍")
    void exponentialBackoffDelaysGrowExponentially() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        long start = System.nanoTime();
        RetrySupport.withExponentialBackoff(3, 20L, 1000L)
                .retryOn(e -> true)
                .execute(() -> {
                    counter.incrementAndGet();
                    throw new RuntimeException("force retry");
                });
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        // 3 次重试，延迟 = 20 + 40 + 80 = 140ms（误差容忍 ±80ms）
        assertThat(counter.get()).isEqualTo(4);
        assertThat(elapsedMs).isBetween(80L, 400L);
    }

    @Test
    @DisplayName("抖动因子启用后仍能正常重试")
    void withJitterShouldStillRetry() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        String result = RetrySupport.withExponentialBackoff(3, 10L, 200L)
                .withJitter()
                .retryOn(e -> e instanceof IllegalStateException)
                .execute(() -> {
                    int attempt = counter.incrementAndGet();
                    if (attempt < 2) {
                        throw new IllegalStateException("fail");
                    }
                    return "success";
                });
        assertThat(result).isEqualTo("success");
        assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("重试回调每次都被触发")
    void onRetryCallbackShouldBeInvoked() throws Exception {
        AtomicInteger retryCount = new AtomicInteger();
        AtomicInteger execCount = new AtomicInteger();
        RetrySupport.withFixedInterval(2, 5)
                .retryOn(e -> true)
                .onRetry(event -> retryCount.incrementAndGet())
                .execute(() -> {
                    execCount.incrementAndGet();
                    if (execCount.get() < 3) {
                        throw new RuntimeException("retry me");
                    }
                    return "done";
                });
        assertThat(execCount.get()).isEqualTo(3);
        assertThat(retryCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("异步重试 — CompletableFuture 正常完成")
    void asyncRetryShouldCompleteFuture() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        CompletableFuture<String> future = RetrySupport.withFixedInterval(3, 10)
                .retryOn(e -> e instanceof IllegalStateException)
                .executeAsync(() -> {
                    int attempt = counter.incrementAndGet();
                    if (attempt < 2) {
                        throw new IllegalStateException("transient");
                    }
                    return "async-ok";
                });
        String result = future.get(2, TimeUnit.SECONDS);
        assertThat(result).isEqualTo("async-ok");
        assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("异步重试 — 重试耗尽 future 异常完成")
    void asyncRetryExhaustedShouldCompleteExceptionally() {
        AtomicInteger counter = new AtomicInteger();
        CompletableFuture<String> future = RetrySupport.withFixedInterval(2, 10)
                .retryOn(e -> true)
                .executeAsync(() -> {
                    counter.incrementAndGet();
                    throw new RuntimeException("always fail");
                });
        assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("always fail");
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("null 任务抛 IllegalArgumentException")
    void nullTaskShouldThrow() {
        assertThatThrownBy(() -> RetrySupport.withFixedInterval(1, 1).execute((java.util.concurrent.Callable<String>) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Task cannot be null");
    }

    @Test
    @DisplayName("TimeoutException 是 checked exception，不捕获时透传")
    void checkedExceptionPropagation() {
        assertThatThrownBy(() ->
                RetrySupport.withFixedInterval(1, 1)
                        .retryOn(e -> false) // 不重试
                        .execute(() -> {
                            throw new TimeoutException();
                        }))
                .isInstanceOf(TimeoutException.class);
    }
}
