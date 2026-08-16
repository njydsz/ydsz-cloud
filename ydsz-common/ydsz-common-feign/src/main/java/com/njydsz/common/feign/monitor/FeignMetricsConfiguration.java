package com.njydsz.common.feign.monitor;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Feign 指标采集配置。
 *
 * <p>注册 Feign 调用的 Micrometer 指标：请求计数、延迟直方图、错误计数、熔断状态。
 *
 * <p>通过 {@code ydsz.feign.metrics.enabled=false} 可关闭以降低指标基数。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
public class FeignMetricsConfiguration {

  /**
   * 创建 Feign 指标收集器单例 Bean。
   *
   * <p>使用 {@link FeignMicrometerCollector#getInstance(MeterRegistry)} 确保全局唯一实例， 避免 {@link
   * FeignResponseMetricsAdapter} 等组件重复创建。
   *
   * @param registry MeterRegistry 实例（由 Spring Boot 自动配置提供）
   * @return FeignMicrometerCollector 单例
   */
  @Bean
  public FeignMicrometerCollector feignMicrometerCollector(MeterRegistry registry) {
    return FeignMicrometerCollector.getInstance(registry);
  }
}
