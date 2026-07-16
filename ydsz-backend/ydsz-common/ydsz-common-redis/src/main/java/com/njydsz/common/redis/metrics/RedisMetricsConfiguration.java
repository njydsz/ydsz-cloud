package com.njydsz.common.redis.metrics;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.redis.config.RedisProperties;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Redis 指标自动配置
 *
 * <p>当 classpath 中存在 Micrometer 时，自动创建 Redis 指标收集器。
 * 指标采集是可选的，仅在实际使用时产生开销。
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
    public RedisMetricsCollector redisMetricsCollector(MeterRegistry registry,
                                                        RedisProperties redisProperties) {
        long threshold = redisProperties.getMetrics() != null
                ? redisProperties.getMetrics().getSlowOperationThresholdMs()
                : 0;
        return RedisMetricsCollector.getOrCreate(registry, threshold);
    }
}
