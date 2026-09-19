package com.njydsz.common.feign.ratelimiter;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.feign.exception.OpenFeignException;

/**
 * Feign Resilience4j 请求限流拦截器。
 *
 * <p>基于 Resilience4j RateLimiter 实现按服务维度的 QPS 限流。 当服务的 QPS 超过配置阈值时，快速失败并抛出 {@link OpenFeignException}。
 *
 * <p>与 Bulkhead 的区别：Bulkhead 限制并发数，RateLimiter 限制 QPS（每秒请求数），两者可叠加使用。
 *
 * <p><b>配置示例（YAML）：</b>
 *
 * <pre>
 * ydsz:
 *   feign:
 *     rate-limiter:
 *       enabled: true
 *       default-limit-for-period: 100
 *       limit-refresh-period-ms: 1000
 *       timeout-duration-ms: 5000
 *       service-limit-for-period:
 *         ydzs-message: 50
 *         ydzs-literule: 200
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public class FeignRateLimiterInterceptor implements RequestInterceptor {

  private static final Logger LOG = LoggerFactory.getLogger(FeignRateLimiterInterceptor.class);

  private final RateLimiterRegistry rateLimiterRegistry;
  private final Duration timeoutDuration;
  private final ConcurrentHashMap<String, RateLimiter> rateLimiterCache = new ConcurrentHashMap<>();

  /**
   * 构造 Feign RateLimiter 拦截器。
   *
   * @param rateLimiterRegistry Resilience4j RateLimiter 注册表
   * @param timeoutDuration 获取许可的超时时间
   */
  public FeignRateLimiterInterceptor(RateLimiterRegistry rateLimiterRegistry, Duration timeoutDuration) {
    this.rateLimiterRegistry = rateLimiterRegistry;
    this.timeoutDuration = timeoutDuration;
  }

  /**
   * 使用默认超时（5 秒）构造 RateLimiter 拦截器。
   *
   * @param rateLimiterRegistry Resilience4j RateLimiter 注册表
   */
  public FeignRateLimiterInterceptor(RateLimiterRegistry rateLimiterRegistry) {
    this(rateLimiterRegistry, Duration.ofSeconds(5));
  }

  @Override
  public void apply(RequestTemplate requestTemplate) {
    String serviceName = extractServiceName(requestTemplate);
    RateLimiter limiter = getOrCreateRateLimiter(serviceName);

    boolean acquired = false;
    try {
      acquired = limiter.acquirePermission();
    } catch (Exception e) {
      LOG.warn("[FeignRateLimiter] 获取限流许可异常 | service={} | error={}", serviceName, e.getMessage());
      throw new OpenFeignException(
          "RATE_LIMIT_ERROR",
          "Rate limiter error for service: " + serviceName + ", error: " + e.getMessage());
    }

    if (!acquired) {
      LOG.warn("[FeignRateLimiter] QPS 超限 | service={}", serviceName);
      throw new OpenFeignException(
          "RATE_LIMIT_EXCEEDED",
          "Rate limit exceeded for service: " + serviceName);
    }
  }

  /**
   * 获取或创建服务级别的 RateLimiter 实例。
   *
   * @param serviceName 服务名称
   * @return RateLimiter 实例
   */
  private RateLimiter getOrCreateRateLimiter(String serviceName) {
    return rateLimiterCache.computeIfAbsent(
        serviceName,
        key -> {
          RateLimiter limiter = rateLimiterRegistry.rateLimiter(key);
          LOG.debug("[FeignRateLimiter] 创建 RateLimiter | service={}", key);
          return limiter;
        });
  }

  /**
   * 从 RequestTemplate 提取服务名称。
   *
   * @param requestTemplate Feign 请求模板
   * @return 服务名称
   */
  private String extractServiceName(RequestTemplate requestTemplate) {
    try {
      if (requestTemplate.feignTarget() != null) {
        return requestTemplate.feignTarget().name();
      }
      String url = requestTemplate.url();
      if (url != null && url.startsWith("http")) {
        return java.net.URI.create(url).getHost();
      }
      return "default";
    } catch (Exception e) {
      return "default";
    }
  }
}
