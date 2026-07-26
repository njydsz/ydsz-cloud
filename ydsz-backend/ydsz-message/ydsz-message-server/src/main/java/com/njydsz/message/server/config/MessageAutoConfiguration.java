package com.njydsz.message.server.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.message.server.metrics.MessageServiceMetrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * 消息模块自动装配。
 *
 * <p>P0-3: 通过 {@code @EnableConfigurationProperties} 注册
 * {@link MessageProperties} 和 {@link ChannelProperties}，
 * 不再依赖 {@code @Component} 注解。
 *
 * <p>P1.3.0 重构：RealtimePushService 已改为委托 common-socket 的
 * RealtimePushTemplate，不再需要在此手动注册；WebSocketConfig / WebSocketClusterConfig
 * 已由 common-socket 自动装配接管。
 *
 * <p>ChannelRouter 为 {@code @Component}，由组件扫描自动注册，无需在此 @Bean。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties({MessageProperties.class, ChannelProperties.class})
public class MessageAutoConfiguration {

    /**
     * P2-4: 注册消息服务可观测性指标 Bean。
     *
     * <p>从 @Component 改为 @Bean 注册，与项目其他模块的 Metrics 注册模式一致。
     * 当 classpath 中不存在 MeterRegistry 时不注册。
     *
     * @param meterRegistryProvider Micrometer 指标注册中心（可选注入）
     * @return MessageServiceMetrics 实例
     */
    @Bean
    @ConditionalOnMissingBean(MessageServiceMetrics.class)
    @ConditionalOnClass(MeterRegistry.class)
    public MessageServiceMetrics messageServiceMetrics(
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            // 降级使用 SimpleMeterRegistry（内存版）
            registry = new SimpleMeterRegistry();
        }
        return new MessageServiceMetrics(registry);
    }
}
