package com.njydsz.common.feign.actuator;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.ToString;

import com.njydsz.common.feign.config.FeignProperties;


/**
 * Feign 模块健康状态快照。
 *
 * <p>提供 Feign 模块自身健康状态的数据结构，不依赖 Spring Boot Actuator API。 上层 web 包可将此快照适配为 {@code HealthIndicator}
 * 或直接暴露为监控指标。
 *
 * <p><b>设计原则：</b>feign 作为 L5 基础模块不直接依赖 Actuator（web 层组件）；通过解耦设计，允许上层按需桥接到 Actuator。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
@Getter
@ToString
public class FeignHealthSnapshot {

  /** 模块是否启用 */
  private final boolean isEnabled;

  /** 各子功能状态详情 */
  private final Map<String, Object> details;

  /** 总体状态：up / down */
  private final String status;

  private FeignHealthSnapshot(boolean isEnabled, Map<String, Object> details, String status) {
    this.isEnabled = isEnabled;
    this.details = details;
    this.status = status;
  }

  /**
   * 根据当前配置构建 Feign 健康状态快照。
   *
   * @param feignProperties Feign 配置属性
   * @return FeignHealthSnapshot 实例
   */
  public static FeignHealthSnapshot from(FeignProperties feignProperties) {
    if (feignProperties == null || !feignProperties.isEnabled()) {
      return new FeignHealthSnapshot(false, new LinkedHashMap<>(), "DOWN");
    }

    Map<String, Object> details = new LinkedHashMap<>();
    details.put("enabled", true);

    // 熔断器状态
    if (feignProperties.getCircuitBreaker() != null) {
      details.put(
          "circuitBreaker.enabled", feignProperties.getCircuitBreaker().isEnabled());
      details.put(
          "circuitBreaker.failureRateThreshold",
          feignProperties.getCircuitBreaker().getFailureRateThreshold());
    }

    // 隔离状态
    if (feignProperties.getBulkhead() != null) {
      details.put("bulkhead.enabled", feignProperties.getBulkhead().isEnabled());
      if (feignProperties.getBulkhead().isEnabled()) {
        details.put(
            "bulkhead.defaultMaxConcurrent",
            feignProperties.getBulkhead().getDefaultMaxConcurrent());
      }
    }

    // 限流状态
    if (feignProperties.getRateLimiter() != null) {
      details.put("rateLimiter.enabled", feignProperties.getRateLimiter().isEnabled());
      if (feignProperties.getRateLimiter().isEnabled()) {
        details.put(
            "rateLimiter.defaultLimitForPeriod",
            feignProperties.getRateLimiter().getDefaultLimitForPeriod());
      }
    }

    // 压缩状态
    if (feignProperties.getCompress() != null) {
      details.put("compress.enabled", feignProperties.getCompress().isEnabled());
    }

    // 重试状态
    if (feignProperties.getRetry() != null) {
      details.put("retry.enabled", feignProperties.getRetry().isEnabled());
      if (feignProperties.getRetry().isEnabled()) {
        details.put("retry.maxAttempts", feignProperties.getRetry().getMaxAttempts());
      }
    }

    return new FeignHealthSnapshot(true, details, "UP");
  }
}
