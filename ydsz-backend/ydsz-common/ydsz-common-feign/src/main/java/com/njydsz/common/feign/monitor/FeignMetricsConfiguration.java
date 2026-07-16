package com.njydsz.common.feign.monitor;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Feign 指标自动配置。
 *
 * <p>当 classpath 中存在 Micrometer 时，自动创建 Feign Micrometer 指标收集器单例。
 * 指标采集是可选的，仅在实际使用时产生开销。
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
     * <p>使用 {@link FeignMicrometerCollector#getInstance(MeterRegistry)} 确保全局唯一实例，
     * 避免 {@link FeignResponseMetricsAdapter} 等组件重复创建。
     *
     * @param registry MeterRegistry 实例（由 Spring Boot 自动配置提供）
     * @return FeignMicrometerCollector 单例
     */
    @Bean
    public FeignMicrometerCollector feignMicrometerCollector(MeterRegistry registry) {
        return FeignMicrometerCollector.getInstance(registry);
    }
}
