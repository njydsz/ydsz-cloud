package com.njydsz.pmis.common.cache.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.internal.concurrent.StripedConcurrentCache;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

/**
 * Resilience4jCacheDecorator 熔断降级测试
 *
 * @author ydsz-pmis-team
 */
@DisplayName("Resilience4jCacheDecorator 熔断降级测试")
class Resilience4jCacheDecoratorTest {

  private Cache<String, String> newCache() {
    return new StripedConcurrentCache<>(100);
  }

  private CircuitBreaker fastTrippingCircuitBreaker() {
    CircuitBreakerConfig config =
        CircuitBreakerConfig.custom()
            .slidingWindowSize(4)
            .failureRateThreshold(50.0f)
            .waitDurationInOpenState(Duration.ofSeconds(1))
            .permittedNumberOfCallsInHalfOpenState(2)
            .build();
    return CircuitBreaker.of("test-cb", config);
  }

  @Nested
  @DisplayName("正常操作")
  class NormalOperations {

    @Test
    @DisplayName("put 和 getIfPresent 正常工作")
    void putAndGet() {
      CircuitBreaker cb = fastTrippingCircuitBreaker();
      Resilience4jCacheDecorator<String, String> cache =
          new Resilience4jCacheDecorator<>(newCache(), cb);

      cache.put("key1", "value1");

      assertThat(cache.getIfPresent("key1")).isEqualTo("value1");
      assertThat(cache.getCircuitBreakerState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("get with loader 正常工作")
    void getWithLoader() {
      Resilience4jCacheDecorator<String, String> cache =
          new Resilience4jCacheDecorator<>(newCache(), "test-loader");

      String result = cache.get("key1", k -> "loaded");

      assertThat(result).isEqualTo("loaded");
    }

    @Test
    @DisplayName("remove 正常工作")
    void remove() {
      Resilience4jCacheDecorator<String, String> cache =
          new Resilience4jCacheDecorator<>(newCache(), "test-remove");

      cache.put("key1", "value1");
      String removed = cache.remove("key1");

      assertThat(removed).isEqualTo("value1");
      assertThat(cache.getIfPresent("key1")).isNull();
    }

    @Test
    @DisplayName("containsKey 正常工作")
    void containsKey() {
      Resilience4jCacheDecorator<String, String> cache =
          new Resilience4jCacheDecorator<>(newCache(), "test-contains");

      cache.put("key1", "value1");

      assertThat(cache.containsKey("key1")).isTrue();
      assertThat(cache.containsKey("missing")).isFalse();
    }

    @Test
    @DisplayName("clear 正常工作")
    void clear() {
      Resilience4jCacheDecorator<String, String> cache =
          new Resilience4jCacheDecorator<>(newCache(), "test-clear");

      cache.put("key1", "value1");
      cache.put("key2", "value2");
      cache.clear();

      assertThat(cache.estimatedSize()).isZero();
    }

    @Test
    @DisplayName("computeIfAbsent 正常工作")
    void computeIfAbsent() {
      Resilience4jCacheDecorator<String, String> cache =
          new Resilience4jCacheDecorator<>(newCache(), "test-compute");

      String result = cache.computeIfAbsent("key1", k -> "computed");

      assertThat(result).isEqualTo("computed");
      assertThat(cache.getIfPresent("key1")).isEqualTo("computed");
    }
  }

  @Nested
  @DisplayName("熔断器状态")
  class CircuitBreakerStateTest {

    @Test
    @DisplayName("正常操作时熔断器保持关闭")
    void staysClosedOnSuccess() {
      CircuitBreaker cb = fastTrippingCircuitBreaker();
      Resilience4jCacheDecorator<String, String> cache =
          new Resilience4jCacheDecorator<>(newCache(), cb);

      for (int i = 0; i < 10; i++) {
        cache.put("key-" + i, "value-" + i);
        cache.getIfPresent("key-" + i);
      }

      assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
      assertThat(cb.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    @DisplayName("默认构造创建有效熔断器")
    void defaultCircuitBreakerCreated() {
      Resilience4jCacheDecorator<String, String> cache =
          new Resilience4jCacheDecorator<>(newCache(), "default-cb");

      assertThat(cache.getCircuitBreakerState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
  }

  @Nested
  @DisplayName("委托和透传")
  class DelegatePassthrough {

    @Test
    @DisplayName("getHitRate 透传到委托缓存")
    void getHitRate() {
      Cache<String, String> delegate = newCache();
      Resilience4jCacheDecorator<String, String> cache =
          new Resilience4jCacheDecorator<>(delegate, "test-hitrate");

      delegate.put("key1", "value1");
      delegate.getIfPresent("key1");
      delegate.getIfPresent("missing");

      assertThat(cache.getHitRate()).isEqualTo(delegate.getHitRate());
    }

    @Test
    @DisplayName("getDelegate 返回原始缓存")
    void getDelegate() {
      Cache<String, String> delegate = newCache();
      Resilience4jCacheDecorator<String, String> cache =
          new Resilience4jCacheDecorator<>(delegate, "test-delegate");

      assertThat(cache.getDelegate()).isSameAs(delegate);
    }

    @Test
    @DisplayName("批量操作正常透传")
    void batchOperations() {
      Resilience4jCacheDecorator<String, String> cache =
          new Resilience4jCacheDecorator<>(newCache(), "test-batch");

      cache.put("k1", "v1");
      cache.put("k2", "v2");

      assertThat(cache.estimatedSize()).isEqualTo(2);
      assertThat(cache.keySet()).containsExactlyInAnyOrder("k1", "k2");
      assertThat(cache.values()).containsExactlyInAnyOrder("v1", "v2");
    }
  }
}
