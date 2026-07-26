package com.njydsz.common.safe.ratelimit.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.njydsz.common.safe.ratelimit.aop.RateLimitAspect;
import com.njydsz.common.safe.ratelimit.cluster.ClusterRateLimiter;
import com.njydsz.common.safe.ratelimit.cluster.RedisClusterRateLimiter;
import com.njydsz.common.safe.ratelimit.core.RateLimitManager;
import com.njydsz.common.safe.ratelimit.metrics.RateLimitMetricsCollector;
import com.njydsz.common.safe.ratelimit.properties.RateLimitProperties;
import com.njydsz.common.safe.ratelimit.provider.ConfigRuleProvider;
import com.njydsz.common.safe.ratelimit.spi.RateLimitRuleProvider;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 限流模块自动配置
 *
 * <p>启用条件：
 * <ul>
 *   <li>classpath 存在 {@link RateLimitManager}</li>
 *   <li>{@code ydsz.ratelimit.enabled=true}（默认 true）</li>
 * </ul>
 *
 * <p><b>注：</b>本类在 {@code AutoConfiguration.imports} 中注册，
 * 由 Spring Boot 4.x 自动装配机制加载。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
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

    /**
     * 集群限流器 Bean
     *
     * <p>当 classpath 中存在 {@link StringRedisTemplate} 时注入到 {@link RedisClusterRateLimiter}；
     * 否则返回 {@code null}，{@link RateLimitManager} 将在 CLUSTER 模式下降级为本地限流。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(StringRedisTemplate.class)
    public ClusterRateLimiter clusterRateLimiter(RateLimitProperties properties,
                                                   ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        return new RedisClusterRateLimiter(
                redisTemplate,
                properties.getClusterKeyPrefix(),
                properties.getFallbackOnError());
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitManager rateLimitManager(RateLimitRuleProvider ruleProvider,
                                              RateLimitProperties properties,
                                              ObjectProvider<ClusterRateLimiter> clusterRateLimiterProvider) {
        ClusterRateLimiter clusterLimiter = clusterRateLimiterProvider.getIfAvailable();
        log.info("Initializing rate limit manager, mode={}, clusterLimiter={}",
                properties.getDefaultMode(), clusterLimiter == null ? "null" : clusterLimiter.getClass().getSimpleName());
        return new RateLimitManager(ruleProvider, properties, clusterLimiter);
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
