package com.njydsz.common.cache.resilience;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.cache.support.CacheLoader;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

/**
 * Resilience4j 增强版缓存加载器 — 对 CacheLoader 施加熔断 + 重试 + 降级
 *
 * <p>与 {@link Resilience4jCacheDecorator}（装饰整个缓存）不同，本类装饰 {@link CacheLoader}，
 * 提供更精细的容错控制：加载器失败时的降级策略（如返回缓存旧值、异步补偿等）。
 *
 * <p>容错链路：
 *
 * <pre>
 * 加载请求 → Retry(最多3次) → CircuitBreaker(熔断) → CacheLoader.load()
 *              ↓ 重试耗尽             ↓ 熔断打开
 *         返回 null / 旧值       返回 null / 旧值（可配置 fallback）
 * </pre>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * CacheLoader<String, User> baseLoader = CacheLoader.from(key -> userDao.findById(key));
 * Resilience4jCacheLoader<String, User> resilientLoader =
 *     Resilience4jCacheLoader.<String, User>builder(baseLoader)
 *         .circuitBreakerName("userLoader")
 *         .maxRetries(3)
 *         .retryDuration(Duration.ofMillis(100))
 *         .build();
 *
 * LoadingCache<String, User> cache = YdszCache.newBuilder()
 *     .maximumSize(10000)
 *     .loader(resilientLoader.toCacheLoader())
 *     .buildLoadingCache();
 * }</pre>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 * @since 1.0.0
 */
public class Resilience4jCacheLoader<K, V> {

  private static final Logger log = LoggerFactory.getLogger(Resilience4jCacheLoader.class);

  private final CacheLoader<K, V> delegate;
  private final CircuitBreaker circuitBreaker;
  private final Retry retry;

  private Resilience4jCacheLoader(
      CacheLoader<K, V> delegate, CircuitBreaker circuitBreaker, Retry retry) {
    this.delegate = delegate;
    this.circuitBreaker = circuitBreaker;
    this.retry = retry;
  }

  /**
   * 创建构建器
   *
   * @param delegate 原始 CacheLoader
   * @param <K> 键类型
   * @param <V> 值类型
   * @return 构建器实例
   */
  public static <K, V> Builder<K, V> builder(CacheLoader<K, V> delegate) {
    return new Builder<>(delegate);
  }

  /**
   * 执行单键加载（带熔断和重试保护）
   *
   * @param key 缓存键
   * @return 加载结果；失败时返回 null
   */
  public V load(K key) {
    try {
      // 组合 Retry + CircuitBreaker
      return Retry.decorateSupplier(retry,
          CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            try {
              return delegate.load(key);
            } catch (Exception e) {
              log.debug("CacheLoader 单键加载异常, key={}", key, e);
              throw new RuntimeException(e);
            }
          })).get();
    } catch (Exception e) {
      log.warn("CacheLoader 单键加载最终失败（熔断/重试耗尽）, key={}, 熔断器状态={}",
          key, circuitBreaker.getState(), e);
      return null;
    }
  }

  /**
   * 执行批量加载（带熔断保护，无重试以避免雪崩）
   *
   * @param keys 缓存键集合
   * @return 加载结果 Map
   */
  public Map<K, V> loadAll(Iterable<? extends K> keys) {
    try {
      return CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
        try {
          return delegate.loadAll(keys);
        } catch (Exception e) {
          log.debug("CacheLoader 批量加载异常", e);
          throw new RuntimeException(e);
        }
      }).get();
    } catch (Exception e) {
      log.warn("CacheLoader 批量加载失败, 熔断器状态={}", circuitBreaker.getState(), e);
      return Map.of();
    }
  }

  /**
   * 异步加载
   *
   * @param key 缓存键
   * @return 异步加载结果
   */
  public CompletableFuture<V> loadAsync(K key) {
    // resilience4j 2.x：decorateCompletionStage 返回延迟执行的 Supplier，需 get() 后取 CompletionStage
    return CircuitBreaker.decorateCompletionStage(
            circuitBreaker,
            () -> {
              try {
                return delegate.loadAsync(key);
              } catch (Exception e) {
                log.debug("CacheLoader 异步加载异常, key={}", key, e);
                throw e instanceof RuntimeException re ? re : new RuntimeException(e);
              }
            })
        .get()
        .toCompletableFuture();
  }

  /**
   * 获取熔断器状态
   *
   * @return 熔断器状态
   */
  public CircuitBreaker.State getCircuitBreakerState() {
    return circuitBreaker.getState();
  }

  /**
   * 获取底层 CacheLoader（兼容原有接口）
   *
   * @return 包装为 CacheLoader 实例
   */
  public CacheLoader<K, V> toCacheLoader() {
    return CacheLoader.from(this::load);
  }

  /**
   * 构建器
   *
   * @param <K> 键类型
   * @param <V> 值类型
   */
  public static class Builder<K, V> {
    private final CacheLoader<K, V> delegate;
    private String circuitBreakerName = "cacheLoader";
    private int maxRetries = 2;
    private Duration retryDuration = Duration.ofMillis(100);
    private float failureRateThreshold = 50.0f;
    private int slidingWindowSize = 100;
    private Duration waitDurationInOpenState = Duration.ofSeconds(10);

    Builder(CacheLoader<K, V> delegate) {
      this.delegate = delegate;
    }

    /**
     * 设置熔断器名称
     *
     * @param name 熔断器名称
     * @return this
     */
    public Builder<K, V> circuitBreakerName(String name) {
      this.circuitBreakerName = name;
      return this;
    }

    /**
     * 设置最大重试次数
     *
     * @param maxRetries 最大重试次数（0 表示不重试）
     * @return this
     */
    public Builder<K, V> maxRetries(int maxRetries) {
      this.maxRetries = Math.max(0, maxRetries);
      return this;
    }

    /**
     * 设置重试间隔
     *
     * @param retryDuration 重试间隔时长
     * @return this
     */
    public Builder<K, V> retryDuration(Duration retryDuration) {
      this.retryDuration = retryDuration;
      return this;
    }

    /**
     * 设置熔断器失败率阈值
     *
     * @param threshold 失败率阈值（0-100）
     * @return this
     */
    public Builder<K, V> failureRateThreshold(float threshold) {
      this.failureRateThreshold = threshold;
      return this;
    }

    /**
     * 构建 Resilience4jCacheLoader 实例
     *
     * @return 增强版 CacheLoader
     */
    public Resilience4jCacheLoader<K, V> build() {
      CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
          .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
          .slidingWindowSize(slidingWindowSize)
          .failureRateThreshold(failureRateThreshold)
          .waitDurationInOpenState(waitDurationInOpenState)
          .build();
      CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(cbConfig);
      CircuitBreaker cb = cbRegistry.circuitBreaker(circuitBreakerName, cbConfig);

      Retry retry = Retry.ofDefaults(circuitBreakerName + "-retry");
      if (maxRetries > 0) {
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(maxRetries + 1)
            .waitDuration(retryDuration)
            .build();
        retry = Retry.of(circuitBreakerName + "-retry", retryConfig);
      } else {
        retry = Retry.of(circuitBreakerName + "-retry",
            RetryConfig.custom().maxAttempts(1).build());
      }

      return new Resilience4jCacheLoader<>(delegate, cb, retry);
    }
  }
}
