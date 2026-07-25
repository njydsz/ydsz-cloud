package com.njydsz.common.ratelimit.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.ratelimit.aop.RateLimitAspect;
import com.njydsz.common.ratelimit.core.RateLimitManager;
import com.njydsz.common.ratelimit.metrics.RateLimitMetricsCollector;
import com.njydsz.common.ratelimit.properties.RateLimitProperties;
import com.njydsz.common.ratelimit.provider.ConfigRuleProvider;
import com.njydsz.common.ratelimit.spi.RateLimitRuleProvider;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 限流模块自动配置
 *
 * <p>启用条件：
 * <ul>
 *   <li>classpath 存在 {@link RateLimitProperties}</li>
 *   <li>{@code ydsz.ratelimit.enabled=true}（默认 true）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
@ConditionalOnClass(RateLimitManager.class)
@ConditionalOnProperty(prefix = "ydsz.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RateLimitRuleProvider rateLimitRuleProvider(RateLimitProperties properties) {
        log.info("Initializing rate limit rule provider, configured rules={}",
                properties.getRules() == null ? 0 : properties.getRules().size());
        return new ConfigRuleProvider(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitManager rateLimitManager(RateLimitRuleProvider ruleProvider,
                                              RateLimitProperties properties) {
        log.info("Initializing rate limit manager, mode={}", properties.getDefaultMode());
        return new RateLimitManager(ruleProvider, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(MeterRegistry.class)
    public RateLimitMetricsCollector rateLimitMetricsCollector(
            RateLimitProperties properties,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            RateLimitManager rateLimitManager) {
        if (!properties.isMetricsEnabled()) {
            log.info("Rate limit metrics disabled by configuration");
            return null;
        }
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            log.info("MeterRegistry not available, skip rate limit metrics");
            return null;
        }
        log.info("Initializing rate limit metrics collector");
        RateLimitMetricsCollector collector = new RateLimitMetricsCollector(registry);
        rateLimitManager.addListener(collector.asListener());
        return collector;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ydsz.ratelimit", name = "aop-enabled", havingValue = "true", matchIfMissing = true)
    public RateLimitAspect rateLimitAspect(RateLimitManager rateLimitManager) {
        log.info("Initializing rate limit AOP aspect");
        return new RateLimitAspect(rateLimitManager);
    }
}
