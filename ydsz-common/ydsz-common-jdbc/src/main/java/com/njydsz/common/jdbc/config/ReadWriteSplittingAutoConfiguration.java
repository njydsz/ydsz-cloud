package com.njydsz.common.jdbc.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import com.njydsz.common.jdbc.datasource.DynamicRoutingDataSource;
import com.njydsz.common.jdbc.interceptor.ReadWriteSplittingInterceptor;
import com.njydsz.common.jdbc.monitor.ReadWriteSplittingMetrics;
import com.njydsz.common.jdbc.monitor.SlaveLatencyMonitor;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 自动读写分离配置类
 *
 * <p>当自研 {@link DynamicRoutingDataSource} 可用且配置启用时，注册 {@link ReadWriteSplittingInterceptor}
 * 作为 MyBatis 外层拦截器 Bean。Spring Boot 会自动将其注入到所有 SqlSessionFactory 中。
 *
 * <p>同时注册 {@link ReadWriteSplittingMetrics}，用于暴露读写分离路由指标到 Micrometer。
 *
 * <p>配置示例：
 * <pre>
 * ydsz:
 *   jdbc:
 *     read-write-splitting:
 *       enabled: true
 *       master-ds: master
 *       slave-ds-list: [slave1, slave2]
 *       load-balance-strategy: round-robin
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(DynamicRoutingDataSource.class)
@ConditionalOnProperty(prefix = "ydsz.jdbc.read-write-splitting", name = "enabled", havingValue = "true")
public class ReadWriteSplittingAutoConfiguration {

    /**
     * 注册读写分离监控指标收集器
     *
     * @param meterRegistry Micrometer 注册表
     * @return ReadWriteSplittingMetrics 实例
     */
    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    public ReadWriteSplittingMetrics readWriteSplittingMetrics(MeterRegistry meterRegistry) {
        return new ReadWriteSplittingMetrics(meterRegistry);
    }

    /**
     * 注册自动读写分离拦截器
     *
     * @param properties     读写分离配置
     * @param metrics        读写分离监控指标（可选，Micrometer 不可用时为 null）
     * @param latencyMonitor 从库延迟监控（可选，未启用延迟检测时为 null）
     * @return ReadWriteSplittingInterceptor 实例
     */
    @Bean
    public ReadWriteSplittingInterceptor readWriteSplittingInterceptor(ReadWriteSplittingProperties properties,
                                                                       ObjectProvider<ReadWriteSplittingMetrics> metrics,
                                                                       ObjectProvider<SlaveLatencyMonitor> latencyMonitor) {
        return new ReadWriteSplittingInterceptor(properties,
                metrics.getIfAvailable(),
                latencyMonitor.getIfAvailable());
    }
}
