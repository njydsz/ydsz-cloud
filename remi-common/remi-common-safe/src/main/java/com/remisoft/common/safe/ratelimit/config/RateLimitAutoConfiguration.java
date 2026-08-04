package com.remisoft.common.safe.ratelimit.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.remisoft.common.safe.ratelimit.aop.RateLimitAspect;
import com.remisoft.common.safe.ratelimit.cluster.ClusterRateLimiter;
import com.remisoft.common.safe.ratelimit.cluster.RedisClusterRateLimiter;
import com.remisoft.common.safe.ratelimit.core.RateLimitManager;
import com.remisoft.common.safe.ratelimit.metrics.RateLimitMetricsCollector;
import com.remisoft.common.safe.ratelimit.properties.RateLimitProperties;
import com.remisoft.common.safe.ratelimit.provider.ConfigRuleProvider;
import com.remisoft.common.safe.ratelimit.spi.RateLimitRuleProvider;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 限流模块自动配置
 *
 * <p>启用条件：
 * <ul>
 *   <li>classpath 存在 {@link RateLimitManager}</li>
 *   <li>{@code remi.ratelimit.enabled=true}（默认 true）</li>
 * </ul>
 *
 * <p><b>注：</b>本类在 {@code AutoConfiguration.imports} 中注册，
 * 由 Spring Boot 4.x 自动装配机制加载。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(RateLimitProperties.class)
@ConditionalOnClass(RateLimitManager.class)
@ConditionalOnProperty(prefix = "remi.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitAutoConfiguration {

    /**
     * 注册限流规则提供方（基于配置文件）。
     *
     * <p>从 {@link RateLimitProperties} 读取规则，供 {@link RateLimitManager} 查询某资源/维度对应的限流阈值。
     * {@code @ConditionalOnMissingBean} 允许外部自定义规则源（如数据库/配置中心）覆盖。
     *
     * @param properties 限流配置（含规则列表）
     * @return 配置型规则提供方
     */
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

    /**
     * 注册限流核心管理器，编排规则查询与具体限流器。
     *
     * <p>聚合 {@link RateLimitRuleProvider}（规则）与可选的 {@link ClusterRateLimiter}（分布式限流）；
     * 集群限流器不可用时，CLUSTER 模式自动降级为本地限流。{@code @ConditionalOnMissingBean} 允许自定义覆盖。
     *
     * @param ruleProvider                规则提供方
     * @param properties                  限流配置（mode、keyPrefix 等）
     * @param clusterRateLimiterProvider  集群限流器（可选，Redis 不可用时为 null）
     * @return 限流管理器
     */
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

    /**
     * 注册限流指标采集器（可选）。
     *
     * <p>当 {@code remi.ratelimit.metrics-enabled=false} 或 classpath 无 {@code MeterRegistry} 时返回 null，
     * 即不采集指标，不影响限流主链路。注入后作为监听器挂到 {@link RateLimitManager}，采集 Counter/Timer。
     *
     * @param properties              限流配置（metrics 开关）
     * @param meterRegistryProvider   Micrometer 注册中心（可选）
     * @param rateLimitManager        限流管理器（挂载监听）
     * @return 指标采集器；未启用或缺失依赖时返回 null
     */
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

    /**
     * 注册方法级限流 AOP 切面（@RateLimit）。
     *
     * <p>默认随限流模块启用（{@code remi.ratelimit.aop-enabled=true}）。拦截标注 {@code @RateLimit} 的方法，
     * 命中限流时抛 {@code RateLimitExceededException}。{@code @ConditionalOnMissingBean} 允许自定义覆盖。
     *
     * @param rateLimitManager 限流管理器（提供决策）
     * @return 限流切面
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "remi.ratelimit", name = "aop-enabled", havingValue = "true", matchIfMissing = true)
    public RateLimitAspect rateLimitAspect(RateLimitManager rateLimitManager) {
        log.info("Initializing rate limit AOP aspect");
        return new RateLimitAspect(rateLimitManager);
    }
}
