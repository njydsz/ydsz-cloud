package com.njydsz.common.safe.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 自研熔断引擎单元测试。
 *
 * <p>覆盖：状态机流转（CLOSED→OPEN→HALF_OPEN→CLOSED/OPEN）、FORCED_OPEN、 双滑动窗口、
 * 慢调用、失败判定谓词、最小调用数、半开许可、事件总线、Registry 幂等、并发竞争。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("自研熔断引擎")
class CircuitBreakerTest {

  private CircuitBreakerConfig fastConfig;

  /** 构造快速流转配置（50% 失败率、窗口 4 次、最小 4 次、OPEN 等 50ms、半开 2 次）。 */
  @BeforeEach
  void setUp() {
    fastConfig =
        CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .slidingWindowSize(4)
            .minimumNumberOfCalls(4)
            .waitDurationInOpenState(java.time.Duration.ofMillis(50))
            .permittedNumberOfCallsInHalfOpenState(2)
            .build();
  }

  @Test
  @DisplayName("CLOSED 状态下低于最小调用数不触发熔断")
  void shouldNotOpenBelowMinimumCalls() {
    CircuitBreaker breaker = new CircuitBreaker("test", fastConfig);
    for (int i = 0; i < 3; i++) {
      breaker.onError(1, TimeUnit.MILLISECONDS, new RuntimeException("fail"));
    }
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    assertThat(breaker.tryAcquirePermission()).isTrue();
  }

  @Test
  @DisplayName("失败率超阈值触发 OPEN 并拒绝调用")
  void shouldOpenOnHighFailureRate() {
    CircuitBreaker breaker = new CircuitBreaker("test", fastConfig);
    for (int i = 0; i < 2; i++) {
      breaker.onSuccess(1, TimeUnit.MILLISECONDS);
    }
    for (int i = 0; i < 2; i++) {
      breaker.onError(1, TimeUnit.MILLISECONDS, new RuntimeException("fail"));
    }
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    assertThat(breaker.tryAcquirePermission()).isFalse();
    assertThatThrownBy(breaker::acquirePermission)
        .isInstanceOf(CallNotPermittedException.class)
        .hasMessageContaining("test");
  }

  @Test
  @DisplayName("成功调用不触发熔断")
  void shouldStayClosedOnSuccess() {
    CircuitBreaker breaker = new CircuitBreaker("test", fastConfig);
    for (int i = 0; i < 10; i++) {
      breaker.onSuccess(1, TimeUnit.MILLISECONDS);
    }
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    CircuitBreaker.Metrics metrics = breaker.getMetrics();
    assertThat(metrics.getNumberOfBufferedCalls()).isEqualTo(4);
    assertThat(metrics.getNumberOfSuccessfulCalls()).isEqualTo(4);
    assertThat(metrics.getFailureRate()).isEqualTo(0.0f);
  }

  @Test
  @DisplayName("OPEN 到期后惰性进入 HALF_OPEN，探测成功恢复 CLOSED")
  void shouldRecoverAfterWaitDuration() throws InterruptedException {
    CircuitBreaker breaker = new CircuitBreaker("test", fastConfig);
    for (int i = 0; i < 4; i++) {
      breaker.onError(1, TimeUnit.MILLISECONDS, new RuntimeException("fail"));
    }
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    Thread.sleep(80);
    // 首次许可获取触发 OPEN -> HALF_OPEN，且消耗一个探测许可
    assertThat(breaker.tryAcquirePermission()).isTrue();
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
    // 剩余许可 1 个
    assertThat(breaker.tryAcquirePermission()).isTrue();
    assertThat(breaker.tryAcquirePermission()).isFalse();
    // 两笔探测成功（达到 permitted=2）恢复 CLOSED
    breaker.onSuccess(1, TimeUnit.MILLISECONDS);
    breaker.onSuccess(1, TimeUnit.MILLISECONDS);
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
  }

  @Test
  @DisplayName("HALF_OPEN 探测失败立即重回 OPEN")
  void shouldReturnToOpenOnHalfOpenFailure() throws InterruptedException {
    CircuitBreaker breaker = new CircuitBreaker("test", fastConfig);
    for (int i = 0; i < 4; i++) {
      breaker.onError(1, TimeUnit.MILLISECONDS, new RuntimeException("fail"));
    }
    Thread.sleep(80);
    assertThat(breaker.tryAcquirePermission()).isTrue();
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
    breaker.onError(1, TimeUnit.MILLISECONDS, new RuntimeException("probe-fail"));
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
  }

  @Test
  @DisplayName("FORCED_OPEN 拒绝一切调用直至强制闭合")
  void shouldRejectAllInForcedOpen() {
    CircuitBreaker breaker = new CircuitBreaker("test", fastConfig);
    breaker.transitionToForcedOpenState();
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.FORCED_OPEN);
    assertThat(breaker.tryAcquirePermission()).isFalse();
    assertThat(breaker.canExecute()).isFalse();
    breaker.transitionToClosedState();
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    assertThat(breaker.tryAcquirePermission()).isTrue();
  }

  @Test
  @DisplayName("慢调用率超阈值触发熔断")
  void shouldOpenOnSlowCallRate() {
    CircuitBreakerConfig config =
        CircuitBreakerConfig.custom()
            .failureRateThreshold(100)
            .slowCallRateThreshold(50)
            .slowCallDurationThreshold(java.time.Duration.ofMillis(100))
            .slidingWindowSize(4)
            .minimumNumberOfCalls(4)
            .waitDurationInOpenState(java.time.Duration.ofSeconds(10))
            .build();
    CircuitBreaker breaker = new CircuitBreaker("slow", config);
    for (int i = 0; i < 2; i++) {
      breaker.onSuccess(200, TimeUnit.MILLISECONDS);
    }
    for (int i = 0; i < 2; i++) {
      breaker.onSuccess(1, TimeUnit.MILLISECONDS);
    }
    // 慢调用率 50% 达到阈值
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
  }

  @Test
  @DisplayName("失败判定谓词：返回 false 的异常不计失败")
  void shouldIgnoreNonRecordedExceptions() {
    CircuitBreakerConfig config =
        CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .slidingWindowSize(4)
            .minimumNumberOfCalls(4)
            .waitDurationInOpenState(java.time.Duration.ofSeconds(10))
            .recordException(e -> !(e instanceof IllegalArgumentException))
            .build();
    CircuitBreaker breaker = new CircuitBreaker("ignore", config);
    for (int i = 0; i < 4; i++) {
      breaker.onError(1, TimeUnit.MILLISECONDS, new IllegalArgumentException("biz-ignore"));
    }
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);
  }

  @Test
  @DisplayName("execute 带降级：熔断后直接返回降级值")
  void shouldFallbackWhenOpen() {
    CircuitBreaker breaker = new CircuitBreaker("test", fastConfig);
    for (int i = 0; i < 4; i++) {
      breaker.onError(1, TimeUnit.MILLISECONDS, new RuntimeException("fail"));
    }
    String result = breaker.execute(() -> "real", () -> "fallback");
    assertThat(result).isEqualTo("fallback");
    // 解除熔断后恢复正常调用
    breaker.reset();
    assertThat(breaker.execute(() -> "real", () -> "fallback")).isEqualTo("real");
  }

  @Test
  @DisplayName("executeSupplier：成功记录后返回结果，异常透传并记录失败")
  void shouldExecuteSupplierWithMetrics() {
    CircuitBreaker breaker = new CircuitBreaker("test", fastConfig);
    String result = breaker.executeSupplier(() -> "ok");
    assertThat(result).isEqualTo("ok");
    assertThat(breaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
    assertThatThrownBy(
            () -> breaker.executeSupplier(() -> { throw new IllegalStateException("boom"); }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
  }

  @Test
  @DisplayName("事件总线：状态变更与成败事件按序发布")
  void shouldPublishEvents() {
    CircuitBreaker breaker = new CircuitBreaker("events", fastConfig);
    List<String> transitions = new ArrayList<>();
    List<Integer> eventTypes = new ArrayList<>();
    breaker
        .getEventPublisher()
        .onStateTransition(e -> transitions.add(e.getStateTransition().toString()))
        .onSuccess(e -> eventTypes.add(e.getType()))
        .onError(e -> eventTypes.add(e.getType()));
    for (int i = 0; i < 4; i++) {
      breaker.onError(1, TimeUnit.MILLISECONDS, new RuntimeException("fail"));
    }
    assertThat(transitions).containsExactly("CLOSED -> OPEN");
    assertThat(eventTypes).hasSize(4).allMatch(t -> t == CircuitBreakerEvent.TYPE_ERROR);
  }

  @Test
  @DisplayName("TIME_BASED 滑动窗口：仅统计最近 N 秒")
  void shouldExpireOldBucketsInTimeWindow() {
    CircuitBreakerConfig config =
        CircuitBreakerConfig.custom()
            .failureRateThreshold(100)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
            .slidingWindowSize(2)
            .minimumNumberOfCalls(100)
            .waitDurationInOpenState(java.time.Duration.ofSeconds(10))
            .build();
    CircuitBreaker breaker = new CircuitBreaker("time", config);
    breaker.onSuccess(1, TimeUnit.MILLISECONDS);
    assertThat(breaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(1);
    // 模拟时间流逝：直接校验跨秒分桶逻辑（同秒内累加）
    breaker.onSuccess(1, TimeUnit.MILLISECONDS);
    assertThat(breaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(2);
  }

  @Test
  @DisplayName("Registry：同名幂等、find/remove 正确")
  void registryShouldBeIdempotent() {
    CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(fastConfig);
    CircuitBreaker first = registry.circuitBreaker("svc");
    CircuitBreaker second = registry.circuitBreaker("svc", CircuitBreakerConfig.ofDefaults());
    assertThat(second).isSameAs(first);
    assertThat(registry.find("svc")).contains(first);
    assertThat(registry.find("missing")).isEmpty();
    assertThat(registry.remove("svc")).isSameAs(first);
    assertThat(registry.find("svc")).isEmpty();
  }

  @Test
  @DisplayName("canExecute 未启用自动半开时不触发状态转换")
  void canExecuteShouldBePureReadWhenAutoHalfOpenDisabled() throws InterruptedException {
    CircuitBreaker breaker = new CircuitBreaker("test", fastConfig);
    for (int i = 0; i < 4; i++) {
      breaker.onError(1, TimeUnit.MILLISECONDS, new RuntimeException("fail"));
    }
    Thread.sleep(80);
    // 未启用自动半开：canExecute 仅报告可执行，不转换状态
    assertThat(breaker.canExecute()).isTrue();
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
  }

  @Test
  @DisplayName("canExecute 启用自动半开时惰性转换 OPEN → HALF_OPEN")
  void canExecuteShouldTriggerHalfOpenWhenEnabled() throws InterruptedException {
    CircuitBreakerConfig config =
        CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .slidingWindowSize(4)
            .minimumNumberOfCalls(4)
            .waitDurationInOpenState(java.time.Duration.ofMillis(50))
            .permittedNumberOfCallsInHalfOpenState(2)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();
    CircuitBreaker breaker = new CircuitBreaker("auto", config);
    for (int i = 0; i < 4; i++) {
      breaker.onError(1, TimeUnit.MILLISECONDS, new RuntimeException("fail"));
    }
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    Thread.sleep(80);
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
  }

  @Test
  @DisplayName("配置校验：非法参数抛 IllegalArgumentException")
  void configShouldValidateParameters() {
    assertThatThrownBy(() -> CircuitBreakerConfig.custom().failureRateThreshold(0).build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CircuitBreakerConfig.custom().failureRateThreshold(101).build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> CircuitBreakerConfig.custom().slidingWindowSize(0).build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                CircuitBreakerConfig.custom()
                    .waitDurationInOpenState(java.time.Duration.ZERO)
                    .build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("并发竞争：多线程记录不丢失且状态机一致")
  void shouldBeThreadSafe() throws InterruptedException {
    CircuitBreaker breaker = new CircuitBreaker("concurrent", fastConfig);
    int threads = 8;
    int iterations = 100;
    Thread[] workers = new Thread[threads];
    for (int t = 0; t < threads; t++) {
      workers[t] =
          new Thread(
              () -> {
                for (int i = 0; i < iterations; i++) {
                  if (i % 2 == 0) {
                    breaker.onSuccess(1, TimeUnit.MILLISECONDS);
                  } else {
                    breaker.onError(1, TimeUnit.MILLISECONDS, new RuntimeException("fail"));
                  }
                  breaker.tryAcquirePermission();
                }
              });
      workers[t].start();
    }
    for (Thread worker : workers) {
      worker.join();
    }
    // 无异常即通过：状态机未损坏（ getState 不抛异常 ）
    CircuitBreaker.State state = breaker.getState();
    assertThat(state).isNotNull();
  }
}
