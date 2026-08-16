package com.njydsz.common.redis.metrics;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import io.micrometer.core.instrument.MeterRegistry;

import com.njydsz.common.redis.config.RedisProperties;

/**
 * Redis 指标采集配置。
 *
 * <p>注册 Redis 连接池、命令执行、慢查询、键空间等 Micrometer 指标。
 *
 * <p>通过 Lettuce Client Resources 注入 MeterRegistry 采集。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
public class RedisMetricsConfiguration {

  /**
   * 创建 Redis 指标收集器 Bean
   *
   * @param registry MeterRegistry 实例（由 Spring Boot 自动配置提供）
   * @return RedisMetricsCollector 实例
   */
  @Bean
  public RedisMetricsCollector redisMetricsCollector(
      MeterRegistry registry, RedisProperties redisProperties) {
    long threshold =
        redisProperties.getMetrics() != null
            ? redisProperties.getMetrics().getSlowOperationThresholdMs()
            : 0;
    return RedisMetricsCollector.getOrCreate(registry, threshold);
  }
}
