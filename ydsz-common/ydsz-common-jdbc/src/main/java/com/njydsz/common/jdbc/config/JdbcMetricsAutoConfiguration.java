package com.njydsz.common.jdbc.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.njydsz.common.jdbc.metrics.HikariMetricsReporter;
import com.njydsz.common.sentry.spi.AlertPublisher;

/**
 * JDBC 模块 HikariCP 指标采集自动配置。
 *
 * <p>配置触发条件：
 *
 * <ul>
 *   <li>MeterRegistry Bean 存在（actuator + micrometer 已引入）
 *   <li>AlertPublisher Bean 存在（ydsz-common-sentry 已引入）
 *   <li>{@code ydzs.jdbc.metrics.enabled=true}（默认 true）
 * </ul>
 *
 * <p>不满足条件时（如业务模块未引入 sentry/actuator），自动跳过，零侵入。
 *
 * @author ydsz
 * @since 26.10.06
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean({MeterRegistry.class, AlertPublisher.class})
@ConditionalOnProperty(
    prefix = "ydsz.jdbc.metrics",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableScheduling
public class JdbcMetricsAutoConfiguration {

  /**
   * 注册 HikariCP 指标采集 Bean。
   *
   * @param meterRegistry Micrometer 指标注册中心
   * @param alertPublisher 告警发布器（来自 ydzs-common-sentry）
   * @return HikariMetricsReporter 实例
   */
  @Bean
  public HikariMetricsReporter hikariMetricsReporter(
      MeterRegistry meterRegistry, AlertPublisher alertPublisher) {
    return new HikariMetricsReporter(meterRegistry, alertPublisher);
  }
}
