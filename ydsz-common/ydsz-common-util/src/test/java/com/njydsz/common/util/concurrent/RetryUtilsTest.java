package com.njydsz.common.util.concurrent;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RetryUtils 重试工具测试。
 *
 * <p>覆盖：首次成功不重试、失败后重试成功、重试耗尽异常语义、
 * retryOn 条件短路、指数退避回调、参数校验与中断处理。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
class RetryUtilsTest {

  /** 测试用零延迟（固定间隔场景） */
  private static final Duration ZERO_DELAY = Duration.ZERO;

  /** 退避测试用初始延迟（毫秒） */
  private static final Duration TINY_INITIAL_DELAY = Duration.ofMillis(1);

  /** 退避测试用最大延迟 */
  private static final Duration TINY_MAX_DELAY = Duration.ofMillis(5);

  @Test
  @DisplayName("首次成功：不触发任何重试")
  void successOnFirstAttempt() {
    AtomicInteger attempts = new AtomicInteger();

    String result =
        RetryUtils.executeWithRetry(
            () -> {
              attempts.incrementAndGet();
              return "ok";
            },
            3,
            ZERO_DELAY);

    assertThat(result).isEqualTo("ok");
    assertThat(attempts.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("失败后重试成功：返回成功值且执行次数正确")
  void retriesThenSucceeds() {
    AtomicInteger attempts = new AtomicInteger();

    String result =
        RetryUtils.executeWithRetry(
            () -> {
              if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("transient failure");
              }
              return "recovered";
            },
            3,
            ZERO_DELAY);

    assertThat(result).isEqualTo("recovered");
    assertThat(attempts.get()).isEqualTo(3);
  }

  @Test
  @DisplayName("重试耗尽：抛 RetryException 并包装最后一次异常")
  void exhaustedThrowsWithLastCause() {
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(
            () ->
                RetryUtils.executeWithRetry(
                    () -> {
                      attempts.incrementAndGet();
                      throw new IllegalStateException("always fails");
                    },
                    2,
                    ZERO_DELAY))
        .isInstanceOf(RetryException.class)
        .hasMessageContaining("3 attempts")
        .hasCauseInstanceOf(IllegalStateException.class)
        .satisfies(e -> assertThat(e.getCause()).hasMessageContaining("always fails"));

    assertThat(attempts.get()).as("总执行次数 = 首次 + 重试次数").isEqualTo(3);
  }

  @Test
  @DisplayName("retryOn 不匹配：异常直接抛出，不消耗重试次数")
  void nonMatchingExceptionSkipsRetry() {
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(
            () ->
                RetryUtils.executeWithRetry(
                    () -> {
                      attempts.incrementAndGet();
                      throw new IllegalArgumentException("not retryable");
                    },
                    3,
                    ZERO_DELAY,
                    e -> e instanceof IllegalStateException))
        .isInstanceOf(RetryException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class);

    assertThat(attempts.get()).as("不匹配的异常应立即终止").isEqualTo(1);
  }

  @Test
  @DisplayName("指数退避：失败后按退避策略重试成功，onRetry 回调触发")
  void backoffRetriesWithCallback() {
    AtomicInteger attempts = new AtomicInteger();
    AtomicInteger callbacks = new AtomicInteger();

    String result =
        RetryUtils.executeWithBackoff(
            () -> {
              if (attempts.incrementAndGet() < 2) {
                throw new IllegalStateException("backoff failure");
              }
              return "backoff-ok";
            },
            RetryUtils.RetryConfig.builder()
                .maxRetries(3)
                .initialDelay(TINY_INITIAL_DELAY)
                .maxDelay(TINY_MAX_DELAY)
                .retryOn(e -> e instanceof IllegalStateException)
                .onRetry(attempt -> callbacks.incrementAndGet())
                .build());

    assertThat(result).isEqualTo("backoff-ok");
    assertThat(attempts.get()).isEqualTo(2);
    assertThat(callbacks.get()).as("每次重试前应触发一次回调").isEqualTo(1);
  }

  @Test
  @DisplayName("指数退避耗尽：抛 RetryException")
  void backoffExhausted() {
    assertThatThrownBy(
            () ->
                RetryUtils.executeWithBackoff(
                    () -> {
                      throw new IllegalStateException("persistent");
                    },
                    RetryUtils.RetryConfig.builder()
                        .maxRetries(2)
                        .initialDelay(TINY_INITIAL_DELAY)
                        .maxDelay(TINY_MAX_DELAY)
                        .build()))
        .isInstanceOf(RetryException.class)
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("中断处理：action 抛 InterruptedException 时恢复中断标志并快速失败")
  void interruptionFailsFast() {
    try {
      assertThatThrownBy(
              () ->
                  RetryUtils.executeWithRetry(
                      () -> {
                        throw new InterruptedException("interrupted");
                      },
                      3,
                      ZERO_DELAY))
          .isInstanceOf(RetryException.class)
          .hasMessageContaining("interrupted");
      assertThat(Thread.interrupted())
          .as("中断标志应被恢复（并在此消费以免污染后续用例）")
          .isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  @DisplayName("参数校验：null action / 负延迟 / null config 均拒绝")
  void parameterValidation() {
    assertThatThrownBy(() -> RetryUtils.executeWithRetry(null, 3, ZERO_DELAY))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> RetryUtils.executeWithRetry(() -> "x", 3, Duration.ofMillis(-1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RetryUtils.executeWithBackoff(() -> "x", null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
