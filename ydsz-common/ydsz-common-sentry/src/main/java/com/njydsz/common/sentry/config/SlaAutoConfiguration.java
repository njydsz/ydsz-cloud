package com.njydsz.common.sentry.config;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.sentry.sla.DefaultSlaCollector;
import com.njydsz.common.sentry.sla.SlaMetricAspect;
import com.njydsz.common.sentry.spi.MetricsCollector;
import com.njydsz.common.sentry.spi.SlaCollector;

/**
 * SLA 指标采集自动配置。
 *
 * <p>装配 {@link DefaultSlaCollector} 与 {@link SlaMetricAspect}， 拦截标注了 SLA 注解的方法自动统计成功率与耗时。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(MetricsAutoConfiguration.class)
@EnableConfigurationProperties(SentryProperties.class)
public class SlaAutoConfiguration {

  /**
   * 装配 SLA 指标采集器，把可用率 / 成功率 / 耗时分位统一写入 {@link MetricsCollector}。
   *
   * @param metricsCollector 指标写出目标
   * @return SLA 采集器
   */
  @Bean
  @ConditionalOnMissingBean(SlaCollector.class)
  @ConditionalOnProperty(
      prefix = "ydsz.sentry.sla",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  /**
   * sla collector。
   * @param metricsCollector 参数
   * @return 结果
   */
  public SlaCollector slaCollector(MetricsCollector metricsCollector) {
    return new DefaultSlaCollector(metricsCollector);
  }

  /**
   * 装配 SLA 埋点切面，拦截标注了 SLA 注解的方法自动统计成功率与耗时。
   *
   * <p>切面运行在业务调用链路上，采集逻辑内部已做异常吞噬， 采集失败不会影响原方法返回值与异常传播。
   *
   * @param slaCollector SLA 采集器
   * @return SLA 埋点切面
   */
  @Bean
  @ConditionalOnMissingBean(SlaMetricAspect.class)
  @ConditionalOnProperty(
      prefix = "ydsz.sentry.sla",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  /**
   * sla metric aspect。
   * @param slaCollector 参数
   * @return 结果
   */
  public SlaMetricAspect slaMetricAspect(SlaCollector slaCollector) {
    return new SlaMetricAspect(slaCollector);
  }
}
