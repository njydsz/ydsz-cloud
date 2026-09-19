package com.njydsz.common.auth.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import com.njydsz.common.auth.health.AuthHealthIndicator;
import com.njydsz.common.auth.metrics.AuthMetricsCollector;

/**
 * 健康检查与指标采集配置。
 *
 * <p>负责装配可观测性相关的 Bean：
 *
 * <ul>
 *   <li>{@link AuthMetricsCollector}（Micrometer 指标采集，需 MeterRegistry 在 classpath）
 *   <li>{@link AuthHealthIndicator}（Redis 健康检查，需 RedisConnectionFactory）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.18
 */
public class HealthMetricsConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(HealthMetricsConfiguration.class);

  /**
   * 创建权限模块 Micrometer 指标采集器 Bean。
   *
   * <p>仅当 MeterRegistry 可用时才装配，否则保持向后兼容（不采集指标）。
   *
   * @param meterRegistryProvider Micrometer 指标注册中心提供者（可选）
   * @return AuthMetricsCollector 实例
   */
  @Bean
  @ConditionalOnMissingBean(AuthMetricsCollector.class)
  @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
  @ConditionalOnBean(type = "io.micrometer.core.instrument.MeterRegistry")
  public AuthMetricsCollector authMetricsCollector(
      ObjectProvider<MeterRegistry> meterRegistryProvider) {
    return new AuthMetricsCollector(meterRegistryProvider.getIfAvailable());
  }

  /**
   * 创建权限模块健康检查指示器 Bean。
   *
   * @param redisConnectionFactory Redis 连接工厂
   * @return AuthHealthIndicator 实例
   */
  @Bean
  @ConditionalOnMissingBean(AuthHealthIndicator.class)
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
  @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
  // CHECKSTYLE.ON: RegexpSinglelineJava
  @ConditionalOnBean(RedisConnectionFactory.class)
  public AuthHealthIndicator authHealthIndicator(RedisConnectionFactory redisConnectionFactory) {
    return new AuthHealthIndicator(redisConnectionFactory);
  }
}
