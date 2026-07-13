package com.njydsz.pmis.common.feign.monitor;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Feign 指标自动配置
 *
 * <p>当 classpath 中存在 Micrometer 时，自动创建 Feign 指标收集器。
 * 指标采集是可选的，仅在实际使用时产生开销。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
public class FeignMetricsConfiguration {

    /**
     * 创建 Feign 指标收集器 Bean
     *
     * @param registry MeterRegistry 实例（由 Spring Boot 自动配置提供）
     * @return FeignMicrometerCollector 实例
     */
    @Bean
    public FeignMicrometerCollector feignMicrometerCollector(MeterRegistry registry) {
        return FeignMicrometerCollector.getOrCreate(registry);
    }
}
